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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Ein Hinweis nach einer Aktion. Er bleibt, bis die Person ihn schliesst. */
enum class ArchiveNotice { SCHEDULE_ADOPTED, DOCUMENT_ADDED, DOCUMENT_DELETED, CONTACT_ADDED, CONTACT_DELETED }

/** Ein Aushang, den der Server erzeugt hat; `name` ist die Art («schichtplan») und gibt den Dateinamen. */
class SharedPdf(val name: String, val bytes: ByteArray)

/** Die Eingabe für einen neuen Kontakt; leere Angaben gehen nicht hinaus. */
data class ContactDraft(
    val name: String = "",
    val role: String = "",
    val group: String = "",
    val phone: String = "",
    val email: String = "",
    val note: String = "",
    val keep: Boolean = false,
)

/**
 * «Ablage und Querschnitt»: Zeitplan, Dokumente, Adressbuch, Suche und Aushänge. Der Stand kommt vom Server und bleibt im
 * Arbeitsspeicher der Sitzung (kein lokaler Speicher): Das Adressbuch (Personendaten Dritter) verlässt ihn mit dem Lager nicht
 * früher und wird beim Schliessen des Lagers gelöscht. Der Server nimmt diese Objekte nicht über die Warteschlange an: Jede
 * Änderung geht direkt hinaus, ein Konflikt erscheint über [writer] im Konfliktbildschirm, ein anderer Fehler als Banner mit
 * «Nochmals versuchen».
 */
