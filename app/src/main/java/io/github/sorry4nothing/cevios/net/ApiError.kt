package io.github.sorry4nothing.cevios.net

import androidx.annotation.StringRes
import io.github.sorry4nothing.cevios.R

/** Einheitliche Fehler der API mit dem Text, den die App anzeigt. Der Server-Text wird nie angezeigt. */
enum class ApiError(@StringRes val text: Int) {
    NOT_SIGNED_IN(R.string.error_not_signed_in),
    FORBIDDEN(R.string.error_forbidden),
    NOT_FOUND(R.string.error_not_found),
    APP_OUTDATED(R.string.error_app_outdated),
    TOO_MANY_REQUESTS(R.string.error_too_many_requests),
    REGISTRATION_CLOSED(R.string.error_registration_closed),
    NETWORK(R.string.error_network),
    UNKNOWN(R.string.error_unknown);

    companion object {
        fun fromStatus(status: Int): ApiError = when (status) {
            401 -> NOT_SIGNED_IN
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            409 -> REGISTRATION_CLOSED // PROGRAMM_GESCHLOSSEN oder PROGRAMM_BEGONNEN
            426 -> APP_OUTDATED
            429 -> TOO_MANY_REQUESTS
            else -> UNKNOWN
        }
    }
}

sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>
    /** `code` ist der Fehlercode des Servers und nur bei 400 gefüllt (z. B. `PASSWORD_COMPROMISED`). */
    data class Failure(val error: ApiError, val code: String? = null) : ApiResult<Nothing>
}
