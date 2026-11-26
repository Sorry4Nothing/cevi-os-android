package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Lager-Check und eigene Punkte gegen einen Fake-Server: Pfade, Felder, Version, Fehler. */
class CheckApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: CheckApi
    private val camp = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val a = "11111111-1111-4111-8111-111111111111"
    private val b = "22222222-2222-4222-8222-222222222222"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = CheckApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun bodyOf(request: mockwebserver3.RecordedRequest): JsonObject = kotlinx.serialization.json.Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun checkReadsLevelCheckersNotesAndExamples() {
        server.enqueue(
            json(
                200,
                """{"stand":"offen","datum":"2027-03-01","pruefer":[
                    {"id":"aufgaben","titel":"Aufgaben","stand":"offen","hinweise":[
                        {"code":"AUFGABEN_UEBERFAELLIG","schwere":"offen","text":"7 Aufgaben sind überfällig.","anzahl":7,
                         "beispiele":[{"id":"$a","titel":"Lagerhaus buchen","datum":"2027-02-20"}]}]},
                    {"id":"packliste","titel":"Packliste","stand":"bereit","hinweise":[]},
                    {"id":"meilensteine","titel":"Meilensteine","stand":"hinweise","hinweise":[
                        {"code":"MEILENSTEIN_NAH","schwere":"hinweis","text":"Ein Meilenstein ist nah.","anzahl":1,
                         "beispiele":[{"id":"$b","titel":"Anmeldeschluss","datum":"2027-03-05","offeneAufgaben":2}]}]}]}""",
            ),
        )
        val check = (runBlocking { api.check(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/check HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(CheckLevel.OPEN, check.level)
        assertEquals(3, check.checkers.size)
        val note = check.checkers.first().notes.single()
        assertTrue(note.blocking)
        assertEquals(7, note.count)
        assertEquals("Lagerhaus buchen", note.examples.single().title)
        assertEquals(2, check.checkers[2].notes.single().examples.single().openTasks)
        assertFalse(check.checkers[2].notes.single().blocking)
    }

    @Test
    fun anUnknownLevelCountsAsOpen() {
        server.enqueue(json(200, """{"stand":"irgendwas","datum":"2027-03-01","pruefer":[]}"""))
        assertEquals(CheckLevel.OPEN, (runBlocking { api.check(camp) } as ApiResult.Ok).value.level)
    }

    @Test
    fun noRightIsForbidden() {
        server.enqueue(json(403, """{"code":"KEIN_RECHT","message":"Kein Recht."}"""))
        assertEquals(ApiError.FORBIDDEN, (runBlocking { api.check(camp) } as ApiResult.Failure).error)
    }

    @Test
    fun pointsAreReadWithVersion() {
        server.enqueue(json(200, """{"punkte":[{"id":"$a","version":3,"titel":"Elternbrief","erledigt":false,"reihenfolge":0}]}"""))
        val points = (runBlocking { api.points(camp) } as ApiResult.Ok).value.points
        assertEquals("GET /api/v1/lager/$camp/check/punkte HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(3L, points.single().version)
        assertEquals("Elternbrief", points.single().title)
    }

    @Test
    fun addingSendsTheTrimmedTitleOnly() {
        server.enqueue(json(201, """{"id":"$a","version":1,"titel":"Elternbrief","erledigt":false,"reihenfolge":0}"""))
        val point = (runBlocking { api.addPoint(camp, "  Elternbrief ") } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/check/punkte HTTP/1.1", request.requestLine)
        assertEquals(setOf("titel"), bodyOf(request).keys)
        assertEquals("Elternbrief", bodyOf(request)["titel"]!!.jsonPrimitive.content)
        assertEquals(a, point.id)
    }

    @Test
    fun theLimitComesBackAsAConflictCode() {
        server.enqueue(json(409, """{"code":"LIMIT_ERREICHT","message":"Zu viele Punkte."}"""))
        val failure = runBlocking { api.addPoint(camp, "Noch einer") } as ApiResult.Failure
        assertEquals("LIMIT_ERREICHT", failure.code)
    }

    @Test
    fun ticksSendOnlyVersionAndDone() {
        server.enqueue(json(200, """{"id":"$a","version":4,"titel":"Elternbrief","erledigt":true,"reihenfolge":0}"""))
        val point = (runBlocking { api.changePoint(camp, a, 3, done = true) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PATCH /api/v1/lager/$camp/check/punkte/$a HTTP/1.1", request.requestLine)
        assertEquals(setOf("version", "erledigt"), bodyOf(request).keys)
        assertEquals("3", bodyOf(request)["version"]!!.jsonPrimitive.content)
        assertEquals("true", bodyOf(request)["erledigt"]!!.jsonPrimitive.content)
        assertTrue(point.done)
        assertEquals(4L, point.version)
    }

    @Test
    fun renamingSendsTheTitle() {
        server.enqueue(json(200, """{"id":"$a","version":5,"titel":"Elternbrief senden","erledigt":false,"reihenfolge":0}"""))
        runBlocking { api.changePoint(camp, a, 4, title = " Elternbrief senden ") }
        val body = bodyOf(server.takeRequest())
        assertEquals(setOf("version", "titel"), body.keys)
        assertEquals("Elternbrief senden", body["titel"]!!.jsonPrimitive.content)
    }

    @Test
    fun aVersionConflictCarriesTheCurrentRow() {
        server.enqueue(
            json(
                409,
                """{"code":"VERSION_KONFLIKT","message":"Geändert.","aktuell":{"id":"$a","version":6,"titel":"Anders","erledigt":false},"abweichend":["titel"]}""",
            ),
        )
        val failure = runBlocking { api.changePoint(camp, a, 4, title = "Meins") } as ApiResult.Failure
        assertEquals(ApiError.VERSION_CONFLICT, failure.error)
        val conflict = failure.conflict
        assertEquals(6L, conflict?.let(::conflictVersion))
        assertEquals(listOf("titel"), conflict?.differing)
    }

    @Test
    fun deletingSendsTheVersionInTheQuery() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertTrue(runBlocking { api.deletePoint(camp, a, 7) } is ApiResult.Ok)
        assertEquals("DELETE /api/v1/lager/$camp/check/punkte/$a?version=7 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun aClosedCampRefusesWriting() {
        server.enqueue(json(409, """{"code":"LAGER_GESCHLOSSEN","message":"Geschlossen."}"""))
        assertEquals(ApiError.LAGER_CLOSED, (runBlocking { api.addPoint(camp, "x") } as ApiResult.Failure).error)
    }
}
