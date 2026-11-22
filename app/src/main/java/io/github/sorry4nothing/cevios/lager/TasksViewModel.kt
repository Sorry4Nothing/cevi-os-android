package io.github.sorry4nothing.cevios.lager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.Background
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Die Sätze der Warteschlange und die Feldnamen des Konfliktdialogs; sie stehen in `strings.xml`, der ViewModel kennt sie nicht. */
interface TaskTexts {
    fun created(title: String): String

    fun changed(title: String): String

    fun statusSet(title: String, status: TaskStatus): String

    fun deleted(title: String): String

    fun ressortQuiet(name: String, quiet: Boolean): String

    fun fieldLabels(): Map<String, String>
}

/** Was «Rückgängig» zurücknimmt; die Oberfläche macht den Satz daraus. */
enum class UndoKind { DONE, STATUS, ASSIGNED, DUE, RESSORT, DELETED }

/** Ein Hinweis mit «Rückgängig». Er bleibt, bis er geschlossen oder ersetzt wird (nichts verschwindet von selbst). */
class UndoNotice(val kind: UndoKind, val count: Int, val status: TaskStatus? = null, val undo: () -> Unit)

/** Eine Aktion mit Netz ist gescheitert; `retry` wiederholt sie. */
class ActionError(val error: ApiError, val retry: (() -> Unit)?, val code: String? = null)

/** Die erkannte Person, das erkannte Ressort einer Zeile der Schnelleingabe: gewählt, offen (noch zu wählen) oder keine. */
sealed interface QuickChoice<out T> {
    data object None : QuickChoice<Nothing>
    data class Chosen<T>(val value: T) : QuickChoice<T>

    /** Mehrdeutig oder unbekannt: Die Person wählt aus `candidates` oder entfernt die Angabe. `text` steht, wie sie es getippt hat. */
    data class Unresolved<T>(val text: String, val candidates: List<T>) : QuickChoice<T>
}

data class QuickItem(
    val line: String,
    val title: String,
    val person: QuickChoice<PersonCandidate>,
    val ressort: QuickChoice<RessortCandidate>,
    val due: String?,
    val dueText: String?,
    val titleError: Boolean,
)

/** Ein ähnliches Ergebnis: die neue Zeile `item` und die vorhandene Aufgabe. */
data class DuplicateAsk(val index: Int, val existing: DuplicateTask)

data class QuickState(
    val text: String = "",
    val items: List<QuickItem> = emptyList(),
    val recognizing: Boolean = false,
    /** Ohne Netz erkennt der Server nichts: Jede Zeile wird ein Titel. */
    val offline: Boolean = false,
    val saving: Boolean = false,
    val showErrors: Boolean = false,
    val duplicate: DuplicateAsk? = null,
    val error: ApiError? = null,
) {
    val unresolved: Boolean get() = items.any { it.person is QuickChoice.Unresolved || it.ressort is QuickChoice.Unresolved || it.titleError }
    val canSubmit: Boolean get() = items.isNotEmpty() && !saving
}

data class DetailState(
    val taskId: String,
    val details: ApiResult<TaskDetails>? = null,
    val history: ApiResult<TaskHistory>? = null,
)

/** Alles, was der Start zeigt. Teile, die die Rolle nicht lesen darf oder die gerade fehlen, sind `null`. */
data class StartData(
    val start: ApiResult<CampStart>? = null,
    val meeting: MeetingEntry? = null,
    val weather: Weather? = null,
    val today: CampToday? = null,
    val news: List<Notification>? = null,
)

/**
 * Aufgaben, Ressorts und Start eines Lagers. Der Stand kommt vom Server (nur im Arbeitsspeicher); jede Änderung geht durch die
 * Warteschlange des Lagers und liegt als `applyQueued` über der Liste, so sieht die Person sie sofort, auch ohne Netz.
 */
