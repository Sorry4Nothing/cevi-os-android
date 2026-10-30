package io.github.sorry4nothing.cevios.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.EditState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** `null` heisst: wird geladen. Die Daten bleiben nur im Arbeitsspeicher. */
class AttendanceViewModel(private val api: AttendanceApi) : ViewModel() {
    private val _overview = MutableStateFlow<ApiResult<AttendanceOverview>?>(null)
    val overview: StateFlow<ApiResult<AttendanceOverview>?> = _overview.asStateFlow()

    private val _save = MutableStateFlow(EditState())
    val save: StateFlow<EditState> = _save.asStateFlow()

    private var openedId: String? = null

    fun load(ageGroupId: String) {
        openedId = ageGroupId
        _overview.value = null
        _save.value = EditState()
        viewModelScope.launch {
            val result = api.overview(ageGroupId)
            if (openedId == ageGroupId) _overview.value = result
        }
    }

    fun clearError() {
        _save.value = EditState()
    }

    /**
     * Speichert den Stand einer Person zu einem Anlass (`present`: da, nicht da oder `null` zum Zurücknehmen) und liest die
     * Übersicht danach neu: Der Server ist die einzige Wahrheit, auch für die Zahlen. Bei einem Fehler bleibt der alte Stand.
     */
    fun record(ageGroupId: String, programId: String, personId: String, leader: Boolean, present: Boolean?) {
        if (_save.value.running) return
        _save.value = EditState(running = true)
        viewModelScope.launch {
            val result = if (leader) api.recordLeader(ageGroupId, programId, personId, present)
            else api.recordChild(ageGroupId, programId, personId, present)
            when (result) {
                is ApiResult.Failure -> _save.value = EditState(error = result.error)
                is ApiResult.Ok -> {
                    _save.value = EditState()
                    val fresh = api.overview(ageGroupId)
                    // Schlägt nur das Neulesen fehl, bleibt der zuletzt gezeigte Stand stehen.
                    if (openedId == ageGroupId && fresh is ApiResult.Ok) _overview.value = fresh
                }
            }
        }
    }
}
