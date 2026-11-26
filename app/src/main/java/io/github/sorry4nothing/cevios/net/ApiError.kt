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

    /**
     * 403 `ZWEITER_FAKTOR_FEHLT` oder `ZWEITER_FAKTOR_SITZUNG` (Sitzung der App ohne Code): Verwaltung und Rechte mit «erweitert» gibt es nur im Web;
     * die App bietet das nicht an, zeigt aber «nur am Computer», falls ein Server es meldet.
     */
    SECOND_FACTOR_MISSING(R.string.error_second_factor),

    /** 503 `IN_WARTUNG`: Der Bereich ist nur lesbar, bis die Wartung endet. */
    MAINTENANCE(R.string.error_maintenance),

    /** 404 beim Einzelchat: Der Server sagt nicht, ob die Stufe, das Elternteil oder das Recht fehlt. */
    DIRECT_CHAT_UNAVAILABLE(R.string.error_direct_chat_unavailable),

    /** 429 beim Senden einer Nachricht: höchstens 20 pro Minute. */
    MESSAGE_TOO_FAST(R.string.error_message_too_fast),

    /** 401 `APP_NACHWEIS_UNGUELTIG` oder `APP_NACHWEIS_FEHLT`: Der Server nimmt die Anmeldung der App nicht an; er nennt keinen Grund. */
    APP_PROOF_INVALID(R.string.error_app_proof_invalid),

    /** 503 `APP_NACHWEIS_NICHT_ERREICHBAR`: Google war für den Server nicht erreichbar. */
    APP_PROOF_UNREACHABLE(R.string.error_app_proof_unreachable),

    /** Das Gerät liefert kein Integrity-Token (kein Google Play, kein Netz oder abgelehnt); die App fragt den Server dann gar nicht. */
    INTEGRITY_UNAVAILABLE(R.string.error_integrity_unavailable),
    NETWORK(R.string.error_network),
    UNKNOWN(R.string.error_unknown);

    companion object {
        private val SECOND_FACTOR_CODES = setOf("ZWEITER_FAKTOR_FEHLT", "ZWEITER_FAKTOR_SITZUNG")
        private val APP_PROOF_CODES = setOf("APP_NACHWEIS_FEHLT", "APP_NACHWEIS_UNGUELTIG")

        /** `code` ist der Fehlercode des Servers (bei 409 und 400); er unterscheidet dort die Fälle. */
        fun from(status: Int, code: String? = null): ApiError = when (status) {
            401 -> if (code in APP_PROOF_CODES) APP_PROOF_INVALID else NOT_SIGNED_IN
            403 -> if (code in SECOND_FACTOR_CODES) SECOND_FACTOR_MISSING else FORBIDDEN
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
            503 -> when (code) {
                "IN_WARTUNG" -> MAINTENANCE
                "APP_NACHWEIS_NICHT_ERREICHBAR" -> APP_PROOF_UNREACHABLE
                else -> UNKNOWN
            }
            else -> UNKNOWN
        }

        fun fromStatus(status: Int): ApiError = from(status)
    }
}

/** Ein Fehler an einem Feld der Antwort (`fehler[]` bei 422): `field` ist die Feld-ID, `code` zum Beispiel `PFLICHT` oder `ZU_LANG`. */
@kotlinx.serialization.Serializable
data class FieldProblem(@kotlinx.serialization.SerialName("feld") val field: String, val code: String)

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
        /** Nur bei 422 `ANTWORTEN_UNGUELTIG` (Formulare): je Feld die ID und ein Code, nie der Wert. */
        val fieldErrors: List<FieldProblem> = emptyList(),
    ) : ApiResult<Nothing> {
        /** Der HTTP-Status der Antwort; `null`, wenn es keine gab. Bewusst ausserhalb von `equals` und `copy`. */
        var status: Int? = null
            internal set
    }
}

/** Gibt der Fehler an dieser Stelle einen genaueren Text: aus `from` wird `to`, alles andere bleibt (auch `code` und Wartezeit). */
fun <T> ApiResult<T>.replacingError(from: ApiError, to: ApiError): ApiResult<T> =
    if (this is ApiResult.Failure && error == from) copy(error = to) else this
