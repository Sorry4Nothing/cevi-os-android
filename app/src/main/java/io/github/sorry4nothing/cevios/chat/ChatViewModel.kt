package io.github.sorry4nothing.cevios.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiFehler
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Zustand beim Senden einer Nachricht. */
data class Sendestand(val laeuft: Boolean = false, val fehler: ApiFehler? = null)

/** `null` heisst: wird geladen. Nachrichten und Namen bleiben nur im Arbeitsspeicher. */
class ChatViewModel(private val api: ChatApi) : ViewModel() {
    private val _liste = MutableStateFlow<ApiResult<ChatListe>?>(null)
    val liste: StateFlow<ApiResult<ChatListe>?> = _liste.asStateFlow()

    private val _verlauf = MutableStateFlow<ApiResult<Verlauf>?>(null)
    val verlauf: StateFlow<ApiResult<Verlauf>?> = _verlauf.asStateFlow()

    private val _aeltereLaedt = MutableStateFlow(false)
    val aeltereLaedt: StateFlow<Boolean> = _aeltereLaedt.asStateFlow()

    private val _senden = MutableStateFlow(Sendestand())
    val senden: StateFlow<Sendestand> = _senden.asStateFlow()

    private var offeneId: String? = null
    private var verlaufJob: Job? = null

    init {
        listeLaden()
    }

    /** Mit `still` bleibt die alte Liste sichtbar, bis die neue da ist. */
    fun listeLaden(still: Boolean = false) {
        if (!still) _liste.value = null
        viewModelScope.launch { _liste.value = api.chats() }
    }

    fun chatOeffnen(id: String) {
        verlaufJob?.cancel()
        offeneId = id
        _verlauf.value = null
        _aeltereLaedt.value = false
        _senden.value = Sendestand()
        verlaufJob = viewModelScope.launch { _verlauf.value = api.nachrichten(id).mapVerlauf(Verlauf()) }
    }

    fun chatSchliessen() {
        verlaufJob?.cancel()
        offeneId = null
    }

    /** Holt die neueste Seite und führt sie mit dem Geladenen zusammen (von Hand oder im Takt). */
    fun aktualisieren() {
        val id = offeneId ?: return
        viewModelScope.launch {
            val ergebnis = api.nachrichten(id)
            if (offeneId != id) return@launch
            // Ein Fehler im Takt ersetzt einen guten Verlauf nicht; nur ein fehlender Verlauf zeigt den Fehler.
            val bisher = (_verlauf.value as? ApiResult.Ok)?.wert
            if (bisher == null || ergebnis is ApiResult.Ok) _verlauf.value = ergebnis.mapVerlauf(bisher ?: Verlauf())
        }
    }

    fun aeltereLaden() {
        val id = offeneId ?: return
        val bisher = (_verlauf.value as? ApiResult.Ok)?.wert ?: return
        val cursor = bisher.naechster ?: return
        if (_aeltereLaedt.value) return
        _aeltereLaedt.value = true
        viewModelScope.launch {
            val ergebnis = api.nachrichten(id, vor = cursor)
            if (offeneId == id) {
                if (ergebnis is ApiResult.Ok) _verlauf.value = ApiResult.Ok(bisher.mitAelterer(ergebnis.wert))
                _aeltereLaedt.value = false
            }
        }
    }

    /** Sendet eine Nachricht; `onGesendet` läuft nur bei Erfolg (der Entwurf bleibt sonst stehen). */
    fun senden(text: String, onGesendet: () -> Unit) {
        val id = offeneId ?: return
        if (_senden.value.laeuft || text.isBlank()) return
        _senden.value = Sendestand(laeuft = true)
        viewModelScope.launch {
            when (val ergebnis = api.senden(id, text.trim())) {
                is ApiResult.Fehler -> _senden.value = Sendestand(fehler = ergebnis.fehler)
                is ApiResult.Ok -> {
                    _senden.value = Sendestand()
                    onGesendet()
                    aktualisieren()
                }
            }
        }
    }

    private fun ApiResult<NachrichtenSeite>.mapVerlauf(bisher: Verlauf): ApiResult<Verlauf> = when (this) {
        is ApiResult.Ok -> ApiResult.Ok(bisher.mitNeuesterSeite(wert))
        is ApiResult.Fehler -> this
    }
}
