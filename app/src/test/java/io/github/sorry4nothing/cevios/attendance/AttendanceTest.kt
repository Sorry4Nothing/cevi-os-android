package io.github.sorry4nothing.cevios.attendance

import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.chat.ProfileRole
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramAgeGroup
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttendanceTest {
    private lateinit var server: MockWebServer
    private lateinit var attendance: AttendanceApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        attendance = AttendanceApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private val body = """
        {"stufe":{"id":"s1","name":"Jungschar"},
         "anlaesse":[
           {"id":"p1","titel":"Gruppenstunde","ganztaegig":false,"beginn":"2026-10-17T12:00:00.000Z","ende":"2026-10-17T14:00:00.000Z","tagVon":null,"tagBis":null,"vergangen":true,"erfassbar":false},
           {"id":"p2","titel":"Gruppenstunde","ganztaegig":false,"beginn":"2026-10-24T12:00:00.000Z","ende":"2026-10-24T14:00:00.000Z","tagVon":null,"tagBis":null,"vergangen":true,"erfassbar":true},
           {"id":"p3","titel":"Lager","ganztaegig":true,"beginn":null,"ende":null,"tagVon":"2026-11-14","tagBis":"2026-11-15","vergangen":false,"erfassbar":false}],
         "zahlen":{"kinder":{"da":1,"von":2,"prozent":50},"leitende":{"da":0,"von":0,"prozent":null}},
         "naechster":{"programmId":"p3","kinder":{"ja":4,"nein":1,"offen":2},"leitende":{"ja":1,"nein":0,"offen":0}},
         "kinder":[{"id":"k1","vorname":"Lena","nachname":"Meier","anwesenheit":["da","nicht_da",null],"zusage":["ja","ja","ja"],"da":1,"von":2,"prozent":50}],
         "leitende":[{"id":"l1","name":"Sarah Meier","rolle":"stufenleitung","anwesenheit":[null,"da",null],"zusage":["offen","ja","ja"],"da":1,"von":1,"prozent":100}]}
    """.trimIndent()

    private fun json(code: Int, text: String) = MockResponse.Builder().code(code).body(text).build()

    private fun program(vararg groups: String) = Program(
        id = "p2", title = "Gruppenstunde", status = "aktiv", allDay = false,
        ageGroups = groups.map { ProgramAgeGroup(it, "Stufe $it") },
    )

    private fun profile(admin: Boolean = false, vararg roles: Pair<String, String>) =
        Profile(id = "u1", admin = admin, roles = roles.map { ProfileRole(it.first, it.second) })

    @Test
    fun theOverviewIsReadAndTheColumnOfTheProgramIsFound() {
        server.enqueue(json(200, body))
        val data = (runBlocking { attendance.overview("s1") } as ApiResult.Ok).value
        assertEquals("GET /api/v1/stufen/s1/anwesenheit HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(3, data.occasions.size)
        val index = data.indexOf("p2")
        assertEquals(1, index)
        assertTrue(data.occasions[index].recordable)
        assertFalse(data.occasions[data.indexOf("p1")].recordable)
        assertFalse(data.occasions[data.indexOf("p3")].past)
        // Die Spalte des Programms: Lena war am 24.10. nicht da, Sarah war da.
        assertEquals("Lena Meier", data.children.single().displayName)
        assertEquals("nicht_da", data.children.single().marks[index])
        assertEquals("Sarah Meier", data.leaders.single().displayName)
        assertTrue(data.leaders.single().isGroupLeader)
        assertEquals("da", data.leaders.single().marks[index])
    }

    @Test
    fun aProgramThatIsNotListedHasNoColumn() {
        server.enqueue(json(200, body))
        val data = (runBlocking { attendance.overview("s1") } as ApiResult.Ok).value
        assertEquals(-1, data.indexOf("unbekannt"))
        assertNull(data.occasions.getOrNull(data.indexOf("unbekannt")))
    }

    @Test
    fun recordingAChildPutsDaTrueFalseOrNull() {
        repeat(3) { server.enqueue(MockResponse.Builder().code(204).build()) }
        runBlocking {
            assertEquals(ApiResult.Ok(Unit), attendance.recordChild("s1", "p2", "k1", true))
            attendance.recordChild("s1", "p2", "k1", false)
            attendance.recordChild("s1", "p2", "k1", null)
        }
        val first = server.takeRequest()
        assertEquals("PUT /api/v1/stufen/s1/anwesenheit/p2/kinder/k1 HTTP/1.1", first.requestLine)
        assertEquals("""{"da":true}""", first.body?.utf8())
        assertEquals("""{"da":false}""", server.takeRequest().body?.utf8())
        // «Zurücknehmen» muss als `null` ankommen: Ohne das Feld lehnt der Server mit 400 ab.
        assertEquals("""{"da":null}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun recordingALeaderUsesTheLeaderPath() {
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { attendance.recordLeader("s1", "p2", "u9", true) }
        assertEquals("PUT /api/v1/stufen/s1/anwesenheit/p2/leitende/u9 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun aClosedWindowIsItsOwnError() {
        server.enqueue(json(409, """{"code":"ANWESENHEIT_GESCHLOSSEN","message":"x"}"""))
        assertEquals(
            ApiResult.Failure(ApiError.ATTENDANCE_CLOSED, "ANWESENHEIT_GESCHLOSSEN"),
            runBlocking { attendance.recordChild("s1", "p2", "k1", true) },
        )
    }

    @Test
    fun otherAgeGroupsAreForbidden() {
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        assertEquals(ApiResult.Failure(ApiError.FORBIDDEN), runBlocking { attendance.overview("s2") })
    }

    @Test
    fun leadersRecordInTheirOwnAgeGroupOfTheProgram() {
        assertEquals("s1", AttendanceRules.ageGroupFor(program("s1"), profile(roles = arrayOf("s1" to "leitung"))))
        assertEquals("s2", AttendanceRules.ageGroupFor(program("s1", "s2"), profile(roles = arrayOf("s2" to "stufenleitung"))))
    }

    @Test
    fun adminsRecordInTheFirstAgeGroupOfTheProgram() {
        assertEquals("s1", AttendanceRules.ageGroupFor(program("s1", "s2"), profile(admin = true)))
    }

    @Test
    fun othersAndUnassignedProgramsGetNoEntry() {
        assertNull(AttendanceRules.ageGroupFor(program("s1"), profile(roles = arrayOf("s2" to "stufenleitung"))))
        assertNull(AttendanceRules.ageGroupFor(program("s1"), profile(roles = arrayOf("s1" to "eltern"))))
        assertNull(AttendanceRules.ageGroupFor(program("s1"), profile()))
        assertNull(AttendanceRules.ageGroupFor(program("s1"), null))
        assertNull(AttendanceRules.ageGroupFor(program(), profile(admin = true)))
    }
}
