package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.images.isProfileImagePath
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Personen in den Lager-Antworten tragen `bildUrl` (`LagerBildUrl`); fehlt es oder ist es `null`, bleibt die Initiale. */
class LagerImageUrlTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private val json = Json { ignoreUnknownKeys = true }
    private val id = "11111111-1111-4111-8111-111111111111"
    private val path = "/api/v1/personen/$id/bild?v=3"

    @Test
    fun taskPersonReadsTheImagePath() {
        val person = json.decodeFromString<TaskPerson>("""{"id":"$id","name":"Anna","ceviName":null,"bildUrl":"$path","automatisch":false}""")
        assertEquals(path, person.imageUrl)
        assertNull(json.decodeFromString<TaskPerson>("""{"id":"$id","name":"Anna","bildUrl":null}""").imageUrl)
        assertNull(json.decodeFromString<TaskPerson>("""{"id":"$id","name":"Anna"}""").imageUrl)
    }

    @Test
    fun ressortPersonReadsTheImagePath() {
        assertEquals(path, json.decodeFromString<RessortPerson>("""{"id":"$id","name":"Anna","ceviName":"Spatz","bildUrl":"$path"}""").imageUrl)
        assertNull(json.decodeFromString<RessortPerson>("""{"id":"$id","name":"Anna","ceviName":"Spatz"}""").imageUrl)
    }

    @Test
    fun theAvatarLoadsOnlyTheRouteOfThePersonImage() {
        assertTrue(isProfileImagePath(path))
        assertFalse(isProfileImagePath("https://boese.example$path"))
        assertFalse(isProfileImagePath("/api/v1/lager/$id/mitglieder"))
    }
}
