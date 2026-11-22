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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Die Feldnamen des Konfliktbildschirms und die Sätze dazu; sie stehen in `strings.xml`, die ViewModels kennen sie nicht. */
interface CommsTexts {
    fun fieldLabels(): Map<String, String>
}

/** Ein Hinweis nach einer Aktion; `undo` ist nur gesetzt, wo sich die Aktion zurücknehmen lässt. */
class MeetingNotice(val kind: Kind, val title: String, val undo: (() -> Unit)? = null, val openTaskId: String? = null) {
    enum class Kind { MOVED_TO_NEXT, TAKEN_OVER, TASK_CREATED }
}

/** Der offene Höck mit den Traktanden und den Punkten für den nächsten Höck. */
data class MeetingState(val id: String, val meeting: ApiResult<Meeting>? = null, val nextItems: List<AgendaItem> = emptyList())

/** Höck-Modus: der Stand vom Server und die Stelle in den Traktanden (die App blättert lokal). */
data class ModeState(val id: String, val mode: ApiResult<MeetingMode>? = null, val index: Int = 0, val indexSet: Boolean = false)

/**
 * Sitzungen eines Lagers: Höcks, Traktanden, Höck-Modus, Protokolle, Beschlüsse und Erwähnungen. Der Stand kommt vom Server (nur im
 * Arbeitsspeicher). Der Server nimmt diese Objekte nicht über die Warteschlange an: Jede Änderung geht direkt hinaus, ein Konflikt
 * erscheint über [writer] im Konfliktbildschirm.
 */
