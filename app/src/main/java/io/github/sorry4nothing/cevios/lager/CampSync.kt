package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Eine Nachricht des Ereignisstroms (`event`, `id`, `data`). Die Daten tragen nie Inhalte, nur die Folgenummer und Objekttypen. */
data class SseMessage(val event: String, val id: Long?, val data: String)

/** Liest Server-Sent Events Zeile für Zeile; eine leere Zeile schliesst eine Nachricht ab, Kommentarzeilen (`:`) sind Herzschläge. */
class SseParser {
    private var event = ""
    private var id: Long? = null
    private val data = StringBuilder()

    /** Gibt die Nachricht zurück, wenn `line` sie abschliesst. */
    fun feed(line: String): SseMessage? {
        if (line.isEmpty()) {
            val message = if (data.isNotEmpty() || event.isNotEmpty()) SseMessage(event.ifEmpty { "message" }, id, data.toString()) else null
            event = ""
            data.setLength(0)
            return message
        }
        if (line.startsWith(":")) return null
        val name = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (name) {
            "event" -> event = value
            "id" -> id = value.toLongOrNull() ?: id
            "data" -> { if (data.isNotEmpty()) data.append('\n'); data.append(value) }
        }
        return null
    }
}

@Serializable
private data class EventData(val bis: Long? = null)

/** Was nach dem Abfragen des Feeds neu ist: die Objekttypen und die Zahl der Einträge; bei `reload` laden die Module ihre Listen neu. */
data class CampChange(val seq: Long, val types: Set<String>, val count: Int, val reload: Boolean = false) {
    companion object {
        /** Kein Objekttyp des Servers: Die Warteschlange hat Änderungen gesendet, die Module laden ihre Listen neu. */
        const val QUEUE_SENT = "_gesendet"
    }
}

sealed interface PullResult {
    /** `change` ist `null`, wenn nichts Neues kam (Feed leer). */
    data class Done(val change: CampChange?) : PullResult

    /** Der Stand war zu alt (410): Die Module laden ihre Listen neu, die Folgenummer ist `seq`. */
    data class Reload(val seq: Long) : PullResult

    data class Failed(val error: ApiError, val retryAfterSeconds: Int? = null) : PullResult
}

/** Wie eine Verbindung des Ereignisstroms endete. */
sealed interface ListenResult {
    /** Der Server hat die Verbindung beendet (`ende`) oder sie ist abgerissen ohne Fehler: sofort neu verbinden. */
    data object Ended : ListenResult

    data class Failed(val error: ApiError, val retryAfterSeconds: Int? = null) : ListenResult
}

/**
 * Hält den Stand eines Lagers aktuell: Der Änderungsfeed liefert, was neu ist, der Ereignisstrom sagt nur «es gibt Neues». Die
 * Folgenummer lebt nur hier, im Arbeitsspeicher. Kein Inhalt kommt aus dem Strom oder dem Feed; die Module holen ihn über ihre
 * eigenen Routen, damit die Rechte der Rolle dort gelten.
 */
class CampSync(private val api: CampApi, private val campId: String) {
    /** Die letzte verarbeitete Folgenummer; `null`, solange noch kein Stand geholt ist. */
    var seq: Long? = null
        private set

    private val json = Json { ignoreUnknownKeys = true }

    /** Holt den Stand des Lagers (Folgenummer und lesbare Objekttypen) und setzt die Folgenummer darauf. */
    suspend fun baseline(): ApiResult<FeedState> = api.feedState(campId).also { if (it is ApiResult.Ok) seq = it.value.seq }

    /** Fragt den Feed ab der letzten Folgenummer, Seite für Seite, bis nichts mehr folgt. Bei 410 holt es den Stand neu. */
    suspend fun pull(): PullResult {
        var current = seq ?: return when (val state = baseline()) {
            is ApiResult.Ok -> PullResult.Reload(state.value.seq)
            is ApiResult.Failure -> PullResult.Failed(state.error, state.retryAfterSeconds)
        }
        val types = linkedSetOf<String>()
        var count = 0
        while (true) {
            when (val page = api.feed(campId, current)) {
                is ApiResult.Failure ->
                    return if (page.error == ApiError.FEED_RELOAD) {
                        when (val state = baseline()) {
                            is ApiResult.Ok -> PullResult.Reload(state.value.seq)
                            is ApiResult.Failure -> PullResult.Failed(state.error, state.retryAfterSeconds)
                        }
                    } else {
                        PullResult.Failed(page.error, page.retryAfterSeconds)
                    }
                is ApiResult.Ok -> {
                    page.value.entries.forEach { types += it.type }
                    count += page.value.entries.size
                    current = page.value.until
                    seq = current
                    if (!page.value.more) return PullResult.Done(if (count == 0) null else CampChange(current, types, count))
                }
            }
        }
    }

    /**
     * Eine Verbindung des Ereignisstroms: Bei `bereit` und `aenderung` mit einer Folgenummer über der eigenen ruft sie `onNews`
     * (dort fragt die App den Feed ab), bei `neu-laden` holt sie den Stand neu und meldet es mit `onReload`. Kehrt zurück, wenn die
     * Verbindung endet.
     */
    suspend fun listen(onNews: suspend () -> Unit, onReload: suspend () -> Unit): ListenResult {
        val parser = SseParser()
        var ended = false
        val result = api.events(campId, seq) { line ->
            if (ended) return@events
            val message = parser.feed(line) ?: return@events
            val bis = runCatching { json.decodeFromString<EventData>(message.data).bis }.getOrNull()
            when (message.event) {
                "bereit", "aenderung" -> if (bis == null || bis > (seq ?: -1)) onNews()
                "neu-laden" -> { baseline(); onReload() }
                "ende" -> ended = true
            }
        }
        return when (result) {
            is ApiResult.Ok -> ListenResult.Ended
            is ApiResult.Failure -> ListenResult.Failed(result.error, result.retryAfterSeconds)
        }
    }
}
