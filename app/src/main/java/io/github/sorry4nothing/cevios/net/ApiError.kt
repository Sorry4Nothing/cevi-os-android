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

    /** 409 `PROGRAMM_BEGONNEN`: Die Leitung kann sich nach dem Beginn nicht mehr ändern (abgesagte Programme gehen noch). */
    PROGRAM_STARTED(R.string.error_program_started),

    /** 409 `ANWESENHEIT_GESCHLOSSEN`: Erfassen geht ab Beginn des Programms und bis 14 Tage danach. */

    /** 409 `LAGER_GESCHLOSSEN`: Das Lager ist abgeschlossen oder archiviert und lässt nur noch lesen. */
    LAGER_CLOSED(R.string.error_lager_closed),

    /** 409 `VERSION_KONFLIKT`: Die Zeile hat inzwischen eine andere Version; `ApiResult.Failure.conflict` trägt den aktuellen Stand. */
    VERSION_CONFLICT(R.string.error_version_conflict),

    /** 410 `FEED_NEU_LADEN`: Der Stand der App ist zu alt für den Änderungsfeed; sie lädt die Listen neu. */
    FEED_RELOAD(R.string.error_feed_reload),

    /** 403 `ZWEITER_FAKTOR_FEHLT`: Die Lagerleitung braucht den zweiten Faktor; die App kann ihn nicht abfragen. */
    SECOND_FACTOR_MISSING(R.string.error_second_factor),

    /** 503 `IN_WARTUNG`: Der Bereich ist nur lesbar, bis die Wartung endet. */
    MAINTENANCE(R.string.error_maintenance),

    /** 404 beim Einzelchat: Der Server sagt nicht, ob die Stufe, das Elternteil oder das Recht fehlt. */
    DIRECT_CHAT_UNAVAILABLE(R.string.error_direct_chat_unavailable),

    /** 429 beim Senden einer Nachricht: höchstens 20 pro Minute. */
    MESSAGE_TOO_FAST(R.string.error_message_too_fast),
    NETWORK(R.string.error_network),
    UNKNOWN(R.string.error_unknown);

    companion object {
        /** `code` ist der Fehlercode des Servers (bei 409 und 400); er unterscheidet dort die Fälle. */
        fun from(status: Int, code: String? = null): ApiError = when (status) {
            401 -> NOT_SIGNED_IN
            403 -> if (code == "ZWEITER_FAKTOR_FEHLT") SECOND_FACTOR_MISSING else FORBIDDEN
            404 -> NOT_FOUND
            409 -> when (code) {
                "PROGRAMM_BEGONNEN" -> PROGRAM_STARTED
                "LAGER_GESCHLOSSEN" -> LAGER_CLOSED
                "VERSION_KONFLIKT" -> VERSION_CONFLICT
                else -> REGISTRATION_CLOSED // sonst PROGRAMM_GESCHLOSSEN
            }
            410 -> FEED_RELOAD
            426 -> APP_OUTDATED
            429 -> TOO_MANY_REQUESTS
            503 -> if (code == "IN_WARTUNG") MAINTENANCE else UNKNOWN
            else -> UNKNOWN
        }

        fun fromStatus(status: Int): ApiError = from(status)
    }
}

/** Der Stand, den der Server bei `VERSION_KONFLIKT` mitliefert (`aktuell`, `abweichend`). */
data class VersionConflict(val current: kotlinx.serialization.json.JsonObject, val differing: List<String>)

sealed interface ApiResult<out T> {
    data class Ok<out T>(val value: T) : ApiResult<T>

    /**
     * `code` ist der Fehlercode des Servers und nur bei 400, 403, 409, 410 und 503 gefüllt (z. B. `PASSWORD_COMPROMISED`).
     * `retryAfterSeconds` nennt bei 429 die Wartezeit aus dem Header `X-Retry-After`, wenn der Server sie schickt.
     */
    data class Failure(
        val error: ApiError,
        val code: String? = null,
        val retryAfterSeconds: Int? = null,
        /** Nur bei `VERSION_KONFLIKT`: der aktuelle Stand der Zeile und die Felder, die jetzt anders stehen. */
        val conflict: VersionConflict? = null,
    ) : ApiResult<Nothing>
}

/** Gibt der Fehler an dieser Stelle einen genaueren Text: aus `from` wird `to`, alles andere bleibt (auch `code` und Wartezeit). */
fun <T> ApiResult<T>.replacingError(from: ApiError, to: ApiError): ApiResult<T> =
    if (this is ApiResult.Failure && error == from) copy(error = to) else this
