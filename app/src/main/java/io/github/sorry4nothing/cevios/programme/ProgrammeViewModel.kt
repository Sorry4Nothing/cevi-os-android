package io.github.sorry4nothing.cevios.programme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiFehler
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Zustand beim Speichern einer Antwort («ja» oder «nein»). `gesendet` merkt sich pro Kind (ID) oder für die eigene
 * Anmeldung ([SCHLUESSEL_LEITUNG]) die Uhrzeit des letzten Sendens: «Gesendet 12:10».
 */
data class Antwortstand(
    val laeuft: Boolean = false,
    val fehler: ApiFehler? = null,
    val gesendet: Map<String, String> = emptyMap(),
)

const val SCHLUESSEL_LEITUNG = "leitung"

private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm")

/** `null` heisst: wird geladen. Die Daten bleiben nur im Arbeitsspeicher. */
class ProgrammeViewModel(private val api: ProgrammeApi) : ViewModel() {
    private val _liste = MutableStateFlow<ApiResult<ProgrammListe>?>(null)
    val liste: StateFlow<ApiResult<ProgrammListe>?> = _liste.asStateFlow()

    private val _detail = MutableStateFlow<ApiResult<Programm>?>(null)
    val detail: StateFlow<ApiResult<Programm>?> = _detail.asStateFlow()

    private val _antwort = MutableStateFlow(Antwortstand())
    val antwort: StateFlow<Antwortstand> = _antwort.asStateFlow()

    private var detailJob: Job? = null
    private var offeneId: String? = null

    init {
        listeLaden()
    }

    fun listeLaden() {
        _liste.value = null
        viewModelScope.launch {
            val heute = LocalDate.now(SCHWEIZ)
            _liste.value = api.liste(heute, heute.plusMonths(FENSTER_MONATE))
        }
    }

    fun detailLaden(id: String) {
        detailJob?.cancel()
        offeneId = id
        _detail.value = null
        _antwort.value = Antwortstand()
        detailJob = viewModelScope.launch { _detail.value = api.lesen(id) }
    }

    fun kindAntworten(programmId: String, kindId: String, ja: Boolean) =
        speichern(programmId, kindId) { api.teilnahmeSetzen(programmId, kindId, ja) }

    fun leitungAntworten(programmId: String, ja: Boolean) =
        speichern(programmId, SCHLUESSEL_LEITUNG) { api.leitungSetzen(programmId, ja) }

    /**
     * Speichert und liest das Programm danach neu: Der Server ist die einzige Wahrheit über den Stand. Bei einem Fehler
     * bleibt der alte Stand sichtbar, die Pillen springen also zurück.
     */
    private fun speichern(programmId: String, schluessel: String, aufruf: suspend () -> ApiResult<Unit>) {
        if (_antwort.value.laeuft) return
        val bisher = _antwort.value.gesendet
        _antwort.value = Antwortstand(laeuft = true, gesendet = bisher)
        viewModelScope.launch {
            when (val ergebnis = aufruf()) {
                is ApiResult.Fehler -> _antwort.value = Antwortstand(fehler = ergebnis.fehler, gesendet = bisher)
                is ApiResult.Ok -> {
                    _antwort.value = Antwortstand(gesendet = bisher + (schluessel to UHRZEIT.format(LocalTime.now(SCHWEIZ))))
                    val neu = api.lesen(programmId)
                    if (offeneId == programmId) _detail.value = neu
                }
            }
        }
    }

    private companion object {
        const val FENSTER_MONATE = 6L
    }
}
