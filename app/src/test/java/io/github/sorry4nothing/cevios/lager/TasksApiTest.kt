package io.github.sorry4nothing.cevios.lager

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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Aufgaben, Ressorts und Start gegen einen Fake-Server: Pfade, Felder, Seitenwechsel, Fehlercodes. */
class TasksApiTest {
    // Ein hängender Test soll mit Namen scheitern, nicht die CI stundenlang blockieren.
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: TasksApi
    private val camp = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val taskId = "11111111-1111-4111-8111-111111111111"
    private val taskId2 = "22222222-2222-4222-8222-222222222222"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = TasksApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun task(id: String, title: String) = """
        {"id":"$id","version":3,"titel":"$title","ressortId":null,"teil":null,"verantwortlichPersonId":null,"status":"offen",
         "notiz":null,"frist":"2027-03-15","fristMeilensteinId":null,"fristVersatzTage":null,"stern":false,"laufend":false,
         "spaeterAb":null,"wiederholung":null,"reihenfolge":0,"quelle":"manuell","erledigtAm":null,
         "verantwortlich":{"id":"p1","name":"Beispiel Person","ceviName":"Spatz","automatisch":true},
         "ressort":{"id":"r1","kuerzel":"LO","name":"Logistik"},"unterstuetzung":[]}
    """.trimIndent()

    @Test
    fun listAllFollowsThePages() {
        server.enqueue(json(200, """{"aufgaben":[${task(taskId, "Offerte einholen")}],"naechster":"$taskId"}"""))
        server.enqueue(json(200, """{"aufgaben":[${task(taskId2, "Zelte prüfen")}],"naechster":null}"""))
        val all = (runBlocking { api.listAll(camp) } as ApiResult.Ok).value
        assertEquals(listOf("Offerte einholen", "Zelte prüfen"), all.map { it.title })
        assertEquals("GET /api/v1/lager/$camp/aufgaben?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/aufgaben?limit=200&nach=$taskId HTTP/1.1", server.takeRequest().requestLine)
        val first = all.first()
        assertEquals("Spatz", first.assignee?.ceviName)
        assertTrue(first.assignee!!.automatic)
        assertEquals("LO", first.ressort?.abbreviation)
        assertEquals(TaskStatus.OPEN, first.status)
    }

    @Test
    fun unknownStatusCountsAsOpen() {
        server.enqueue(json(200, """{"aufgaben":[${task(taskId, "X").replace("\"offen\"", "\"neu_vom_server\"")}],"naechster":null}"""))
        val all = (runBlocking { api.listAll(camp) } as ApiResult.Ok).value
        assertEquals(TaskStatus.OPEN, all.single().status)
    }

