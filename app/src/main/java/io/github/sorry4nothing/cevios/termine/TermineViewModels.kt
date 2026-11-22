package io.github.sorry4nothing.cevios.termine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * Der persönliche Kalenderlink im Profil. Den Klartext des Tokens gibt es nur direkt nach dem Erzeugen und nur hier im
 * Arbeitsspeicher ([fresh]); [forget] löscht ihn, sobald die Person das Profil verlässt. Er steht nie in einem Log.
 */
class CalendarLinkViewModel(private val api: TermineApi) : ViewModel() {
    data class Busy(val running: Boolean = false, val error: ApiError? = null)

    private val _status = MutableStateFlow<ApiResult<CalendarLinkStatus>?>(null)
    val status: StateFlow<ApiResult<CalendarLinkStatus>?> = _status.asStateFlow()

    private val _fresh = MutableStateFlow<CalendarLinkNew?>(null)
    val fresh: StateFlow<CalendarLinkNew?> = _fresh.asStateFlow()

    private val _busy = MutableStateFlow(Busy())
    val busy: StateFlow<Busy> = _busy.asStateFlow()

    fun load() {
        if (_status.value == null) fetch()
    }

    fun reload() {
        _status.value = null
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch { _status.value = api.linkStatus() }
    }

    /** Die ganze Adresse des frisch erzeugten Links, `null`, wenn es keinen gibt. */
    fun url(): String? = _fresh.value?.let { api.linkUrl(it.path) }

    /** Erzeugt einen neuen Link; ein bisheriger ist sofort ungültig. */
    fun create() {
        if (_busy.value.running) return
        _busy.value = Busy(running = true)
        viewModelScope.launch {
            when (val result = api.createLink()) {
                is ApiResult.Failure -> _busy.value = Busy(error = result.error)
                is ApiResult.Ok -> {
                    _fresh.value = result.value
                    _status.value = ApiResult.Ok(CalendarLinkStatus(true, result.value.createdAt, null, result.value.ending))
                    _busy.value = Busy()
                }
            }
        }
    }

    fun revoke() {
        if (_busy.value.running) return
        _busy.value = Busy(running = true)
        viewModelScope.launch {
            when (val result = api.revokeLink()) {
                is ApiResult.Failure -> _busy.value = Busy(error = result.error)
                is ApiResult.Ok -> {
                    _fresh.value = null
                    _status.value = ApiResult.Ok(CalendarLinkStatus(false))
                    _busy.value = Busy()
                }
            }
        }
    }

    fun dismissError() {
        if (!_busy.value.running) _busy.value = Busy()
    }

    fun forget() {
        _fresh.value = null
    }
}

/** «Termin vorschlagen» mit der Liste der eigenen Vorschläge. Die Eingabe bleibt im Arbeitsspeicher, bis sie gesendet ist. */
class ProposalsViewModel(private val api: TermineApi, private val today: () -> LocalDate = { LocalDate.now(SWISS_ZONE) }) : ViewModel() {
    /** `problems` ist `null`, solange die Person noch nicht zu senden versucht hat: Vorher steht kein Fehler am Feld. */
    data class SendState(
        val running: Boolean = false,
        val error: ApiError? = null,
        val code: String? = null,
        val problems: Set<ProposalProblem>? = null,
        /** Die ID des gesendeten Vorschlags, solange die Erfolgsmeldung steht (für «Rückgängig»). */
        val sentId: String? = null,
        val undone: Boolean = false,
    )

    private val _options = MutableStateFlow<ApiResult<ProposalOptions>?>(null)
    val options: StateFlow<ApiResult<ProposalOptions>?> = _options.asStateFlow()

    private val _mine = MutableStateFlow<ApiResult<ProposalPage>?>(null)
    val mine: StateFlow<ApiResult<ProposalPage>?> = _mine.asStateFlow()

