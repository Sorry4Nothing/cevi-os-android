package io.github.sorry4nothing.cevios.programme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** `null` heisst: wird geladen. Die Daten bleiben nur im Arbeitsspeicher. */
class ProgrammeViewModel(private val api: ProgrammeApi) : ViewModel() {
    private val _liste = MutableStateFlow<ApiResult<ProgrammListe>?>(null)
    val liste: StateFlow<ApiResult<ProgrammListe>?> = _liste.asStateFlow()

    private val _detail = MutableStateFlow<ApiResult<Programm>?>(null)
    val detail: StateFlow<ApiResult<Programm>?> = _detail.asStateFlow()

    private var detailJob: Job? = null

    init {
        listeLaden()
    }

    fun listeLaden() {
        _liste.value = null
        viewModelScope.launch {
            val heute = LocalDate.now(SCHWEIZ)
            _liste.value = api.liste(heute, heute.plusMonths(FENSTER_MONATE))
        }
    }

    fun detailLaden(id: String) {
        detailJob?.cancel()
        _detail.value = null
        detailJob = viewModelScope.launch { _detail.value = api.lesen(id) }
    }

    private companion object {
        const val FENSTER_MONATE = 6L
    }
}
