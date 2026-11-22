package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Pinnwand, Umfragen, Reaktionen, Eingang und Benachrichtigungen gegen einen Fake-Server. */
class CommsApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: CommsApi
    private val camp = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val postId = "11111111-1111-4111-8111-111111111111"
    private val optionA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val optionB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = CommsApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val poll = """{"id":"p1","version":2,"mehrfach":false,"schliesstAm":null,"geschlossen":false,"namenFuerTeam":false,"namenSichtbar":false,"teilnehmende":14,
        "optionen":[{"id":"$optionA","text":"Schwimmen","stimmen":9,"eigene":true,"personen":[]},{"id":"$optionB","text":"Wanderung","stimmen":5,"eigene":false,"personen":[]}]}"""

    private val post = """{"id":"$postId","version":3,"titel":"Abfahrt am Samstag","text":"Treffpunkt Bahnhof","wichtig":true,"angeheftet":true,"sichtbarAb":null,
        "sichtbarBis":"2027-07-22T21:59:00.000Z","vonPersonId":"p1","erstelltAm":"2027-07-01T08:00:00.000Z","von":{"id":"p1","name":"Beispiel Person","ceviName":"Luchs"},
        "empfaenger":{"anAlle":true,"rollen":[],"personen":[]},
        "dateien":[{"id":"d1","version":1,"anzeigename":"Billette.pdf","link":"https://example.org/b","typ":"application/pdf","groesse":120000,"hinzugefuegtVon":null,"hinzugefuegtAm":"2027-07-01T08:00:00.000Z"}],
        "umfrage":$poll,"reaktionen":{"zaehler":{"daumen":7},"eigene":null}}"""

    @Test
    fun postsFollowTheirFields() {
        server.enqueue(json(200, """{"beitraege":[$post],"naechster":null}"""))
        val posts = (runBlocking { api.posts(camp, all = false) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/pinnwand?limit=100 HTTP/1.1", server.takeRequest().requestLine)
        val first = posts.single()
        assertTrue(first.important && first.pinned)
        assertEquals("Luchs", first.author?.ceviName)
        assertEquals("Billette.pdf", first.files.single().name)
        assertEquals(7, first.reactions.count(Reaction.THUMBS_UP))
        assertEquals(14, first.poll!!.participants)
    }

    @Test
    fun editorsAskForAllPosts() {
        server.enqueue(json(200, """{"beitraege":[],"naechster":null}"""))
        runBlocking { api.posts(camp, all = true) }
        assertEquals("GET /api/v1/lager/$camp/pinnwand?limit=100&status=alle HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun voteSendsExactlyTheChosenOptions() {
        server.enqueue(json(200, poll))
        val updated = (runBlocking { api.vote(camp, postId, listOf(optionB)) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/pinnwand/$postId/umfrage/stimme HTTP/1.1", request.requestLine)
        assertEquals("""{"optionIds":["$optionB"]}""", request.body!!.utf8())
        assertEquals(9, updated.options.first().votes)
    }

    @Test
    fun reactionUsesTheKeyNeverAnEmoji() {
        server.enqueue(json(200, """{"zaehler":{"herz":2},"eigene":"herz"}"""))
        val state = (runBlocking { api.react(camp, postId, Reaction.HEART) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/reaktionen/pinnwand_beitrag/$postId HTTP/1.1", request.requestLine)
        assertEquals("""{"emoji":"herz"}""", request.body!!.utf8())
        assertEquals(Reaction.HEART, Reaction.from(state.own))
    }

    @Test
    fun anonymousInboxEntryHasNoNameAndTheCodeComesOnce() {
        server.enqueue(
            json(
                200,
                """{"eintraege":[{"id":"e1","version":1,"art":"feedback","text":"Nachtruhe","anonym":true,"status":"neu","antwort":null,"beantwortetTag":null,"erstelltTag":"2027-07-01",
                    "ressortId":null,"traktandumId":null,"von":null}],"naechster":null}""",
            ),
        )
        val entry = (runBlocking { api.inbox(camp) } as ApiResult.Ok).value.single()
        assertTrue(entry.anonymous)
        assertNull(entry.from)
        server.enqueue(json(201, """{"anonym":true,"rueckfragecode":"ABCD-EFGH-JKLM-NPQR-STUV","art":"frage","status":"neu","antwort":null,"beantwortetTag":null,"erstelltTag":"2027-07-01"}"""))
        val sent = (runBlocking { api.sendInbox(camp, InboxKind.QUESTION, "Wer holt die Zelte?", anonymous = true) } as ApiResult.Ok).value
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("""{"art":"frage","text":"Wer holt die Zelte?","anonym":true}""", request.body!!.utf8())
        assertEquals("ABCD-EFGH-JKLM-NPQR-STUV", sent.code)
        assertNull(sent.id)
    }

    @Test
    fun codeLookupPutsTheCodeInTheBodyNotTheAddress() {
        server.enqueue(json(200, """{"art":"frage","status":"erledigt","antwort":"Jonas holt sie.","beantwortetTag":"2027-07-02","erstelltTag":"2027-07-01"}"""))
        val view = (runBlocking { api.codeStatus(camp, normalizeCode("abcd-efgh jklm")) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/eingang/status HTTP/1.1", request.requestLine)
        assertEquals("""{"code":"ABCDEFGHJKLM"}""", request.body!!.utf8())
        assertEquals(InboxStatus.DONE, view.status)
    }

    @Test
    fun answeringNamesTheVersion() {
        server.enqueue(
            json(
                200,
                """{"id":"e1","version":2,"art":"idee","text":"x","anonym":false,"status":"in_arbeit","antwort":"Gute Idee","beantwortetTag":"2027-07-02","erstelltTag":"2027-07-01",
                   "ressortId":null,"traktandumId":null,"von":{"id":"p2","name":"Beispiel Person","ceviName":null}}""",
            ),
        )
        val entry = InboxEntry("e1", 1, "idee", "x", false, "neu", null, null, "2027-07-01")
        val body = kotlinx.serialization.json.buildJsonObject {
            put("antwort", kotlinx.serialization.json.JsonPrimitive("Gute Idee"))
            put("status", kotlinx.serialization.json.JsonPrimitive("in_arbeit"))
        }
        val changed = (runBlocking { api.answerInbox(camp, entry, body) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PATCH /api/v1/lager/$camp/eingang/e1 HTTP/1.1", request.requestLine)
        assertEquals("""{"version":1,"antwort":"Gute Idee","status":"in_arbeit"}""", request.body!!.utf8())
        assertEquals(InboxStatus.IN_PROGRESS, changed.status)
    }

    @Test
    fun markReadSendsEmptyBodyForAll() {
        server.enqueue(json(204, ""))
        server.enqueue(json(204, ""))
        runBlocking { api.markRead(camp, null) }
        runBlocking { api.markRead(camp, listOf("n1")) }
        assertEquals("{}", server.takeRequest().body!!.utf8())
        assertEquals("""{"ids":["n1"]}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun settingsAreReadAndSetPerKind() {
        server.enqueue(json(200, """{"arten":[{"art":"erwaehnung","aktiv":true},{"art":"frist","aktiv":false}]}"""))
        val settings = (runBlocking { api.settings() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/ich/lager-benachrichtigungen HTTP/1.1", server.takeRequest().requestLine)
        assertFalse(settings.kinds.first { it.kind == "frist" }.active)
        server.enqueue(json(204, ""))
        runBlocking { api.setSetting("frist", true) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/ich/lager-benachrichtigungen HTTP/1.1", request.requestLine)
        assertEquals("""{"arten":[{"art":"frist","aktiv":true}]}""", request.body!!.utf8())
    }
}