    private val _draft = MutableStateFlow(ProposalDraft())
    val draft: StateFlow<ProposalDraft> = _draft.asStateFlow()

    private val _send = MutableStateFlow(SendState())
    val send: StateFlow<SendState> = _send.asStateFlow()

    /** Wahr, sobald bekannt ist, ob die Person vorschlagen darf; für den Einstieg im Profil. */
    fun loadOptions() {
        viewModelScope.launch { _options.value = api.proposalOptions() }
    }

    fun reloadOptions() {
        _options.value = null
        loadOptions()
    }

    fun loadMine() {
        viewModelScope.launch { _mine.value = api.myProposals() }
    }

    fun edit(change: (ProposalDraft) -> ProposalDraft) {
        _draft.update(change)
        // Wer nach einem Fehler weitertippt, sieht die Meldung am Feld verschwinden, sobald es stimmt.
        _send.update { state -> state.problems?.let { state.copy(problems = proposalProblems(_draft.value, today())) } ?: state }
    }

    fun submit() {
        val state = _send.value
        if (state.running) return
        val draft = _draft.value
        val problems = proposalProblems(draft, today())
        if (problems.isNotEmpty()) {
            _send.value = SendState(problems = problems)
            return
        }
        val request = proposalRequest(draft, today()) ?: return
        _send.value = SendState(running = true, problems = emptySet())
        viewModelScope.launch {
            when (val result = api.propose(request)) {
                is ApiResult.Failure -> _send.value = SendState(error = result.error, code = result.code, problems = emptySet())
                is ApiResult.Ok -> {
                    _draft.value = ProposalDraft()
                    _send.value = SendState(sentId = result.value.id)
                    loadMine()
                }
            }
        }
    }

    /** «Rückgängig» nach dem Senden: zieht den Vorschlag zurück, solange er offen ist. */
    fun undo() {
        val id = _send.value.sentId ?: return
        viewModelScope.launch {
            when (val result = api.withdraw(id)) {
                is ApiResult.Failure -> _send.value = SendState(error = result.error, code = result.code, problems = emptySet())
                is ApiResult.Ok -> {
                    _send.value = SendState(undone = true)
                    loadMine()
                }
            }
        }
    }

    fun withdraw(id: String) {
        viewModelScope.launch {
            when (val result = api.withdraw(id)) {
                is ApiResult.Failure -> _send.value = SendState(error = result.error, code = result.code, problems = emptySet())
                is ApiResult.Ok -> loadMine()
            }
        }
    }

    fun dismissNotice() {
        if (!_send.value.running) _send.value = SendState()
    }
}

/** Die Anmeldung zu einem Anlass: der Zustand je Teilnehmende (ein Kind oder die Person selbst). */
class EventViewModel(private val api: TermineApi) : ViewModel() {
    enum class Done { REGISTERED, CHANGED, UNREGISTERED }

    data class ParticipantState(
        val answers: Map<String, JsonElement> = emptyMap(),
        val running: Boolean = false,
        val error: ApiError? = null,
        /** Schlüssel der Felder, die noch fehlen oder falsch sind; `null`, solange die Person nicht zu senden versucht hat. */
        val problems: Set<String>? = null,
        /** `true`, wenn der Server die Antworten ablehnte, ohne dass die App ein Feld nennen kann (400). */
        val rejected: Boolean = false,
        val done: Done? = null,
    )

    private val _form = MutableStateFlow<ApiResult<EventForm>?>(null)
    val form: StateFlow<ApiResult<EventForm>?> = _form.asStateFlow()

    private val _states = MutableStateFlow<Map<String, ParticipantState>>(emptyMap())
    val states: StateFlow<Map<String, ParticipantState>> = _states.asStateFlow()

    private var openedId: String? = null

