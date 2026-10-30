package io.github.sorry4nothing.cevios.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Die ViewModels (Programme, Chat) einer angemeldeten Person. Sie liegen in einem eigenen Speicher, der beim Abmelden
 * geleert wird: Die nächste Person sieht nichts von der vorherigen, auch nicht im Arbeitsspeicher.
 */
class SessionScope : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

sealed interface AuthState {
    /** Die App fragt den Server, ob eine Sitzung besteht. */
    data object Loading : AuthState

    /** Die Antwort kam nicht (offline, Fehler); `error` sagt warum. */
    data class Failed(val error: ApiError) : AuthState

    /** Niemand angemeldet. `expired`: Die Sitzung ist abgelaufen oder wurde beendet. */
    data class SignedOut(val expired: Boolean) : AuthState

    /** Die Person hat noch ein Startpasswort und muss ein eigenes wählen. */
    data object MustChangePassword : AuthState

    /** Erster Start: Die Person nimmt die Datenschutzerklärung zur Kenntnis. */
    data class Welcome(val profile: Profile) : AuthState

    data class SignedIn(val profile: Profile, val scope: SessionScope) : AuthState
}

/** Zustand eines Formulars (Anmelden, Passwort, Datenschutz): läuft gerade etwas, und was ging schief. */
data class FormState(val running: Boolean = false, @StringRes val error: Int? = null, val waitSeconds: Int? = null)

/**
 * Wer ist angemeldet, und was muss die Person noch tun. Passwörter liegen nie hier: Die Eingabe bleibt im Bildschirm und
 * geht direkt an den Server.
 */
class AuthViewModel(
    private val api: AuthApi,
    unauthorized: Flow<Unit>,
    private val forgetSession: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    /** Die Person hat gerade ihr Passwort gespeichert (Hinweis im Willkommen und im Profil). */
    private val _passwordSaved = MutableStateFlow(false)
    val passwordSaved: StateFlow<Boolean> = _passwordSaved.asStateFlow()

    private var scope: SessionScope? = null

    init {
        start()
        viewModelScope.launch {
            unauthorized.collect { if (_state.value !is AuthState.SignedOut) leave(expired = true) }
        }
    }

    fun start() {
        _state.value = AuthState.Loading
        viewModelScope.launch { resolve() }
    }

    fun signIn(username: String, password: String) {
        if (_form.value.running) return
        _form.value = FormState(running = true)
        viewModelScope.launch {
            when (val result = api.signIn(username.trim(), password)) {
                is ApiResult.Failure -> _form.value = failureForm(result, signInErrorText(result))
                is ApiResult.Ok -> if (result.value.twoFactorRedirect) {
                    // Admins melden sich nur im Admin-Bereich an: kein Sitzungscookie, auch kein halbes (`two_factor`).
                    forgetSession()
                    _form.value = FormState(error = R.string.login_admin_only)
                } else {
                    resolve()
                    _form.value = FormState()
                }
            }
        }
    }

    fun changePassword(current: String, new: String) {
        if (_form.value.running) return
        _form.value = FormState(running = true)
        viewModelScope.launch {
            when (val result = api.changePassword(current, new)) {
                is ApiResult.Failure -> if (result.error != ApiError.NOT_SIGNED_IN) _form.value = failureForm(result, PasswordRules.errorText(result))
                is ApiResult.Ok -> {
                    _passwordSaved.value = true
                    resolve()
                    _form.value = FormState()
                }
            }
        }
    }

    fun acceptPrivacy() {
        val welcome = _state.value as? AuthState.Welcome ?: return
        if (_form.value.running) return
        _form.value = FormState(running = true)
        viewModelScope.launch {
            when (val result = api.acceptPrivacy()) {
                is ApiResult.Failure -> if (result.error != ApiError.NOT_SIGNED_IN) _form.value = failureForm(result, result.error.text)
                is ApiResult.Ok -> {
                    _form.value = FormState()
                    _passwordSaved.value = false
                    enter(welcome.profile)
                }
            }
        }
    }

    /** Beendet die Sitzung auf dem Server und leert alles auf dem Gerät. Offline geht es auch: Dann endet sie dort mit ihrem Ablauf. */
    fun signOut() {
        if (_form.value.running) return
        _form.value = FormState(running = true)
        viewModelScope.launch {
            api.signOut()
            leave(expired = false)
        }
    }

    /** Das Profil lädt neu: Danach zeigt die App, ob noch ein Schritt fehlt (Passwort, Datenschutz). */
    private suspend fun resolve() {
        when (val result = api.session()) {
            is ApiResult.Failure -> fail(result.error)
            is ApiResult.Ok -> {
                val session = result.value
                if (session == null) leave(expired = false)
                else if (session.user.mussPasswortAendern) _state.value = AuthState.MustChangePassword
                else loadProfile()
            }
        }
    }

    private suspend fun loadProfile() {
        when (val result = api.profile()) {
            is ApiResult.Failure -> fail(result.error)
            is ApiResult.Ok -> {
                val profile = result.value
                when {
                    profile.mustChangePassword -> _state.value = AuthState.MustChangePassword
                    profile.privacyReadAt == null -> _state.value = AuthState.Welcome(profile)
                    else -> enter(profile)
                }
            }
        }
    }

    /** Bei 429 mit Wartezeit sagt der Text, wie lange; sonst der Text des Aufrufs. */
    private fun failureForm(failure: ApiResult.Failure, @StringRes text: Int) =
        if (failure.error == ApiError.TOO_MANY_REQUESTS && failure.retryAfterSeconds != null) {
            FormState(error = R.string.error_too_many_wait, waitSeconds = failure.retryAfterSeconds)
        } else {
            FormState(error = text)
        }

    private fun fail(error: ApiError) {
        if (error == ApiError.NOT_SIGNED_IN) leave(expired = true) else _state.value = AuthState.Failed(error)
    }

    private fun enter(profile: Profile) {
        val fresh = SessionScope().also { scope = it }
        _state.value = AuthState.SignedIn(profile, fresh)
    }

    /** Verlässt die Sitzung: Cookie, Daten im Arbeitsspeicher und Formular sind weg. */
    private fun leave(expired: Boolean) {
        forgetSession()
        scope?.viewModelStore?.clear()
        scope = null
        _form.value = FormState()
        _passwordSaved.value = false
        _state.value = AuthState.SignedOut(expired)
    }

    /** Beim Wechsel des Bildschirms: ein Fehler des vorigen gehört nicht auf den nächsten. */
    fun clearError() {
        _form.value = FormState()
    }

    fun clearPasswordSaved() {
        _passwordSaved.value = false
    }

    override fun onCleared() {
        scope?.viewModelStore?.clear()
    }
}