class MeetingsViewModel(
    private val api: MeetingsApi,
    private val camp: CampViewModel,
    private val texts: CommsTexts,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val campId: String? get() = openCamp.value

    val writer = DirectWriter(viewModelScope) { texts.fieldLabels() }

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie. Wer die Punkte für den nächsten Höck nicht lesen darf (Team), fragt sie nicht ab. */
    var rights: MeetingRights = MeetingRights.of(CampRole.READ_ONLY, true)

    private val _meetings = MutableStateFlow<ApiResult<List<Meeting>>?>(null)
    val meetings: StateFlow<ApiResult<List<Meeting>>?> = _meetings.asStateFlow()

    private val _state = MutableStateFlow<MeetingState?>(null)
    val state: StateFlow<MeetingState?> = _state.asStateFlow()

    private val _mode = MutableStateFlow<ModeState?>(null)
    val mode: StateFlow<ModeState?> = _mode.asStateFlow()

    private val _members = MutableStateFlow<List<MemberPresence>>(emptyList())

    /** Die Mitglieder des Lagers (für Verantwortliche und Erwähnungen); nur Rollen, die den Höck-Modus lesen, bekommen sie. */
    val members: StateFlow<List<MemberPresence>> = _members.asStateFlow()

    private val _minutes = MutableStateFlow<ApiResult<List<Minutes>>?>(null)
    val minutes: StateFlow<ApiResult<List<Minutes>>?> = _minutes.asStateFlow()

    private val _decisions = MutableStateFlow<ApiResult<List<Decision>>?>(null)
    val decisions: StateFlow<ApiResult<List<Decision>>?> = _decisions.asStateFlow()

    private val _mentions = MutableStateFlow<ApiResult<List<Mention>>?>(null)
    val mentions: StateFlow<ApiResult<List<Mention>>?> = _mentions.asStateFlow()

    private val _notice = MutableStateFlow<MeetingNotice?>(null)
    val notice: StateFlow<MeetingNotice?> = _notice.asStateFlow()

    /** Die Datei für den Kalender, die der Server erzeugt hat; die Oberfläche teilt sie und ruft [calendarShared] auf. */
    private val _calendar = MutableStateFlow<ByteArray?>(null)
    val calendar: StateFlow<ByteArray?> = _calendar.asStateFlow()

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
        _meetings.value = null
        _state.value = null
        _mode.value = null
        _minutes.value = null
        _decisions.value = null
        _mentions.value = null
        _members.value = emptyList()
        _notice.value = null
        _calendar.value = null
        writer.clear()
    }

    private fun onChange(change: CampChange) {
        if (campId == null) return
        fun touches(prefix: String) = change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { it.startsWith(prefix) }
        if (touches("hoeck") || touches("traktandum")) {
            if (_meetings.value != null) loadMeetings(silent = true)
            _state.value?.let { loadMeeting(it.id, silent = true) }
            _mode.value?.let { loadMode(it.id, silent = true) }
        }
        if (touches("protokoll") || touches("beschluss")) {
            if (_minutes.value != null) loadMinutes(silent = true)
            if (_decisions.value != null) loadDecisions(silent = true)
        }
        if (touches("erwaehnung") || touches("beschluss") || touches("traktandum")) {
            if (_mentions.value != null) loadMentions(silent = true)
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ---- Höcks ----

    fun loadMeetings(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _meetings.value = null
            val result = api.meetings(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _meetings.value == null) _meetings.value = result
        }
    }

    fun openMeeting(meetingId: String) {
        if (_state.value?.id != meetingId) _state.value = MeetingState(meetingId)
        loadMeeting(meetingId, silent = _state.value?.meeting != null)
    }

    fun closeMeeting() {
        _state.value = null
        _notice.value = null
    }

    fun loadMeeting(meetingId: String, silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            val result = api.meeting(id, meetingId)
            val next = if (rights.readAll) (api.nextMeetingItems(id) as? ApiResult.Ok)?.value?.items.orEmpty() else emptyList()
            if (campId != id) return@launch
            _state.update { current ->
                if (current?.id != meetingId) current
                else if (result is ApiResult.Ok || !silent || current.meeting == null) current.copy(meeting = result, nextItems = next) else current
            }
        }
    }

    fun createMeeting(title: String, kind: MeetingKind, date: String, time: String?, place: String?, guests: String?, onCreated: (Meeting) -> Unit) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            val body = buildJsonObject {
                put("titel", title.trim())
                put("art", kind.wire)
                put("datum", date)
                time?.takeIf { it.isNotBlank() }?.let { put("zeit", it.trim()) }
                place?.takeIf { it.isNotBlank() }?.let { put("ort", it.trim()) }
                guests?.takeIf { it.isNotBlank() }?.let { put("gaeste", it.trim()) }
            }
            when (val result = api.createMeeting(id, body)) {
                is ApiResult.Ok -> {
                    loadMeetings(silent = true)
                    onCreated(result.value)
                }
                is ApiResult.Failure -> writer.fail(result) { createMeeting(title, kind, date, time, place, guests, onCreated) }
            }
        }
    }

    /** Ändert Felder eines Höcks (`null` als Wert leert das Feld). Ein Konflikt erscheint im Konfliktbildschirm. */
    fun changeMeeting(meeting: Meeting, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}) {
        val id = campId ?: return
        writer.submit(
            summary, fields, meeting.version,
            call = { v -> api.changeMeeting(id, meeting.id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) },
            onCurrent = { reloadMeeting(meeting.id) },
        ) {
            loadMeetings(silent = true)
            _state.update { s -> if (s?.id == meeting.id) s.copy(meeting = ApiResult.Ok(it)) else s }
            onDone()
        }
    }

    fun deleteMeeting(meeting: Meeting, onDone: () -> Unit) {
        val id = campId ?: return
        writer.submit("", emptyMap(), meeting.version, { v -> api.deleteMeeting(id, meeting.copy(version = v)) }, { loadMeetings(silent = true) }) {
            loadMeetings(silent = true)
            onDone()
        }
    }

    private fun reloadMeeting(meetingId: String) {
        _state.value?.takeIf { it.id == meetingId }?.let { loadMeeting(meetingId, silent = false) }
        loadMeetings(silent = true)
    }

    /** Lädt den Kalendereintrag, den der Server für diesen Höck erzeugt; die Oberfläche teilt ihn über die Systemfunktion. */
    fun loadCalendar(meetingId: String) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.calendarFile(id, meetingId)) {
                is ApiResult.Ok -> _calendar.value = result.value
                is ApiResult.Failure -> writer.fail(result) { loadCalendar(meetingId) }
            }
        }
    }

    fun calendarShared() {
        _calendar.value = null
    }

    // ---- Traktanden ----

    /** Legt ein Traktandum an (ohne `meetingId`: ein Punkt für den nächsten Höck). */
    fun createItem(meetingId: String?, title: String, text: String?, responsible: List<String>, taskId: String? = null, onDone: (AgendaItem) -> Unit = {}) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            val body = buildJsonObject {
                put("titel", title.trim())
                meetingId?.let { put("hoeckId", it) }
                text?.takeIf { it.isNotBlank() }?.let { put("text", it.trim()) }
                if (responsible.isNotEmpty()) put("verantwortlichePersonIds", JsonArray(responsible.map(::JsonPrimitive)))
                taskId?.let { put("aufgabeId", it) }
            }
            when (val result = api.createItem(id, body)) {
                is ApiResult.Ok -> {
                    syncMentionsOf(result.value, null)
                    meetingId?.let { loadMeeting(it, silent = true) }
                    onDone(result.value)
                }
                is ApiResult.Failure -> writer.fail(result) { createItem(meetingId, title, text, responsible, taskId, onDone) }
            }
        }
    }

    fun changeItem(item: AgendaItem, fields: Map<String, JsonElement>, summary: String, onDone: (AgendaItem) -> Unit = {}) {
        val id = campId ?: return
        writer.submit(
            summary, fields, item.version,
            call = { v -> api.changeItem(id, item.id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) },
            onCurrent = { item.meetingId?.let { loadMeeting(it, silent = false) } ?: _state.value?.let { loadMeeting(it.id, silent = false) } },
        ) { changed ->
            viewModelScope.launch { syncMentionsOf(changed, item) }
            _state.value?.let { loadMeeting(it.id, silent = true) }
            _mode.value?.let { loadMode(it.id, silent = true) }
            onDone(changed)
        }
    }

    fun deleteItem(item: AgendaItem) {
        val id = campId ?: return
        writer.submit("", emptyMap(), item.version, { v -> api.deleteItem(id, item.copy(version = v)) }, { _state.value?.let { loadMeeting(it.id) } }) {
            _state.value?.let { s -> loadMeeting(s.id, silent = true) }
        }
    }

    /** «Auf nächsten Höck»: das Traktandum wandert in die Liste für den nächsten Höck; «Rückgängig» holt es zurück. */
    fun moveToNext(item: AgendaItem) {
        val previous = item.meetingId
        changeItem(item, mapOf("hoeckId" to JsonNull), summary = item.title) { moved ->
            _notice.value = MeetingNotice(MeetingNotice.Kind.MOVED_TO_NEXT, item.title, undo = previous?.let { meetingId ->
                { changeItem(moved, mapOf("hoeckId" to JsonPrimitive(meetingId)), summary = item.title) { _notice.value = null } }
            })
        }
    }

    /** Neue Reihenfolge nach «Nach oben» oder «Nach unten»; die Liste ist sofort neu geordnet, der Server bestätigt. */
    fun moveItem(items: List<AgendaItem>, index: Int, delta: Int) {
        val id = campId ?: return
        val state = _state.value ?: return
        val reordered = MeetingRules.move(items, index, delta) ?: return
        writer.dismissError()
        viewModelScope.launch {
            // Sofort sichtbar: die neue Reihenfolge in der Anzeige, bis der Server geantwortet hat.
            val optimistic = (state.meeting as? ApiResult.Ok)?.value?.let { m ->
                m.copy(agenda = reordered.mapIndexed { i, item -> item.copy(order = i) })
            }
            optimistic?.let { opt -> _state.update { s -> if (s?.id == state.id) s.copy(meeting = ApiResult.Ok(opt)) else s } }
            when (val result = api.reorderItems(id, state.id, reordered)) {
                is ApiResult.Ok -> loadMeeting(state.id, silent = true)
                is ApiResult.Failure -> {
                    loadMeeting(state.id, silent = true)
                    writer.fail(result) { moveItem(items, index, delta) }
                }
            }
        }
    }

    fun takeOver(meeting: Meeting, ids: List<String>? = null) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.takeOver(id, meeting.id, ids)) {
                is ApiResult.Ok -> {
                    loadMeeting(meeting.id, silent = true)
                    _notice.value = MeetingNotice(MeetingNotice.Kind.TAKEN_OVER, result.value.count.toString())
                }
                is ApiResult.Failure -> writer.fail(result) { takeOver(meeting, ids) }
            }
        }
    }

    /** Gleicht die Erwähnungen ab, wenn der Inhalt jetzt oder vorher Marken hatte (entfallene Marken nimmt der Server zurück). */
    private suspend fun syncMentionsOf(after: AgendaItem, before: AgendaItem?) {
        val id = campId ?: return
        if (hasMentionMarks(after.title, after.text, after.decision, before?.title, before?.text, before?.decision)) {
            api.syncMentions(id, "traktandum", after.id)
        }
    }

    // ---- Höck-Modus ----

    fun openMode(meetingId: String) {
        if (_mode.value?.id != meetingId) _mode.value = ModeState(meetingId)
        loadMode(meetingId, silent = _mode.value?.mode != null)
    }

    fun closeMode() {
        _mode.value = null
    }

    fun loadMode(meetingId: String, silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            val result = api.mode(id, meetingId)
            if (campId != id) return@launch
            _mode.update { current ->
                if (current?.id != meetingId) return@update current
                val items = (result as? ApiResult.Ok)?.value?.meeting?.agenda?.let(MeetingRules::sorted).orEmpty()
                // Die erste Anzeige beginnt beim ersten offenen Traktandum; danach bestimmt die Person, wo sie steht.
                val index = if (current.indexSet) current.index.coerceIn(0, maxOf(items.size - 1, 0)) else MeetingRules.firstOpen(items)
                if (result is ApiResult.Ok || !silent || current.mode == null) current.copy(mode = result, index = index, indexSet = current.indexSet || result is ApiResult.Ok)
                else current
            }
        }
    }

    /** Lädt die Mitglieder für die Wahl von Verantwortlichen und Erwähnungen (über den Höck-Modus, eine Abfrage). */
    fun loadMembers(meetingId: String) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            (api.mode(id, meetingId) as? ApiResult.Ok)?.value?.let { if (campId == id) _members.value = it.members }
        }
    }

    fun goTo(index: Int) {
        _mode.update { it?.copy(index = index, indexSet = true) }
    }

    /** Haken bei einer Person: sofort sichtbar, bei einem Fehler wieder zurück und mit Hinweis. */
    fun setPresent(meetingId: String, personId: String, present: Boolean) {
        val id = campId ?: return
        writer.dismissError()
        updateMember(personId, present)
        viewModelScope.launch {
            when (val result = api.setPresent(id, meetingId, personId, present)) {
                is ApiResult.Ok -> Unit
                is ApiResult.Failure -> {
                    updateMember(personId, !present)
                    writer.fail(result) { setPresent(meetingId, personId, present) }
                }
            }
        }
    }

    private fun updateMember(personId: String, present: Boolean) {
        _mode.update { state ->
            val ok = (state?.mode as? ApiResult.Ok)?.value ?: return@update state
            state.copy(mode = ApiResult.Ok(ok.copy(members = ok.members.map { if (it.id == personId) it.copy(present = present) else it })))
        }
    }

    // ---- Protokolle und Beschlüsse ----

    fun loadMinutes(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _minutes.value = null
            val result = api.minutes(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _minutes.value == null) _minutes.value = result
        }
    }

    fun loadDecisions(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _decisions.value = null
            val result = api.decisions(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _decisions.value == null) _decisions.value = result
        }
    }

    /** Verknüpft ein Protokoll (Datei am Ablageort) mit einem Höck: zuerst die Datei, dann das Protokoll. */
    fun linkMinutes(meeting: Meeting, title: String, fileName: String, link: String, onDone: () -> Unit) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            val file = when (val added = api.addFile(id, fileName.trim(), link.trim())) {
                is ApiResult.Ok -> added.value
                is ApiResult.Failure -> return@launch writer.fail(added) { linkMinutes(meeting, title, fileName, link, onDone) }
            }
            val body = buildJsonObject {
                put("art", MinutesKind.SESSION.wire)
                put("titel", title.trim())
                put("datum", meeting.date)
                put("dateiId", file.id)
                put("hoeckId", meeting.id)
            }
            when (val created = api.createMinutes(id, body)) {
                is ApiResult.Ok -> {
                    loadMinutes(silent = true)
                    loadMeeting(meeting.id, silent = true)
                    onDone()
                }
                is ApiResult.Failure -> writer.fail(created) { linkMinutes(meeting, title, fileName, link, onDone) }
            }
        }
    }

    fun changeMinutes(minutes: Minutes, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}) {
        val id = campId ?: return
        writer.submit(
            summary, fields, minutes.version,
            call = { v -> api.changeMinutes(id, minutes.id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) },
            onCurrent = { loadMinutes(silent = false) },
        ) {
            loadMinutes(silent = true)
            onDone()
        }
    }

    fun createDecision(text: String, minutesId: String?, meetingId: String?, onDone: () -> Unit = {}) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            val body = buildJsonObject {
                put("text", text.trim())
                minutesId?.let { put("protokollId", it) }
                meetingId?.let { put("hoeckId", it) }
            }
            when (val result = api.createDecision(id, body)) {
                is ApiResult.Ok -> {
                    if (hasMentionMarks(text)) api.syncMentions(id, "beschluss", result.value.id)
                    loadDecisions(silent = true)
                    onDone()
                }
                is ApiResult.Failure -> writer.fail(result) { createDecision(text, minutesId, meetingId, onDone) }
            }
        }
    }

    fun changeDecision(decision: Decision, text: String, summary: String, onDone: () -> Unit = {}) {
        val id = campId ?: return
        val fields = mapOf<String, JsonElement>("text" to JsonPrimitive(text.trim()))
        writer.submit(
            summary, fields, decision.version,
            call = { v -> api.changeDecision(id, decision.id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) },
            onCurrent = { loadDecisions(silent = false) },
        ) { changed ->
            viewModelScope.launch { if (hasMentionMarks(text, decision.text)) api.syncMentions(id, "beschluss", changed.id) }
            loadDecisions(silent = true)
            onDone()
        }
    }

    fun deleteDecision(decision: Decision) {
        val id = campId ?: return
        writer.submit("", emptyMap(), decision.version, { v -> api.deleteDecision(id, decision.copy(version = v)) }, { loadDecisions() }) { loadDecisions(silent = true) }
    }

    // ---- Erwähnungen ----

    fun loadMentions(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _mentions.value = null
            val result = api.mentions(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _mentions.value == null) _mentions.value = result
        }
    }

    fun setMentionDone(mention: Mention, done: Boolean) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.setMentionDone(id, mention.id, done)) {
                is ApiResult.Ok -> replaceMention(result.value)
                is ApiResult.Failure -> writer.fail(result) { setMentionDone(mention, done) }
            }
        }
    }

    /** «Als Aufgabe»: Der Server legt eine Aufgabe für die Person an (höchstens eine je Erwähnung) und verknüpft sie. */
    fun mentionToTask(mention: Mention) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.mentionToTask(id, mention.id)) {
                is ApiResult.Ok -> {
                    replaceMention(mention.copy(taskId = result.value.taskId))
                    _notice.value = MeetingNotice(MeetingNotice.Kind.TASK_CREATED, mention.title, openTaskId = result.value.taskId)
                    camp.announceTasksChanged()
                }
                is ApiResult.Failure -> writer.fail(result) { mentionToTask(mention) }
            }
        }
    }

    private fun replaceMention(updated: Mention) {
        _mentions.update { current ->
            val ok = current as? ApiResult.Ok ?: return@update current
            ApiResult.Ok(ok.value.map { if (it.id == updated.id) updated else it })
        }
    }
}

/** Zeigt einen Fehler mit Netz an (und `retry`); die Oberfläche schreibt den Text. */
fun DirectWriter.fail(result: ApiResult.Failure, retry: () -> Unit) = showError(ActionError(domainError(result.error), retry, result.code))
