package io.github.sorry4nothing.cevios.lager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.Background
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate

/** Ein Hinweis nach einer Aktion. Er bleibt, bis die Person ihn schliesst. */
enum class LiveNotice { ATTENDANCE_SAVED, REQUEST_SENT, REQUESTS_ACCEPTED, REQUESTS_DECLINED, DEADLINE_SET, DEADLINE_CLEARED, ATTENDANCE_RELOADED }

/**
 * «Im Lager»: Heute, Tagesseiten, Anwesenheit, Einkauf, Packliste, Notfall, Infos und eigene To-dos. Der Stand kommt vom Server und
 * bleibt im Arbeitsspeicher der Sitzung (kein lokaler Speicher). Der Server nimmt diese Objekte nicht über die Warteschlange an:
 * Jede Änderung geht direkt hinaus, ein Konflikt erscheint über [writer] im Konfliktbildschirm, ein anderer Fehler als Banner mit
 * «Nochmals versuchen». Haken (To-dos, Packliste, Einkauf) erscheinen sofort und springen zurück, wenn der Server sie nicht annimmt.
 *
 * Das Notfall-Fenster behält den zuletzt geladenen Stand, wenn das Netz fehlt: Die Nummern bleiben erreichbar, solange der Prozess
 * lebt. Nach dem Ende des Prozesses ohne Netz ist nichts da.
 */