    @Test
    fun listFailureKeepsTheError() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"x"}"""))
        assertTrue(runBlocking { api.listAll(camp) } is ApiResult.Failure)
    }

    @Test
    fun detailsReadChecklistLinksAndKeywords() {
        server.enqueue(
            json(
                200,
                """{"aufgabeId":"$taskId","checkliste":[{"id":"c1","version":1,"text":"Anrufen","erledigt":true,"reihenfolge":0,"erledigtVon":{"id":"p1","name":"Beispiel Person"}}],
                   "links":[{"id":"l1","version":1,"titel":"Offerte","url":"https://example.org/offerte"}],"stichworte":["Zelt"]}""",
            ),
        )
        val details = (runBlocking { api.details(camp, taskId) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/aufgaben/$taskId/details HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(details.checklist.single().done)
        assertEquals("https://example.org/offerte", details.links.single().url)
        assertEquals(listOf("Zelt"), details.keywords)
    }

    @Test
    fun historyHasAuthorsAndEntries() {
        server.enqueue(
            json(
                200,
                """{"erstelltAm":"2027-01-02T10:00:00.000Z","erstelltVon":{"id":"p1","name":"Beispiel Person"},"geaendertAm":"2027-01-03T10:00:00.000Z",
                   "geaendertVon":null,"eintraege":[{"seq":7,"objekttyp":"aufgabe","id":"$taskId","art":"geaendert","version":3,"zeitpunkt":"2027-01-03T10:00:00.000Z"}]}""",
            ),
        )
        val history = (runBlocking { api.history(camp, taskId) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/aufgaben/$taskId/verlauf HTTP/1.1", server.takeRequest().requestLine)
        assertNull(history.changedBy)
        assertEquals("geaendert", history.entries.single().kind)
    }

    @Test
    fun teamIsToldTheDetailsDoNotExist() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"x"}"""))
        val failure = runBlocking { api.details(camp, taskId) } as ApiResult.Failure
        assertEquals(ApiError.NOT_FOUND, failure.error)
    }

    @Test
    fun duplicatesEncodeTheTitle() {
        server.enqueue(json(200, """{"gruppen":[{"ressortId":"r1","titel":"Zelte","aufgaben":[{"id":"$taskId","version":2,"titel":"Zelte","status":"laeuft"}]}]}"""))
        val groups = (runBlocking { api.duplicates(camp, "Zelte & Planen", "r1") } as ApiResult.Ok).value.groups
        assertEquals("GET /api/v1/lager/$camp/aufgaben/duplikate?titel=Zelte+%26+Planen&ressortId=r1 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(TaskStatus.RUNNING.wire, groups.single().tasks.single().statusWire)
    }

    @Test
    fun quickSendsTextAndReadsSuggestions() {
        server.enqueue(
            json(
                200,
                """{"vorschlaege":[{"zeile":"Zelte prüfen @Spatz bis Freitag #LO","titel":"Zelte prüfen",
                    "person":{"status":"gefunden","text":"Spatz","kandidaten":[{"id":"p1","name":"Beispiel Person","ceviName":"Spatz"}]},
                    "ressort":{"status":"mehrdeutig","text":"L","kandidaten":[{"id":"r1","kuerzel":"LO","name":"Logistik"},{"id":"r2","kuerzel":"LA","name":"Lager"}]},
                    "frist":{"datum":"2027-03-19","text":"Freitag"},"hinweise":[]}]}""",
            ),
        )
        val suggestions = (runBlocking { api.quick(camp, "Zelte prüfen @Spatz bis Freitag #LO") } as ApiResult.Ok).value.suggestions
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/aufgaben/schnell HTTP/1.1", request.requestLine)
        assertTrue(request.body!!.utf8().contains("\"text\""))
        val item = TasksViewModel.quickItem(suggestions.single())
        // Genau eine Person gefunden: sie gilt. Zwei Ressorts: Die Person muss wählen, nichts wird geraten.
        assertEquals("Spatz", (item.person as QuickChoice.Chosen).value.ceviName)
        assertTrue(item.ressort is QuickChoice.Unresolved)
        assertEquals("2027-03-19", item.due)
    }

    @Test
    fun startReadsCountdownProgressAndOwnTasks() {
        server.enqueue(
            json(
                200,
                """{"heute":"2027-03-01","countdown":{"von":"2027-07-17","bis":"2027-07-24","tageBisBeginn":138,"tageBisEnde":145},
                   "fortschritt":{"aufgaben":{"gesamt":10,"erledigt":4,"ueberfaellig":2},"meilensteine":{"gesamt":5,"vergangen":2,"naechster":{"id":"m1","version":1,"titel":"Anmeldeschluss","datum":"2027-04-01","art":"frist"}}},
                   "ueberfaellig":[${task(taskId, "A")}],"eigene":[${task(taskId2, "B")}]}""",
            ),
        )
        val start = (runBlocking { api.start(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/start HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(40, percentDone(start.progress.tasks))
        assertEquals("Anmeldeschluss", start.progress.milestones?.next?.title)
        assertEquals(1, start.own?.size)
    }

    @Test
    fun teamGetsNoStart() {
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        val failure = runBlocking { api.start(camp) } as ApiResult.Failure
        assertEquals(ApiError.FORBIDDEN, failure.error)
    }

    @Test
    fun weatherTodayMeetingsAndNews() {
        server.enqueue(json(200, """{"ort":null,"stand":"2027-03-01T08:00:00.000Z","tage":[{"datum":"2027-03-02","code":95,"tmax":12.5,"tmin":4,"regen":2,"boeen":40,"zustand":"gewitter","warnungen":["gewitter","boeen"]}]}"""))
        server.enqueue(json(200, """{"datum":"2027-03-02","lagertag":{"id":"t1","datum":"2027-03-02","label":"Tag 2"},"tagesseite":{"seite":{"tageschef":{"id":"p1","name":"Beispiel Person"}},"bloecke":[],"aufgaben":[]},"wetter":null,"anwesenheit":null,"schichten":{"dienste":[{"id":"d1","name":"Küche"}],"schichten":[]},"hoecks":[]}"""))
        server.enqueue(json(200, """{"hoecks":[{"id":"h1","version":1,"art":"hoeck","datum":"2027-03-05","zeit":"19:30","titel":"Höck","ort":null,"gaeste":null,"protokollId":null,"anzahlTraktanden":3,"anzahlAnwesende":0}],"naechster":null}"""))
        server.enqueue(json(200, """{"eintraege":[{"id":"n1","art":"aufgabe_zugeteilt","objektTyp":"aufgabe","objektId":"$taskId","gelesen":false,"erstelltAm":"2027-03-01T08:00:00.000Z"}],"naechster":null}"""))
        val weather = (runBlocking { api.weather(camp) } as ApiResult.Ok).value
        val today = (runBlocking { api.today(camp) } as ApiResult.Ok).value
        val meetings = (runBlocking { api.meetings(camp, "2027-03-01") } as ApiResult.Ok).value.meetings
        val news = (runBlocking { api.notifications(camp) } as ApiResult.Ok).value.entries
        assertEquals(listOf("gewitter", "boeen"), weather.days.single().warnings)
        assertEquals("Tag 2", today.lagertag?.label)
        assertEquals("Beispiel Person", today.dayPage?.page?.dayLead?.name)
        assertEquals(1, today.shifts?.duties?.size)
        assertEquals(3, meetings.single().agendaCount)
        assertEquals("aufgabe", news.single().objectType)
        assertEquals("GET /api/v1/lager/$camp/wetter HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/heute HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/hoecks?von=2027-03-01&limit=5 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/benachrichtigungen?limit=10 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun ressortsAreReadWithLeaders() {
        server.enqueue(
            json(
                200,
                """{"ressorts":[{"id":"r1","version":1,"kuerzel":"LO","name":"Logistik","leitungPersonId":"p1","stvPersonId":null,"notiz":"Tipp","parentId":null,"reihenfolge":0,
                    "leitung":{"id":"p1","name":"Beispiel Person","ceviName":"Spatz"},"stv":null,"ruhig":true}],"naechster":null}""",
            ),
        )
        val ressort = (runBlocking { api.ressorts(camp) } as ApiResult.Ok).value.single()
        assertTrue(ressort.quiet)
        assertEquals("GET /api/v1/lager/$camp/ressorts?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Beispiel Person (Spatz)", ressort.leader?.display)
        assertEquals("Tipp", ressort.tips)
    }

    @Test
    fun ressortWithoutQuietFieldIsNotQuiet() {
        server.enqueue(json(200, """{"id":"r1","version":1,"kuerzel":"LO","name":"Logistik","leitungPersonId":null,"stvPersonId":null,"notiz":null,"parentId":null,"reihenfolge":0}"""))
        assertFalse((runBlocking { api.ressort(camp, "r1") } as ApiResult.Ok).value.quiet)
    }

    @Test
    fun mergeSendsBothVersions() {
        server.enqueue(json(200, task(taskId, "Zelte")))
        runBlocking { api.merge(camp, taskId, MergeBody(3, taskId2, 5)) }
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/aufgaben/$taskId/zusammenfuehren HTTP/1.1", request.requestLine)
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"mitId\":\"$taskId2\"") && body.contains("\"mitVersion\":5") && body.contains("\"version\":3"))
    }

    @Test
    fun reorderUsesThePositionAndTheSeenVersion() {
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.reorder(camp, listOf(taskId2 to 5L, taskId to 3L)) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/aufgaben/reihenfolge HTTP/1.1", request.requestLine)
        assertTrue(request.body!!.utf8().contains("\"eintraege\":[{\"id\":\"$taskId2\",\"version\":5},{\"id\":\"$taskId\",\"version\":3}]"))
    }

    @Test
    fun checkpointTickSendsVersionAndDone() {
        server.enqueue(json(200, """{"id":"c1","version":2,"text":"Anrufen","erledigt":true,"reihenfolge":0}"""))
        val done = (runBlocking { api.setCheckpoint(camp, taskId, Checkpoint("c1", 1, "Anrufen"), true) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PATCH /api/v1/lager/$camp/aufgaben/$taskId/checkliste/c1 HTTP/1.1", request.requestLine)
        assertTrue(request.body!!.utf8().let { it.contains("\"version\":1") && it.contains("\"erledigt\":true") })
        assertEquals(2L, done.version)
    }
}
