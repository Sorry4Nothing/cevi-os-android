package io.github.sorry4nothing.cevios.chat

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

class ChatApiTest {
    private lateinit var server: MockWebServer
    private lateinit var chat: ChatApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val speicher = object : SitzungSpeicher {
            override fun lesen(): String? = null
            override fun schreiben(wert: String?) = Unit
        }
        chat = ChatApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(speicher, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    @Test
    fun chatsLiestGruppeUndEinzelchat() {
        server.enqueue(
            json(
                200,
                """{"chats":[
                {"id":"c1","typ":"stufe","stufe":{"id":"s1","name":"Pfadi","kuerzel":"PF"},"eltern":null,
                 "stufenleitung":[{"id":"l1","name":"Beispiel Leitung"}]},
                {"id":"c2","typ":"direkt","stufe":{"id":"s1","name":"Pfadi","kuerzel":"PF"},
                 "eltern":{"id":"e1","name":"Beispiel Eltern"},"stufenleitung":[]}]}""",
            ),
        )
        val chats = (runBlocking { chat.chats() } as ApiResult.Ok).wert.chats
        assertEquals("GET /api/v1/chats HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(chats[0].istGruppe && chats[0].eltern == null)
        assertEquals("Beispiel Leitung", chats[0].stufenleitung.single().name)
        assertEquals("Beispiel Eltern", chats[1].eltern?.name)
        assertTrue(!chats[1].istGruppe)
    }

    @Test
    fun nachrichtenOhneUndMitCursor() {
        server.enqueue(json(200, """{"nachrichten":[],"naechster":null}"""))
        server.enqueue(json(200, """{"nachrichten":[],"naechster":null}"""))
        runBlocking {
            chat.nachrichten("c1")
            chat.nachrichten("c1", vor = "0190a1b2")
        }
        assertEquals("GET /api/v1/chats/c1/nachrichten HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/chats/c1/nachrichten?vor=0190a1b2 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun ausgeblendeteNachrichtHatKeinenText() {
        server.enqueue(
            json(
                200,
                """{"nachrichten":[
                {"id":"m2","text":null,"erstelltAm":"2027-01-01T10:00:00.000Z","autor":{"id":"a","name":"Ich"},
                 "programm":null,"ausgeblendet":true,"eigene":true},
                {"id":"m1","text":"Hallo","erstelltAm":"2027-01-01T09:00:00.000Z","autor":{"id":"b","name":"Du"},
                 "programm":{"id":"p1","titel":"Lager"},"ausgeblendet":false,"eigene":false}],"naechster":"m1"}""",
            ),
        )
        val seite = (runBlocking { chat.nachrichten("c1") } as ApiResult.Ok).wert
        assertNull(seite.nachrichten[0].text)
        assertTrue(seite.nachrichten[0].ausgeblendet && seite.nachrichten[0].eigene)
        assertEquals("Lager", seite.nachrichten[1].programm?.titel)
        assertEquals("m1", seite.naechster)
    }

    @Test
    fun sendenPostetDenTextUndLiestDieKennung() {
        server.enqueue(json(201, """{"id":"m9"}"""))
        val ergebnis = runBlocking { chat.senden("c1", "Hallo") }
        assertEquals(ApiResult.Ok(NachrichtKennung("m9")), ergebnis)
        val anfrage = server.takeRequest()
        assertEquals("POST /api/v1/chats/c1/nachrichten HTTP/1.1", anfrage.requestLine)
        assertEquals("""{"text":"Hallo"}""", anfrage.body?.utf8())
    }

    @Test
    fun einzelchatDesElternteilsSendetNurDieStufe() {
        server.enqueue(json(200, """{"id":"c7"}"""))
        assertEquals(ApiResult.Ok(ChatKennung("c7")), runBlocking { chat.einzelchat("s1") })
        val anfrage = server.takeRequest()
        assertEquals("POST /api/v1/chats/direkt HTTP/1.1", anfrage.requestLine)
        assertEquals("""{"stufeId":"s1"}""", anfrage.body?.utf8())
    }

    @Test
    fun einzelchatDerStufenleitungNenntDasElternteil() {
        server.enqueue(json(200, """{"id":"c7"}"""))
        runBlocking { chat.einzelchat("s1", "e1") }
        assertEquals("""{"stufeId":"s1","elternUserId":"e1"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun ausblendenUndMeldenPostenOhneBody() {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking {
            assertEquals(ApiResult.Ok(Unit), chat.ausblenden("m1"))
            assertEquals(ApiResult.Ok(Unit), chat.melden("m1"))
        }
        val ausblenden = server.takeRequest()
        assertEquals("POST /api/v1/nachrichten/m1/ausblenden HTTP/1.1", ausblenden.requestLine)
        assertEquals(0, ausblenden.body?.size ?: 0)
        assertEquals("POST /api/v1/nachrichten/m1/melden HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun ausblendenOhneRechtIst403() {
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        assertEquals(ApiResult.Fehler(ApiFehler.KEINE_BERECHTIGUNG), runBlocking { chat.ausblenden("m1") })
    }

    @Test
    fun hilfeLiestDenTextOderKeinen() {
        server.enqueue(json(200, """{"vertrauenspersonen":"Vertrauensperson: Beispiel, erreichbar am Abend."}"""))
        server.enqueue(json(200, """{"vertrauenspersonen":null}"""))
        runBlocking {
            assertEquals("Vertrauensperson: Beispiel, erreichbar am Abend.", (chat.hilfe() as ApiResult.Ok).wert.vertrauenspersonen)
            assertNull((chat.hilfe() as ApiResult.Ok).wert.vertrauenspersonen)
        }
        assertEquals("GET /api/v1/hilfe HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun profilLiestNurRollenStufenUndKinderDerStufen() {
        server.enqueue(
            json(
                200,
                """{"id":"u1","name":"Beispiel","benutzername":"b","mussPasswortAendern":false,"admin":false,
                "rollen":[{"stufeId":"s1","typ":"stufenleitung"}],
                "stufen":[{"id":"s1","name":"Pfadi","kuerzel":"PF"}],
                "kinder":[{"id":"k1","vorname":"Mia","nachname":"Muster","stufeId":"s1"}]}""",
            ),
        )
        val profil = (runBlocking { chat.profil() } as ApiResult.Ok).wert
        assertEquals("GET /api/v1/ich HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(profil.istStufenleitung("s1"))
        assertEquals(ProfilKind("k1", "s1"), profil.kinder.single())
    }

    @Test
    fun zuVieleNachrichtenGibtDenFehlerFuerZuVieleAnfragen() {
        server.enqueue(json(429, """{"code":"ZU_VIELE_ANFRAGEN","message":"x"}"""))
        assertEquals(ApiResult.Fehler(ApiFehler.ZU_VIELE_ANFRAGEN), runBlocking { chat.senden("c1", "Hallo") })
    }

    @Test
    fun keinMitgliedIstNichtGefunden() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"x"}"""))
        assertEquals(ApiResult.Fehler(ApiFehler.NICHT_GEFUNDEN), runBlocking { chat.nachrichten("fremd") })
    }
}
