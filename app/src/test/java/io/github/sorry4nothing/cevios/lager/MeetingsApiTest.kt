package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Höcks, Traktanden, Protokolle, Beschlüsse und Erwähnungen gegen einen Fake-Server: Pfade, Felder, Konflikt. */
class MeetingsApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: MeetingsApi
    private val camp = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val meetingId = "11111111-1111-4111-8111-111111111111"
    private val itemId = "22222222-2222-4222-8222-222222222222"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = MeetingsApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val item = """{"id":"$itemId","version":4,"hoeckId":"$meetingId","titel":"Offerte Lagerhaus","text":"Zwei Häuser haben geantwortet.","entscheid":null,
        "links":[{"titel":"Offerte","url":"https://example.org/o"}],"reihenfolge":1,"aufgabeId":null,"status":"offen",
        "verantwortliche":[{"id":"p1","name":"Beispiel Person","ceviName":"Spatz"}]}"""

    @Test
    fun meetingListKeepsTheTeamViewWithoutKindAndCounts() {
        server.enqueue(
            json(
                200,
                """{"hoecks":[{"id":"$meetingId","version":2,"art":"lagerleitung","datum":"2027-03-17","zeit":"19:30","titel":"Höck März","ort":"Gruppenraum","gaeste":null,
                    "protokollId":null,"anzahlTraktanden":5,"anzahlAnwesende":3},
                   {"id":"$itemId","version":1,"datum":"2027-03-24","zeit":null,"titel":"Team-Höck","ort":null}],"naechster":null}""",
            ),
        )
        val all = (runBlocking { api.meetings(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/hoecks?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(MeetingKind.LEADERSHIP, all[0].kind)
        assertEquals(5, all[0].agendaCount)
        assertEquals(MeetingKind.TEAM, all[1].kind)
        assertNull(all[1].agendaCount)
    }

    @Test
    fun meetingReadsAgendaWithResponsibleAndLinks() {
        server.enqueue(
            json(
                200,
                """{"id":"$meetingId","version":2,"art":"lagerleitung","datum":"2027-03-17","zeit":"19:30","titel":"Höck März","ort":null,"gaeste":null,"protokollId":null,
                   "anwesende":[{"id":"p1","name":"Beispiel Person","ceviName":"Spatz"}],"traktanden":[$item]}""",
            ),
        )
        val meeting = (runBlocking { api.meeting(camp, meetingId) } as ApiResult.Ok).value
        val agenda = meeting.agenda.single()
        assertEquals("Offerte Lagerhaus", agenda.title)
        assertEquals("Beispiel Person (Spatz)", agenda.responsible.single().display)
        assertEquals("https://example.org/o", agenda.links.single().url)
        assertEquals(AgendaStatus.OPEN, agenda.status)
        assertEquals(1, meeting.present.size)
    }

    @Test
    fun agendaItemReadsTheDurationAndClearsItWithNull() {
        server.enqueue(json(200, item.replace("\"status\":\"offen\"", "\"status\":\"offen\",\"dauerMinuten\":15")))
        val withDuration = (runBlocking { api.changeItem(camp, itemId, buildJsonObject { put("version", 4); put("dauerMinuten", 15) }) } as ApiResult.Ok).value
        assertEquals(15, withDuration.minutes)
        assertEquals("""{"version":4,"dauerMinuten":15}""", server.takeRequest().body!!.utf8())
        server.enqueue(json(200, item.replace("\"status\":\"offen\"", "\"status\":\"offen\",\"dauerMinuten\":null")))
        val cleared = (runBlocking { api.changeItem(camp, itemId, buildJsonObject { put("version", 5); put("dauerMinuten", kotlinx.serialization.json.JsonNull) }) } as ApiResult.Ok).value
        assertNull(cleared.minutes)
        assertEquals("""{"version":5,"dauerMinuten":null}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun changeItemMovesToNextMeetingWithNullAndVersion() {
        server.enqueue(json(200, item.replace("\"hoeckId\":\"$meetingId\"", "\"hoeckId\":null")))
        val body = buildJsonObject { put("version", 4); put("hoeckId", kotlinx.serialization.json.JsonNull) }
        val changed = (runBlocking { api.changeItem(camp, itemId, body) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PATCH /api/v1/lager/$camp/traktanden/$itemId HTTP/1.1", request.requestLine)
        assertEquals("""{"version":4,"hoeckId":null}""", request.body!!.utf8())
        assertNull(changed.meetingId)
    }

    @Test
    fun versionConflictCarriesTheCurrentStateAndTheDifferingFields() {
        server.enqueue(
            json(409, """{"code":"VERSION_KONFLIKT","message":"x","aktuell":{"id":"$itemId","version":5,"titel":"Offerte neu"},"abweichend":["titel"]}"""),
        )
        val failure = runBlocking { api.changeItem(camp, itemId, buildJsonObject { put("version", 4); put("titel", "Meine Offerte") }) } as ApiResult.Failure
        assertEquals(ApiError.VERSION_CONFLICT, failure.error)
        val conflict = failure.conflict
        assertNotNull(conflict)
        assertEquals(listOf("titel"), conflict!!.differing)
        assertEquals(5L, conflictVersion(conflict))
        assertEquals("Offerte neu", (conflict.current["titel"] as JsonPrimitive).content)
    }

    @Test
    fun otherNinesAreNotConflicts() {
        server.enqueue(json(409, """{"code":"LAGER_GESCHLOSSEN","message":"x"}"""))
        val failure = runBlocking { api.changeItem(camp, itemId, buildJsonObject { put("version", 4) }) } as ApiResult.Failure
        assertEquals(ApiError.LAGER_CLOSED, failure.error)
        assertNull(failure.conflict)
    }

    @Test
    fun deleteMeetingWithAgendaKeepsTheCode() {
        server.enqueue(json(409, """{"code":"HOECK_HAT_TRAKTANDEN","message":"x"}"""))
        val failure = runBlocking { api.deleteMeeting(camp, Meeting(meetingId, 2, date = "2027-03-17", title = "Höck")) } as ApiResult.Failure
        assertEquals("DELETE /api/v1/lager/$camp/hoecks/$meetingId?version=2 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("HOECK_HAT_TRAKTANDEN", failure.code)
        assertEquals(ApiError.UNKNOWN, domainError(failure.error))
    }

    @Test
    fun presenceUsesPutAndDelete() {
        server.enqueue(json(204, ""))
        server.enqueue(json(204, ""))
        assertTrue(runBlocking { api.setPresent(camp, meetingId, "p1", true) } is ApiResult.Ok)
        assertTrue(runBlocking { api.setPresent(camp, meetingId, "p1", false) } is ApiResult.Ok)
        assertEquals("PUT /api/v1/lager/$camp/hoecks/$meetingId/anwesende/p1 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("DELETE /api/v1/lager/$camp/hoecks/$meetingId/anwesende/p1 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun modeReadsMembersAndRessortRound() {
        server.enqueue(
            json(
                200,
                """{"hoeck":{"id":"$meetingId","version":2,"art":"lagerleitung","datum":"2027-03-17","zeit":null,"titel":"Höck","ort":null,"gaeste":null,"protokollId":null,"traktanden":[$item]},
                   "mitglieder":[{"id":"p1","name":"Beispiel Person","ceviName":"Spatz","rolle":"leitung","anwesend":true},{"id":"p2","name":null,"ceviName":"Fuchs","rolle":"team","anwesend":false}],
                   "ressortRunde":[{"id":"r1","kuerzel":"LO","name":"Logistik","leitung":null,"aufgaben":{"gesamt":4,"erledigt":1,"offen":3,"ueberfaellig":2}}]}""",
            ),
        )
        val mode = (runBlocking { api.mode(camp, meetingId) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/hoecks/$meetingId/modus HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(1, MeetingRules.presentCount(mode.members))
        assertEquals("Fuchs", mode.members[1].display)
        assertEquals(2, mode.ressortRound!!.single().tasks!!.overdue)
    }

    @Test
    fun takeOverSendsOnlyTheChosenIds() {
        server.enqueue(json(200, """{"uebernommen":2}"""))
        val taken = (runBlocking { api.takeOver(camp, meetingId, listOf(itemId)) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/hoecks/$meetingId/traktanden/uebernehmen HTTP/1.1", request.requestLine)
        assertEquals("""{"traktandumIds":["$itemId"]}""", request.body!!.utf8())
        assertEquals(2, taken.count)
    }

    @Test
    fun reorderSendsTheIdsWithTheirVersions() {
        server.enqueue(json(204, ""))
        val items = listOf(
            AgendaItem(itemId, 4, meetingId, "A"),
            AgendaItem("33333333-3333-4333-8333-333333333333", 2, meetingId, "B"),
        )
        assertTrue(runBlocking { api.reorderItems(camp, meetingId, items) } is ApiResult.Ok)
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/traktanden/reihenfolge HTTP/1.1", request.requestLine)
        assertEquals(
            """{"hoeckId":"$meetingId","eintraege":[{"id":"$itemId","version":4},{"id":"33333333-3333-4333-8333-333333333333","version":2}]}""",
            request.body!!.utf8(),
        )
    }

    @Test
    fun calendarFileIsFetchedFromTheServer() {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/calendar; charset=utf-8").body("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n").build())
        val bytes = (runBlocking { api.calendarFile(camp, meetingId) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/hoecks/$meetingId.ics HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(String(bytes).startsWith("BEGIN:VCALENDAR"))
    }

    @Test
    fun minutesAreLinksToFilesAndDeletedFilesAreNull() {
        server.enqueue(
            json(
                200,
                """{"protokolle":[{"id":"$itemId","version":1,"art":"sitzung","titel":"Protokoll März","datum":"2027-03-17","hoeckId":"$meetingId","dateiId":"d1",
                    "datei":{"id":"d1","version":1,"anzeigename":"Protokoll.docx","link":"https://example.org/p","typ":null,"groesse":null,"hinzugefuegtVon":null,"hinzugefuegtAm":"2027-03-17T20:00:00.000Z"},
                    "erstelltAm":"2027-03-17T20:00:00.000Z","erstelltVon":null},
                   {"id":"x","version":1,"art":"notiz","titel":"Notiz","datum":"2027-03-18","hoeckId":null,"dateiId":"d2","datei":null,"erstelltAm":"2027-03-18T08:00:00.000Z","erstelltVon":null}],"naechster":null}""",
            ),
        )
        val all = (runBlocking { api.minutes(camp) } as ApiResult.Ok).value
        assertEquals("https://example.org/p", all[0].file!!.link)
        assertEquals(MinutesKind.NOTE, all[1].kind)
        assertNull(all[1].file)
    }

    @Test
    fun mentionsReadTitleAndTask() {
        server.enqueue(
            json(
                200,
                """{"erwaehnungen":[{"id":"m1","quelleTyp":"traktandum","quelleId":"$itemId","titel":"Offerte @{person:p1}","erledigt":false,"aufgabeId":null,"erstelltAm":"2027-03-17T20:00:00.000Z"}],"naechster":null}""",
            ),
        )
        val all = (runBlocking { api.mentions(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/erwaehnungen?limit=100 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("traktandum", all.single().sourceType)
    }

    @Test
    fun mentionToTaskAnswersWithTheTaskId() {
        server.enqueue(json(201, """{"aufgabeId":"$itemId"}"""))
        val task = (runBlocking { api.mentionToTask(camp, "m1") } as ApiResult.Ok).value
        assertEquals("POST /api/v1/lager/$camp/erwaehnungen/m1/als-aufgabe HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(itemId, task.taskId)
    }
}
