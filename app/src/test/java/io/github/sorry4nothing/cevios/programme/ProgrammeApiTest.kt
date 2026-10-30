package io.github.sorry4nothing.cevios.programme

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiFehler
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SitzungSpeicher
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ProgrammeApiTest {
    private lateinit var server: MockWebServer
    private lateinit var programme: ProgrammeApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val speicher = object : SitzungSpeicher {
            override fun lesen(): String? = null
            override fun schreiben(wert: String?) = Unit
        }
        programme = ProgrammeApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(speicher, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val lager = """
        {"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","titel":"Sommerlager","status":"aktiv","sichtbarkeit":"eltern",
         "ganztaegig":true,"beginn":null,"ende":null,"tagVon":"2027-07-10","tagBis":"2027-07-17",
         "googleOrt":"Zeltplatz","treffpunkt":null,"schlussOrt":"Bahnhof","schlussZeit":"16:30:00",
         "mitnehmen":"Schlafsack","infoEltern":null,"stufen":[{"id":"a1","name":"Stufe A"}],"googleBeschreibung":null}
    """.trimIndent()

    @Test
    fun listeFragtDasFensterAbUndLiestAlleFelder() {
        server.enqueue(json(200, """{"programme":[$lager]}"""))
        val ergebnis = runBlocking { programme.liste(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)) }
        assertEquals("GET /api/v1/programme?von=2027-07-01&bis=2027-07-31 HTTP/1.1", server.takeRequest().requestLine)
        val p = (ergebnis as ApiResult.Ok).wert.programme.single()
        assertEquals("Sommerlager", p.titel)
        assertEquals("Zeltplatz", p.ort)
        assertEquals("Stufe A", p.stufen.single().name)
        assertEquals("16:30:00", p.schlussZeit)
    }

    @Test
    fun detailLiestEinProgramm() {
        server.enqueue(json(200, lager.replace("\"infoEltern\":null", "\"infoEltern\":\"Bitte pünktlich\"")))
        val p = (runBlocking { programme.lesen("7c9e6679-7425-40de-944b-e07fc1f90ae7") } as ApiResult.Ok).wert
        assertEquals("Bitte pünktlich", p.infoEltern)
        assertEquals("GET /api/v1/programme/7c9e6679-7425-40de-944b-e07fc1f90ae7 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun unbekanntesProgrammIstNichtGefunden() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"Nicht gefunden"}"""))
        val ergebnis = runBlocking { programme.lesen("7c9e6679-7425-40de-944b-e07fc1f90ae7") }
        assertEquals(ApiResult.Fehler(ApiFehler.NICHT_GEFUNDEN), ergebnis)
    }

    @Test
    fun treffpunktSchlaegtGoogleOrt() {
        server.enqueue(json(200, lager.replace("\"treffpunkt\":null", "\"treffpunkt\":\"Pfadiheim\"")))
        assertEquals("Pfadiheim", (runBlocking { programme.lesen("x") } as ApiResult.Ok).wert.ort)
    }

    @Test
    fun ganztaegigeProgrammeHabenKalendertage() {
        server.enqueue(json(200, lager))
        val p = (runBlocking { programme.lesen("x") } as ApiResult.Ok).wert
        assertEquals(Zeit.Ganztaegig(LocalDate.of(2027, 7, 10), LocalDate.of(2027, 7, 17)), p.zeit())
        assertTrue(!p.abgesagt && !p.pruefen)
    }

    @Test
    fun ohneOrtGibtEsKeinenOrt() {
        server.enqueue(json(200, lager.replace("\"googleOrt\":\"Zeltplatz\"", "\"googleOrt\":\"  \"")))
        assertNull((runBlocking { programme.lesen("x") } as ApiResult.Ok).wert.ort)
    }
}
