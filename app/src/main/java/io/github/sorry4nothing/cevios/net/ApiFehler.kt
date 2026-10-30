package io.github.sorry4nothing.cevios.net

import androidx.annotation.StringRes
import io.github.sorry4nothing.cevios.R

/** Einheitliche Fehler der API mit dem Text, den die App anzeigt. Der Server-Text wird nie angezeigt. */
enum class ApiFehler(@StringRes val text: Int) {
    NICHT_ANGEMELDET(R.string.fehler_nicht_angemeldet),
    KEINE_BERECHTIGUNG(R.string.fehler_keine_berechtigung),
    NICHT_GEFUNDEN(R.string.fehler_nicht_gefunden),
    APP_VERALTET(R.string.fehler_app_veraltet),
    ZU_VIELE_ANFRAGEN(R.string.fehler_zu_viele_anfragen),
    NETZWERK(R.string.fehler_netzwerk),
    UNBEKANNT(R.string.fehler_unbekannt);

    companion object {
        fun vonStatus(status: Int): ApiFehler = when (status) {
            401 -> NICHT_ANGEMELDET
            403 -> KEINE_BERECHTIGUNG
            404 -> NICHT_GEFUNDEN
            426 -> APP_VERALTET
            429 -> ZU_VIELE_ANFRAGEN
            else -> UNBEKANNT
        }
    }
}

sealed interface ApiResult<out T> {
    data class Ok<out T>(val wert: T) : ApiResult<T>
    data class Fehler(val fehler: ApiFehler) : ApiResult<Nothing>
}
