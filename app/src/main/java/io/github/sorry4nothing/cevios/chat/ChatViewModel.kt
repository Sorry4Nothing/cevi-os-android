package io.github.sorry4nothing.cevios.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiFehler
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Zustand beim Senden einer Nachricht. */
data class Sendestand(val laeuft: Boolean = false, val fehler: ApiFehler? = null)

/** Zustand von Ausblenden, Melden und Einzelchat-Öffnen. `erledigt` ist der Text der Bestätigung. */
data class Aktionsstand(val laeuft: Boolean = false, val fehler: ApiFehler? = null, @StringRes val erledigt: Int? = null)

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

    private val _aktion = MutableStateFlow(Aktionsstand())
    val aktion: StateFlow<Aktionsstand> = _aktion.asStateFlow()

    private val _profil = MutableStateFlow<ApiResult<Profil>?>(null)
    val profil: StateFlow<ApiResult<Profil>?> = _profil.asStateFlow()

    private val _hilfe = MutableStateFlow<ApiResult<HilfeAntwort>?>(null)
    val hilfe: StateFlow<ApiResult<HilfeAntwort>?> = _hilfe.asStateFlow()

    private var offeneId: String? = null
    private var verlaufJob: Job? = null

    init {
        listeLaden()
        profilLaden()
    }

    /** Das Profil entscheidet, welche Aktionen die App anbietet (Stufenleitung, Kinder). */
    fun profilLaden() {
        viewModelScope.launch { _profil.value = api.profil() }
    }

    fun hilfeLaden() {
        _hilfe.value = null
        viewModelScope.launch { _hilfe.value = api.hilfe() }
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
        _aktion.value = Aktionsstand()
        verlaufJob = viewModelScope.launch { _verlauf.value = api.nachrichten(id).mapVerlauf(Verlauf()) }
    }

    fun chatSchliessen() {
        verlaufJob?.cancel()
        offeneId = null
        _aktion.value = Aktionsstand()
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

    /** Blendet eine Nachricht für alle aus (nur Stufenleitung im Gruppenchat; der Server entscheidet). */
    fun ausblenden(nachrichtId: String) = aktion(R.string.aktion_ausgeblendet) {
        api.ausblenden(nachrichtId).also { if (it is ApiResult.Ok) aktualisieren() }
    }

    /** Meldet eine Nachricht den Admins. */
    fun melden(nachrichtId: String) = aktion(R.string.aktion_gemeldet) { api.melden(nachrichtId) }

    /**
     * Öffnet den Einzelchat der Stufe (für Stufenleitungen mit dem Elternteil) und lädt die Chatliste neu,
     * damit der Titel stimmt. `onChat` bekommt die ID des Chats.
     */
    fun einzelchatOeffnen(stufeId: String, elternUserId: String?, onChat: (String) -> Unit) {
        if (_aktion.value.laeuft) return
        _aktion.value = Aktionsstand(laeuft = true)
        viewModelScope.launch {
            when (val ergebnis = api.einzelchat(stufeId, elternUserId)) {
                is ApiResult.Fehler -> _aktion.value = Aktionsstand(fehler = ergebnis.fehler)
                is ApiResult.Ok -> {
                    _aktion.value = Aktionsstand()
                    _liste.value = api.chats()
                    onChat(ergebnis.wert.id)
                }
            }
        }
    }

    fun aktionQuittieren() {
        _aktion.value = Aktionsstand()
    }

    private fun aktion(@StringRes erledigt: Int? = null, aufruf: suspend () -> ApiResult<Unit>) {
        if (_aktion.value.laeuft) return
        _aktion.value = Aktionsstand(laeuft = true)
        viewModelScope.launch {
            _aktion.value = when (val ergebnis = aufruf()) {
                is ApiResult.Fehler -> Aktionsstand(fehler = ergebnis.fehler)
                is ApiResult.Ok -> Aktionsstand(erledigt = erledigt)
            }
        }
    }

    private fun ApiResult<NachrichtenSeite>.mapVerlauf(bisher: Verlauf): ApiResult<Verlauf> = when (this) {
        is ApiResult.Ok -> ApiResult.Ok(bisher.mitNeuesterSeite(wert))
        is ApiResult.Fehler -> this
    }
}