class LiveViewModel(
    private val api: LiveApi,
    private val camp: CampViewModel,
    private val texts: CommsTexts,
    /** Die ID der eigenen Person (für den eigenen Dienst und die eigene Anwesenheit). */
    val selfId: String,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val campId: String? get() = openCamp.value

    val writer = DirectWriter(viewModelScope) { texts.fieldLabels() }

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie. */
    var rights: LiveRights = LiveRights.of(CampRole.READ_ONLY, true)

    private val _schedule = MutableStateFlow<ApiResult<Schedule>?>(null)
    val schedule: StateFlow<ApiResult<Schedule>?> = _schedule.asStateFlow()

    private val _today = MutableStateFlow<ApiResult<TodayView>?>(null)
    val today: StateFlow<ApiResult<TodayView>?> = _today.asStateFlow()

    private val _todos = MutableStateFlow<ApiResult<CampTodoList>?>(null)
    val todos: StateFlow<ApiResult<CampTodoList>?> = _todos.asStateFlow()

    private val _myWeek = MutableStateFlow<MyWeek?>(null)
    val myWeek: StateFlow<MyWeek?> = _myWeek.asStateFlow()

    private val _day = MutableStateFlow<ApiResult<DayPage>?>(null)
    val day: StateFlow<ApiResult<DayPage>?> = _day.asStateFlow()
    private var dayId: String? = null

    private val _days = MutableStateFlow<ApiResult<DayList>?>(null)
    val days: StateFlow<ApiResult<DayList>?> = _days.asStateFlow()

    private val _own = MutableStateFlow<ApiResult<AttendanceOwn>?>(null)
    val own: StateFlow<ApiResult<AttendanceOwn>?> = _own.asStateFlow()

    private val _draft = MutableStateFlow<Map<AttendanceKey, Attendance>>(emptyMap())

    /** Die Änderungen, die die Person gemacht und noch nicht gespeichert hat. */
    val draft: StateFlow<Map<AttendanceKey, Attendance>> = _draft.asStateFlow()

    private val _summary = MutableStateFlow<ApiResult<AttendanceSummary>?>(null)
    val summary: StateFlow<ApiResult<AttendanceSummary>?> = _summary.asStateFlow()

    private val _requests = MutableStateFlow<ApiResult<List<AttendanceRequest>>?>(null)
    val requests: StateFlow<ApiResult<List<AttendanceRequest>>?> = _requests.asStateFlow()

    private val _shopping = MutableStateFlow<ApiResult<List<ShopTotal>>?>(null)
    val shopping: StateFlow<ApiResult<List<ShopTotal>>?> = _shopping.asStateFlow()

    private val _onlyMine = MutableStateFlow(false)

    /** «Nur was ich einkaufe». */
    val onlyMine: StateFlow<Boolean> = _onlyMine.asStateFlow()

    private val _shopUndo = MutableStateFlow<ShopItem?>(null)

    /** Der Artikel, den die Person eben angelegt hat; «Rückgängig» nimmt ihn wieder von der Liste. */
    val shopUndo: StateFlow<ShopItem?> = _shopUndo.asStateFlow()

    private val _suggestions = MutableStateFlow<List<ShopSuggestion>>(emptyList())
    val suggestions: StateFlow<List<ShopSuggestion>> = _suggestions.asStateFlow()
    private var suggestionJob: kotlinx.coroutines.Job? = null

    private val _pack = MutableStateFlow<ApiResult<PackList>?>(null)
    val pack: StateFlow<ApiResult<PackList>?> = _pack.asStateFlow()

    private val _emergency = MutableStateFlow<ApiResult<EmergencyState>?>(null)

    /** Der zuletzt geladene Stand des Notfall-Fensters; er bleibt, wenn eine neue Anfrage scheitert. */
    val emergency: StateFlow<ApiResult<EmergencyState>?> = _emergency.asStateFlow()

    private val _emergencyStale = MutableStateFlow(false)

    /** Wahr, solange das Notfall-Fenster den alten Stand zeigt, weil die letzte Anfrage scheiterte (kein Netz). */
    val emergencyStale: StateFlow<Boolean> = _emergencyStale.asStateFlow()

    private val _infos = MutableStateFlow<ApiResult<List<InfoSection>>?>(null)
    val infos: StateFlow<ApiResult<List<InfoSection>>?> = _infos.asStateFlow()

    private val _notice = MutableStateFlow<LiveNotice?>(null)
    val notice: StateFlow<LiveNotice?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** Wahr, solange eine Aktion läuft (Knöpfe zeigen den Ladekreis). */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { camp.changes.collect(::onChange) }
    }

    fun open(id: String) {
        if (openCamp.value == id) return
        reset()
        openCamp.value = id
        // Der Notfall-Stand liegt bereit, bevor jemand ihn braucht (es gibt keinen lokalen Speicher, nur diesen Arbeitsspeicher).
        loadEmergency(silent = true)
    }

    fun close() {
        reset()
        openCamp.value = null
    }

    private fun reset() {
        _schedule.value = null; _today.value = null; _todos.value = null; _myWeek.value = null; _day.value = null; dayId = null; _days.value = null
        _own.value = null; _draft.value = emptyMap(); _summary.value = null; _requests.value = null
        _shopping.value = null; _onlyMine.value = false; _suggestions.value = emptyList(); _shopUndo.value = null
        _pack.value = null; _emergency.value = null; _emergencyStale.value = false; _infos.value = null
        _notice.value = null; _busy.value = false
        suggestionJob?.cancel()
        writer.clear()
    }

    private fun onChange(change: CampChange) {
        if (campId == null) return
        fun touches(vararg prefixes: String) = change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { t -> prefixes.any { t.startsWith(it) } }
        if (touches("tagesseite", "aufgabe", "schicht", "dienst")) {
            if (_today.value != null) loadToday(silent = true)
            if (dayId != null && _day.value != null) loadDay(dayId!!, silent = true)
        }
        if (touches("anwesenheit")) {
            if (_own.value != null) loadOwn(silent = true)
            if (_summary.value != null) loadSummary(silent = true)
            if (_requests.value != null) loadRequests(silent = true)
        }
        if (touches("einkauf") && _shopping.value != null) loadShopping(silent = true)
        if (touches("packliste") && _pack.value != null) loadPack(silent = true)
        if (touches("notfall", "info")) {
            loadEmergency(silent = true)
            if (_infos.value != null) loadInfos(silent = true)
        }
        if (change.reload && _days.value != null) loadDays(silent = true)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun <T> launchLoad(target: MutableStateFlow<ApiResult<T>?>, silent: Boolean, fetch: suspend (String) -> ApiResult<T>, after: (ApiResult<T>) -> Unit = {}) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) target.value = null
            val result = fetch(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || target.value == null) target.value = result
            after(result)
        }
    }

    /** Sendet eine Änderung ohne Version und zeigt einen Fehler mit «Nochmals versuchen». `onFail` räumt auf (Haken zurücksetzen, neu laden). */
    private fun <T> send(retry: () -> Unit, call: suspend (String) -> ApiResult<T>, onFail: (ApiResult.Failure) -> Unit = {}, onOk: (T) -> Unit = {}) {
        val id = campId ?: return
        writer.dismissError()
        _busy.value = true
        viewModelScope.launch {
            when (val result = call(id)) {
                is ApiResult.Ok -> {
                    _busy.value = false
                    onOk(result.value)
                }
                is ApiResult.Failure -> {
                    _busy.value = false
                    onFail(result)
                    writer.fail(result, retry)
                }
            }
        }
    }

    /** Ändert eine Zeile mit Version; ein Konflikt erscheint im Konfliktbildschirm. */
    private fun <T> submit(summary: String, fields: Map<String, JsonElement>, version: Long, call: suspend (String, JsonObject) -> ApiResult<T>, onCurrent: () -> Unit, onOk: (T) -> Unit = {}) {
        val id = campId ?: return
        writer.submit(summary, fields, version, call = { v -> call(id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) }, onCurrent = onCurrent, onOk = onOk)
    }

    /** Löscht eine Zeile mit Version. */
    private fun <T> remove(version: Long, call: suspend (String, Long) -> ApiResult<T>, onCurrent: () -> Unit, onOk: () -> Unit = {}) {
        val id = campId ?: return
        writer.submit("", emptyMap(), version, call = { v -> call(id, v) }, onCurrent = onCurrent) { onOk() }
    }

    // ---- Heute, Zeitplan, Meine Woche ----

    fun loadSchedule(silent: Boolean = false): Unit = launchLoad(_schedule, silent, api::schedule)

    fun loadToday(silent: Boolean = false): Unit = launchLoad(_today, silent, api::today)

    /** Die eigenen Dienste der nächsten sieben Tage («Mein Bereich» des Teams); fehlt das Recht, bleibt es leer. */
    fun loadMyWeek() {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            val result = api.myWeek(id)
            if (campId == id) _myWeek.value = (result as? ApiResult.Ok)?.value
        }
    }

    // ---- Eigene To-dos ----

    fun loadTodos(silent: Boolean = false): Unit = launchLoad(_todos, silent, api::todos)

    fun addTodo(text: String): Unit =
        send({ addTodo(text) }, { api.addTodo(it, text) }) { todo -> updateTodos { it + todo } }

    fun toggleTodo(todo: CampTodo) {
        val id = campId ?: return
        val done = !todo.done
        writer.dismissError()
        updateTodos { list -> list.map { if (it.id == todo.id) it.copy(done = done) else it } }
        viewModelScope.launch {
            val result = api.changeTodo(id, todo.id, buildJsonObject { put("erledigt", done) })
            if (result is ApiResult.Failure) {
                updateTodos { list -> list.map { if (it.id == todo.id) it.copy(done = !done) else it } }
                writer.fail(result) { toggleTodo(todo.copy(done = !done)) }
            }
        }
    }

    fun deleteTodo(todo: CampTodo): Unit =
        send({ deleteTodo(todo) }, { api.deleteTodo(it, todo.id) }) { updateTodos { list -> list.filterNot { it.id == todo.id } } }

    private fun updateTodos(change: (List<CampTodo>) -> List<CampTodo>) {
        _todos.update { state -> (state as? ApiResult.Ok)?.let { ApiResult.Ok(CampTodoList(change(it.value.todos))) } ?: state }
    }

    // ---- Tagesseiten ----

    fun loadDays(silent: Boolean = false): Unit = launchLoad(_days, silent, api::days)

    fun loadDay(lagertagId: String, silent: Boolean = false) {
        if (dayId != lagertagId) {
            dayId = lagertagId
            _day.value = null
        }
        launchLoad(_day, silent, { api.day(it, lagertagId) })
    }

    fun closeDay() {
        dayId = null
        _day.value = null
    }

    /** Setzt Tageschef und Notiz; gibt es noch keine Seite, legt der Server sie an. */
    fun changeDayPage(page: DayPage, fields: Map<String, JsonElement>, summary: String) {
        val lagertagId = page.lagertag.id
        val row = page.page
        if (row == null) {
            send({ changeDayPage(page, fields, summary) }, { api.createDayPage(it, lagertagId, JsonObject(fields)) }) { loadDay(lagertagId, silent = true) }
        } else {
            submit(summary, fields, row.version, { id, body -> api.changeDayPage(id, lagertagId, body) }, { loadDay(lagertagId) }) { loadDay(lagertagId, silent = true) }
        }
    }

    fun addBlock(lagertagId: String, kind: BlockKind, text: String): Unit =
        send({ addBlock(lagertagId, kind, text) }, { api.addBlock(it, lagertagId, kind, text) }) { loadDay(lagertagId, silent = true) }

    fun changeBlock(lagertagId: String, block: DayBlock, text: String): Unit =
        submit(block.kind?.wire.orEmpty(), mapOf("text" to JsonPrimitive(text.trim())), block.version, { id, body -> api.changeBlock(id, lagertagId, block.id, body) }, { loadDay(lagertagId) }) {
            loadDay(lagertagId, silent = true)
        }

    fun deleteBlock(lagertagId: String, block: DayBlock): Unit =
        remove(block.version, { id, v -> api.deleteBlock(id, lagertagId, block.copy(version = v)) }, { loadDay(lagertagId) }) { loadDay(lagertagId, silent = true) }

    // ---- Anwesenheit ----

    fun loadOwn(silent: Boolean = false): Unit = launchLoad(_own, silent, api::ownAttendance)

    fun loadSummary(silent: Boolean = false): Unit = launchLoad(_summary, silent, api::attendanceSummary)

    fun loadRequests(silent: Boolean = false): Unit = launchLoad(_requests, silent, api::openRequests)

    /** Ein Tipp auf ein Feld: Da, Frei, Offen. Der Entwurf bleibt bei der Person, bis sie speichert oder anfragt. */
    fun tapCell(key: AttendanceKey, current: Attendance) {
        _draft.update { draft -> draft + (key to current.next()) }
    }

    /** «Ganzer Tag»: alle Abschnitte eines Tages auf einen Wert, mit einem Tipp. */
    fun tapDay(dayId: String, sections: List<DaySection>, current: List<Attendance>) {
        val value = LiveRules.wholeDay(current)
        _draft.update { draft -> draft + sections.associate { AttendanceKey(dayId, it.id) to value } }
    }

    fun discardDraft() {
        _draft.value = emptyMap()
    }

    /** Trägt die offenen Felder ein, die die Person geändert hat (bis zur Frist); Felder, die eine Anfrage brauchen, bleiben im Entwurf. */
    fun saveAttendance() {
        val own = (_own.value as? ApiResult.Ok)?.value ?: return
        val plan = LiveRules.plan(own, _draft.value, LiveRules.deadlinePassed(own.deadline, now()))
        if (plan.direct.isEmpty()) return
        send(
            { saveAttendance() },
            { api.setOwnAttendance(it, plan.direct) },
            onFail = { failure -> if (failure.code == NEEDS_REQUEST_CODE) { loadOwn(silent = true); _notice.value = LiveNotice.ATTENDANCE_RELOADED } },
        ) {
            val saved = plan.direct.map { AttendanceKey(it.dayId, it.sectionId) }.toSet()
            _draft.update { draft -> draft.filterKeys { it !in saved } }
            _notice.value = LiveNotice.ATTENDANCE_SAVED
            loadOwn(silent = true)
        }
    }

    /** Stellt je geändertes Feld, das schon bestätigt ist (oder nach der Frist), eine Anfrage an die Lagerleitung. */
    fun requestAttendance(note: String?) {
        val own = (_own.value as? ApiResult.Ok)?.value ?: return
        val plan = LiveRules.plan(own, _draft.value, LiveRules.deadlinePassed(own.deadline, now()))
        if (plan.requests.isEmpty()) return
        send(
            { requestAttendance(note) },
            { api.requestAttendance(it, plan.requests, note) },
            onFail = { failure -> if (failure.code == NO_REQUEST_NEEDED_CODE || failure.code == REQUEST_OPEN_CODE) loadOwn(silent = true) },
        ) {
            val sent = plan.requests.map { AttendanceKey(it.dayId, it.sectionId) }.toSet()
            _draft.update { draft -> draft.filterKeys { it !in sent } }
            _notice.value = LiveNotice.REQUEST_SENT
            loadOwn(silent = true)
        }
    }

    /** Nimmt Anfragen an oder lehnt sie ab (die Lagerleitung). Jede nennt die gesehene Version. */
    fun decide(accept: Boolean, requests: List<AttendanceRequest>): Unit =
        send({ decide(accept, requests) }, { api.decideRequests(it, accept, requests) }, onFail = { loadRequests(silent = true) }) {
            _notice.value = if (accept) LiveNotice.REQUESTS_ACCEPTED else LiveNotice.REQUESTS_DECLINED
            loadRequests(silent = true)
            loadSummary(silent = true)
        }

    /** Setzt die Frist auf das Ende des Tages `date` (Zürcher Zeit); `null` hebt sie auf. */
    fun setDeadline(date: LocalDate?): Unit =
        send({ setDeadline(date) }, { api.setDeadline(it, date?.let(LiveRules::deadlineAt)) }) { result ->
            _own.update { state -> (state as? ApiResult.Ok)?.let { ApiResult.Ok(it.value.copy(deadline = result.deadline)) } ?: state }
            _notice.value = if (date == null) LiveNotice.DEADLINE_CLEARED else LiveNotice.DEADLINE_SET
        }

    // ---- Einkauf ----

    fun loadShopping(silent: Boolean = false): Unit = launchLoad(_shopping, silent, { api.shoppingTotals(it, _onlyMine.value) })

    fun setOnlyMine(only: Boolean) {
        if (_onlyMine.value == only) return
        _onlyMine.value = only
        loadShopping(silent = false)
    }

    /** Fragt die Vorschläge zum Anfang des getippten Namens (nach einer kurzen Pause, nur der letzte Aufruf zählt). */
    fun suggest(prefix: String) {
        val id = campId ?: return
        suggestionJob?.cancel()
        if (prefix.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch(Background()) {
            kotlinx.coroutines.delay(SUGGEST_DELAY_MS)
            val result = api.suggestions(id, prefix)
            if (campId == id) _suggestions.value = (result as? ApiResult.Ok)?.value?.suggestions.orEmpty()
        }
    }

    fun clearSuggestions() {
        suggestionJob?.cancel()
        _suggestions.value = emptyList()
    }

    fun addShopItem(name: String, amount: Double?, unit: String, buyerId: String?, onDone: () -> Unit = {}) {
        val body = buildJsonObject {
            put("name", name.trim())
            amount?.let { put("menge", it) }
            unit.takeIf { it.isNotBlank() }?.let { put("einheit", it.trim()) }
            buyerId?.let { put("einkaeuferPersonId", it) }
        }
        send({ addShopItem(name, amount, unit, buyerId, onDone) }, { api.addShopItem(it, body) }) { item ->
            _shopUndo.value = item
            loadShopping(silent = true)
            onDone()
        }
    }

    fun dismissShopUndo() {
        _shopUndo.value = null
    }

    /** Nimmt den eben angelegten Artikel wieder von der Liste. */
    fun undoShopAdd() {
        val item = _shopUndo.value ?: return
        _shopUndo.value = null
        send({ undoShopAdd() }, { api.deleteShopItem(it, item.id, item.version) }) { loadShopping(silent = true) }
    }

    /** Ändert den einen Artikel hinter einer Zeile (Name, Menge, Einheit, Einkaufende Person). */
    fun changeShopItem(line: ShopLine, fields: Map<String, JsonElement>, onDone: () -> Unit = {}) {
        val entry = line.entries.singleOrNull() ?: return
        submit(line.name, fields, entry.version, { id, body -> api.changeShopItem(id, entry.id, body) }, { loadShopping() }) { loadShopping(silent = true); onDone() }
    }

    /** Hakt eine Zeile ab (alle Artikel dahinter) oder macht sie wieder offen. Der Haken erscheint sofort und springt zurück, wenn es scheitert. */
    fun toggleShopLine(line: ShopLine) {
        val id = campId ?: return
        val done = !line.done
        writer.dismissError()
        updateShopping { list -> list.map { if (sameLine(it, line)) it.copy(done = done) else it } }
        viewModelScope.launch {
            var failure: ApiResult.Failure? = null
            for (entry in line.entries) {
                val result = api.changeShopItem(id, entry.id, buildJsonObject { put("version", entry.version); put("erledigt", done) })
                if (result is ApiResult.Failure) {
                    failure = result
                    break
                }
            }
            if (failure != null) {
                updateShopping { list -> list.map { if (sameLine(it, line)) it.copy(done = !done) else it } }
                if (failure.conflict != null) loadShopping(silent = true)
                writer.fail(failure) { toggleShopLine(line.copy(done = !done)) }
            } else {
                loadShopping(silent = true)
            }
        }
    }

    fun deleteShopLine(line: ShopLine): Unit =
        send({ deleteShopLine(line) }, { id ->
            var last: ApiResult<Unit> = ApiResult.Ok(Unit)
            for (entry in line.entries) {
                last = api.deleteShopItem(id, entry.id, entry.version)
                if (last is ApiResult.Failure) break
            }
            last
        }, onFail = { loadShopping(silent = true) }) { loadShopping(silent = true) }

    private fun sameLine(total: ShopTotal, line: ShopLine) = total.entries.map { it.id } == line.entries.map { it.id }

    private fun updateShopping(change: (List<ShopTotal>) -> List<ShopTotal>) {
        _shopping.update { state -> (state as? ApiResult.Ok)?.let { ApiResult.Ok(change(it.value)) } ?: state }
    }

    // ---- Packliste ----

    fun loadPack(silent: Boolean = false): Unit = launchLoad(_pack, silent, api::packList)

    /** Hakt einen Punkt ab oder macht ihn wieder offen. Der Haken erscheint sofort und springt zurück, wenn es scheitert. */
    fun togglePack(row: PackRow) {
        val id = campId ?: return
        val checked = !row.checked
        writer.dismissError()
        setPackRow(row.id, row.own) { checked }
        viewModelScope.launch {
            val result = if (row.own) {
                api.changeOwnPackItem(id, row.id, buildJsonObject { put("abgehakt", checked) }).let { if (it is ApiResult.Failure) it else ApiResult.Ok(Unit) }
            } else {
                api.setPackState(id, row.id, if (checked) PackState.CHECKED else PackState.OPEN)
            }
            if (result is ApiResult.Failure) {
                setPackRow(row.id, row.own) { !checked }
                writer.fail(result) { togglePack(row.copy(checked = !checked)) }
            }
        }
    }

    /** Nimmt einen Punkt der Vorlage aus der eigenen Liste; die Vorlage und die Listen der anderen bleiben. */
    fun removePackItem(row: PackRow): Unit =
        send({ removePackItem(row) }, { api.setPackState(it, row.id, PackState.REMOVED) }) { loadPack(silent = true) }

    /** Bringt einen entfernten Punkt der Vorlage zurück. */
    fun restorePackItem(itemId: String): Unit =
        send({ restorePackItem(itemId) }, { api.setPackState(it, itemId, PackState.OPEN) }) { loadPack(silent = true) }

    fun addOwnPackItem(section: String, text: String, onDone: () -> Unit = {}): Unit =
        send({ addOwnPackItem(section, text, onDone) }, { api.addOwnPackItem(it, section, text) }) { loadPack(silent = true); onDone() }

    fun deleteOwnPackItem(row: PackRow): Unit =
        send({ deleteOwnPackItem(row) }, { api.deleteOwnPackItem(it, row.id) }) { loadPack(silent = true) }

    private fun setPackRow(rowId: String, own: Boolean, checked: () -> Boolean) {
        _pack.update { state ->
            val ok = state as? ApiResult.Ok ?: return@update state
            val value = checked()
            val list = ok.value
            ApiResult.Ok(
                if (own) {
                    list.copy(own = list.own.map { if (it.id == rowId) it.copy(checked = value) else it })
                } else {
                    list.copy(items = list.items.map { if (it.id == rowId) it.copy(stateWire = (if (value) PackState.CHECKED else PackState.OPEN).wire) else it })
                },
            )
        }
    }

    // ---- Notfall ----

    /** Lädt das Notfall-Fenster. Scheitert die Anfrage, bleibt der letzte Stand stehen (und `emergencyStale` ist wahr). */
    fun loadEmergency(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent && _emergency.value !is ApiResult.Ok) _emergency.value = null
            val result = api.emergency(id)
            if (campId != id) return@launch
            when (result) {
                is ApiResult.Ok -> {
                    _emergency.value = ApiResult.Ok(EmergencyState(result.value, now()))
                    _emergencyStale.value = false
                }
                is ApiResult.Failure -> {
                    if (_emergency.value is ApiResult.Ok) _emergencyStale.value = true else _emergency.value = result
                }
            }
        }
    }

    // ---- Infos ----

    fun loadInfos(silent: Boolean = false): Unit = launchLoad(_infos, silent, api::infos)

    fun addInfo(title: String, text: String, onDone: () -> Unit = {}) {
        val body = buildJsonObject {
            put("titel", title.trim())
            text.takeIf { it.isNotBlank() }?.let { put("text", it.trim()) }
        }
        send({ addInfo(title, text, onDone) }, { api.addInfo(it, body) }) { loadInfos(silent = true); onDone() }
    }

    fun changeInfo(section: InfoSection, title: String, text: String, onDone: () -> Unit = {}) {
        val fields = buildMap<String, JsonElement> {
            if (title.trim() != section.title) put("titel", JsonPrimitive(title.trim()))
            if (text.trim() != section.text.orEmpty()) put("text", textField(text))
        }
        if (fields.isEmpty()) return onDone()
        submit(section.title, fields, section.version, { id, body -> api.changeInfo(id, section.id, body) }, { loadInfos() }) { loadInfos(silent = true); onDone() }
    }

    fun deleteInfo(section: InfoSection): Unit =
        remove(section.version, { id, v -> api.deleteInfo(id, section.copy(version = v)) }, { loadInfos() }) { loadInfos(silent = true) }

    /** Verschiebt einen Abschnitt um eine Stelle («Nach oben», «Nach unten»); die Liste zeigt es sofort. */
    fun moveInfo(list: List<InfoSection>, sectionId: String, delta: Int) {
        val moved = LiveRules.moved(list, sectionId, delta)
        if (moved == list) return
        _infos.value = ApiResult.Ok(moved)
        send({ moveInfo(list, sectionId, delta) }, { api.reorderInfos(it, moved) }, onFail = { loadInfos(silent = true) }) { loadInfos(silent = true) }
    }

    private companion object {
        const val SUGGEST_DELAY_MS = 250L
        const val NEEDS_REQUEST_CODE = "ANFRAGE_NOETIG"
        const val NO_REQUEST_NEEDED_CODE = "KEINE_ANFRAGE_NOETIG"
        const val REQUEST_OPEN_CODE = "ANFRAGE_OFFEN"
    }
}