class TasksViewModel(
    private val api: TasksApi,
    private val camp: CampViewModel,
    private val me: TaskPerson,
    private val texts: TaskTexts,
    private val today: () -> LocalDate = ::todayInCamp,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val rawTasks = MutableStateFlow<ApiResult<List<CampTask>>?>(null)
    private val rawRessorts = MutableStateFlow<ApiResult<List<Ressort>>?>(null)

    private val _members = MutableStateFlow<List<TaskPerson>?>(null)

    /** Die Mitglieder des Lagers für die Personenauswahl (von der Einteilung geladen); `null`, solange sie fehlen. */
    val members: StateFlow<List<TaskPerson>?> = _members.asStateFlow()

    fun setMembers(list: List<TaskPerson>?) {
        _members.value = list
    }

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie, sobald das Lager geladen ist. */
    var rights: TaskRights = TaskRights.of(NO_PERMISSIONS)

    /** Die Aufgaben, wie sie die Person sehen soll: Stand des Servers mit den wartenden Änderungen darüber. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<ApiResult<List<CampTask>>?> = openCamp.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(rawTasks, rawRessorts, camp.queueFor(id).state) { list, ressorts, queue ->
                when (list) {
                    null -> null
                    is ApiResult.Failure -> list
                    is ApiResult.Ok -> {
                        val refs = ressortRefs(list.value, (ressorts as? ApiResult.Ok)?.value.orEmpty())
                        ApiResult.Ok(applyQueued(list.value, queue.changes, peopleMap(list.value), refs))
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ressorts: StateFlow<ApiResult<List<Ressort>>?> = rawRessorts.asStateFlow()

    private val _view = MutableStateFlow(TaskView.LIST)
    val view: StateFlow<TaskView> = _view.asStateFlow()
    private val _mineSort = MutableStateFlow(MineSort.DUE)
    val mineSort: StateFlow<MineSort> = _mineSort.asStateFlow()
    private val _onlyOverdue = MutableStateFlow(false)
    val onlyOverdue: StateFlow<Boolean> = _onlyOverdue.asStateFlow()

    /** `null`: keine Auswahl. Eine Menge (auch leer): Die Liste zeigt Häkchen zum Wählen (Entwurf L9). */
    private val _selection = MutableStateFlow<Set<String>?>(null)
    val selection: StateFlow<Set<String>?> = _selection.asStateFlow()

    private val _undo = MutableStateFlow<UndoNotice?>(null)
    val undo: StateFlow<UndoNotice?> = _undo.asStateFlow()

    private val _error = MutableStateFlow<ActionError?>(null)
    val error: StateFlow<ActionError?> = _error.asStateFlow()

    private val _quick = MutableStateFlow(QuickState())
    val quick: StateFlow<QuickState> = _quick.asStateFlow()
    private val _quickOpen = MutableStateFlow(false)
    val quickOpen: StateFlow<Boolean> = _quickOpen.asStateFlow()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()

    private val _bundle = MutableStateFlow<ApiResult<RessortBundle>?>(null)
    val ressortBundle: StateFlow<ApiResult<RessortBundle>?> = _bundle.asStateFlow()
    private var bundleId: String? = null

    private val _start = MutableStateFlow(StartData())
    val start: StateFlow<StartData> = _start.asStateFlow()

    /** Zählt jede erfolgreiche Aktualisierung der Liste; der Start lädt dann auch neu. */
    private val _reloads = MutableStateFlow(0)
    val reloads: StateFlow<Int> = _reloads.asStateFlow()

    private var quickJob: Job? = null
    private var tasksRequested = 0
    private var tasksApplied = 0
    private val duplicateMerge = DuplicateMerge()

    init {
        viewModelScope.launch { camp.changes.collect(::onChange) }
    }

    private val campId: String? get() = openCamp.value

    // ---- Lager öffnen ----

    fun open(id: String) {
        if (openCamp.value == id) return
        reset()
        openCamp.value = id
        loadTasks(silent = false)
    }

    fun close() {
        reset()
        openCamp.value = null
    }

    private fun reset() {
        quickJob?.cancel()
        rawTasks.value = null
        rawRessorts.value = null
        _selection.value = null
        _undo.value = null
        _error.value = null
        _quick.value = QuickState()
        duplicateMerge.clear()
        _quickOpen.value = false
        _detail.value = null
        _bundle.value = null
        bundleId = null
        _start.value = StartData()
        _view.value = TaskView.LIST
        _mineSort.value = MineSort.DUE
        _onlyOverdue.value = false
    }

    fun retryTasks() = loadTasks(silent = false)

    private fun loadTasks(silent: Boolean) {
        val id = campId ?: return
        val seq = ++tasksRequested
        // Nur was vor der Anfrage gesendet war, steht sicher in der Antwort; später Gesendetes bleibt über der Liste.
        val sentBefore = camp.queueFor(id).state.value.changes.filter { it.state == ChangeState.SENT }.map { it.clientOpId }.toSet()
        viewModelScope.launch(Background()) {
            if (!silent) rawTasks.value = null
            val result = api.listAll(id)
            // Eine ältere Antwort, die nach einer neueren eintrifft, überschreibt sie nicht.
            if (campId != id || seq < tasksApplied) return@launch
            tasksApplied = seq
            // Eine stille Aktualisierung lässt den alten Stand stehen, wenn sie scheitert.
            if (result is ApiResult.Ok || !silent || rawTasks.value == null) rawTasks.value = result
            if (result is ApiResult.Ok) {
                _reloads.update { it + 1 }
                // Alles, was die Person gesendet hat, steht jetzt in der Liste; die Anzeige braucht die Ebene darüber nicht mehr.
                camp.queueFor(id).clearSent(sentBefore)
            }
        }
    }

    private fun onChange(change: CampChange) {
        if (campId == null) return
        val tasksChanged = change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { it.startsWith("aufgabe") }
        if (tasksChanged) {
            loadTasks(silent = true)
            _detail.value?.let { loadDetail(it.taskId, silent = true) }
        }
        if (change.reload || change.types.any { it.startsWith("ressort") }) {
            if (rawRessorts.value != null) loadRessorts(silent = true)
            bundleId?.let { loadBundle(it, silent = true) }
        }
    }

    // ---- Ansicht ----

    fun setView(view: TaskView) {
        _view.value = view
    }

    fun setMineSort(sort: MineSort) {
        _mineSort.value = sort
    }

    fun setOnlyOverdue(only: Boolean) {
        _onlyOverdue.value = only
    }

    // ---- Auswahl ----

    fun startSelecting(first: String? = null) {
        _selection.value = if (first == null) emptySet() else setOf(first)
    }

    fun toggleSelected(id: String) {
        _selection.update { current -> current?.let { if (id in it) it - id else it + id } }
    }

    fun endSelecting() {
        _selection.value = null
    }

    // ---- Hinweise ----

    fun dismissUndo() {
        _undo.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    private fun fail(error: ApiError, retry: (() -> Unit)?) {
        _error.value = ActionError(error, retry)
    }

    // ---- Änderungen an Aufgaben (Warteschlange) ----

    private fun currentTasks(): List<CampTask> = (tasks.value as? ApiResult.Ok)?.value.orEmpty()

    private fun taskById(id: String) = currentTasks().firstOrNull { it.id == id }

    private fun queueOp(task: CampTask, type: String, payload: JsonObject, summary: String) {
        val id = campId ?: return
        val route = routeTaskChange(camp.queueFor(id), task, type, payload, summary) { camp.enqueue(id, type, payload, summary, texts.fieldLabels()) }
        when (route) {
            ChangeRoute.REVISED -> camp.sendNow(id)
            // Die Anlage ist unterwegs: Nichts geht still verloren, die Person bekommt es gesagt und wiederholt, sobald die Aufgabe da ist.
            ChangeRoute.BLOCKED -> _error.value = ActionError(ApiError.UNKNOWN, { retryQueueOp(task, type, payload, summary) }, CREATING_CODE)
            ChangeRoute.ENQUEUED, ChangeRoute.MERGED -> Unit
        }
    }

    /** Wiederholt eine blockierte Änderung an der Aufgabe, wie sie jetzt dasteht (nach der Anlage hat sie eine echte ID und Version). */
    private fun retryQueueOp(task: CampTask, type: String, payload: JsonObject, summary: String) {
        val now = currentTasks().firstOrNull { it.queuedId != null && it.queuedId == task.queuedId } ?: return
        queueOp(now, type, JsonObject(payload + ("id" to JsonPrimitive(now.id)) + ("version" to JsonPrimitive(now.version))), summary)
    }

    private fun changeFields(task: CampTask, fields: Map<String, JsonElement>) =
        queueOp(task, TaskOps.CHANGE, TaskPayloads.change(task, fields), texts.changed(task.title))

    /** Setzt den Status. `undoable`: zeigt «Rückgängig» mit dem alten Status. */
    fun setStatus(task: CampTask, status: TaskStatus, undoable: Boolean = true) {
        if (!rights.setStatus || task.status == status) return
        val before = task.status
        queueOp(task, TaskOps.STATUS, TaskPayloads.status(task, status), texts.statusSet(task.title, status))
        if (undoable) {
            _undo.value = UndoNotice(if (status == TaskStatus.DONE) UndoKind.DONE else UndoKind.STATUS, 1, status) {
                taskById(task.id)?.let { setStatus(it, before, undoable = false) }
                _undo.value = null
            }
        }
    }

    /** Der Haken vor der Aufgabe: erledigt oder wieder offen. */
    fun toggleDone(task: CampTask) = setStatus(task, if (task.done) TaskStatus.OPEN else TaskStatus.DONE)

    fun setStatusAll(ids: Set<String>, status: TaskStatus) {
        if (!rights.setStatus) return
        val targets = currentTasks().filter { it.id in ids && it.status != status }
        val before = targets.associate { it.id to it.status }
        targets.forEach { setStatus(it, status, undoable = false) }
        _undo.value = UndoNotice(UndoKind.STATUS, targets.size, status) {
            before.forEach { (id, old) -> taskById(id)?.let { setStatus(it, old, undoable = false) } }
            _undo.value = null
        }
    }

    fun rename(task: CampTask, title: String) {
        if (!rights.edit || title.isBlank() || title == task.title) return
        changeFields(task, mapOf("titel" to JsonPrimitive(title.trim())))
    }

    fun setNote(task: CampTask, note: String) {
        if (!rights.edit) return
        changeFields(task, mapOf("notiz" to (note.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)))
    }

    /** Frist setzen (`null` löscht sie). */
    fun setDue(task: CampTask, date: String?) {
        if (!rights.edit) return
        changeFields(task, mapOf("frist" to (date?.let(::JsonPrimitive) ?: JsonNull)))
    }

    fun setAssignee(task: CampTask, personId: String?) {
        if (!rights.edit) return
        val before = task.assigneeId
        changeFields(task, mapOf("verantwortlichPersonId" to (personId?.let(::JsonPrimitive) ?: JsonNull)))
        _undo.value = UndoNotice(UndoKind.ASSIGNED, 1) {
            taskById(task.id)?.let { changeFields(it, mapOf("verantwortlichPersonId" to (before?.let(::JsonPrimitive) ?: JsonNull))) }
            _undo.value = null
        }
    }

    /** «Ich übernehme». */
    fun take(task: CampTask) = setAssignee(task, me.id)

    fun setStar(task: CampTask, starred: Boolean) {
        if (!rights.edit) return
        changeFields(task, mapOf("stern" to JsonPrimitive(starred)))
    }

    /** «Später erinnern»: Die Aufgabe bleibt bis zu diesem Datum ruhig im Abschnitt «Später» (`null` hebt das auf). */
    fun snooze(task: CampTask, date: String?) {
        if (!rights.edit) return
        changeFields(task, mapOf("spaeterAb" to (date?.let(::JsonPrimitive) ?: JsonNull)))
    }

    fun setAssigneeAll(ids: Set<String>, personId: String?) {
        if (!rights.edit) return
        val targets = currentTasks().filter { it.id in ids }
        val before = targets.associate { it.id to it.assigneeId }
        targets.forEach { changeFields(it, mapOf("verantwortlichPersonId" to (personId?.let(::JsonPrimitive) ?: JsonNull))) }
        _undo.value = UndoNotice(UndoKind.ASSIGNED, targets.size) {
            before.forEach { (id, old) -> taskById(id)?.let { changeFields(it, mapOf("verantwortlichPersonId" to (old?.let(::JsonPrimitive) ?: JsonNull))) } }
            _undo.value = null
        }
    }

    fun setDueAll(ids: Set<String>, date: String?) {
        if (!rights.edit) return
        val targets = currentTasks().filter { it.id in ids }
        val before = targets.associate { it.id to it.due }
        targets.forEach { changeFields(it, mapOf("frist" to (date?.let(::JsonPrimitive) ?: JsonNull))) }
        _undo.value = UndoNotice(UndoKind.DUE, targets.size) {
            before.forEach { (id, old) -> taskById(id)?.let { changeFields(it, mapOf("frist" to (old?.let(::JsonPrimitive) ?: JsonNull))) } }
            _undo.value = null
        }
    }

    fun setRessortAll(ids: Set<String>, ressortId: String?) {
        if (!rights.edit) return
        val targets = currentTasks().filter { it.id in ids }
        val before = targets.associate { it.id to it.ressortId }
        targets.forEach { changeFields(it, mapOf("ressortId" to (ressortId?.let(::JsonPrimitive) ?: JsonNull))) }
        _undo.value = UndoNotice(UndoKind.RESSORT, targets.size) {
            before.forEach { (id, old) -> taskById(id)?.let { changeFields(it, mapOf("ressortId" to (old?.let(::JsonPrimitive) ?: JsonNull))) } }
            _undo.value = null
        }
    }

    /**
     * Löscht Aufgaben. Der Server löscht weich (die Zeile bleibt, der Feed meldet «gelöscht»): Es gibt in der API keine Route, die
     * eine gelöschte Aufgabe zurückholt, darum zeigt die App hier kein «Rückgängig» (Route fehlt, siehe Bericht).
     */
    fun delete(ids: Set<String>) {
        if (!rights.edit) return
        currentTasks().filter { it.id in ids }.forEach { queueOp(it, TaskOps.DELETE, TaskPayloads.delete(it), texts.deleted(it.title)) }
        _selection.value = null
    }

    /** Verschiebt eine Aufgabe innerhalb von `list` um `delta` Stellen (−1: nach oben). Braucht Netz (die Warteschlange kennt es nicht). */
    fun move(list: List<CampTask>, taskId: String, delta: Int) {
        val id = campId ?: return
        if (!rights.edit) return
        val entries = TaskRules.moved(list, taskId, delta) ?: return
        viewModelScope.launch {
            when (val result = api.reorder(id, entries)) {
                is ApiResult.Ok -> loadTasks(silent = true)
                is ApiResult.Failure -> fail(result.error) { move(list, taskId, delta) }
            }
        }
    }

    // ---- Detail ----

    fun openTask(taskId: String) {
        _detail.value = DetailState(taskId)
        loadDetail(taskId, silent = false)
    }

    fun closeTask() {
        _detail.value = null
    }

    fun retryDetail() = _detail.value?.let { loadDetail(it.taskId, silent = false) }

    private fun loadDetail(taskId: String, silent: Boolean) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            val (details, history) = coroutineScope {
                // Das Team darf die Details nicht lesen (404): Es fragt gar nicht erst und sieht keine Checkliste.
                val d = async { if (rights.readAll) api.details(id, taskId) else ApiResult.Ok(TaskDetails()) }
                val h = async { if (rights.readHistory) api.history(id, taskId) else null }
                d.await() to h.await()
            }
            _detail.update { current ->
                if (current?.taskId != taskId) current
                else current.copy(
                    details = if (silent && details is ApiResult.Failure && current.details != null) current.details else details,
                    history = history ?: current.history,
                )
            }
        }
    }

    private fun afterDetailWrite(taskId: String, result: ApiResult<*>, retry: () -> Unit) {
        when (result) {
            is ApiResult.Ok -> loadDetail(taskId, silent = true)
            is ApiResult.Failure -> fail(result.error, retry)
        }
    }

    fun toggleCheckpoint(task: CampTask, checkpoint: Checkpoint) {
        val id = campId ?: return
        if (!rights.setStatus) return
        viewModelScope.launch {
            afterDetailWrite(task.id, api.setCheckpoint(id, task.id, checkpoint, !checkpoint.done)) { toggleCheckpoint(task, checkpoint) }
        }
    }

    fun addCheckpoint(task: CampTask, text: String) {
        val id = campId ?: return
        if (!rights.edit || text.isBlank()) return
        viewModelScope.launch { afterDetailWrite(task.id, api.addCheckpoint(id, task.id, text.trim())) { addCheckpoint(task, text) } }
    }

    fun deleteCheckpoint(task: CampTask, checkpoint: Checkpoint) {
        val id = campId ?: return
        if (!rights.edit) return
        viewModelScope.launch { afterDetailWrite(task.id, api.deleteCheckpoint(id, task.id, checkpoint)) { deleteCheckpoint(task, checkpoint) } }
    }

    fun moveCheckpoint(task: CampTask, list: List<Checkpoint>, checkpointId: String, delta: Int) {
        val id = campId ?: return
        if (!rights.edit) return
        val from = list.indexOfFirst { it.id == checkpointId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val reordered = list.toMutableList().also { it.add(to, it.removeAt(from)) }
        viewModelScope.launch {
            afterDetailWrite(task.id, api.reorderCheckpoints(id, task.id, reordered.map { it.id to it.version })) { moveCheckpoint(task, list, checkpointId, delta) }
        }
    }

    fun addLink(task: CampTask, title: String, url: String) {
        val id = campId ?: return
        if (!rights.edit) return
        viewModelScope.launch { afterDetailWrite(task.id, api.addLink(id, task.id, title.trim(), url.trim())) { addLink(task, title, url) } }
    }

    fun deleteLink(task: CampTask, link: TaskLink) {
        val id = campId ?: return
        if (!rights.edit) return
        viewModelScope.launch { afterDetailWrite(task.id, api.deleteLink(id, task.id, link)) { deleteLink(task, link) } }
    }

    // ---- Schnelleingabe ----

    fun openQuick() {
        if (!rights.edit) return
        _quick.value = QuickState()
        _quickOpen.value = true
    }

    fun closeQuick() {
        quickJob?.cancel()
        duplicateMerge.clear()
        _quickOpen.value = false
        _quick.value = QuickState()
    }

    /** Der Text ändert sich: Kurz nach dem letzten Tippen fragt die App den Server, was er erkennt. */
    fun editQuick(text: String) {
        _quick.update { it.copy(text = text, duplicate = null, error = null, showErrors = false) }
        quickJob?.cancel()
        val id = campId ?: return
        if (text.isBlank()) {
            _quick.update { it.copy(items = emptyList(), recognizing = false) }
            return
        }
        quickJob = viewModelScope.launch(Background()) {
            delay(RECOGNIZE_DELAY_MS)
            _quick.update { it.copy(recognizing = true) }
            when (val result = api.quick(id, text)) {
                is ApiResult.Ok -> _quick.update { state ->
                    if (state.text != text) state else state.copy(items = result.value.suggestions.map(::quickItem), recognizing = false, offline = false, error = null)
                }
                is ApiResult.Failure -> _quick.update { state ->
                    if (state.text != text) state
                    // Ohne Netz bleibt jede Zeile ein Titel; andere Fehler zeigt das Blatt.
                    else if (result.error == ApiError.NETWORK) state.copy(items = offlineItems(text), recognizing = false, offline = true, error = null)
                    else state.copy(recognizing = false, error = result.error)
                }
            }
        }
    }

    fun chooseQuickPerson(index: Int, person: PersonCandidate?) = updateQuickItem(index) {
        it.copy(person = person?.let { p -> QuickChoice.Chosen(p) } ?: QuickChoice.None)
    }

    fun chooseQuickRessort(index: Int, ressort: RessortCandidate?) = updateQuickItem(index) {
        it.copy(ressort = ressort?.let { r -> QuickChoice.Chosen(r) } ?: QuickChoice.None)
    }

    fun chooseQuickDue(index: Int, date: String?) = updateQuickItem(index) { it.copy(due = date, dueText = null) }

    private fun updateQuickItem(index: Int, block: (QuickItem) -> QuickItem) {
        _quick.update { state -> state.copy(items = state.items.mapIndexed { i, item -> if (i == index) block(item) else item }) }
    }

    /** Die Ressorts für die Wahl in der Schnelleingabe und in der Mehrfachauswahl. */
    fun loadRessortsIfNeeded() {
        if (rawRessorts.value == null) loadRessorts(silent = false)
    }

    /**
     * «Erfassen»: Ist noch etwas offen (eine Person, die der Server nicht eindeutig fand), zeigt das Blatt es an. Sonst fragt die
     * App den Server nach ähnlichen Aufgaben; gibt es eine, fragt das Blatt nach («Zusammenführen» oder «Beide behalten»).
     * Ohne Treffer geht jede Zeile als Aufgabe in die Warteschlange.
     */
    fun submitQuick() {
        val id = campId ?: return
        val state = _quick.value
        if (!state.canSubmit) return
        if (state.unresolved) {
            _quick.update { it.copy(showErrors = true) }
            return
        }
        if (state.offline) {
            createAll(state.items, merge = null)
            return
        }
        _quick.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            for ((index, item) in state.items.withIndex()) {
                val ressortId = (item.ressort as? QuickChoice.Chosen)?.value?.id
                when (val result = api.duplicates(id, item.title, ressortId)) {
                    is ApiResult.Ok -> result.value.groups.firstOrNull()?.tasks?.firstOrNull()?.let { existing ->
                        _quick.update { it.copy(saving = false, duplicate = DuplicateAsk(index, existing)) }
                        return@launch
                    }
                    // Ohne Antwort legt die App an, wie wenn es keine ähnliche gäbe: Der Hinweis ist eine Hilfe, keine Pflicht.
                    is ApiResult.Failure -> Unit
                }
            }
            // Die Person hat den Text unterdessen geändert: Die Zeilen von vorhin gelten nicht mehr.
            if (_quick.value.text != state.text) {
                _quick.update { it.copy(saving = false) }
                return@launch
            }
            createAll(state.items, merge = null)
        }
    }

    /** «Beide behalten»: Alle Zeilen werden Aufgaben. */
    fun keepBoth() {
        createAll(_quick.value.items, merge = null)
    }

    /** «Zusammenführen»: Die Zeile mit dem Treffer geht in die vorhandene Aufgabe auf, die anderen werden Aufgaben. */
    fun mergeDuplicate() {
        val id = campId ?: return
        val state = _quick.value
        val ask = state.duplicate ?: return
        val item = state.items.getOrNull(ask.index) ?: return
        _quick.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = duplicateMerge.merge(
                item,
                create = { api.create(id, quickPayload(item)) },
                merge = { created -> api.merge(id, ask.existing.id, MergeBody(ask.existing.version, created.id, created.version)) },
            )
            when (result) {
                is ApiResult.Failure -> _quick.update { it.copy(saving = false, error = result.error) }
                is ApiResult.Ok -> createAll(state.items.filterIndexed { i, _ -> i != ask.index }, merge = result.value)
            }
        }
    }

    private fun createAll(items: List<QuickItem>, merge: CampTask?) {
        val id = campId ?: return
        // Eine Zeile, die ein gescheiterter «Zusammenführen»-Versuch schon angelegt hat, legt die Warteschlange nicht noch einmal an.
        val already = items.count { duplicateMerge.createdFor(it) != null }
        items.filter { duplicateMerge.createdFor(it) == null }.forEach { camp.enqueue(id, TaskOps.CREATE, quickPayload(it), texts.created(it.title), texts.fieldLabels()) }
        if (merge != null || already > 0) loadTasks(silent = true)
        closeQuick()
    }

    // ---- Ressorts ----

    fun retryRessorts() = loadRessorts(silent = false)

    private fun loadRessorts(silent: Boolean) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) rawRessorts.value = null
            val result = api.ressorts(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || rawRessorts.value == null) rawRessorts.value = result
        }
    }

    /**
     * «Ruhiges Ressort» an- oder ausschalten (Recht `ressorts.bearbeiten`). Die Änderung geht über die Warteschlange; bis der Server
     * sie bestätigt, zeigt die Ansicht den neuen Wert.
     */
    fun setRessortQuiet(ressort: Ressort, quiet: Boolean) {
        val id = campId ?: return
        if (!rights.editRessorts || ressort.quiet == quiet) return
        val payload = buildJsonObject { put("id", ressort.id); put("version", ressort.version); put("ruhig", quiet) }
        camp.enqueue(id, RESSORT_QUIET_OP, payload, texts.ressortQuiet(ressort.name, quiet), texts.fieldLabels())
        val shown = ressort.copy(quiet = quiet)
        (rawRessorts.value as? ApiResult.Ok)?.let { ok -> rawRessorts.value = ApiResult.Ok(ok.value.map { if (it.id == ressort.id) shown else it }) }
        _bundle.update { current -> (current as? ApiResult.Ok)?.takeIf { it.value.ressort.id == ressort.id }?.let { ApiResult.Ok(it.value.copy(ressort = shown)) } ?: current }
    }

    fun openRessort(ressortId: String) {
        bundleId = ressortId
        _bundle.value = null
        loadBundle(ressortId, silent = false)
    }

    fun closeRessort() {
        bundleId = null
        _bundle.value = null
    }

    fun retryRessort() = bundleId?.let { loadBundle(it, silent = false) }

    private fun loadBundle(ressortId: String, silent: Boolean) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            val result = coroutineScope {
                val ressort = async { api.ressort(id, ressortId) }
                val links = async { api.ressortLinks(id, ressortId) }
                val notes = async { api.ressortNotes(id, ressortId) }
                val r = ressort.await()
                if (r is ApiResult.Failure) return@coroutineScope r
                val l = links.await()
                val n = notes.await()
                // Ein Ressort ohne lesbare Links oder Notizen zeigt sich trotzdem; ein Netzfehler nicht.
                when {
                    l is ApiResult.Failure && l.error == ApiError.NETWORK -> l
                    n is ApiResult.Failure && n.error == ApiError.NETWORK -> n
                    else -> ApiResult.Ok(RessortBundle((r as ApiResult.Ok).value, (l as? ApiResult.Ok)?.value?.links.orEmpty(), (n as? ApiResult.Ok)?.value?.notes.orEmpty()))
                }
            }
            if (bundleId != ressortId) return@launch
            if (result is ApiResult.Ok || !silent || _bundle.value == null) _bundle.value = result
        }
    }

    fun addRessortNote(ressortId: String, text: String) {
        val id = campId ?: return
        if (!rights.editRessorts || text.isBlank()) return
        viewModelScope.launch {
            when (val result = api.addRessortNote(id, ressortId, text.trim())) {
                is ApiResult.Ok -> loadBundle(ressortId, silent = true)
                is ApiResult.Failure -> fail(result.error) { addRessortNote(ressortId, text) }
            }
        }
    }

    // ---- Start ----

    /** Lädt, was der Start zeigt. Die Lagerleitung und die Leitung sehen alles, was die Rolle liest; fehlt ein Recht, fehlt der Teil. */
    fun loadStart(silent: Boolean) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _start.value = StartData()
            val now = today()
            val data = coroutineScope {
                val start = async { if (rights.seeLeadershipStart) api.start(id) else null }
                val meetings = async { if (rights.seeLeadershipStart) api.meetings(id, now.toString()) else null }
                val weather = async { if (rights.seeLeadershipStart) api.weather(id) else null }
                val todayPage = async { if (rights.seeLeadershipStart) api.today(id) else null }
                val news = async { api.notifications(id) }
                StartData(
                    start = start.await(),
                    meeting = (meetings.await() as? ApiResult.Ok)?.value?.meetings?.let { nextMeeting(it, now) },
                    weather = (weather.await() as? ApiResult.Ok)?.value,
                    today = (todayPage.await() as? ApiResult.Ok)?.value,
                    news = (news.await() as? ApiResult.Ok)?.value?.entries,
                )
            }
            if (campId != id) return@launch
            // Eine stille Aktualisierung behält den alten Stand, wenn das Netz fehlt.
            val failed = data.start is ApiResult.Failure && silent && (_start.value.start is ApiResult.Ok)
            if (!failed) _start.value = data
        }
    }

    // ---- Hilfen ----

    private fun peopleMap(list: List<CampTask>): Map<String, TaskPerson> = TaskRules.people(list, me).associateBy { it.id }

    private fun ressortRefs(list: List<CampTask>, ressorts: List<Ressort>): Map<String, TaskRessortRef> {
        val refs = LinkedHashMap<String, TaskRessortRef>()
        ressorts.forEach { refs[it.id] = TaskRessortRef(it.id, it.abbreviation, it.name) }
        list.forEach { task -> task.ressort?.let { refs.putIfAbsent(it.id, it) } }
        return refs
    }

    /** Die Person, die die Aufgabe als «ich» sieht. */
    val self: TaskPerson get() = me

    companion object {
        private const val RECOGNIZE_DELAY_MS = 500L

        /** `ActionError.code`, wenn eine Änderung an einer Aufgabe wartet, deren Anlage gerade unterwegs ist. */
        const val CREATING_CODE = "ANLAGE_LAEUFT"

        /** Aus dem Vorschlag des Servers eine Zeile: Findet er genau eine Person oder ein Ressort, gilt sie; sonst wählt die Person. */
        fun quickItem(suggestion: QuickSuggestion): QuickItem {
            val person = suggestion.person?.let { p ->
                when {
                    p.status == Recognition.FOUND && p.candidates.size == 1 -> QuickChoice.Chosen(p.candidates.first())
                    else -> QuickChoice.Unresolved(p.text, p.candidates)
                }
            } ?: QuickChoice.None
            val ressort = suggestion.ressort?.let { r ->
                when {
                    r.status == Recognition.FOUND && r.candidates.size == 1 -> QuickChoice.Chosen(r.candidates.first())
                    else -> QuickChoice.Unresolved(r.text, r.candidates)
                }
            } ?: QuickChoice.None
            return QuickItem(
                line = suggestion.line,
                title = suggestion.title,
                person = person,
                ressort = ressort,
                due = suggestion.due?.date,
                dueText = suggestion.due?.text,
                titleError = suggestion.hints.any { it == "TITEL_FEHLT" || it == "TITEL_ZU_LANG" },
            )
        }

        /** Ohne Netz: Jede nicht leere Zeile wird ein Titel, so wie sie dasteht (bis 20 Zeilen, wie beim Server). */
        fun offlineItems(text: String): List<QuickItem> =
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES).map {
                QuickItem(it, it.take(MAX_TITLE), QuickChoice.None, QuickChoice.None, null, null, titleError = false)
            }

        fun quickPayload(item: QuickItem): JsonObject = TaskPayloads.create(
            title = item.title,
            ressortId = (item.ressort as? QuickChoice.Chosen)?.value?.id,
            assigneeId = (item.person as? QuickChoice.Chosen)?.value?.id,
            due = item.due,
        )

        private const val MAX_LINES = 20
        private const val MAX_TITLE = 200
    }
}
