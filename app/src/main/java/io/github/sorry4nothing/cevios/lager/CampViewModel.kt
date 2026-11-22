package io.github.sorry4nothing.cevios.lager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.Background
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Der Lagerbereich einer Sitzung: meine Lager, das offene Lager mit Rolle, die Warteschlange und der Stand der Verbindung. Alles
 * bleibt im Arbeitsspeicher der Sitzung (Entscheid des Nutzers zum lokalen Speicher steht aus). `null` heisst: wird geladen.
 */
class CampViewModel(
    private val api: CampApi,
    private val store: QueueStore = MemoryQueueStore(),
) : ViewModel() {
    private val _camps = MutableStateFlow<ApiResult<List<Camp>>?>(null)
    val camps: StateFlow<ApiResult<List<Camp>>?> = _camps.asStateFlow()

    /** Ungelesene Benachrichtigungen je Lager (nur Lager, die man noch ändern kann). */
    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unread: StateFlow<Map<String, Int>> = _unread.asStateFlow()

    private val _detail = MutableStateFlow<ApiResult<Camp>?>(null)
    val detail: StateFlow<ApiResult<Camp>?> = _detail.asStateFlow()

    private val _readable = MutableStateFlow<Set<String>?>(null)

    private val _online = MutableStateFlow(true)

    /** Falsch, solange die Verbindung zum Server fehlt (Ereignisstrom oder Senden scheitert am Netz). */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _changes = MutableSharedFlow<CampChange>(extraBufferCapacity = 8)

    /** Meldet jede Änderung im offenen Lager; die Module holen ihre Daten dann selbst. */
    val changes: SharedFlow<CampChange> = _changes.asSharedFlow()

    /** Das Lager, das den Zugriff verloren hat (404 beim Ereignisstrom: Rolle entzogen oder Lager weg). */
    private val _lostAccess = MutableStateFlow<String?>(null)
    val lostAccess: StateFlow<String?> = _lostAccess.asStateFlow()

    private val _pushMissing = MutableStateFlow(false)

    /** Wahr, wenn das Ziel eines Pushes nicht (mehr) zu sehen ist. */
    val pushMissing: StateFlow<Boolean> = _pushMissing.asStateFlow()

    private val queues = mutableMapOf<String, OfflineQueue>()
    private var openedId: String? = null

    init {
        loadCamps()
    }

    fun loadCamps() {
        _camps.value = null
        fetchCamps(background = false)
    }

    /** Liest die Liste still neu (Wechsel des Bereichs, Rückkehr in die App). */
    fun refreshCamps() = fetchCamps(background = true)

    private fun fetchCamps(background: Boolean) {
        viewModelScope.launch(if (background) Background() else kotlin.coroutines.EmptyCoroutineContext) {
            val result = api.list()
            when (result) {
                is ApiResult.Ok -> {
                    _camps.value = ApiResult.Ok(result.value.camps)
                    result.value.camps.filter { !it.readOnly }.forEach { camp ->
                        launch(Background()) { refreshUnread(camp.id) }
                    }
                }
                // Eine stille Aktualisierung lässt den alten Stand stehen.
                is ApiResult.Failure -> if (!background || _camps.value == null) _camps.value = result
            }
        }
    }

    private suspend fun refreshUnread(campId: String) {
        (api.unread(campId) as? ApiResult.Ok)?.let { count -> _unread.update { it + (campId to count.value.count) } }
    }

    /** Öffnet ein Lager: Detail laden, Warteschlange bereitstellen. */
    fun open(campId: String) {
        if (openedId != campId) {
            openedId = campId
            _detail.value = null
            _readable.value = null
            _lostAccess.value = null
        }
        queueFor(campId)
        viewModelScope.launch { _detail.value = api.read(campId) }
    }

    fun close() {
        openedId = null
        _detail.value = null
        _readable.value = null
    }

    /** Die Objekttypen, die die Rolle im offenen Lager lesen darf (aus dem Feed); `null`, bis sie da sind. */
    val readableTypes: StateFlow<Set<String>?> = _readable.asStateFlow()

    fun queueFor(campId: String): OfflineQueue = queues.getOrPut(campId) { OfflineQueue(campId, store, api) }

    /**
     * Nimmt eine Änderung auf und schickt sie gleich los. Ohne Netz bleibt sie in der Warteschlange und geht mit der nächsten
     * Verbindung (oder mit «Jetzt senden») weg.
     */
    fun enqueue(campId: String, type: String, payload: JsonObject, summary: String, fieldLabels: Map<String, String> = emptyMap()) {
        queueFor(campId).enqueue(type, payload, summary, fieldLabels)
        sendNow(campId)
    }

    fun sendNow(campId: String) {
        viewModelScope.launch { flushAndAnnounce(queueFor(campId)) }
    }

    /** Sendet die Warteschlange; kam etwas an, melden die Module (über [changes]), dass sie neu laden sollen. */
    private suspend fun flushAndAnnounce(queue: OfflineQueue) {
        if (queue.flush() is FlushResult.Sent) {
            _online.value = true
            _changes.tryEmit(CampChange(0, setOf(CampChange.QUEUE_SENT), 0))
        }
    }

    /**
     * Hält das offene Lager aktuell, solange die Ansicht sichtbar ist (im Vordergrund): holt den Stand, hört auf den
     * Ereignisstrom, fragt bei Neuem den Feed ab und schickt wartende Änderungen. Bricht die Verbindung ab, versucht es mit
     * wachsenden Pausen erneut. Aufruf in einem `LaunchedEffect` mit `repeatOnLifecycle`; die Anfragen laufen im Hintergrund.
     */
    suspend fun follow(campId: String) = withContext(Background()) {
        coroutineScope {
            val sync = CampSync(api, campId)
            val queue = queueFor(campId)
            launch {
                while (true) {
                    delay(RESEND_MS)
                    if (queue.state.value.waiting > 0) flushAndAnnounce(queue)
                }
            }
            var failures = 0
            while (true) {
                val failure: Pair<ApiError, Int?>? = run {
                    when (val base = sync.baseline()) {
                        is ApiResult.Failure -> return@run base.error to base.retryAfterSeconds
                        is ApiResult.Ok -> _readable.value = base.value.types.toSet()
                    }
                    _online.value = true
                    failures = 0
                    launch { refreshUnread(campId) }
                    launch { flushAndAnnounce(queue) }
                    val result = sync.listen(
                        onNews = { handlePull(campId, sync) },
                        onReload = { _changes.tryEmit(CampChange(sync.seq ?: 0, emptySet(), 0, reload = true)) },
                    )
                    (result as? ListenResult.Failed)?.let { it.error to it.retryAfterSeconds }
                }
                if (failure == null) {
                    delay(RECONNECT_MS)
                    continue
                }
                val (error, retryAfter) = failure
                when (error) {
                    ApiError.NOT_FOUND, ApiError.FORBIDDEN, ApiError.SECOND_FACTOR_MISSING -> {
                        _lostAccess.value = campId
                        return@coroutineScope
                    }
                    ApiError.NOT_SIGNED_IN -> return@coroutineScope
                    ApiError.NETWORK -> _online.value = false
                    else -> Unit
                }
                failures++
                delay(retryAfter?.let { it * 1000L } ?: (RECONNECT_MS shl failures.coerceAtMost(4)).coerceAtMost(MAX_BACKOFF_MS))
            }
        }
    }

    private suspend fun handlePull(campId: String, sync: CampSync) {
        when (val result = sync.pull()) {
            is PullResult.Done -> {
                result.change?.let { _changes.tryEmit(it) }
                refreshUnread(campId)
            }
            is PullResult.Reload -> _changes.tryEmit(CampChange(result.seq, emptySet(), 0, reload = true))
            // Das Netz fehlt oder der Server antwortet nicht: Der Strom meldet es mit seiner Verbindung.
            is PullResult.Failed -> if (result.error == ApiError.NETWORK) _online.value = false
        }
    }

    /** Löst das Ziel eines Lager-Pushes auf (`typ` und `id` aus dem Push). Das Ziel gibt es nur, wenn die Person es sehen darf. */
    fun resolvePush(type: String, id: String, onFound: (PushResolution) -> Unit) {
        viewModelScope.launch {
            when (val result = api.resolve(type, id)) {
                is ApiResult.Ok -> onFound(result.value)
                is ApiResult.Failure -> if (result.error != ApiError.NOT_SIGNED_IN) _pushMissing.value = true
            }
        }
    }

    fun dismissPushMissing() {
        _pushMissing.value = false
    }

    private companion object {
        const val RESEND_MS = 15_000L
        const val RECONNECT_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
