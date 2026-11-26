package io.github.sorry4nothing.cevios.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Was der Bildschirm der obersten Seite zeigt. */
sealed interface InfoState {
    data object Loading : InfoState

    /** `stale`: Neu laden ging nicht, das ist der zuletzt geladene Text. */
    data class Content(val document: InfoDocument, val stale: Boolean = false) : InfoState
    data class Failed(val error: ApiError) : InfoState
}

/**
 * Die offenen Seiten (ein Link kann von einer Seite zur nächsten führen; Zurück geht eine Seite zurück) und ihr Text. Der zuletzt
 * geladene Text bleibt nur hier im Arbeitsspeicher; er ist öffentlich, aber nicht für später gedacht.
 */
class InfoViewModel(private val api: InfoApi) : ViewModel() {
    private val _stack = MutableStateFlow<List<InfoPage>>(emptyList())

    /** Die offenen Seiten, die oberste zuletzt; leer, wenn keine offen ist. */
    val stack: StateFlow<List<InfoPage>> = _stack.asStateFlow()

    private val _state = MutableStateFlow<InfoState>(InfoState.Loading)
    val state: StateFlow<InfoState> = _state.asStateFlow()

    private val cache = HashMap<InfoPage, InfoDocument>()
    private var job: Job? = null

    fun open(page: InfoPage) {
        _stack.value = _stack.value + page
        load(page)
    }

    /** Geht eine Seite zurück; gibt `false` zurück, wenn es keine Seite davor gibt (dann ist sie jetzt geschlossen). */
    fun back(): Boolean {
        val rest = _stack.value.dropLast(1)
        _stack.value = rest
        job?.cancel()
        val top = rest.lastOrNull() ?: return false
        load(top)
        return true
    }

    fun close() {
        job?.cancel()
        _stack.value = emptyList()
    }

    fun retry() {
        _stack.value.lastOrNull()?.let(::load)
    }

    private fun load(page: InfoPage) {
        job?.cancel()
        val cached = cache[page]
        _state.value = if (cached != null) InfoState.Content(cached) else InfoState.Loading
        job = viewModelScope.launch {
            val result = api.page(page)
            // Eine Antwort für eine Seite, die nicht mehr oben liegt, zählt nicht.
            val isTop = _stack.value.lastOrNull() == page
            when (result) {
                is ApiResult.Ok -> {
                    cache[page] = result.value
                    if (isTop) _state.value = InfoState.Content(result.value)
                }
                is ApiResult.Failure -> if (isTop) {
                    _state.value = if (cached != null) InfoState.Content(cached, stale = true) else InfoState.Failed(result.error)
                }
            }
        }
    }
}
