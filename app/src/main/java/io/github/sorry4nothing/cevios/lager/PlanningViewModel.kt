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

/** Ein Hinweis nach einer Aktion (kein Rückgängig: Der Server bietet dafür keine Route). */
enum class PlanNotice { SWAP_ACCEPTED, SWAP_DECLINED, SWAP_REQUESTED, SWAP_WITHDRAWN, COUNT_RESET, MIX_APPLIED, SUGGESTION_APPLIED, IMPORTED }

/** Was die Abzählliste zeigt: Teilnehmende oder Team, optional eine Stufe oder eine Gruppe. */
data class CountFilter(val team: Boolean = false, val stage: String? = null, val groupId: String? = null)

/**
 * Die Einteilung eines Lagers: Teilnehmende, Zimmer, Fahrten, Gruppen, Schichtplan, Diensttausch und Abzählliste. Der Stand kommt vom
 * Server (nur im Arbeitsspeicher). Der Server nimmt diese Objekte nicht über die Warteschlange an: Jede Änderung geht direkt hinaus,
 * ein Konflikt erscheint über [writer] im Konfliktbildschirm, ein anderer Fehler als Banner mit «Nochmals versuchen».
 */
class PlanningViewModel(
    private val api: PlanningApi,
    private val camp: CampViewModel,
    private val texts: CommsTexts,
    /** Die ID der eigenen Person (für «deine Dienste» und «meine Anfragen»). */
    val selfId: String,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val campId: String? get() = openCamp.value

    val writer = DirectWriter(viewModelScope) { texts.fieldLabels() }

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie. */
    var rights: PlanRights = PlanRights.of(NO_PERMISSIONS)

    private val _participants = MutableStateFlow<ApiResult<List<Participant>>?>(null)
    val participants: StateFlow<ApiResult<List<Participant>>?> = _participants.asStateFlow()

    private val _rooms = MutableStateFlow<ApiResult<RoomList>?>(null)
    val rooms: StateFlow<ApiResult<RoomList>?> = _rooms.asStateFlow()

    private val _openPeople = MutableStateFlow<ApiResult<List<OpenPerson>>?>(null)
    val openPeople: StateFlow<ApiResult<List<OpenPerson>>?> = _openPeople.asStateFlow()

    private val _trips = MutableStateFlow<ApiResult<List<Trip>>?>(null)
    val trips: StateFlow<ApiResult<List<Trip>>?> = _trips.asStateFlow()

    private val _withoutTrip = MutableStateFlow<Map<TripDirection, List<TripCandidate>>>(emptyMap())

    /** Personen ohne Fahrt je Richtung; leer für das Team (der Server lässt es nicht zu). */
    val withoutTrip: StateFlow<Map<TripDirection, List<TripCandidate>>> = _withoutTrip.asStateFlow()

    private val _groups = MutableStateFlow<ApiResult<GroupList>?>(null)
    val groups: StateFlow<ApiResult<GroupList>?> = _groups.asStateFlow()

    private val _mix = MutableStateFlow<MixResult?>(null)

    /** Die Vorschau der gemischten Einteilung, solange die Person sie ansieht. */
    val mix: StateFlow<MixResult?> = _mix.asStateFlow()

    private val _plan = MutableStateFlow<ApiResult<ShiftPlan>?>(null)
    val plan: StateFlow<ApiResult<ShiftPlan>?> = _plan.asStateFlow()

    private val _schedule = MutableStateFlow<ApiResult<Schedule>?>(null)
    val schedule: StateFlow<ApiResult<Schedule>?> = _schedule.asStateFlow()

    private val _suggestion = MutableStateFlow<ShiftSuggestion?>(null)

    /** Der Vorschlag für die leeren Dienste, solange die Person ihn ansieht. */
    val suggestion: StateFlow<ShiftSuggestion?> = _suggestion.asStateFlow()

    private val _swaps = MutableStateFlow<ApiResult<List<Swap>>?>(null)
    val swaps: StateFlow<ApiResult<List<Swap>>?> = _swaps.asStateFlow()

    private val _count = MutableStateFlow<ApiResult<CountPage>?>(null)
    val count: StateFlow<ApiResult<CountPage>?> = _count.asStateFlow()

    private val _filter = MutableStateFlow(CountFilter())
    val filter: StateFlow<CountFilter> = _filter.asStateFlow()

    private val _importPreview = MutableStateFlow<ImportResult?>(null)
    val importPreview: StateFlow<ImportResult?> = _importPreview.asStateFlow()

    private val _notice = MutableStateFlow<PlanNotice?>(null)
    val notice: StateFlow<PlanNotice?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** Wahr, solange eine Aktion läuft (Knöpfe zeigen den Ladekreis). */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _people = MutableStateFlow<List<RessortPerson>>(emptyList())

    /** Die bekannten Konten des Lagers für Auswahlen (es gibt keine Route mit allen Mitgliedern). */
    val people: StateFlow<List<RessortPerson>> = _people.asStateFlow()
    private var extraPeople: List<RessortPerson> = emptyList()

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
        _participants.value = null; _rooms.value = null; _openPeople.value = null; _trips.value = null; _withoutTrip.value = emptyMap()
        _groups.value = null; _mix.value = null; _plan.value = null; _schedule.value = null; _suggestion.value = null; _swaps.value = null
        _count.value = null; _filter.value = CountFilter(); _importPreview.value = null; _notice.value = null; _busy.value = false
        _people.value = emptyList()
        writer.clear()
    }

    /** Personen, die andere Module schon kennen (Aufgaben, Höck-Modus); sie ergänzen die Auswahl. */
    fun setExtraPeople(list: List<RessortPerson>) {
        if (list == extraPeople) return
        extraPeople = list
        recomputePeople()
    }

    private fun recomputePeople() {
        _people.value = PlanRules.knownPeople(
            (_plan.value as? ApiResult.Ok)?.value, (_groups.value as? ApiResult.Ok)?.value, (_rooms.value as? ApiResult.Ok)?.value,
            (_trips.value as? ApiResult.Ok)?.value, extraPeople,
        )
    }

    private fun onChange(change: CampChange) {
        if (campId == null) return
        fun touches(vararg prefixes: String) = change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { t -> prefixes.any { t.startsWith(it) } }
        if (touches("teilnehmer") && _participants.value != null) loadParticipants(silent = true)
        if (touches("zimmer", "teilnehmer") && _rooms.value != null) loadRooms(silent = true)
        if (touches("fahrt", "teilnehmer") && _trips.value != null) loadTrips(silent = true)
        if (touches("gruppe", "teilnehmer") && _groups.value != null) loadGroups(silent = true)
        if (touches("dienst", "schicht") && _plan.value != null) loadPlan(silent = true)
        if (touches("diensttausch", "schicht") && _swaps.value != null) loadSwaps(silent = true)
        if (touches("abzaehlen", "teilnehmer") && _count.value != null) loadCount(silent = true)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** `current` fragt, ob die Antwort noch zur Anfrage passt (zum Beispiel der Filter der Abzählliste); sonst fällt sie weg. */
    private fun <T> launchLoad(
        target: MutableStateFlow<ApiResult<T>?>, silent: Boolean, fetch: suspend (String) -> ApiResult<T>,
        after: () -> Unit = {}, current: () -> Boolean = { true },
    ) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) target.value = null
            val result = fetch(id)
            if (campId != id || !current()) return@launch
            if (result is ApiResult.Ok || !silent || target.value == null) target.value = result
            after()
        }
    }

    /** Sendet eine Änderung ohne Version (Zuordnung, Besetzung, Haken, Anlegen) und zeigt einen Fehler mit «Nochmals versuchen». */
    private fun <T> send(retry: () -> Unit, call: suspend (String) -> ApiResult<T>, onOk: (T) -> Unit = {}) {
        val id = campId ?: return
        writer.dismissError()
        _busy.value = true
        viewModelScope.launch {
            val result = call(id)
            _busy.value = false
            // Das Lager ist unterdessen zu oder gewechselt: Weder Fehler noch Folgeaktion gehören noch zu dieser Ansicht.
            if (campId != id) return@launch
            when (result) {
                is ApiResult.Ok -> onOk(result.value)
                is ApiResult.Failure -> writer.fail(result, retry)
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

    // ---- Teilnehmende ----

    fun loadParticipants(silent: Boolean = false): Unit = launchLoad(_participants, silent, api::participants)

    fun createParticipant(first: String, last: String, stage: String, cevi: String, onDone: () -> Unit = {}) {
        send({ createParticipant(first, last, stage, cevi, onDone) }, {
            api.createParticipant(it, buildJsonObject {
                put("vorname", first.trim()); put("nachname", last.trim())
                stage.takeIf { s -> s.isNotBlank() }?.let { s -> put("stufe", s.trim()) }
                cevi.takeIf { s -> s.isNotBlank() }?.let { s -> put("ceviName", s.trim()) }
            })
        }) { loadParticipants(silent = true); onDone() }
    }

    fun changeParticipant(participant: Participant, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}): Unit =
        submit(summary, fields, participant.version, { id, body -> api.changeParticipant(id, participant.id, body) }, { loadParticipants() }) { loadParticipants(silent = true); onDone() }

    fun deleteParticipant(participant: Participant, onDone: () -> Unit = {}): Unit =
        remove(participant.version, { id, v -> api.deleteParticipant(id, participant.copy(version = v)) }, { loadParticipants() }) { loadParticipants(silent = true); onDone() }

    /** «Nicht dabei» oder wieder dabei; der Server nimmt Abgewählte aus ihrer Gruppe. */
    fun setExcluded(ids: List<String>, excluded: Boolean): Unit =
        send({ setExcluded(ids, excluded) }, { api.setExcluded(it, ids, excluded) }) { loadParticipants(silent = true); loadGroups(silent = true) }

    fun previewImport(text: String): Unit = send({ previewImport(text) }, { api.importParticipants(it, text, apply = false) }) { _importPreview.value = it }

    fun applyImport(text: String, onDone: () -> Unit): Unit =
        send({ applyImport(text, onDone) }, { api.importParticipants(it, text, apply = true) }) {
            _importPreview.value = null
            _notice.value = PlanNotice.IMPORTED
            loadParticipants(silent = true)
            onDone()
        }

    fun clearImport() {
        _importPreview.value = null
    }

    // ---- Zimmer und Zelte ----

    fun loadRooms(silent: Boolean = false): Unit = launchLoad(_rooms, silent, api::rooms, ::recomputePeople)

    /** Lädt die Personen, die noch in keinem Zimmer liegen (nur Rollen mit dem Recht zu ändern). */
    fun loadOpenPeople(team: Boolean): Unit = launchLoad(_openPeople, false, { api.openPeople(it, team) })

    fun clearOpenPeople() {
        _openPeople.value = null
    }

    fun createRoom(name: String, kind: RoomKind, seats: Int, note: String, onDone: () -> Unit = {}): Unit =
        send({ createRoom(name, kind, seats, note, onDone) }, {
            api.createRoom(it, buildJsonObject {
                put("name", name.trim()); put("art", kind.wire); put("plaetze", seats)
                note.takeIf { s -> s.isNotBlank() }?.let { s -> put("notiz", s.trim()) }
            })
        }) { loadRooms(silent = true); onDone() }

    fun changeRoom(room: Room, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}): Unit =
        submit(summary, fields, room.version, { id, body -> api.changeRoom(id, room.id, body) }, { loadRooms() }) { loadRooms(silent = true); onDone() }

    fun deleteRoom(room: Room, onDone: () -> Unit = {}): Unit =
        remove(room.version, { id, v -> api.deleteRoom(id, room.copy(version = v)) }, { loadRooms() }) { loadRooms(silent = true); onDone() }

    fun assignToRoom(roomId: String, participantIds: List<String>, personIds: List<String>, onDone: () -> Unit = {}): Unit =
        send({ assignToRoom(roomId, participantIds, personIds, onDone) }, { api.assignToRoom(it, roomId, participantIds, personIds) }) { loadRooms(silent = true); onDone() }

    fun removeFromRoom(roomId: String, member: RoomMember): Unit =
        send({ removeFromRoom(roomId, member) }, { api.removeFromRoom(it, roomId, ids(member, true), ids(member, false)) }) { loadRooms(silent = true) }

    /**
     * Verschiebt eine Person in ein anderes Zimmer: erst heraus, dann hinein (der Server lässt niemanden in zwei Zimmern liegen).
     * Scheitert das Hinein, wiederholt «Nochmals versuchen» nur noch das Hinein; die Liste lädt neu und zeigt, wo die Person liegt.
     */
    fun moveToRoom(fromId: String, toId: String, member: RoomMember, removed: Boolean = false) {
        var gone = removed
        send({ moveToRoom(fromId, toId, member, gone) }, { id ->
            moveInTwoSteps(
                gone,
                { api.removeFromRoom(id, fromId, ids(member, true), ids(member, false)) },
                { api.assignToRoom(id, toId, ids(member, true), ids(member, false)) },
                { gone = true },
            ).also { if (it is ApiResult.Failure && gone) loadRooms(silent = true) }
        }) { loadRooms(silent = true) }
    }

    private fun ids(member: RoomMember, participants: Boolean): List<String> = if (member.isParticipant == participants) listOf(member.id) else emptyList()

    // ---- Fahrten ----

    fun loadTrips(silent: Boolean = false) {
        launchLoad(_trips, silent, api::trips, ::recomputePeople)
        if (rights.seeAll) loadWithoutTrip()
    }

    private fun loadWithoutTrip() {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            val map = buildMap {
                TripDirection.entries.forEach { d -> (api.withoutTrip(id, d) as? ApiResult.Ok)?.let { put(d, it.value) } }
            }
            if (campId == id) _withoutTrip.value = map
        }
    }

    fun createTrip(body: JsonObject, onDone: () -> Unit = {}): Unit = send({ createTrip(body, onDone) }, { api.createTrip(it, body) }) { loadTrips(silent = true); onDone() }

    fun changeTrip(trip: Trip, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}): Unit =
        submit(summary, fields, trip.version, { id, body -> api.changeTrip(id, trip.id, body) }, { loadTrips() }) { loadTrips(silent = true); onDone() }

    fun deleteTrip(trip: Trip, onDone: () -> Unit = {}): Unit =
        remove(trip.version, { id, v -> api.deleteTrip(id, trip.copy(version = v)) }, { loadTrips() }) { loadTrips(silent = true); onDone() }

    fun addPassengers(trip: Trip, candidates: List<TripCandidate>, onDone: () -> Unit = {}): Unit =
        send({ addPassengers(trip, candidates, onDone) }, { id ->
            var last: ApiResult<TripPassengerAdded> = ApiResult.Ok(TripPassengerAdded("", 0))
            for (candidate in candidates) {
                last = api.addPassenger(id, trip.id, candidate)
                if (last is ApiResult.Failure) break
            }
            last
        }) { loadTrips(silent = true); onDone() }

    fun removePassenger(trip: Trip, passenger: TripPerson): Unit =
        remove(passenger.version, { id, v -> api.removePassenger(id, trip.id, passenger.copy(version = v)) }, { loadTrips() }) { loadTrips(silent = true) }

    /**
     * Verschiebt eine mitfahrende Person in eine andere Fahrt derselben Richtung (erst heraus, dann hinein). Scheitert das Hinein,
     * wiederholt «Nochmals versuchen» nur noch das Hinein (die alte Zeile gibt es nicht mehr); die Liste lädt neu.
     */
    fun movePassenger(from: Trip, passenger: TripPerson, to: Trip, removed: Boolean = false) {
        var gone = removed
        send({ movePassenger(from, passenger, to, gone) }, { id ->
            moveInTwoSteps(
                gone,
                { api.removePassenger(id, from.id, passenger) },
                { api.addPassenger(id, to.id, TripCandidate(passenger.personId, passenger.teilnehmerId, passenger.name, passenger.stage)) },
                { gone = true },
            ).also { if (it is ApiResult.Failure && gone) loadTrips(silent = true) }
        }) { loadTrips(silent = true) }
    }

    // ---- Gruppen ----

    fun loadGroups(silent: Boolean = false): Unit = launchLoad(_groups, silent, api::groups, ::recomputePeople)

    fun createGroup(name: String, onDone: () -> Unit = {}): Unit = send({ createGroup(name, onDone) }, { api.createGroup(it, name.trim()) }) { loadGroups(silent = true); onDone() }

    fun changeGroup(group: Group, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}): Unit =
        submit(summary, fields, group.version, { id, body -> api.changeGroup(id, group.id, body) }, { loadGroups() }) { loadGroups(silent = true); onDone() }

    fun deleteGroup(group: Group, onDone: () -> Unit = {}): Unit =
        remove(group.version, { id, v -> api.deleteGroup(id, group.copy(version = v)) }, { loadGroups() }) { loadGroups(silent = true); onDone() }

    /** `groupId` null nimmt die Personen aus ihrer Gruppe. */
    fun assignToGroup(groupId: String?, participantIds: List<String>, personIds: List<String>, leader: Boolean, onDone: () -> Unit = {}): Unit =
        send({ assignToGroup(groupId, participantIds, personIds, leader, onDone) }, { api.assignToGroup(it, groupId, participantIds, personIds, leader) }) {
            loadGroups(silent = true)
            onDone()
        }

    fun previewMix(count: Int): Unit = send({ previewMix(count) }, { api.mixGroups(it, count, apply = false, seed = null) }) { _mix.value = it }

    /** Übernimmt die Vorschau mit demselben Startwert, damit die Einteilung der Vorschau entspricht. */
    fun applyMix(count: Int, seed: Long, onDone: () -> Unit = {}): Unit =
        send({ applyMix(count, seed, onDone) }, { api.mixGroups(it, count, apply = true, seed = seed) }) {
            _mix.value = null
            _notice.value = PlanNotice.MIX_APPLIED
            loadGroups(silent = true)
            onDone()
        }

    fun clearMix() {
        _mix.value = null
    }

    // ---- Schichtplan ----

    fun loadPlan(silent: Boolean = false) {
        launchLoad(_plan, silent, api::shiftPlan, ::recomputePeople)
        launchLoad(_schedule, silent, api::schedule)
    }

    fun createDuty(name: String, sectionId: String, minimum: Int, night: Boolean, onDone: () -> Unit = {}): Unit =
        send({ createDuty(name, sectionId, minimum, night, onDone) }, {
            api.createDuty(it, buildJsonObject {
                put("name", name.trim()); put("tagesabschnittId", sectionId); put("mindestbesetzung", minimum); put("nacht", night)
            })
        }) { loadPlan(silent = true); onDone() }

    fun changeDuty(duty: Duty, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}): Unit =
        submit(summary, fields, duty.version, { id, body -> api.changeDuty(id, duty.id, body) }, { loadPlan() }) { loadPlan(silent = true); onDone() }

    fun deleteDuty(duty: Duty, onDone: () -> Unit = {}): Unit =
        remove(duty.version, { id, v -> api.deleteDuty(id, duty.copy(version = v)) }, { loadPlan() }) { loadPlan(silent = true); onDone() }

    /** Setzt die Besetzung eines Dienstes an einem Tag auf genau diese Personen. */
    fun staffDuty(dayId: String, dutyId: String, personIds: List<String>, participantIds: List<String>, onDone: () -> Unit = {}): Unit =
        send({ staffDuty(dayId, dutyId, personIds, participantIds, onDone) }, { api.staffDuty(it, dayId, dutyId, personIds, participantIds) }) { loadPlan(silent = true); onDone() }

    fun previewSuggestion(withParticipants: Boolean, onlyPresent: Boolean): Unit =
        send({ previewSuggestion(withParticipants, onlyPresent) }, { api.suggestShifts(it, apply = false, seed = null, withParticipants = withParticipants, onlyPresent = onlyPresent) }) { _suggestion.value = it }

    fun applySuggestion(seed: Long, withParticipants: Boolean, onlyPresent: Boolean, onDone: () -> Unit = {}): Unit =
        send({ applySuggestion(seed, withParticipants, onlyPresent, onDone) }, { api.suggestShifts(it, apply = true, seed = seed, withParticipants = withParticipants, onlyPresent = onlyPresent) }) {
            _suggestion.value = null
            _notice.value = PlanNotice.SUGGESTION_APPLIED
            loadPlan(silent = true)
            onDone()
        }

    fun clearSuggestion() {
        _suggestion.value = null
    }

    // ---- Diensttausch ----

    /** Die Leitung sieht alle Anfragen, alle anderen die eigenen. */
    fun loadSwaps(silent: Boolean = false): Unit = launchLoad(_swaps, silent, { if (rights.decideSwap) api.swaps(it) else api.ownSwaps(it) })

    fun requestSwap(shiftId: String, withShiftId: String?, withPersonId: String?, note: String?, onDone: () -> Unit = {}): Unit =
        send({ requestSwap(shiftId, withShiftId, withPersonId, note, onDone) }, { api.requestSwap(it, shiftId, withShiftId, withPersonId, note) }) {
            _notice.value = PlanNotice.SWAP_REQUESTED
            loadSwaps(silent = true)
            onDone()
        }

    fun decideSwap(swap: Swap, accept: Boolean, takeOverBy: String? = null) {
        val id = campId ?: return
        writer.submit(
            "", emptyMap(), swap.version, call = { v -> api.decideSwap(id, swap, v, accept, takeOverBy) },
            onCurrent = { loadSwaps() },
        ) {
            _notice.value = if (accept) PlanNotice.SWAP_ACCEPTED else PlanNotice.SWAP_DECLINED
            loadSwaps(silent = true)
            if (accept) loadPlan(silent = true)
        }
    }

    fun withdrawSwap(swap: Swap) {
        val id = campId ?: return
        writer.submit("", emptyMap(), swap.version, call = { v -> api.withdrawSwap(id, swap, v) }, onCurrent = { loadSwaps() }) {
            _notice.value = PlanNotice.SWAP_WITHDRAWN
            loadSwaps(silent = true)
        }
    }

    // ---- Abzählliste ----

    fun loadCount(silent: Boolean = false) {
        val f = _filter.value
        launchLoad(_count, silent, { api.count(it, f.team, f.stage, f.groupId) }, current = { _filter.value == f })
    }

    fun setFilter(filter: CountFilter) {
        if (filter == _filter.value) return
        _filter.value = filter
        loadCount(silent = false)
    }

    /** Haken sofort sichtbar; scheitert das Senden, springt er zurück und der Fehler erscheint. */
    fun toggleCount(entry: CountEntry) {
        val id = campId ?: return
        val checked = !entry.checked
        writer.dismissError()
        updateEntry(entry.id, checked)
        viewModelScope.launch {
            val result = api.setChecked(id, listOfNotNull(entry.teilnehmerId), listOfNotNull(entry.personId), checked)
            if (result is ApiResult.Failure) {
                updateEntry(entry.id, !checked)
                writer.fail(result) { toggleCount(entry.copy(checked = !checked)) }
            }
        }
    }

    private fun updateEntry(entryId: String, checked: Boolean) {
        _count.update { state ->
            val ok = state as? ApiResult.Ok ?: return@update state
            val page = ok.value
            val before = page.entries.firstOrNull { it.id == entryId } ?: return@update state
            if (before.checked == checked) return@update state
            ApiResult.Ok(
                page.copy(
                    entries = page.entries.map { if (it.id == entryId) it.copy(checked = checked) else it },
                    present = (page.present + if (checked) 1 else -1).coerceIn(0, page.total),
                ),
            )
        }
    }

    fun resetCount(): Unit =
        send({ resetCount() }, { api.resetCount(it) }) {
            _notice.value = PlanNotice.COUNT_RESET
            loadCount(silent = true)
        }
}

private fun nullableString(value: String?): JsonElement = value?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it.trim()) } ?: JsonNull

/** Ein Feld mit Text für eine Änderung: leer wird zu `null` und leert das Feld beim Server. */
fun textField(value: String): JsonElement = nullableString(value)
