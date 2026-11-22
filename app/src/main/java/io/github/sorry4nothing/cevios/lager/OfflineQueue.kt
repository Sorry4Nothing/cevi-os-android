package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Wo eine Änderung steht: wartet aufs Senden, gesendet, mit Konflikt (neuere Version) oder abgelehnt. */
enum class ChangeState { WAITING, SENT, CONFLICT, REJECTED }

/**
 * Eine Änderung, die ohne Netz entstanden ist. `clientOpId` bleibt bei jeder Wiederholung gleich: Der Server schreibt sie dann nur
 * einmal. `summary` ist der Satz für die Liste («Aufgabe ‹Zelte zählen› erledigt»), `fieldLabels` nennt die Felder der Nutzlast in
 * Worten für den Konfliktdialog. Beides bleibt nur im Arbeitsspeicher.
 */
data class QueuedChange(
    val clientOpId: String,
    val type: String,
    val payload: JsonObject,
    val summary: String,
    val createdAt: Long,
    val fieldLabels: Map<String, String> = emptyMap(),
    val state: ChangeState = ChangeState.WAITING,
    /** Bei CONFLICT `VERSION_KONFLIKT`, bei REJECTED der Code des Servers (zum Beispiel `KEINE_BERECHTIGUNG`). */
    val code: String? = null,
    /** Nur bei SENT: IDs und Versionen, die der Server geschrieben hat (damit eine Ansicht die neue Version kennt, bevor sie neu lädt). */
    val result: JsonObject? = null,
    /** Nur bei CONFLICT: der aktuelle Stand der Zeile in der Ansicht der Rolle. */
    val current: JsonObject? = null,
    /** Nur bei CONFLICT: die Felder, die jetzt anders stehen. */
    val differing: List<String> = emptyList(),
)

/** Alle Änderungen eines Lagers, ob das Netz fehlt und ob gerade gesendet wird. */
data class QueueState(val changes: List<QueuedChange> = emptyList(), val offline: Boolean = false, val sending: Boolean = false) {
    val waiting: Int get() = changes.count { it.state == ChangeState.WAITING }

    /** Konflikte und Ablehnungen verschwinden nie still: Sie bleiben, bis die Person entschieden hat. */
    val needsDecision: List<QueuedChange> get() = changes.filter { it.state == ChangeState.CONFLICT || it.state == ChangeState.REJECTED }
}

/** Wo die Warteschlange liegt. Heute nur im Arbeitsspeicher: Die dauerhafte Ablage wartet auf den Entscheid des Nutzers. */
interface QueueStore {
    fun load(campId: String): List<QueuedChange>

    fun save(campId: String, changes: List<QueuedChange>)
}

/** Arbeitsspeicher der Sitzung: weg mit dem Prozess und beim Abmelden. */
class MemoryQueueStore : QueueStore {
    private val data = mutableMapOf<String, List<QueuedChange>>()

    override fun load(campId: String): List<QueuedChange> = data[campId].orEmpty()

    override fun save(campId: String, changes: List<QueuedChange>) {
        data[campId] = changes
    }
}

/** Wer die Änderungen eines Lagers entgegennimmt: der Server (`CampApi`) oder in Tests ein Ersatz. */
fun interface QueueSender {
    suspend fun sendQueue(id: String, operations: List<QueueOperation>): ApiResult<QueueResponse>
}

/** Ergebnis eines Sendeversuchs; `retryAfterSeconds` nennt bei zu vielen Anfragen die Wartezeit. */
sealed interface FlushResult {
    data object Idle : FlushResult
    data object Sent : FlushResult
    data class Failed(val error: ApiError, val retryAfterSeconds: Int? = null) : FlushResult
}

/**
 * Sammelt Änderungen eines Lagers und schickt sie über `POST /api/v1/lager/{id}/aenderungen`. Eine Operation läuft nie doppelt:
 * Die `clientOpId` bleibt, bis der Server geantwortet hat. Wer nach einem Konflikt «Meine behalten» wählt, bekommt eine neue
 * `clientOpId` und die aktuelle `version` (so verlangt es der Server).
 */