    fun load(id: String) {
        openedId = id
        _form.value = null
        _states.value = emptyMap()
        viewModelScope.launch {
            val result = api.event(id)
            if (openedId != id) return@launch
            _form.value = result
            if (result is ApiResult.Ok) {
                _states.value = result.value.participants.associate { keyOf(it.childId) to ParticipantState(answers = it.registration?.takeIf { r -> r.registered }?.answers.orEmpty()) }
            }
        }
    }

    fun setAnswer(childId: String?, key: String, value: JsonElement) = update(childId) { state ->
        val answers = state.answers + (key to value)
        val fields = (form.value as? ApiResult.Ok)?.value?.fields.orEmpty()
        state.copy(answers = answers, done = null, error = null, rejected = false, problems = state.problems?.let { missingRequired(fields, answers) })
    }

    fun setText(childId: String?, key: String, text: String) = setAnswer(childId, key, JsonPrimitive(text))

    fun setChoice(childId: String?, key: String, value: Boolean) = setAnswer(childId, key, JsonPrimitive(value))

    fun submit(childId: String?) {
        val form = (_form.value as? ApiResult.Ok)?.value ?: return
        val id = openedId ?: return
        val state = _states.value[keyOf(childId)] ?: return
        if (state.running) return
        val missing = missingRequired(form.fields, state.answers)
        if (missing.isNotEmpty()) {
            update(childId) { it.copy(problems = missing) }
            return
        }
        val wasRegistered = form.participants.firstOrNull { it.childId == childId }?.registration?.registered == true
        update(childId) { it.copy(running = true, error = null, rejected = false, problems = emptySet(), done = null) }
        viewModelScope.launch {
            val body = EventRegistrationRequest(childId, cleanAnswers(form.fields, state.answers))
            when (val result = api.register(id, body)) {
                is ApiResult.Ok -> {
                    setRegistration(childId, result.value)
                    update(childId) { it.copy(running = false, done = if (wasRegistered) Done.CHANGED else Done.REGISTERED) }
                }
                is ApiResult.Failure -> {
                    // Der Anmeldeschluss ist unterdessen vorbei: Das Formular wechselt in den gesperrten Zustand.
                    if (result.error == ApiError.REGISTRATION_CLOSED) _form.update { (it as? ApiResult.Ok)?.let { ok -> ApiResult.Ok(ok.value.copy(open = false)) } ?: it }
                    update(childId) { it.copy(running = false, error = result.error, rejected = result.code == "ANTWORTEN_UNGUELTIG" || (result.error == ApiError.UNKNOWN && result.code != null)) }
                }
            }
        }
    }

    /** Meldet ab (auch als «Rückgängig» nach dem Anmelden). */
    fun unregister(childId: String?) {
        val id = openedId ?: return
        if (_states.value[keyOf(childId)]?.running == true) return
        update(childId) { it.copy(running = true, error = null, done = null) }
        viewModelScope.launch {
            val result = if (childId == null) api.unregister(id) else api.unregisterChild(id, childId)
            when (result) {
                is ApiResult.Ok -> {
                    setRegistration(childId, null)
                    update(childId) { it.copy(running = false, answers = emptyMap(), done = Done.UNREGISTERED, problems = null) }
                }
                is ApiResult.Failure -> update(childId) { it.copy(running = false, error = result.error) }
            }
        }
    }

    fun dismiss(childId: String?) = update(childId) { it.copy(done = null, error = null, rejected = false) }

    private fun setRegistration(childId: String?, registration: EventRegistration?) {
        _form.update { current ->
            val ok = current as? ApiResult.Ok ?: return@update current
            ApiResult.Ok(ok.value.copy(participants = ok.value.participants.map { if (it.childId == childId) it.copy(registration = registration) else it }))
        }
    }

    private fun update(childId: String?, change: (ParticipantState) -> ParticipantState) {
        _states.update { current -> current + (keyOf(childId) to change(current[keyOf(childId)] ?: ParticipantState())) }
    }

    companion object {
        private const val PERSON = "person"

        fun keyOf(childId: String?) = childId ?: PERSON
    }
}
