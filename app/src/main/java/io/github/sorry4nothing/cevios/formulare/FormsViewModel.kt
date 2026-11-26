package io.github.sorry4nothing.cevios.formulare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.Background
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Warum das Senden nicht geklappt hat; jeder Grund hat einen eigenen Satz. */
enum class SendFailure { NETWORK, TOO_FAST, ALREADY, FULL, CLOSED, TOO_OFTEN, LOCKED, MAINTENANCE, FORBIDDEN, NOT_FOUND, INVALID, OTHER }

/** Ordnet die Antwort des Servers einem Grund zu (Status und Code, nie der Text des Servers). */
fun sendFailureOf(failure: ApiResult.Failure): SendFailure = when {
    failure.error == ApiError.NETWORK -> SendFailure.NETWORK
    failure.status == 422 -> if (failure.code == "ZU_SCHNELL") SendFailure.TOO_FAST else SendFailure.INVALID
    failure.status == 409 -> when (failure.code) {
        "BEREITS_BEANTWORTET" -> SendFailure.ALREADY
        "FORMULAR_VOLL" -> SendFailure.FULL
        else -> SendFailure.OTHER
    }
    failure.status == 410 -> SendFailure.CLOSED
    failure.status == 423 -> SendFailure.LOCKED
    failure.status == 429 -> SendFailure.TOO_OFTEN
    failure.status == 503 && failure.code == "IN_WARTUNG" -> SendFailure.MAINTENANCE
    failure.status == 403 -> SendFailure.FORBIDDEN
    failure.status == 404 -> SendFailure.NOT_FOUND
    else -> SendFailure.OTHER
}

/** Ein Grund, bei dem ein zweiter Tipp auf «Nochmals versuchen» sinnvoll ist; nie von selbst. */
fun SendFailure.canRetry(): Boolean = this == SendFailure.NETWORK || this == SendFailure.TOO_FAST || this == SendFailure.OTHER

/**
 * Die Formulare der Website: Liste, ein Formular ausfüllen, senden. Die Eingaben liegen nur hier im Arbeitsspeicher und gehen mit
 * [close] und mit der Sitzung weg. Gesendet wird genau einmal je Tipp: Solange eine Antwort aussteht, nimmt [submit] nichts an, und die
 * App wiederholt nie von selbst (die Antwort ist ein Eintrag beim Verein, kein Idempotenzschlüssel schützt vor einem Doppel).
 */
class FormsViewModel(
    private val api: FormsApi,
    /** Millisekunden, nur zum Messen (monoton). */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    /** `problems` ist `null`, solange die Person noch nicht zu senden versucht hat: Vorher steht kein Fehler am Feld. */
    data class SendState(
        val running: Boolean = false,
        val problems: Map<String, FormProblem>? = null,
        val failure: SendFailure? = null,
        val done: Boolean = false,
    )

    private val _list = MutableStateFlow<ApiResult<FormList>?>(null)
    val list: StateFlow<ApiResult<FormList>?> = _list.asStateFlow()

    private val _definition = MutableStateFlow<ApiResult<FormDefinition>?>(null)
    val definition: StateFlow<ApiResult<FormDefinition>?> = _definition.asStateFlow()

    private val _values = MutableStateFlow<Map<String, FormValue>>(emptyMap())
    val values: StateFlow<Map<String, FormValue>> = _values.asStateFlow()

    private val _send = MutableStateFlow(SendState())
    val send: StateFlow<SendState> = _send.asStateFlow()

    private var openedId: String? = null
    private var shownAt = 0L

    /** Prüft still, ob es offene Formulare gibt (für die Zeile im Profil); ein Fehler zeigt nichts. */
    fun check() {
        viewModelScope.launch(Background()) { _list.value = api.list() }
    }

    /** Beim Öffnen der Liste und bei «Nochmals versuchen»: zeigt bis dahin das Skelett. */
    fun reloadList() {
        _list.value = null
        viewModelScope.launch { _list.value = api.list() }
    }

    fun open(id: String) {
        openedId = id
        _definition.value = null
        _values.value = emptyMap()
        _send.value = SendState()
        viewModelScope.launch {
            val result = api.definition(id)
            if (openedId != id) return@launch
            _definition.value = result
            // Die Zeit zählt ab dem Moment, in dem das Formular da ist, wie beim Laden der Seite im Web.
            shownAt = clock()
        }
    }

    /** Schliesst das Formular und vergisst die Eingaben. */
    fun close() {
        openedId = null
        _definition.value = null
        _values.value = emptyMap()
        _send.value = SendState()
    }

    fun setText(fieldId: String, text: String) = change(fieldId, FormValue.Text(text))

    fun setFlag(fieldId: String, on: Boolean) = change(fieldId, FormValue.Flag(on))

    fun toggleChoice(fieldId: String, option: String) {
        val chosen = (_values.value[fieldId] as? FormValue.Multi)?.chosen.orEmpty()
        change(fieldId, FormValue.Multi(if (option in chosen) chosen - option else chosen + option))
    }

    private fun change(fieldId: String, value: FormValue) {
        if (_send.value.running) return
        _values.update { it + (fieldId to value) }
        // Nach einem Fehlversuch prüft die App das geänderte Feld sofort neu, damit der Hinweis verschwindet, sobald es stimmt.
        val form = (_definition.value as? ApiResult.Ok)?.value ?: return
        _send.update { state ->
            val shown = state.problems ?: return@update state.copy(failure = null, done = false)
            val fresh = FormRules.problems(form, _values.value)[fieldId]
            state.copy(problems = if (fresh == null) shown - fieldId else shown + (fieldId to fresh), failure = null, done = false)
        }
    }

    fun dismiss() = _send.update { it.copy(failure = null) }

    fun submit() {
        val form = (_definition.value as? ApiResult.Ok)?.value ?: return
        val id = openedId ?: return
        if (_send.value.running || !FormRules.canSend(form)) return
        val problems = FormRules.problems(form, _values.value)
        if (problems.isNotEmpty()) {
            _send.value = SendState(problems = problems)
            return
        }
        _send.value = SendState(running = true, problems = emptyMap())
        val answers = FormRules.body(form, _values.value)
        viewModelScope.launch {
            // Ein sehr schnelles Formular wartet die fehlende Zeit ab, statt die Zeit zu erfinden: Gemessen wird, was wirklich vergangen ist.
            delay(FormRules.waitBeforeSend(clock() - shownAt))
            val result = api.send(id, FormAnswerRequest(answers, homepage = "", durationMs = (clock() - shownAt).coerceAtLeast(0)))
            if (openedId != id) return@launch
            when (result) {
                is ApiResult.Ok -> {
                    _values.value = emptyMap()
                    _definition.update { (it as? ApiResult.Ok)?.let { ok -> ApiResult.Ok(ok.value.copy(answered = true)) } ?: it }
                    _send.value = SendState(done = true)
                    check()
                }
                is ApiResult.Failure -> {
                    val failure = sendFailureOf(result)
                    _definition.update { current ->
                        val ok = current as? ApiResult.Ok ?: return@update current
                        when (failure) {
                            SendFailure.ALREADY -> ApiResult.Ok(ok.value.copy(answered = true))
                            SendFailure.CLOSED -> ApiResult.Ok(ok.value.copy(closed = true))
                            else -> current
                        }
                    }
                    val server = result.fieldErrors.associate { it.field to FormProblem.fromCode(it.code) }
                    _send.value = SendState(problems = server.takeIf { failure == SendFailure.INVALID } ?: emptyMap(), failure = failure)
                }
            }
        }
    }
}