class OfflineQueue(
    private val campId: String,
    private val store: QueueStore,
    private val api: QueueSender,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(QueueState(store.load(campId)))
    val state: StateFlow<QueueState> = _state.asStateFlow()
    private val lock = Mutex()

    private fun update(block: (List<QueuedChange>) -> List<QueuedChange>) {
        _state.update { it.copy(changes = block(it.changes).also { changes -> store.save(campId, changes) }) }
    }

    /** Nimmt eine Änderung auf; sie wartet, bis [flush] sie sendet. */
    fun enqueue(type: String, payload: JsonObject, summary: String, fieldLabels: Map<String, String> = emptyMap()): QueuedChange {
        val change = QueuedChange(newId(), type, payload, summary, now(), fieldLabels)
        update { it + change }
        return change
    }

    /**
     * Ändert eine Änderung, die noch wartet (zum Beispiel ein zweiter Tipp auf denselben Status): Sie geht mit der neuen Nutzlast
     * und derselben `clientOpId` weg. Eine Änderung, die schon unterwegs oder entschieden ist, bleibt wie sie ist (`false`).
     */
    fun revise(clientOpId: String, payload: JsonObject, summary: String): Boolean {
        // Während des Sendens kann die Nutzlast schon unterwegs sein: Dann bleibt sie, wie sie ist.
        if (_state.value.sending || _state.value.changes.none { it.clientOpId == clientOpId && it.state == ChangeState.WAITING }) return false
        update { all -> all.map { if (it.clientOpId == clientOpId && it.state == ChangeState.WAITING) it.copy(payload = payload, summary = summary) else it } }
        return true
    }

    /** Nimmt eine Änderung zurück, die noch wartet und nicht unterwegs ist (die Aufgabe, die sie anlegen wollte, wurde gelöscht). */
    fun cancel(clientOpId: String): Boolean {
        if (_state.value.sending || _state.value.changes.none { it.clientOpId == clientOpId && it.state == ChangeState.WAITING }) return false
        update { all -> all.filterNot { it.clientOpId == clientOpId } }
        return true
    }

    /** Sendet alle wartenden Änderungen, 100 je Anfrage, der Reihe nach. Gleichzeitige Aufrufe warten aufeinander. */
    suspend fun flush(): FlushResult = lock.withLock {
        var sentAny = false
        while (true) {
            val batch = _state.value.changes.filter { it.state == ChangeState.WAITING }.take(MAX_BATCH)
            if (batch.isEmpty()) break
            _state.update { it.copy(sending = true) }
            // Auch bei einer Ausnahme (oder beim Abbruch) darf «sending» nicht stehen bleiben: Sonst liessen sich wartende Änderungen nie mehr ändern.
            val result = try {
                api.sendQueue(campId, batch.map { QueueOperation(it.clientOpId, it.type, it.payload) })
            } finally {
                _state.update { it.copy(sending = false) }
            }
            when (result) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(offline = false) }
                    apply(batch, result.value.results)
                    sentAny = true
                    // Eine Antwort ohne Ergebnis für eine Operation wiederholt die Anfrage nicht endlos: Sie bleibt wartend.
                    val ids = batch.map { it.clientOpId }.toSet()
                    if (_state.value.changes.any { it.clientOpId in ids && it.state == ChangeState.WAITING }) {
                        return@withLock FlushResult.Failed(ApiError.UNKNOWN)
                    }
                }
                is ApiResult.Failure -> {
                    when (result.error) {
                        ApiError.NETWORK -> _state.update { it.copy(offline = true) }
                        // Das Lager ist zu: Keine dieser Änderungen kommt mehr an, die Person soll es sehen.
                        ApiError.LAGER_CLOSED -> reject(batch.map { it.clientOpId }, "LAGER_GESCHLOSSEN")
                        ApiError.NOT_FOUND -> reject(batch.map { it.clientOpId }, "NICHT_GEFUNDEN")
                        else -> Unit
                    }
                    val gone = result.error == ApiError.LAGER_CLOSED || result.error == ApiError.NOT_FOUND
                    return@withLock if (gone) FlushResult.Sent else FlushResult.Failed(result.error, result.retryAfterSeconds)
                }
            }
        }
        if (sentAny) FlushResult.Sent else FlushResult.Idle
    }

    private fun reject(ids: List<String>, code: String) =
        update { all -> all.map { if (it.clientOpId in ids) it.copy(state = ChangeState.REJECTED, code = code) else it } }

    private fun apply(batch: List<QueuedChange>, results: List<QueueResult>) {
        val byId = results.associateBy { it.clientOpId }
        update { all ->
            all.map { change ->
                val result = byId[change.clientOpId]?.takeIf { r -> batch.any { it.clientOpId == r.clientOpId } } ?: return@map change
                when (result.status) {
                    "ok" -> change.copy(state = ChangeState.SENT, code = null, result = result.result)
                    "konflikt" -> change.copy(
                        state = ChangeState.CONFLICT,
                        code = result.code ?: "VERSION_KONFLIKT",
                        current = result.current,
                        differing = result.differing,
                    )
                    else -> change.copy(state = ChangeState.REJECTED, code = result.code)
                }
            }
        }
    }

    /**
     * «Meine behalten»: schickt die Änderung noch einmal, mit neuer `clientOpId` und der aktuellen `version` aus dem Stand, den der
     * Server mitgeliefert hat.
     */
    fun keepMine(clientOpId: String) = update { all ->
        all.map { change ->
            if (change.clientOpId != clientOpId || change.state != ChangeState.CONFLICT) return@map change
            val version = change.current?.get("version")
            val payload = if (version is JsonPrimitive) JsonObject(change.payload + ("version" to version)) else change.payload
            change.copy(clientOpId = newId(), payload = payload, state = ChangeState.WAITING, code = null, current = null, differing = emptyList())
        }
    }

    /** «Aktuelle übernehmen» und «Verwerfen»: Die Änderung fällt weg, der Stand des Servers gilt. */
    fun discard(clientOpId: String) = update { all -> all.filterNot { it.clientOpId == clientOpId && it.state != ChangeState.WAITING } }

    /** Räumt gesendete Änderungen aus der Liste, sobald die Person sie gesehen hat. */
    fun clearSent() = update { all -> all.filterNot { it.state == ChangeState.SENT } }

    private companion object {
        const val MAX_BATCH = 100
    }
}
