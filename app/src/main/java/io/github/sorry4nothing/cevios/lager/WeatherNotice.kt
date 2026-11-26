package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind

/**
 * Der Satz, der sagt, warum es kein Wetter gibt (`ort.status` von `GET …/wetter`), mit dem, was die Person tun kann.
 * `gefunden` und ein unbekannter oder fehlender Stand geben `null`: Dann gibt es keinen eigenen Grund, die Anzeige bleibt wie sie ist.
 */
@StringRes
fun weatherPlaceText(place: WeatherPlace?): Int? = when (place?.status) {
    "kein_ort" -> R.string.weather_place_none
    "nicht_gefunden" -> R.string.weather_place_not_found
    "mehrdeutig" -> R.string.weather_place_ambiguous
    "nicht_erreichbar" -> R.string.weather_place_unreachable
    "offen" -> R.string.weather_place_pending
    else -> null
}

/** Der Hinweis zum fehlenden Wetter; nichts, wenn der Ort gefunden ist. Live-Region, damit ihn der Screenreader nach dem Laden nennt. */
@Composable
fun WeatherPlaceNotice(place: WeatherPlace?, modifier: Modifier = Modifier) {
    val text = weatherPlaceText(place) ?: return
    NoticeBanner(NoticeKind.INFO, stringResource(text), modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

/**
 * Lädt den Ort fürs Wetter, sobald `missing` gilt (es gibt kein Wetter, die Rolle darf es lesen), und zeigt dann den Grund.
 * Sonst fragt die Seite nichts zusätzlich.
 */
@Composable
fun WeatherMissing(vm: LiveViewModel, missing: Boolean) {
    val place by vm.weatherPlace.collectAsState()
    LaunchedEffect(missing) { if (missing) vm.loadWeatherPlace() }
    if (missing) WeatherPlaceNotice(place)
}