class ArchiveViewModel(
    private val api: ArchiveApi,
    private val meetingsApi: MeetingsApi,
    private val tasksApi: TasksApi,
    private val camp: CampViewModel,
    private val texts: CommsTexts,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val campId: String? get() = openCamp.value

    val writer = DirectWriter(viewModelScope) { texts.fieldLabels() }

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie. */
    var rights: ArchiveRights = ArchiveRights.of(NO_PERMISSIONS)

    private val _timeline = MutableStateFlow<ApiResult<TimelineData>?>(null)
    val timeline: StateFlow<ApiResult<TimelineData>?> = _timeline.asStateFlow()

    private val _proposal = MutableStateFlow<ScheduleProposal?>(null)

    /** Was dem Zeitplan noch fehlt (Lagertage, Abschnitte); nur für die Lagerleitung, sonst leer. */
    val proposal: StateFlow<ScheduleProposal?> = _proposal.asStateFlow()

    private val _documents = MutableStateFlow<ApiResult<List<CampDocument>>?>(null)
    val documents: StateFlow<ApiResult<List<CampDocument>>?> = _documents.asStateFlow()

    private val _contacts = MutableStateFlow<ApiResult<List<Contact>>?>(null)
    val contacts: StateFlow<ApiResult<List<Contact>>?> = _contacts.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _search = MutableStateFlow<ApiResult<SearchResult>?>(null)

    /** Das Ergebnis der letzten Suche; `null` heisst: noch nicht gesucht. */
    val search: StateFlow<ApiResult<SearchResult>?> = _search.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _printing = MutableStateFlow<Printout?>(null)

    /** Der Aushang, den der Server gerade erzeugt. */
    val printing: StateFlow<Printout?> = _printing.asStateFlow()

    private val _pdf = MutableStateFlow<SharedPdf?>(null)

    /** Das PDF, das der Server erzeugt hat; die Oberfläche teilt es und ruft [pdfShared] auf. */
    val pdf: StateFlow<SharedPdf?> = _pdf.asStateFlow()

    private val _notice = MutableStateFlow<ArchiveNotice?>(null)
    val notice: StateFlow<ArchiveNotice?> = _notice.asStateFlow()

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
        _timeline.value = null; _proposal.value = null; _documents.value = null; _contacts.value = null
        _query.value = ""; _search.value = null; _searching.value = false
        _printing.value = null; _pdf.value = null; _notice.value = null; _busy.value = false
        writer.clear()
    }

    private fun onChange(change: CampChange) {
        if (campId == null) return
        // Ein gelöschter Dokumentlink oder Adressbuch-Kontakt verschwindet sofort aus der Liste.
        change.deletedIds.takeIf { it.isNotEmpty() }?.let { gone ->
            _documents.update { it.withoutIds(gone) { doc -> doc.id } }
            _contacts.update { it.withoutIds(gone) { contact -> contact.id } }
        }
        fun touches(vararg prefixes: String) = change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { t -> prefixes.any { t.startsWith(it) } }
        if (touches("meilenstein", "lagertag", "tagesabschnitt", "hoeck", "aufgabe") && _timeline.value != null) loadTimeline(silent = true)
        if (touches("dokument", "datei") && _documents.value != null) loadDocuments(silent = true)
        if (touches("adressbuch") && _contacts.value != null) loadContacts(silent = true)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun <T> launchLoad(target: MutableStateFlow<ApiResult<T>?>, silent: Boolean, fetch: suspend (String) -> ApiResult<T>) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) target.value = null
            val result = fetch(id)
            if (campId != id) return@launch
            if (replacesState(result, silent, target.value != null)) target.value = result
        }
    }

    /** Sendet eine Änderung ohne Version und zeigt einen Fehler mit «Nochmals versuchen». */
    private fun <T> send(retry: () -> Unit, call: suspend (String) -> ApiResult<T>, onOk: (T) -> Unit = {}) {
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
                    writer.fail(result, retry)
                }
            }
        }
    }

    // ---- Zeitplan ----

    /** Der Zeitplan, die Höcks und die Aufgaben mit Frist; nur der Zeitplan ist Pflicht, fällt ein anderer Teil aus, fehlt nur er. */
    fun loadTimeline(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _timeline.value = null
            val result = coroutineScope {
                val schedule = async { api.schedule(id) }
                val meetings = async { meetingsApi.meetings(id) }
                val tasks = async { tasksApi.listAll(id) }
                when (val s = schedule.await()) {
                    is ApiResult.Failure -> s
                    is ApiResult.Ok -> ApiResult.Ok(
                        TimelineData(
                            s.value,
                            (meetings.await() as? ApiResult.Ok)?.value.orEmpty(),
                            (tasks.await() as? ApiResult.Ok)?.value.orEmpty(),
                        ),
                    )
                }
            }
            if (campId != id) return@launch
            if (replacesState(result, silent, _timeline.value != null)) _timeline.value = result
            if (rights.editSchedule && result is ApiResult.Ok) {
                val proposal = (api.proposal(id) as? ApiResult.Ok)?.value?.takeUnless { it.isEmpty }
                // Der Vorschlag gehört zum Lager der Anfrage: «Übernehmen» legt sonst Tage im falschen Lager an.
                if (campId == id) _proposal.value = proposal
            }
        }
    }

    /** Legt die vorgeschlagenen Lagertage und Abschnitte an; die Lagerleitung bestätigt damit den Vorschlag. */
    fun adoptProposal() {
        val proposal = _proposal.value ?: return
        send({ adoptProposal() }, { api.adoptProposal(it, proposal) }) {
            _proposal.value = null
            _notice.value = ArchiveNotice.SCHEDULE_ADOPTED
            loadTimeline(silent = true)
        }
    }

    // ---- Dokumente ----

    fun loadDocuments(silent: Boolean = false): Unit = launchLoad(_documents, silent, api::documents)

    /** Legt die Verknüpfung und das Dokument an; der Server prüft den Link streng (nur https, erlaubte Hosts). */
    fun addDocument(title: String, link: String, teamVisible: Boolean, onDone: () -> Unit) {
        send(
            { addDocument(title, link, teamVisible, onDone) },
            { id ->
                when (val file = api.addFile(id, title, link)) {
                    is ApiResult.Failure -> file
                    is ApiResult.Ok -> api.addDocument(id, title, file.value.id, teamVisible)
                }
            },
        ) {
            _notice.value = ArchiveNotice.DOCUMENT_ADDED
            loadDocuments(silent = true)
            onDone()
        }
    }

    fun deleteDocument(document: CampDocument) {
        val version = document.version ?: return
        writer.dismissError()
        val id = campId ?: return
        writer.submit("", emptyMap(), version, call = { v -> api.deleteDocument(id, document.id, v) }, onCurrent = { loadDocuments() }) {
            _notice.value = ArchiveNotice.DOCUMENT_DELETED
            loadDocuments(silent = true)
        }
    }

    // ---- Adressbuch ----

    fun loadContacts(silent: Boolean = false): Unit = launchLoad(_contacts, silent, api::contacts)

    fun addContact(draft: ContactDraft, onDone: () -> Unit) {
        val body = buildJsonObject {
            put("name", draft.name.trim())
            listOf("funktion" to draft.role, "gruppe" to draft.group, "telefon" to draft.phone, "email" to draft.email, "notiz" to draft.note)
                .forEach { (key, value) -> value.trim().takeIf { it.isNotEmpty() }?.let { put(key, it) } }
            if (draft.keep) put("behalten", true)
        }
        send({ addContact(draft, onDone) }, { api.addContact(it, body) }) {
            _notice.value = ArchiveNotice.CONTACT_ADDED
            loadContacts(silent = true)
            onDone()
        }
    }

    /** «Fürs nächste Lager behalten»: nimmt den Kontakt aus der Aufräumfrist oder stellt sie wieder her. */
    fun setKeep(contact: Contact, keep: Boolean) {
        val id = campId ?: return
        val fields = mapOf<String, JsonElement>("behalten" to JsonPrimitive(keep))
        writer.submit(
            contact.name, fields, contact.version,
            call = { v -> api.changeContact(id, contact.id, buildJsonObject { put("version", v); put("behalten", keep) }) },
            onCurrent = { loadContacts() },
        ) { changed ->
            _contacts.update { state -> (state as? ApiResult.Ok)?.let { ok -> ApiResult.Ok(ok.value.map { if (it.id == changed.id) changed else it }) } ?: state }
        }
    }

    fun deleteContact(contact: Contact) {
        val id = campId ?: return
        writer.dismissError()
        writer.submit("", emptyMap(), contact.version, call = { v -> api.deleteContact(id, contact.id, v) }, onCurrent = { loadContacts() }) {
            _notice.value = ArchiveNotice.CONTACT_DELETED
            loadContacts(silent = true)
        }
    }

    // ---- Suche ----

    fun setQuery(text: String) {
        _query.value = text
    }

    /** Sucht auf dem Server (er filtert nach Rolle). Weniger als zwei Zeichen suchen nicht; ohne Netz bleibt der Fehler stehen. */
    fun runSearch() {
        val id = campId ?: return
        val text = _query.value
        if (!SearchRules.valid(text)) {
            _search.value = null
            // Eine Suche, die noch läuft, hat ihr Ergebnis verloren (der Text hat sich geändert): Der Ladekreis darf nicht stehen bleiben.
            _searching.value = false
            return
        }
        _searching.value = true
        viewModelScope.launch {
            val result = api.search(id, text)
            if (campId == id && _query.value == text) {
                _search.value = result
                _searching.value = false
            }
        }
    }

    fun clearSearch() {
        _query.value = ""
        _search.value = null
        _searching.value = false
    }

    // ---- Aushänge ----

    /** Lässt den Server den Aushang als PDF erzeugen; die Oberfläche teilt ihn über die Systemauswahl. */
    fun loadPrintout(printout: Printout) {
        val id = campId ?: return
        if (_printing.value != null) return
        writer.dismissError()
        _printing.value = printout
        viewModelScope.launch {
            val result = api.printout(id, printout)
            // Das Lager ist unterdessen gewechselt: Das PDF gehört nicht mehr zu dieser Ansicht (`reset` hat «printing» schon geleert).
            if (campId != id) return@launch
            when (result) {
                is ApiResult.Ok -> _pdf.value = SharedPdf(printout.wire, result.value)
                is ApiResult.Failure -> writer.fail(result) { loadPrintout(printout) }
            }
            _printing.value = null
        }
    }

    fun pdfShared() {
        _pdf.value = null
    }
}
