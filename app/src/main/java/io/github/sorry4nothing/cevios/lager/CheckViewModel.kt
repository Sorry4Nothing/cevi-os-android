package io.github.sorry4nothing.cevios.lager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.Background
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Ein Hinweis nach einer Aktion. Er bleibt, bis die Person ihn schliesst. */
enum class CheckNotice { POINT_ADDED, POINT_DELETED }

/**
 * Der Lager-Check: Der Server rechnet ihn bei jeder Anfrage aus den Daten des Lagers, die App hält nur die letzte Antwort im
 * Arbeitsspeicher der Sitzung (kein lokaler Speicher) und lädt neu, wenn der Feed etwas meldet, das den Check ändert. Eigene Punkte
 * gehen direkt hinaus (keine Warteschlangen-Operation, keine Idempotenz-Kennung im Vertrag): ohne Netz scheitert die Aktion sichtbar,
 * ein Konflikt erscheint über [writer] im Konfliktbildschirm.
 */
class CheckViewModel(
    private val api: CheckApi,
    private val camp: CampViewModel,
    labels: () -> Map<String, String>,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val campId: String? get() = openCamp.value

    val writer = DirectWriter(viewModelScope, labels)

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie. */
    var rights: CheckRights = CheckRights(read = false, edit = false)

    private val _data = MutableStateFlow<ApiResult<CheckData>?>(null)
    val data: StateFlow<ApiResult<CheckData>?> = _data.asStateFlow()

    private val _notice = MutableStateFlow<CheckNotice?>(null)
    val notice: StateFlow<CheckNotice?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { camp.changes.collect(::onChange) }
    }

    fun open(id: String) {
        if (openCamp.value == id) return
        reset()
        openCamp.value = id
    }

    fun close() {
        reset()
        openCamp.value = null
    }

    private fun reset() {
        _data.value = null; _notice.value = null; _busy.value = false
        writer.clear()
    }

    private fun onChange(change: CampChange) {
        if (campId == null || _data.value == null) return
        val touches = change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { t -> WATCHED.any(t::startsWith) }
        if (touches) load(silent = true)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** Lädt den Check und die eigenen Punkte; nur der Check ist Pflicht, fehlen die Punkte, fehlt nur dieser Teil. */
    fun load(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _data.value = null
            val result = coroutineScope {
                val check = async { api.check(id) }
                val points = async { api.points(id) }
                when (val c = check.await()) {
                    is ApiResult.Failure -> c
                    is ApiResult.Ok -> ApiResult.Ok(CheckData(c.value, (points.await() as? ApiResult.Ok)?.value?.points))
                }
            }
            if (campId != id) return@launch
            if (replacesState(result, silent, _data.value != null)) _data.value = result
        }
    }

    private fun <T> send(retry: () -> Unit, call: suspend (String) -> ApiResult<T>, onOk: (T) -> Unit) {
        val id = campId ?: return
        writer.dismissError()
        _busy.value = true
        viewModelScope.launch {
            val result = call(id)
            if (campId != id) return@launch
            _busy.value = false
            when (result) {
                is ApiResult.Ok -> onOk(result.value)
                is ApiResult.Failure -> writer.fail(result, retry)
            }
        }
    }

    fun addPoint(title: String, onDone: () -> Unit) {
        val text = CheckRules.validTitle(title) ?: return
        send({ addPoint(text, onDone) }, { api.addPoint(it, text) }) {
            _notice.value = CheckNotice.POINT_ADDED
            load(silent = true)
            onDone()
        }
    }

    fun setDone(point: CheckItem, done: Boolean) = change(point, "erledigt" to JsonPrimitive(done)) { api, id, v -> api.changePoint(id, point.id, v, done = done) }

    fun rename(point: CheckItem, title: String, onDone: () -> Unit) {
        val text = CheckRules.validTitle(title) ?: return
        change(point, "titel" to JsonPrimitive(text), onDone) { api, id, v -> api.changePoint(id, point.id, v, title = text) }
    }

    private fun change(point: CheckItem, field: Pair<String, JsonElement>, onDone: () -> Unit = {}, call: suspend (CheckApi, String, Long) -> ApiResult<CheckItem>) {
        val id = campId ?: return
        writer.dismissError()
        writer.submit(point.title, mapOf(field), point.version, call = { v -> call(api, id, v) }, onCurrent = { load(silent = true) }) { changed ->
            _data.update { state -> (state as? ApiResult.Ok)?.let { ok -> ApiResult.Ok(ok.value.copy(points = ok.value.points?.let { CheckRules.replace(it, changed) })) } ?: state }
            load(silent = true)
            onDone()
        }
    }

    fun deletePoint(point: CheckItem) {
        val id = campId ?: return
        writer.dismissError()
        writer.submit("", emptyMap(), point.version, call = { v -> api.deletePoint(id, point.id, v) }, onCurrent = { load(silent = true) }) {
            _notice.value = CheckNotice.POINT_DELETED
            load(silent = true)
        }
    }
}

// Objekttypen des Feeds, nach denen der Check neu rechnet (Namen des Servers sind nicht im Vertrag aufgezählt: Vorsilben).
private val WATCHED = listOf("aufgabe", "meilenstein", "packliste", "notfall", "kontakt", "adressbuch", "check")
