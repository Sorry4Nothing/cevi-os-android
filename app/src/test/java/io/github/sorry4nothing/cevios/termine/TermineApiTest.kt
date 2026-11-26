package io.github.sorry4nothing.cevios.termine

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
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

class TermineApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var api: TermineApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = TermineApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val token = "A".repeat(43)

    @Test
    fun linkStatusNeverCarriesTheToken() {
        server.enqueue(json(200, """{"aktiv":true,"erstelltAm":"2027-01-01T10:00:00Z","zuletztAbgerufenAm":null,"endung":"aB3_"}"""))
        val status = (runBlocking { api.linkStatus() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/ich/kalender-link HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(status.active)
        assertEquals("aB3_", status.ending)
        assertNull(status.lastFetchedAt)
    }

    @Test
    fun createdLinkIsPostedWithoutBodyAndNeverPrinted() {
        server.enqueue(json(201, """{"token":"$token","pfad":"/ical/$token.ics","erstelltAm":"2027-01-01T10:00:00Z","endung":"AAAA"}"""))
        val fresh = (runBlocking { api.createLink() } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/ich/kalender-link HTTP/1.1", request.requestLine)
        assertEquals(0L, (request.body?.size ?: 0).toLong())
        assertEquals("/ical/$token.ics", fresh.path)
        // Der Token ist ein Geheimnis: Er steht in keiner Textausgabe.
        assertFalse(fresh.toString().contains(token))
        assertTrue(api.linkUrl(fresh.path).endsWith("/ical/$token.ics"))
    }

    @Test
    fun revokingTheLinkIsADelete() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Ok(Unit), runBlocking { api.revokeLink() })
        assertEquals("DELETE /api/v1/ich/kalender-link HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun tooManyLinkChangesAreAnError() {
        server.enqueue(json(429, """{"code":"ZU_VIELE_ANFRAGEN","message":"x"}"""))
        assertEquals(ApiError.TOO_MANY_REQUESTS, (runBlocking { api.createLink() } as ApiResult.Failure).error)
    }

    @Test
    fun optionsTellWhetherThePersonMayPropose() {
        server.enqueue(json(200, """{"darfVorschlagen":true,"tags":[{"id":"t1","name":"Wölfe","typ":"stufe"}]}"""))
        val options = (runBlocking { api.proposalOptions() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/termin-vorschlaege/optionen HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(options.mayPropose)
        assertEquals(listOf(ProposalTag("t1", "Wölfe", "stufe")), options.tags)
    }

    @Test
    fun aProposalIsSentAsTheContractSaysAndOmitsWhatIsNotSet() {
        server.enqueue(json(201, """{"id":"p1","status":"offen"}"""))
        val request = ProposalRequest("Waldspiel", false, start = "2027-05-01T14:00+02:00", end = "2027-05-01T17:00+02:00", tagIds = listOf("t1"))
        val created = (runBlocking { api.propose(request) } as ApiResult.Ok).value
        val sent = server.takeRequest()
        assertEquals("POST /api/v1/termin-vorschlaege HTTP/1.1", sent.requestLine)
        assertEquals("""{"titel":"Waldspiel","ganztaegig":false,"beginn":"2027-05-01T14:00+02:00","ende":"2027-05-01T17:00+02:00","tagIds":["t1"]}""", sent.body?.utf8())
        assertEquals("offen", created.status)
    }

    @Test
    fun theLimitOfOpenProposalsKeepsItsCode() {
        server.enqueue(json(409, """{"code":"VORSCHLAG_LIMIT","message":"x"}"""))
        val failure = runBlocking { api.propose(ProposalRequest("x", true, dayFrom = "2027-05-01", dayTo = "2027-05-01", tagIds = listOf("t"))) } as ApiResult.Failure
        assertEquals("VORSCHLAG_LIMIT", failure.code)
    }

    @Test
    fun myProposalsAreReadWithTheirDecision() {
        server.enqueue(
            json(
                200,
                """{"vorschlaege":[{"id":"p1","status":"abgelehnt","titel":"Waldspiel","ganztaegig":true,"beginn":null,"ende":null,
                   "tagVon":"2027-05-01","tagBis":"2027-05-01","ort":null,"beschreibung":null,"bemerkung":null,"tags":[],
                   "begruendung":"Termin ist schon belegt","entschiedenAm":"2027-04-01T10:00:00Z","erstelltAm":"2027-03-01T10:00:00Z","vorgeschlagenVon":"u1"}],
                   "weitere":false,"naechste":null}""",
            ),
        )
        val page = (runBlocking { api.myProposals() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/termin-vorschlaege/meine?limit=50 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("abgelehnt", page.proposals.single().status)
        assertEquals("Termin ist schon belegt", page.proposals.single().reason)
    }

    @Test
    fun withdrawingAProposalIsADelete() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Ok(Unit), runBlocking { api.withdraw("p1") })
        assertEquals("DELETE /api/v1/termin-vorschlaege/p1 HTTP/1.1", server.takeRequest().requestLine)
    }

    private val event = """
        {"id":"e1","terminId":"t1","titel":"Sommerlager","ganztaegig":true,"beginn":null,"ende":null,"tagVon":"2027-07-17","tagBis":"2027-07-24",
         "anmeldungOffen":true,"frist":"2027-06-01T20:00:00Z",
         "felder":[{"schluessel":"schwimmt","beschriftung":"Mein Kind schwimmt sicher","typ":"ja_nein","optionen":[],"pflicht":true},
                   {"schluessel":"shirt","beschriftung":"T-Shirt-Grösse","typ":"auswahl","optionen":["128","140"],"pflicht":false}],
         "teilnehmende":[{"kindId":"k1","name":"Mia","konto":[{"schluessel":"vorname","beschriftung":"Vorname","wert":"Mia"}],"anmeldung":null}]}
    """.trimIndent()

    @Test
    fun anEventCarriesFieldsAndWhatTheAccountKnows() {
        server.enqueue(json(200, event))
        val form = (runBlocking { api.event("e1") } as ApiResult.Ok).value
        assertEquals("GET /api/v1/anlaesse/e1 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(listOf("ja_nein", "auswahl"), form.fields.map { it.type })
        assertEquals(listOf("128", "140"), form.fields[1].options)
        assertEquals("Mia", form.participants.single().account.single().value)
        assertNull(form.participants.single().registration)
    }

    @Test
    fun registeringSendsTheChildAndTheAnswersAsPut() {
        server.enqueue(json(200, """{"id":"a1","status":"angemeldet","antworten":{"schwimmt":true,"shirt":"128"},"geaendertAm":"2027-01-01T10:00:00Z"}"""))
        val body = EventRegistrationRequest("k1", mapOf("schwimmt" to JsonPrimitive(true), "shirt" to JsonPrimitive("128")))
        val registration = (runBlocking { api.register("e1", body) } as ApiResult.Ok).value
        val sent = server.takeRequest()
        assertEquals("PUT /api/v1/anlaesse/e1/anmeldung HTTP/1.1", sent.requestLine)
        assertEquals("""{"kindId":"k1","antworten":{"schwimmt":true,"shirt":"128"}}""", sent.body?.utf8())
        assertTrue(registration.registered)
        assertEquals(true, registration.answers.getValue("schwimmt").asAnswerBoolean())
    }

    @Test
    fun aPersonRegistersWithoutAChildId() {
        server.enqueue(json(200, """{"id":"a1","status":"angemeldet","antworten":{},"geaendertAm":"2027-01-01T10:00:00Z"}"""))
        runBlocking { api.register("e1", EventRegistrationRequest(null, emptyMap())) }
        assertEquals("""{"antworten":{}}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun aClosedRegistrationIsAConflict() {
        server.enqueue(json(409, """{"code":"ANMELDUNG_GESCHLOSSEN","message":"x"}"""))
        val failure = runBlocking { api.register("e1", EventRegistrationRequest("k1", emptyMap())) } as ApiResult.Failure
        assertEquals(ApiError.REGISTRATION_CLOSED, failure.error)
    }

    @Test
    fun unregisteringAPersonAndAChildUseTheirOwnForms() {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.unregister("e1") }
        runBlocking { api.unregisterChild("e1", "k1") }
        assertEquals("DELETE /api/v1/anlaesse/e1/anmeldung HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("DELETE /api/v1/anlaesse/e1/anmeldung?kindId=k1 HTTP/1.1", server.takeRequest().requestLine)
    }
}
