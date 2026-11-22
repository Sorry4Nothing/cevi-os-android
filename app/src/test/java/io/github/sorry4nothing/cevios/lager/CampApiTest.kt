package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

/** Die Lager-Routen gegen einen Fake-Server: Pfade, Felder, Fehlercodes. */
class CampApiTest {
    // Ein hängender Test soll mit Namen scheitern, nicht die CI stundenlang blockieren.
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var camps: CampApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        camps = CampApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val id = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    private val camp = """
        {"id":"$id","titel":"Sommerlager Beispiel","von":"2027-07-17","bis":"2027-07-24","vorbereitungAb":null,
         "ort":"Lagerhaus","adresse":null,"status":"aktiv","farbe":null,"logoMediumId":null,"rolle":"lagerleitung"}
    """.trimIndent()

    @Test
    fun listReadsCampsWithRole() {
        server.enqueue(json(200, """{"lager":[$camp]}"""))
        val list = (runBlocking { camps.list() } as ApiResult.Ok).value.camps
        assertEquals("GET /api/v1/lager HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Sommerlager Beispiel", list.single().title)
        assertEquals(CampRole.LEADERSHIP, list.single().role)
        assertFalse(list.single().readOnly)
    }

    @Test
    fun detailCarriesWriteProtection() {
        server.enqueue(json(200, camp.replace("\"rolle\"", "\"schreibgeschuetzt\":true,\"rolle\"").replace("aktiv", "abgeschlossen")))
        val detail = (runBlocking { camps.read(id) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$id HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(detail.readOnly)
        assertFalse(detail.permissions().mayWrite("aufgaben.bearbeiten"))
    }

    @Test
    fun detailCarriesTheGrantedRightsAndThePracticeFlag() {
        server.enqueue(json(200, camp.replace("\"rolle\"", "\"uebung\":true,\"schreibgeschuetzt\":false,\"rechte\":[\"aufgaben.eigene\",\"pinnwand.lesen\"],\"rolle\"")))
        val detail = (runBlocking { camps.read(id) } as ApiResult.Ok).value
        assertTrue(detail.practice)
        val permissions = detail.permissions()
        assertTrue(permissions.mayWrite("aufgaben.eigene") && permissions.may("pinnwand.lesen"))
        assertFalse(permissions.may("aufgaben.bearbeiten"))
    }

    @Test
    fun theListNamesNoRightsAndNoPracticeCampByDefault() {
        server.enqueue(json(200, """{"lager":[$camp]}"""))
        val listed = (runBlocking { camps.list() } as ApiResult.Ok).value.camps.single()
        assertFalse(listed.practice)
        assertEquals(null, listed.grantedRights)
        assertFalse(listed.permissions().may("aufgaben.eigene"))
    }

    @Test
    fun listWithoutDetailFollowsStatus() {
        assertTrue(Camp(id, "x", "2027-07-17", "2027-07-24", null, "archiviert", "team").readOnly)
        assertFalse(Camp(id, "x", "2027-07-17", "2027-07-24", null, "entwurf", "team").readOnly)
    }

    @Test
    fun unknownRoleGetsTheSmallest() {
        assertEquals(CampRole.READ_ONLY, CampRole.from("etwas-neues"))
        assertFalse(Camp(id, "x", "2027-07-17", "2027-07-24", null, "aktiv", "etwas-neues").permissions().mayWrite("aufgaben.eigene"))
    }

    @Test
    fun rightsAreReadForTheLeadership() {
        server.enqueue(
            json(
                200,
                """{"rechte":[{"recht":"aufgaben.bearbeiten","bedeutung":"Aufgaben ändern",
                    "standard":{"leitung":true,"team":false,"lesen":false},"aktuell":{"leitung":true,"team":true,"lesen":false}}]}""",
            ),
        )
        val right = (runBlocking { camps.rights(id) } as ApiResult.Ok).value.rights.single()
        assertEquals("GET /api/v1/lager/$id/rechte HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(right.current.team)
        assertFalse(right.standard.team)
    }

    @Test
    fun feedStateAndPagesAreRead() {
        server.enqueue(json(200, """{"seq":42,"objekttypen":["aufgabe","ressort"]}"""))
        server.enqueue(
            json(
                200,
                """{"eintraege":[{"seq":43,"objekttyp":"aufgabe","id":"$id","art":"neu","version":1,"zeitpunkt":"2027-07-17T10:00:00Z"}],
                    "bis":43,"weitere":false}""",
            ),
        )
        val state = (runBlocking { camps.feedState(id) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$id/aenderungen HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(42L, state.seq)
        assertEquals(listOf("aufgabe", "ressort"), state.types)
        val page = (runBlocking { camps.feed(id, 42) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$id/aenderungen?seit=42 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("aufgabe", page.entries.single().type)
        assertEquals(43L, page.until)
        assertFalse(page.more)
    }

    @Test
    fun goneMeansReloadTheLists() {
        server.enqueue(json(410, """{"code":"FEED_NEU_LADEN","message":"zu alt","seq":99}"""))
        assertEquals(ApiResult.Failure(ApiError.FEED_RELOAD, "FEED_NEU_LADEN"), runBlocking { camps.feed(id, 1) })
    }

    @Test
    fun queueIsSentWithClientOpIds() {
        server.enqueue(
            json(200, """{"ergebnisse":[{"clientOpId":"a","status":"ok","ergebnis":{"id":"x","version":2}},{"clientOpId":"b","status":"konflikt","code":"VERSION_KONFLIKT","aktuell":{"titel":"neu","version":5},"abweichend":["titel"]},{"clientOpId":"c","status":"abgelehnt","code":"KEINE_BERECHTIGUNG"}]}"""),
        )
        val ops = listOf("a", "b", "c").map { QueueOperation(it, "aufgabe.aendern", buildJsonObject { put("titel", "x") }) }
        val results = (runBlocking { camps.sendQueue(id, ops) } as ApiResult.Ok).value.results
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$id/aenderungen HTTP/1.1", request.requestLine)
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"operationen\""))
        assertTrue(body.contains("\"clientOpId\":\"a\""))
        assertTrue(body.contains("\"nutzlast\":{\"titel\":\"x\"}"))
        assertEquals(listOf("ok", "konflikt", "abgelehnt"), results.map { it.status })
        assertEquals(listOf("titel"), results[1].differing)
        assertEquals("KEINE_BERECHTIGUNG", results[2].code)
    }

    @Test
    fun closedCampAnswers409() {
        server.enqueue(json(409, """{"code":"LAGER_GESCHLOSSEN","message":"zu"}"""))
        val result = runBlocking { camps.sendQueue(id, listOf(QueueOperation("a", "x", buildJsonObject {}))) }
        assertEquals(ApiError.LAGER_CLOSED, (result as ApiResult.Failure).error)
    }

    @Test
    fun maintenanceIsItsOwnError() {
        server.enqueue(json(503, """{"code":"IN_WARTUNG","message":"x","hinweis":null,"ende":null}"""))
        assertEquals(ApiError.MAINTENANCE, (runBlocking { camps.list() } as ApiResult.Failure).error)
    }

    @Test
    fun leadershipWithoutSecondFactorGetsItsOwnError() {
        server.enqueue(json(403, """{"code":"ZWEITER_FAKTOR_FEHLT","message":"x"}"""))
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        assertEquals(ApiError.SECOND_FACTOR_MISSING, (runBlocking { camps.read(id) } as ApiResult.Failure).error)
        assertEquals(ApiResult.Failure(ApiError.FORBIDDEN), runBlocking { camps.read(id) })
    }

    @Test
    fun unreadAndResolveUseTheirRoutes() {
        server.enqueue(json(200, """{"anzahl":3}"""))
        server.enqueue(json(200, """{"lagerId":"$id","objektTyp":"aufgabe","objektId":"$id"}"""))
        assertEquals(3, (runBlocking { camps.unread(id) } as ApiResult.Ok).value.count)
        assertEquals("GET /api/v1/lager/$id/benachrichtigungen/ungelesen HTTP/1.1", server.takeRequest().requestLine)
        val target = (runBlocking { camps.resolve("lager_aufgabe", id) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/aufloesen/lager_aufgabe/$id HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(id, target.campId)
        assertEquals("aufgabe", target.objectType)
    }

    @Test
    fun unknownPushTargetIs404() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"x"}"""))
        assertEquals(ApiError.NOT_FOUND, (runBlocking { camps.resolve("lager_aufgabe", id) } as ApiResult.Failure).error)
    }
}
