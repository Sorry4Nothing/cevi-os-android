package io.github.sorry4nothing.cevios.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Zustand beim Speichern einer Antwort («ja» oder «nein»). `sentAt` merkt sich pro Kind (ID) oder für die eigene
 * Anmeldung ([LEADER_KEY]) die Uhrzeit des letzten Sendens: «Gesendet 12:10».
 */
data class RsvpState(
    val running: Boolean = false,
    val error: ApiError? = null,
    val sentAt: Map<String, String> = emptyMap(),
)

const val LEADER_KEY = "leitung"

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** `null` heisst: wird geladen. Die Daten bleiben nur im Arbeitsspeicher. */
class ProgramsViewModel(private val api: ProgramsApi) : ViewModel() {
    private val _list = MutableStateFlow<ApiResult<ProgramList>?>(null)
    val list: StateFlow<ApiResult<ProgramList>?> = _list.asStateFlow()

    private val _detail = MutableStateFlow<ApiResult<Program>?>(null)
    val detail: StateFlow<ApiResult<Program>?> = _detail.asStateFlow()

    private val _rsvp = MutableStateFlow(RsvpState())
    val rsvp: StateFlow<RsvpState> = _rsvp.asStateFlow()

    private var detailJob: Job? = null
    private var openedId: String? = null

    init {
        loadList()
    }

    fun loadList() {
        _list.value = null
        viewModelScope.launch {
            val today = LocalDate.now(SWISS_ZONE)
            _list.value = api.list(today, today.plusMonths(WINDOW_MONTHS))
        }
    }

    fun loadDetail(id: String) {
        detailJob?.cancel()
        openedId = id
        _detail.value = null
        _rsvp.value = RsvpState()
        detailJob = viewModelScope.launch { _detail.value = api.read(id) }
    }

    fun rsvpChild(programId: String, childId: String, attending: Boolean) =
        save(programId, childId) { api.setAttendance(programId, childId, attending) }

    fun rsvpLeader(programId: String, attending: Boolean) =
        save(programId, LEADER_KEY) { api.setLeaderRsvp(programId, attending) }

    /**
     * Speichert und liest das Programm danach neu: Der Server ist die einzige Wahrheit über den Stand. Bei einem Fehler
     * bleibt der alte Stand sichtbar, die Pillen springen also zurück.
     */
    private fun save(programId: String, key: String, block: suspend () -> ApiResult<Unit>) {
        if (_rsvp.value.running) return
        val existing = _rsvp.value.sentAt
        _rsvp.value = RsvpState(running = true, sentAt = existing)
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Failure -> _rsvp.value = RsvpState(error = result.error, sentAt = existing)
                is ApiResult.Ok -> {
                    _rsvp.value = RsvpState(sentAt = existing + (key to TIME_FORMAT.format(LocalTime.now(SWISS_ZONE))))
                    val fresh = api.read(programId)
                    if (openedId == programId) _detail.value = fresh
                }
            }
        }
    }

    private companion object {
        const val WINDOW_MONTHS = 6L
    }
}
