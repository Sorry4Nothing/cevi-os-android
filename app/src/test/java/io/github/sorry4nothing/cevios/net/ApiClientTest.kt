package io.github.sorry4nothing.cevios.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private class MemorySpeicher(var wert: String? = null) : SitzungSpeicher {
    override fun lesen() = wert
    override fun schreiben(wert: String?) {
        this.wert = wert
    }
}

@Serializable
private data class Ich(val name: String)

class ApiClientTest {
    private lateinit var server: MockWebServer
    private val speicher = MemorySpeicher()

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() = server.close()

    private fun client(s: SitzungSpeicher = speicher) =
        ApiClient(server.url("/").toString(), ApiClient.httpClient(s, "1.2.3"))

    private fun antwort(code: Int = 200, body: String = "{}", vararg cookies: String) =
        MockResponse.Builder().code(code).body(body).apply { cookies.forEach { addHeader("Set-Cookie", it) } }.build()

    @Test
    fun schicktVersionUndHerkunftUndKeinCookieOhneSitzung() {
        server.enqueue(antwort())
        runBlocking { client().get<Unit>("/api/v1/ich") }
        val anfrage = server.takeRequest()
        assertEquals("1.2.3", anfrage.headers["X-App-Version"])
        assertEquals("cevi-os://", anfrage.headers["Origin"])
        assertNull(anfrage.headers["Cookie"])
    }

    @Test
    fun merktCookieAusAntwortUndSchicktEsDanach() {
        val cookie = "__Secure-better-auth.session_token=abc%3Ddef"
        server.enqueue(antwort(200, "{}", "$cookie; Path=/; Max-Age=7776000; HttpOnly; Secure; SameSite=Lax"))
        server.enqueue(antwort())
        runBlocking {
            val api = client()
            api.get<Unit>("/api/auth/get-session")
            assertEquals(cookie, speicher.wert)
            api.get<Unit>("/api/v1/ich")
        }
        server.takeRequest()
        assertEquals(cookie, server.takeRequest().headers["Cookie"])
    }

    @Test
    fun neuesCookieErsetztAltesUndMaxAgeNullLoeschtEs() {
        speicher.wert = "a=1; b=2"
        server.enqueue(antwort(200, "{}", "a=neu; Path=/", "b=; Max-Age=0; Path=/"))
        runBlocking { client().get<Unit>("/api/auth/get-session") }
        assertEquals("a=neu", speicher.wert)
    }

    @Test
    fun letztesCookieGeloescht_speicherLeer() {
        speicher.wert = "a=1"
        server.enqueue(antwort(200, "{}", "a=; Max-Age=0; Path=/"))
        runBlocking { client().get<Unit>("/api/auth/sign-out") }
        assertNull(speicher.wert)
    }

    @Test
    fun decodiertJsonUndIgnoriertUnbekannteFelder() {
        server.enqueue(antwort(200, """{"name":"Test","extra":1}"""))
        val ergebnis = runBlocking { client().get<Ich>("/api/v1/ich") }
        assertEquals(ApiResult.Ok(Ich("Test")), ergebnis)
    }

    @Test
    fun postSendetJson() {
        server.enqueue(antwort(200, """{"name":"Test"}"""))
        val ergebnis = runBlocking { client().post<Ich, Ich>("/api/v1/x", Ich("Test")) }
        assertEquals(ApiResult.Ok(Ich("Test")), ergebnis)
        val anfrage = server.takeRequest()
        assertEquals("POST", anfrage.method)
        assertEquals("""{"name":"Test"}""", anfrage.body?.utf8())
        assertEquals("application/json; charset=utf-8", anfrage.headers["Content-Type"])
    }

    @Test
    fun statusWirdZuEinheitlichemFehler() {
        val erwartet = mapOf(
            401 to ApiFehler.NICHT_ANGEMELDET,
            403 to ApiFehler.KEINE_BERECHTIGUNG,
            404 to ApiFehler.NICHT_GEFUNDEN,
            409 to ApiFehler.ANMELDUNG_GESCHLOSSEN,
            426 to ApiFehler.APP_VERALTET,
            429 to ApiFehler.ZU_VIELE_ANFRAGEN,
            500 to ApiFehler.UNBEKANNT,
        )
        erwartet.forEach { (status, fehler) ->
            server.enqueue(antwort(status, """{"code":"X","message":"Server-Text"}"""))
            assertEquals("Status $status", ApiResult.Fehler(fehler), runBlocking { client().get<Ich>("/api/v1/ich") })
        }
    }

    @Test
    fun kaputtesJsonIstUnbekannterFehler() {
        server.enqueue(antwort(200, "kein json"))
        assertEquals(ApiResult.Fehler(ApiFehler.UNBEKANNT), runBlocking { client().get<Ich>("/api/v1/ich") })
    }

    @Test
    fun ausgefalleneVerbindungIstNetzwerkfehler() {
        val tot = MockWebServer().apply { start() }
        val url = tot.url("/").toString()
        tot.close()
        val api = ApiClient(url, ApiClient.httpClient(speicher, "1.2.3"))
        assertEquals(ApiResult.Fehler(ApiFehler.NETZWERK), runBlocking { api.get<Ich>("/api/v1/ich") })
    }

    @Test
    fun weiterleitungWirdNichtGefolgt() {
        speicher.wert = "a=1"
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/anderswo").toString()).build())
        assertEquals(ApiResult.Fehler(ApiFehler.UNBEKANNT), runBlocking { client().get<Ich>("/api/v1/ich") })
        assertEquals(1, server.requestCount)
    }
}
