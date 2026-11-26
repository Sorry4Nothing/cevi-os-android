package io.github.sorry4nothing.cevios.termine

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class RoomsApiTest {
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

    private val roomId = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    @Test
    fun theRoomsKnowTheirTeamOrNone() {
        server.enqueue(
            json(
                200,
                """{"raeume":[
                    {"id":"$roomId","name":"Pfadiheim Beispielwald","ort":null,"beschreibung":null,"kapazitaet":30,"aktiv":true,"team":{"id":"t1","name":"Team Mat"}},
                    {"id":"r2","name":"Gruppenraum Keller","ort":"Keller","beschreibung":null,"kapazitaet":null,"aktiv":true,"team":null}
                ],"weitere":false,"naechste":null}""",
            ),
        )
        val page = (runBlocking { api.rooms() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/raeume?limit=100 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Team Mat", page.rooms[0].team?.name)
        assertEquals(30, page.rooms[0].capacity)
        assertNull(page.rooms[1].team)
        assertEquals("Keller", page.rooms[1].place)
    }

    @Test
    fun aRequestIsSentAsTheContractSays() {
        server.enqueue(json(201, """{"id":"a1","status":"angefragt"}"""))
        val body = RoomRequestBody("2027-12-12T14:00+01:00", "2027-12-12T18:00+01:00", "Waldspiel Adler")
        val created = (runBlocking { api.requestRoom(roomId, body) } as ApiResult.Ok).value
        val sent = server.takeRequest()
        assertEquals("POST /api/v1/raeume/$roomId/anfragen HTTP/1.1", sent.requestLine)
        assertEquals("""{"beginn":"2027-12-12T14:00+01:00","ende":"2027-12-12T18:00+01:00","zweck":"Waldspiel Adler"}""", sent.body?.utf8())
        assertEquals("angefragt", created.status)
    }

    @Test
    fun aClashWithABookingKeepsItsCode() {
        server.enqueue(json(409, """{"code":"RAUM_BELEGT","message":"x"}"""))
        val failure = runBlocking { api.requestRoom(roomId, RoomRequestBody("a", "b", "c")) } as ApiResult.Failure
        assertEquals("RAUM_BELEGT", failure.code)
    }

    @Test
    fun theLimitAndTheTimeRulesKeepTheirCodes() {
        server.enqueue(json(409, """{"code":"ANFRAGE_LIMIT","message":"x"}"""))
        server.enqueue(json(400, """{"code":"ZEIT_VERGANGEN","message":"x"}"""))
        server.enqueue(json(400, """{"code":"ZEIT_ZU_LANG","message":"x"}"""))
        val codes = (1..3).map { (runBlocking { api.requestRoom(roomId, RoomRequestBody("a", "b", "c")) } as ApiResult.Failure).code }
        assertEquals(listOf("ANFRAGE_LIMIT", "ZEIT_VERGANGEN", "ZEIT_ZU_LANG"), codes)
    }

    @Test
    fun aRoomThatIsGoneIsNotFound() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"x"}"""))
        val failure = runBlocking { api.requestRoom(roomId, RoomRequestBody("a", "b", "c")) } as ApiResult.Failure
        assertEquals(ApiError.NOT_FOUND, failure.error)
    }

    @Test
    fun myRequestsComeInEveryStateWithTheReasonOfARefusal() {
        server.enqueue(
            json(
                200,
                """{"anfragen":[
                    {"id":"a1","raumId":"$roomId","raumName":"Pfadiheim Beispielwald","beginn":"2027-12-12T13:00:00Z","ende":"2027-12-12T17:00:00Z","zweck":"Waldspiel","status":"angefragt","begruendung":null,"entschiedenAm":null,"erstelltAm":"2027-11-01T10:00:00Z"},
                    {"id":"a2","raumId":"$roomId","raumName":"Pfadiheim Beispielwald","beginn":"2027-11-21T08:00:00Z","ende":"2027-11-21T11:00:00Z","zweck":"Inventur","status":"abgelehnt","begruendung":"Schon vergeben","entschiedenAm":"2027-11-02T10:00:00Z","erstelltAm":"2027-11-01T10:00:00Z"}
                ],"weitere":false,"naechste":null}""",
            ),
        )
        val page = (runBlocking { api.myRoomRequests() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/raeume/anfragen/meine?limit=50 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(listOf("angefragt", "abgelehnt"), page.requests.map { it.status })
        assertNull(page.requests[0].reason)
        assertEquals("Schon vergeben", page.requests[1].reason)
    }

    @Test
    fun withdrawingIsADeleteOfTheRequest() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Ok(Unit), runBlocking { api.cancelRoomRequest("a1") })
        assertEquals("DELETE /api/v1/raeume/anfragen/a1 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun theOpenRequestsNameWhoAskedAndAnEmptyListMeansNothingToDecide() {
        server.enqueue(
            json(
                200,
                """{"anfragen":[
                    {"id":"a1","raumId":"$roomId","raumName":"Pfadiheim Beispielwald","beginn":"2027-11-20T08:00:00Z","ende":"2027-11-20T11:00:00Z","zweck":"Gruppenstunde","status":"angefragt","begruendung":null,"entschiedenAm":null,"erstelltAm":"2027-11-01T10:00:00Z","vonName":"Beispiel Person"}
                ],"weitere":true,"naechste":"7c9e6679-7425-40de-944b-e07fc1f90ae7"}""",
            ),
        )
        val page = (runBlocking { api.openRoomRequests() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/raeume/anfragen/offen?limit=50 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Beispiel Person", page.requests.single().requesterName)
        assertTrue(page.more)
        server.enqueue(json(200, """{"anfragen":[],"weitere":false,"naechste":null}"""))
        assertTrue((runBlocking { api.openRoomRequests() } as ApiResult.Ok).value.requests.isEmpty())
    }

    @Test
    fun confirmingSendsNoReasonAndRejectingSendsOne() {
        server.enqueue(json(200, """{"id":"a1","status":"bestaetigt"}"""))
        val confirmed = (runBlocking { api.decideRoomRequest("a1", RoomDecisionBody("annehmen")) } as ApiResult.Ok).value
        val first = server.takeRequest()
        assertEquals("POST /api/v1/raeume/anfragen/a1/entscheid HTTP/1.1", first.requestLine)
        // Der Vertrag erlaubt bei `annehmen` keine Begründung: Das Feld fehlt ganz.
        assertEquals("""{"entscheid":"annehmen"}""", first.body?.utf8())
        assertEquals("bestaetigt", confirmed.status)
        server.enqueue(json(200, """{"id":"a2","status":"abgelehnt"}"""))
        runBlocking { api.decideRoomRequest("a2", RoomDecisionBody("ablehnen", "Schon vergeben")) }
        assertEquals("""{"entscheid":"ablehnen","begruendung":"Schon vergeben"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun theConflictsOfADecisionKeepTheirCodeAndAreNeverSentTwice() {
        server.enqueue(json(409, """{"code":"RAUM_BELEGT","message":"x"}"""))
        val busy = runBlocking { api.decideRoomRequest("a1", RoomDecisionBody("annehmen")) } as ApiResult.Failure
        assertEquals(DecisionNotice.BUSY, decisionNotice(true, busy))
        server.enqueue(json(409, """{"code":"STATUS_UNGUELTIG","message":"x"}"""))
        val done = runBlocking { api.decideRoomRequest("a1", RoomDecisionBody("annehmen")) } as ApiResult.Failure
        assertEquals(DecisionNotice.ALREADY_DECIDED, decisionNotice(true, done))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun onlyThePlatformRoleStillMeetsTheSecondFactorAndTheAppSaysComputerOnly() {
        // Team, Gruppenrollen und Abteilungsleitung brauchen ihn nicht mehr (#859); beide Codes des Servers bleiben «nur am Computer».
        listOf("ZWEITER_FAKTOR_FEHLT", "ZWEITER_FAKTOR_SITZUNG").forEach { code ->
            server.enqueue(json(403, """{"code":"$code","message":"x"}"""))
            val failure = runBlocking { api.decideRoomRequest("a1", RoomDecisionBody("annehmen")) } as ApiResult.Failure
            assertEquals(DecisionNotice.SECOND_FACTOR, decisionNotice(true, failure))
        }
        server.enqueue(json(403, """{"code":"VERBOTEN","message":"x"}"""))
        val forbidden = runBlocking { api.decideRoomRequest("a1", RoomDecisionBody("annehmen")) } as ApiResult.Failure
        assertEquals(DecisionNotice.NOT_ALLOWED, decisionNotice(true, forbidden))
    }

    @Test
    fun anAlreadyDecidedRequestCannotBeWithdrawn() {
        server.enqueue(json(409, """{"code":"STATUS_UNGUELTIG","message":"x"}"""))
        val failure = runBlocking { api.cancelRoomRequest("a1") } as ApiResult.Failure
        assertEquals("STATUS_UNGUELTIG", failure.code)
    }
}
