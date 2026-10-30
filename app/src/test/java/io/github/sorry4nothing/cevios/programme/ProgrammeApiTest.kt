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
    fun detailLiestKinderUndEigeneAnmeldung() {
        val detail = lager.replace(
            "\"googleBeschreibung\":null",
            """"googleBeschreibung":null,"teilnahmeOffen":true,"darfErgaenzen":false,"eigeneAnmeldung":"offen",
               "kinder":[{"id":"k1","vorname":"Mia","status":"ja"},{"id":"k2","vorname":"Noah","status":null}]""",
        )
        server.enqueue(json(200, detail))
        val p = (runBlocking { programme.lesen("x") } as ApiResult.Ok).wert
        assertTrue(p.teilnahmeOffen)
        assertEquals("offen", p.eigeneAnmeldung)
        assertEquals(listOf(ProgrammKind("k1", "Mia", "ja"), ProgrammKind("k2", "Noah", null)), p.kinder)
    }

    @Test
    fun listeneintragHatKeineKinderUndKeineAnmeldung() {
        server.enqueue(json(200, lager))
        val p = (runBlocking { programme.lesen("x") } as ApiResult.Ok).wert
        assertTrue(p.kinder.isEmpty() && !p.teilnahmeOffen)
        assertNull(p.eigeneAnmeldung)
    }

    @Test
    fun teilnahmeSendetJaAnDenKindPfad() {
        server.enqueue(MockResponse.Builder().code(204).build())
        val ergebnis = runBlocking { programme.teilnahmeSetzen("p1", "k1", ja = true) }
        assertEquals(ApiResult.Ok(Unit), ergebnis)
        val anfrage = server.takeRequest()
        assertEquals("PUT /api/v1/programme/p1/teilnahmen/k1 HTTP/1.1", anfrage.requestLine)
        assertEquals("""{"status":"ja"}""", anfrage.body?.utf8())
    }

    @Test
    fun leitungSendetNeinAnDenLeitungPfad() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Ok(Unit), runBlocking { programme.leitungSetzen("p1", ja = false) })
        val anfrage = server.takeRequest()
        assertEquals("PUT /api/v1/programme/p1/leitung HTTP/1.1", anfrage.requestLine)
        assertEquals("""{"status":"nein"}""", anfrage.body?.utf8())
    }

    @Test
    fun geschlossenesProgrammGibtEinenEigenenFehler() {
        server.enqueue(json(409, """{"code":"PROGRAMM_GESCHLOSSEN","message":"zu"}"""))
        assertEquals(
            ApiResult.Fehler(ApiFehler.ANMELDUNG_GESCHLOSSEN),
            runBlocking { programme.teilnahmeSetzen("p1", "k1", ja = true) },
        )
    }

    @Test
    fun ohneOrtGibtEsKeinenOrt() {
        server.enqueue(json(200, lager.replace("\"googleOrt\":\"Zeltplatz\"", "\"googleOrt\":\"  \"")))
        assertNull((runBlocking { programme.lesen("x") } as ApiResult.Ok).wert.ort)
    }
}
