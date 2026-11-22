package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Ein Konflikt, den die Person lösen muss: die Gegenüberstellung für den `ConflictScreen` und die zwei Wege hinaus. */
class DirectConflict(val change: QueuedChange, val keepMine: () -> Unit, val takeCurrent: () -> Unit)

/**
 * Schickt Änderungen an Objekten, die der Server nicht über die Warteschlange annimmt (Höcks, Traktanden, Pinnwand …), und
 * kümmert sich um das, was dabei schiefgehen kann:
 *  - `VERSION_KONFLIKT` ohne abweichende Felder: die Änderung geht still mit der neuen Version noch einmal hinaus.
 *  - `VERSION_KONFLIKT` mit abweichenden Feldern: [conflict] zeigt «Deine Fassung» und «Aktueller Stand»; ein Konflikt verschwindet
 *    nie still, die Person behält ihre Fassung (Versand mit der neuen Version) oder übernimmt die aktuelle.
 *  - jeder andere Fehler: [error] mit «Nochmals versuchen».
 */
class DirectWriter(private val scope: CoroutineScope, private val labels: () -> Map<String, String>) {
    private val _conflict = MutableStateFlow<DirectConflict?>(null)
    val conflict: StateFlow<DirectConflict?> = _conflict.asStateFlow()

    private val _error = MutableStateFlow<ActionError?>(null)
    val error: StateFlow<ActionError?> = _error.asStateFlow()

    fun showError(error: ActionError) {
        _error.value = error
    }

    fun dismissError() {
        _error.value = null
    }

    fun clear() {
        _conflict.value = null
        _error.value = null
    }

    /**
     * `call` bekommt die Version, mit der gesendet wird. `sent` sind die Felder, die die Person ändert (für die Gegenüberstellung);
     * `summary` ist der Satz dazu. `onCurrent` lädt den Stand des Servers neu, wenn die Person die aktuelle Fassung übernimmt.
     */
    fun <T> submit(
        summary: String,
        sent: Map<String, JsonElement>,
        version: Long,
        call: suspend (Long) -> ApiResult<T>,
        onCurrent: () -> Unit,
        onOk: (T) -> Unit,
    ) {
        _error.value = null
        scope.launch {
            when (val result = call(version)) {
                is ApiResult.Ok -> onOk(result.value)
                is ApiResult.Failure -> {
                    val conflict = result.conflict
                    val newVersion = conflict?.let(::conflictVersion)
                    if (result.error == ApiError.VERSION_CONFLICT && conflict != null && newVersion != null) {
                        val differing = conflict.differing.filter { it in sent.keys }
                        when {
                            // Nichts, was die Person ändert, hat sich unterdessen geändert: erneut senden.
                            differing.isEmpty() && sent.isNotEmpty() && newVersion != version -> submit(summary, sent, newVersion, call, onCurrent, onOk)
                            // Eine Löschung hat nichts zum Gegenüberstellen: Der Stand wird neu geladen, die Person sieht den Grund.
                            sent.isEmpty() -> {
                                onCurrent()
                                _error.value = ActionError(result.error, null, result.code)
                            }
                            else -> _conflict.value = DirectConflict(
                                change = directConflict(summary, sent, conflict, labels()),
                                keepMine = {
                                    _conflict.value = null
                                    submit(summary, sent, newVersion, call, onCurrent, onOk)
                                },
                                takeCurrent = {
                                    _conflict.value = null
                                    onCurrent()
                                },
                            )
                        }
                    } else {
                        _error.value = ActionError(domainError(result.error), { submit(summary, sent, version, call, onCurrent, onOk) }, result.code)
                    }
                }
            }
        }
    }
}

/** Die 409-Codes der Programme (Anmeldeschluss, Programmbeginn) gelten im Lager nicht: Dort steht der Code, der Text ist allgemein. */
fun domainError(error: ApiError): ApiError = when (error) {
    ApiError.REGISTRATION_CLOSED, ApiError.PROGRAM_STARTED -> ApiError.UNKNOWN
    else -> error
}
