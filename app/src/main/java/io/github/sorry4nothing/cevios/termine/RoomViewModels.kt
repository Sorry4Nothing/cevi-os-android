package io.github.sorry4nothing.cevios.termine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * «Raum anfragen» (Entwurf K2) und «Meine Raumanfragen» (K3). Die Eingabe bleibt im Arbeitsspeicher, bis sie gesendet ist; es gibt
 * keine Warteschlange: Die Anfrage geht nur mit Netz hinaus, weil der Server die Überschneidung prüft.
 */
class RoomsViewModel(
    private val api: TermineApi,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(SWISS_ZONE) },
) : ViewModel() {
    /** `problems` ist `null`, solange die Person noch nicht zu senden versucht hat: Vorher steht kein Fehler am Feld. */
    data class SendState(
        val running: Boolean = false,
        val error: ApiError? = null,
        val code: String? = null,
        val problems: Set<RoomProblem>? = null,
        /** Die ID der gesendeten Anfrage, solange die Erfolgsmeldung steht (für «Rückgängig»). */
        val sentId: String? = null,
        val undone: Boolean = false,
    )

    /** Was nach «Zurückziehen» oder «Absagen» in der Liste steht. */
    enum class ListNotice { WITHDRAWN, CANCELLED, FAILED, NOT_POSSIBLE }

    data class ListState(val cancelling: String? = null, val notice: ListNotice? = null)

    private val _rooms = MutableStateFlow<ApiResult<RoomPage>?>(null)
    val rooms: StateFlow<ApiResult<RoomPage>?> = _rooms.asStateFlow()

    private val _mine = MutableStateFlow<ApiResult<RoomRequestPage>?>(null)
    val mine: StateFlow<ApiResult<RoomRequestPage>?> = _mine.asStateFlow()

    private val _draft = MutableStateFlow(RoomDraft())
    val draft: StateFlow<RoomDraft> = _draft.asStateFlow()

    private val _send = MutableStateFlow(SendState())
    val send: StateFlow<SendState> = _send.asStateFlow()

    private val _list = MutableStateFlow(ListState())
    val list: StateFlow<ListState> = _list.asStateFlow()

    /** Lädt die Räume neu und zeigt bis dahin das Skelett. */
    fun reloadRooms() {
        _rooms.value = null
        fetchRooms()
    }

    private fun fetchRooms() {
        viewModelScope.launch {
            val result = api.rooms()
            _rooms.value = result
            // Gibt es nur einen Raum, ist er gewählt: Die Person muss nichts ankreuzen, was nicht zur Wahl steht.
            if (result is ApiResult.Ok) result.value.rooms.singleOrNull()?.let { only -> _draft.update { if (it.roomId == null) it.copy(roomId = only.id) else it } }
        }
    }

    /** Lädt die eigenen Anfragen neu und zeigt bis dahin das Skelett. */
    fun reloadMine() {
        _mine.value = null
        _list.value = ListState()
        refreshMine()
    }

    /** Lädt still nach: Die Liste bleibt stehen, bis die neue da ist. */
    private fun refreshMine() {
        viewModelScope.launch { _mine.value = api.myRoomRequests() }
    }

    fun edit(change: (RoomDraft) -> RoomDraft) {
        _draft.update(change)
        // Wer nach einem Fehler weitertippt, sieht die Meldung am Feld verschwinden, sobald es stimmt.
        _send.update { state -> state.problems?.let { state.copy(problems = roomProblems(_draft.value, now())) } ?: state }
    }

    fun submit() {
        if (_send.value.running) return
        val draft = _draft.value
        val problems = roomProblems(draft, now())
        if (problems.isNotEmpty()) {
            _send.value = SendState(problems = problems)
            return
        }
        val roomId = draft.roomId ?: return
        val body = roomRequestBody(draft, now()) ?: return
        _send.value = SendState(running = true, problems = emptySet())
        viewModelScope.launch {
            when (val result = api.requestRoom(roomId, body)) {
                is ApiResult.Failure -> {
                    _send.value = SendState(error = result.error, code = result.code, problems = emptySet())
                    // Der Raum ist nicht mehr da (inaktiv oder gelöscht): Die Wahl fällt weg, die Liste lädt still neu.
                    if (result.error == ApiError.NOT_FOUND) {
                        _draft.update { it.copy(roomId = null) }
                        fetchRooms()
                    }
                }
                is ApiResult.Ok -> {
                    _draft.value = RoomDraft()
                    _send.value = SendState(sentId = result.value.id)
                    refreshMine()
                }
            }
        }
    }

    /** «Rückgängig» nach dem Senden: zieht die Anfrage zurück. */
    fun undo() {
        val id = _send.value.sentId ?: return
        viewModelScope.launch {
            when (val result = api.cancelRoomRequest(id)) {
                is ApiResult.Failure -> _send.value = SendState(error = result.error, code = result.code, problems = emptySet())
                is ApiResult.Ok -> {
                    _send.value = SendState(undone = true)
                    refreshMine()
                }
            }
        }
    }

    /** Zieht eine offene Anfrage zurück oder sagt eine bestätigte Buchung ab (`confirmed`). */
    fun cancel(id: String, confirmed: Boolean) {
        if (_list.value.cancelling != null) return
        _list.value = ListState(cancelling = id)
        viewModelScope.launch {
            when (val result = api.cancelRoomRequest(id)) {
                is ApiResult.Ok -> {
                    _list.value = ListState(notice = if (confirmed) ListNotice.CANCELLED else ListNotice.WITHDRAWN)
                    refreshMine()
                }
                is ApiResult.Failure -> {
                    // 409 `STATUS_UNGUELTIG`: Inzwischen entschieden oder vorbei; die Liste zeigt den neuen Stand.
                    val stale = result.code == "STATUS_UNGUELTIG" || result.error == ApiError.NOT_FOUND
                    _list.value = ListState(notice = if (stale) ListNotice.NOT_POSSIBLE else ListNotice.FAILED)
                    if (stale) refreshMine()
                }
            }
        }
    }

    /** Schliesst die Meldung. Läuft gerade etwas, bleibt der Stand, damit die Antwort nicht ins Leere geht. */
    fun dismissNotice() {
        if (!_send.value.running) _send.value = SendState()
        if (_list.value.cancelling == null) _list.value = ListState()
    }
}
