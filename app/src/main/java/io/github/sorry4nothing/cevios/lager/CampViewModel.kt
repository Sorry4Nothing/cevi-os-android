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

    private val _news = MutableStateFlow<CampNews?>(null)

    /** Der Punkt an der Pille: `null`, solange der Stand fehlt (dann gilt der Zähler der Benachrichtigungen). */
    val news: StateFlow<CampNews?> = _news.asStateFlow()

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
    private val rightsReads = RightsReads()

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
                    launch(Background()) { refreshNews() }
                    result.value.camps.filter { !it.readOnly }.forEach { camp ->
                        launch(Background()) { refreshUnread(camp.id) }
                    }
                }
                // Eine stille Aktualisierung lässt den alten Stand stehen.
                is ApiResult.Failure -> if (!background || _camps.value == null) _camps.value = result
            }
        }
    }

    /** Liest den Neuigkeiten-Stand still neu; ein Fehler lässt den alten Punkt stehen. */
    suspend fun refreshNews() = withContext(Background()) {
        (api.news() as? ApiResult.Ok)?.let { _news.value = it.value }
        Unit
    }

    suspend fun refreshUnread(campId: String) {
        (api.unread(campId) as? ApiResult.Ok)?.let { count -> _unread.update { it + (campId to count.value.count) } }
    }

    /** Öffnet ein Lager: Detail laden, Warteschlange bereitstellen. */
    fun open(campId: String) {
        if (openedId != campId) {
            openedId = campId
            _detail.value = null
            _readable.value = null
            _lostAccess.value = null
            rightsReads.reset()
        }
        queueFor(campId)
        viewModelScope.launch {
            val result = api.read(campId)
            // Eine späte Antwort für ein früher geöffnetes Lager gibt dem jetzigen nicht seine Rechte.
            if (openedId == campId) _detail.value = result
        }
    }

    fun close() {
        openedId = null
        _detail.value = null
        _readable.value = null
        rightsReads.reset()
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

    /** Eine andere Stelle hat Aufgaben angelegt (zum Beispiel «Als Aufgabe» bei einer Erwähnung): Die Aufgabenliste lädt neu. */
    fun announceTasksChanged() {
        _changes.tryEmit(CampChange(0, setOf("aufgabe"), 0))
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
            val resend = launch {
                while (true) {
                    delay(RESEND_MS)
                    if (queue.state.value.waiting > 0) flushAndAnnounce(queue)
                }
            }
            var failures = 0
            var interrupted = false
            // Kehrt die Ansicht in den Vordergrund zurück, können die Rechte der Rolle anders sein.
            if (rightsReads.followStarted(campId)) launch { refreshRights(campId) }
            while (true) {
                val failure: Pair<ApiError, Int?>? = run {
                    val step = connectStep(sync.seq, interrupted)
                    // Der Stand ersetzt die Folgenummer nur beim ersten Mal; sonst ginge verloren, was seit der letzten Verbindung geschah.
                    when (val base = if (step == ConnectStep.BASELINE) sync.baseline() else api.feedState(campId)) {
                        is ApiResult.Failure -> return@run base.error to base.retryAfterSeconds
                        is ApiResult.Ok -> _readable.value = base.value.types.toSet()
                    }
                    _online.value = true
                    launch { refreshUnread(campId) }
                    launch { flushAndAnnounce(queue) }
                    // Scheitert das Nachziehen, bleibt die Unterbrechung offen: Die nächste Verbindung versucht es wieder.
                    if (step != ConnectStep.CATCH_UP || handlePull(campId, sync) !is PullResult.Failed) interrupted = false
                    val openedAt = System.nanoTime()
                    val result = sync.listen(
                        onNews = { handlePull(campId, sync) },
                        onReload = { announceReload(campId, sync.seq ?: 0) },
                    )
                    // Ein Strom, der gleich wieder endet, zählt als Fehler: Sonst wüchse die Pause nie und die App fragte im Takt von Sekunden.
                    val lasted = (System.nanoTime() - openedAt) / 1_000_000 >= STREAM_OK_MS
                    if (lasted) failures = 0
                    (result as? ListenResult.Failed)?.let { it.error to it.retryAfterSeconds }
                        ?: if (lasted) null else ApiError.UNKNOWN to null
                }
                if (failure == null) {
                    delay(RECONNECT_MS)
                    continue
                }
                val (error, retryAfter) = failure
                interrupted = true
                when (error) {
                    // coroutineScope wartet auf seine Kinder: Ohne Abbruch liefe die Sendeschleife weiter und fragte ein verlorenes Lager ab.
                    ApiError.NOT_FOUND, ApiError.FORBIDDEN -> {
                        _lostAccess.value = campId
                        resend.cancel()
                        return@coroutineScope
                    }
                    ApiError.NOT_SIGNED_IN -> {
                        resend.cancel()
                        return@coroutineScope
                    }
                    ApiError.NETWORK -> _online.value = false
                    else -> Unit
                }
                failures++
                delay(retryAfter?.let { it * 1000L } ?: (RECONNECT_MS shl failures.coerceAtMost(4)).coerceAtMost(MAX_BACKOFF_MS))
            }
        }
    }

    private suspend fun handlePull(campId: String, sync: CampSync): PullResult {
        val result = sync.pull()
        when (result) {
            is PullResult.Done -> {
                result.change?.let { _changes.tryEmit(it) }
                refreshUnread(campId)
            }
            is PullResult.Reload -> announceReload(campId, result.seq)
            // Das Netz fehlt oder der Server antwortet nicht: Der Strom meldet es mit seiner Verbindung.
            is PullResult.Failed -> if (result.error == ApiError.NETWORK) _online.value = false
        }
        return result
    }

    /** Die Module laden ihre Listen neu; auch die Rechte der Rolle können sich geändert haben. */
    private suspend fun announceReload(campId: String, seq: Long) {
        _changes.tryEmit(CampChange(seq, emptySet(), 0, reload = true))
        refreshRights(campId)
    }

    /** Liest die Rechte der Rolle still neu; ein Fehler lässt die bisherigen stehen. */
    private suspend fun refreshRights(campId: String) {
        val result = api.read(campId)
        if (openedId == campId && result is ApiResult.Ok) _detail.value = result
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
        const val STREAM_OK_MS = 10_000L
    }
}
