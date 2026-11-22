package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
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
import org.junit.Test
import java.time.LocalDate

class ProgramsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var programs: ProgramsApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        programs = ProgramsApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    /** Ein Termin aus «Mein Kalender» (`GET /api/v1/termine/mein` und `/termine/{id}`). */
    private val camp = """
        {"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","titel":"Sommerlager","status":"aktiv",
         "ganztaegig":true,"beginn":null,"ende":null,"tagVon":"2027-07-10","tagBis":"2027-07-17",
         "ort":"Zeltplatz","treffpunkt":null,"schlussOrt":"Bahnhof","schlussZeit":"16:30",
         "mitnehmen":"Schlafsack","tags":[{"id":"t1","name":"Stufe A","typ":"stufe"},{"id":"t2","name":"Team Mat","typ":"team"}],
         "anlass":{"id":"e1","anmeldeschluss":"2027-06-01T20:00:00Z","anmeldungOffen":true,"angemeldet":false},"kinder":[]}
    """.trimIndent()

    private fun page(vararg entries: String, more: Boolean = false, next: String? = null) =
        """{"termine":[${entries.joinToString(",")}],"weitere":$more,"naechste":${next?.let { "\"$it\"" } ?: "null"}}"""

    @Test
    fun listAsksForWindowOfMyCalendarAndReadsAllFields() {
        server.enqueue(json(200, page(camp)))
        val result = runBlocking { programs.list(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)) }
        assertEquals("GET /api/v1/termine/mein?von=2027-07-01&bis=2027-07-31&limit=100 HTTP/1.1", server.takeRequest().requestLine)
        val p = (result as ApiResult.Ok).value.programs.single()
        assertEquals("Sommerlager", p.title)
        assertEquals("Zeltplatz", p.location)
        assertEquals(listOf("Stufe A" to "stufe", "Team Mat" to "team"), p.tags.map { it.name to it.type })
        assertEquals("16:30", p.closingTime)
        assertEquals(ProgramEvent("e1", "2027-06-01T20:00:00Z", open = true, registered = false), p.event)
    }

    @Test
    fun listFollowsThePagesUntilTheLastOne() {
        val second = camp.replace("Sommerlager", "Waldspiel")
        server.enqueue(json(200, page(camp, more = true, next = "abc/+=")))
        server.enqueue(json(200, page(second)))
        val list = (runBlocking { programs.list(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)) } as ApiResult.Ok).value.programs
        assertEquals(listOf("Sommerlager", "Waldspiel"), list.map { it.title })
        server.takeRequest()
        // Der Wert für «nach» geht unverändert zurück, nur für die Adresse kodiert.
        assertEquals("GET /api/v1/termine/mein?von=2027-07-01&bis=2027-07-31&limit=100&nach=abc%2F%2B%3D HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun aFailingPageFailsTheWholeList() {
        server.enqueue(json(200, page(camp, more = true, next = "x")))
        server.enqueue(json(500, """{"code":"FEHLER","message":"x"}"""))
        val result = runBlocking { programs.list(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)) }
        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun detailReadsOneProgram() {
        server.enqueue(json(200, camp))
        val p = (runBlocking { programs.read("7c9e6679-7425-40de-944b-e07fc1f90ae7") } as ApiResult.Ok).value
        assertEquals("Schlafsack", p.bringAlong)
        assertEquals("Bahnhof", p.closingLocation)
        assertEquals("GET /api/v1/termine/7c9e6679-7425-40de-944b-e07fc1f90ae7 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun unknownProgramIsNotFound() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"Nicht gefunden"}"""))
        val result = runBlocking { programs.read("7c9e6679-7425-40de-944b-e07fc1f90ae7") }
        assertEquals(ApiResult.Failure(ApiError.NOT_FOUND), result)
    }

    @Test
    fun meetingPointBeatsGoogleLocation() {
        server.enqueue(json(200, camp.replace("\"treffpunkt\":null", "\"treffpunkt\":\"14:00 Uhr\\nPfadiheim\"")))
        assertEquals("Pfadiheim", (runBlocking { programs.read("x") } as ApiResult.Ok).value.location)
    }

    @Test
    fun meetingPointTextSplitsIntoTimeAndPlace() {
        assertEquals(MeetingPoint("14:00", "Pfadiheim\nEingang Nord"), parseMeetingPoint("14:00 Uhr\nPfadiheim\nEingang Nord"))
        assertEquals(MeetingPoint("09:30", null), parseMeetingPoint("09:30 Uhr"))
        assertEquals(MeetingPoint(null, "Bahnhof"), parseMeetingPoint("Bahnhof"))
        assertEquals(MeetingPoint(null, null), parseMeetingPoint(null))
        assertEquals(MeetingPoint(null, null), parseMeetingPoint("  \n "))
    }

    @Test
    fun allDayProgramsHaveCalendarDays() {
        server.enqueue(json(200, camp))
        val p = (runBlocking { programs.read("x") } as ApiResult.Ok).value
        assertEquals(Timing.AllDay(LocalDate.of(2027, 7, 10), LocalDate.of(2027, 7, 17)), p.time())
        assertTrue(!p.cancelled && !p.inReview)
    }

    @Test
    fun detailReadsChildrenAndOwnRegistration() {
        val detail = camp.replace(
            "\"kinder\":[]",
            """"teilnahmeOffen":true,"eigeneAnmeldung":"offen",
               "kinder":[{"id":"k1","vorname":"Mia","status":"ja"},{"id":"k2","vorname":"Noah","status":null}]""",
        )
        server.enqueue(json(200, detail))
        val p = (runBlocking { programs.read("x") } as ApiResult.Ok).value
        assertTrue(p.attendanceOpen)
        assertEquals("offen", p.ownRegistration)
        assertEquals(listOf(ProgramChild("k1", "Mia", "ja"), ProgramChild("k2", "Noah", null)), p.children)
    }

    @Test
    fun listEntryHasNoChildrenAndNoRegistration() {
        server.enqueue(json(200, camp))
        val p = (runBlocking { programs.read("x") } as ApiResult.Ok).value
        assertTrue(p.children.isEmpty() && !p.attendanceOpen)
        assertNull(p.ownRegistration)
    }

    @Test
    fun attendanceSendsYesToChildPath() {
        server.enqueue(MockResponse.Builder().code(204).build())
        val result = runBlocking { programs.setAttendance("p1", "k1", attending = true) }
        assertEquals(ApiResult.Ok(Unit), result)
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/programme/p1/teilnahmen/k1 HTTP/1.1", request.requestLine)
        assertEquals("""{"status":"ja"}""", request.body?.utf8())
    }

    @Test
    fun leaderSendsNoToLeaderPath() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(ApiResult.Ok(Unit), runBlocking { programs.setLeaderRsvp("p1", attending = false) })
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/programme/p1/leitung HTTP/1.1", request.requestLine)
        assertEquals("""{"status":"nein"}""", request.body?.utf8())
    }

    @Test
    fun closedProgramGivesOwnError() {
        server.enqueue(json(409, """{"code":"PROGRAMM_GESCHLOSSEN","message":"zu"}"""))
        assertEquals(
            ApiResult.Failure(ApiError.REGISTRATION_CLOSED, "PROGRAMM_GESCHLOSSEN"),
            runBlocking { programs.setAttendance("p1", "k1", attending = true) },
        )
    }

    @Test
    fun ageGroupsAndProfileAreRead() {
        server.enqueue(json(200, """{"stufen":[{"id":"s1","name":"Pfadi","kuerzel":"PF","reihenfolge":1}]}"""))
        assertEquals(
            listOf(ProgramAgeGroup("s1", "Pfadi")),
            (runBlocking { programs.ageGroups() } as ApiResult.Ok).value.ageGroups,
        )
        assertEquals("GET /api/v1/stufen HTTP/1.1", server.takeRequest().requestLine)
        server.enqueue(json(200, """{"id":"u1","admin":true,"rollen":[{"stufeId":"s1","typ":"stufenleitung"}]}"""))
        val profile = (runBlocking { programs.profile() } as ApiResult.Ok).value
        assertTrue(profile.admin && profile.isGroupLeader("s1"))
    }

    @Test
    fun withoutLocationThereIsNoLocation() {
        server.enqueue(json(200, camp.replace("\"ort\":\"Zeltplatz\"", "\"ort\":\"  \"")))
        assertNull((runBlocking { programs.read("x") } as ApiResult.Ok).value.location)
    }

    @Test
    fun contactsAreReadWithAndWithoutNumber() {
        server.enqueue(
            json(
                200,
                """{"kontakte":[{"id":"l1","name":"Beispiel Leitung A","telefon":"+41 79 000 00 01"},
                   {"id":"l2","name":"Beispiel Leitung B","telefon":null}]}""",
            ),
        )
        val contacts = (runBlocking { programs.contacts("p1") } as ApiResult.Ok).value.contacts
        assertEquals("GET /api/v1/programme/p1/kontakte HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(listOf("+41 79 000 00 01", null), contacts.map { it.phone })
        assertEquals("Beispiel Leitung B", contacts[1].name)
    }

    @Test
    fun noRegisteredLeaderMeansEmptyContacts() {
        server.enqueue(json(200, """{"kontakte":[]}"""))
        assertTrue((runBlocking { programs.contacts("p1") } as ApiResult.Ok).value.contacts.isEmpty())
    }

    @Test
    fun listEntryCarriesTheStatusOfOwnChildren() {
        server.enqueue(
            json(
                200,
                page(camp.replace("\"kinder\":[]", "\"kinder\":[{\"id\":\"k1\",\"vorname\":\"Noah\",\"status\":\"ja\"},{\"id\":\"k2\",\"vorname\":\"Lena\",\"status\":null}]")),
            ),
        )
        val p = (runBlocking { programs.list(LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31)) } as ApiResult.Ok).value.programs.single()
        assertEquals(listOf("Noah" to "ja", "Lena" to null), p.children.map { it.firstName to it.status })
    }

    @Test
    fun contactImageUrlIsRead() {
        server.enqueue(json(200, """{"kontakte":[{"id":"s1","name":"Sarah","bildUrl":"/api/v1/personen/s1/bild","telefon":null}]}"""))
        val contacts = (runBlocking { programs.contacts("p1") } as ApiResult.Ok).value
        assertEquals("/api/v1/personen/s1/bild", contacts.contacts.single().imageUrl)
    }

    @Test
    fun registrationsAreReadOnlyAGet() {
        server.enqueue(json(200, """{"zahlen":{"kinder":{"ja":1,"nein":0,"offen":0},"leitende":{"ja":0,"nein":0,"offen":0}},"kinder":[{"id":"k1","vorname":"Lena","nachname":"Meier","stufeId":"s1","status":"ja"}],"leitende":[]}"""))
        val result = (runBlocking { programs.registrations("p1") } as ApiResult.Ok).value
        assertEquals(1, result.children.size)
        assertEquals("GET /api/v1/programme/p1/teilnahmen HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun registrationsForbiddenForOtherStagesIsForbidden() {
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        assertEquals(ApiResult.Failure(ApiError.FORBIDDEN), runBlocking { programs.registrations("p1") })
    }
}
