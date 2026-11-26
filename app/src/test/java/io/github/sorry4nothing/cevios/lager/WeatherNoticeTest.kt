package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.R
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Die Textwahl für den Wetterort: ein eigener Satz je Grund, nichts bei `gefunden`. */
class WeatherNoticeTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun text(status: String?) = weatherPlaceText(status?.let { WeatherPlace(it) })

    @Test
    fun everyReasonHasItsOwnSentence() {
        val ids = listOf("kein_ort", "nicht_gefunden", "mehrdeutig", "nicht_erreichbar", "offen").map { text(it) }
        assertEquals(
            listOf(R.string.weather_place_none, R.string.weather_place_not_found, R.string.weather_place_ambiguous, R.string.weather_place_unreachable, R.string.weather_place_pending),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertNotEquals(null, ids.first())
    }

    @Test
    fun aFoundPlaceAndMissingOrUnknownStatesSayNothing() {
        assertNull(text("gefunden"))
        assertNull(text("neu_vom_server"))
        assertNull(text(null))
    }

    @Test
    fun theWeatherAnswerCarriesThePlace() {
        val weather = Json { ignoreUnknownKeys = true }.decodeFromString<Weather>("""{"ort":{"status":"mehrdeutig","name":null},"stand":null,"tage":[]}""")
        assertEquals("mehrdeutig", weather.place?.status)
        assertEquals(R.string.weather_place_ambiguous, weatherPlaceText(weather.place))
        assertNull(Json { ignoreUnknownKeys = true }.decodeFromString<Weather>("""{"tage":[]}""").place)
    }
}
