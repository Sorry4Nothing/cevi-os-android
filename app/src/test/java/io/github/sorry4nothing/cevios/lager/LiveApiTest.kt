package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

/** Anwesenheit, Tagesseiten, Einkauf, Packliste, Notfall, Infos und To-dos gegen einen Fake-Server: Pfade, Felder, Konflikt. */
class LiveApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: LiveApi
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
        api = LiveApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun bodyOf(request: mockwebserver3.RecordedRequest): JsonObject = kotlinx.serialization.json.Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    // ---- Anwesenheit ----

    @Test
    fun ownAttendanceReadsEntriesRequestsAndDeadline() {
        server.enqueue(
            json(
                200,
                """{"eintraege":[{"id":"$a","version":2,"personId":"$b","teilnehmerId":null,"lagertagId":"$a","tagesabschnittId":"$b","wert":"A"}],
                    "anfragen":[{"id":"$b","version":1,"personId":"$b","lagertagId":"$a","tagesabschnittId":"$b","wert":"F","notiz":null,"status":"offen","entschiedenVon":null,"entschiedenAm":null,"erstelltAm":"2027-07-01T10:00:00Z"}],
                    "frist":"2027-07-17T21:59:00.000Z"}""",
            ),
        )
        val own = (runBlocking { api.ownAttendance(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/anwesenheit/eigene HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(Attendance.PRESENT, own.entries.single().value)
        assertEquals(RequestStatus.OPEN, own.requests.single().status)
        assertEquals(Attendance.ABSENT, own.requests.single().value)
        assertEquals("2027-07-17T21:59:00.000Z", own.deadline)
    }

    @Test
    fun settingOwnAttendanceSendsEveryFieldWithItsValue() {
        server.enqueue(json(200, """{"eintraege":[]}"""))
        runBlocking { api.setOwnAttendance(camp, listOf(AttendanceField(a, b, Attendance.PRESENT), AttendanceField(a, a, Attendance.ABSENT))) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/anwesenheit/eigene HTTP/1.1", request.requestLine)
        val fields = bodyOf(request)["eintraege"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("A", "F"), fields.map { it["wert"]!!.jsonPrimitive.content })
        assertEquals(setOf("lagertagId", "tagesabschnittId", "wert"), fields[0].keys)
    }

    @Test
    fun lockedFieldCarriesTheCode() {
        server.enqueue(json(409, """{"code":"ANFRAGE_NOETIG","message":"x","gesperrt":[{"lagertagId":"$a","tagesabschnittId":"$b","grund":"frist"}]}"""))
        val failure = runBlocking { api.setOwnAttendance(camp, listOf(AttendanceField(a, b, Attendance.ABSENT))) } as ApiResult.Failure
        assertEquals("ANFRAGE_NOETIG", failure.code)
    }

    @Test
    fun requestSendsTheFieldsAndOnlyANonBlankNote() {
        server.enqueue(json(201, """{"anfragen":[]}"""))
        runBlocking { api.requestAttendance(camp, listOf(AttendanceField(a, b, Attendance.OPEN)), "  ") }
        assertEquals(setOf("eintraege"), bodyOf(server.takeRequest()).keys)
        server.enqueue(json(201, """{"anfragen":[]}"""))
        runBlocking { api.requestAttendance(camp, listOf(AttendanceField(a, b, Attendance.OPEN)), " Krank ") }
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/anwesenheit/anfragen HTTP/1.1", request.requestLine)
        assertEquals("Krank", bodyOf(request)["notiz"]!!.jsonPrimitive.content)
    }

    @Test
    fun openRequestsReadAllPagesOfTheOpenOnes() {
        server.enqueue(json(200, """{"anfragen":[{"id":"$a","version":1,"personId":"$b","lagertagId":"$a","tagesabschnittId":"$b","wert":"A"}],"naechster":"$b"}"""))
        server.enqueue(json(200, """{"anfragen":[{"id":"$b","version":3,"personId":"$a","lagertagId":"$a","tagesabschnittId":"$b","wert":"F"}],"naechster":null}"""))
        val all = (runBlocking { api.openRequests(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/anwesenheit/anfragen?status=offen&limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/anwesenheit/anfragen?status=offen&limit=200&nach=$b HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(2, all.size)
    }

    @Test
    fun decidingSendsTheDecisionAndEveryVersion() {
        server.enqueue(json(200, """{"anfragen":[]}"""))
        val request = AttendanceRequest(a, 4, b, a, b, "A")
        runBlocking { api.decideRequests(camp, accept = false, requests = listOf(request)) }
        val sent = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/anwesenheit/anfragen/entscheid HTTP/1.1", sent.requestLine)
        assertEquals("abgelehnt", bodyOf(sent)["entscheid"]!!.jsonPrimitive.content)
        val entry = bodyOf(sent)["anfragen"]!!.jsonArray.single().jsonObject
        assertEquals(a, entry["id"]!!.jsonPrimitive.content)
        assertEquals("4", entry["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun deadlineIsSetAndCleared() {
        server.enqueue(json(200, """{"frist":"2027-07-17T21:59:00.000Z"}"""))
        runBlocking { api.setDeadline(camp, "2027-07-17T21:59:00Z") }
        assertEquals("2027-07-17T21:59:00Z", bodyOf(server.takeRequest())["frist"]!!.jsonPrimitive.content)
        server.enqueue(json(200, """{"frist":null}"""))
        val cleared = (runBlocking { api.setDeadline(camp, null) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/anwesenheit/frist HTTP/1.1", request.requestLine)
        assertEquals(JsonNull, bodyOf(request)["frist"])
        assertNull(cleared.deadline)
    }

    @Test
    fun summaryCountsPerSectionAndValue() {
        server.enqueue(json(200, """{"zeilen":[{"lagertagId":"$a","tagesabschnittId":"$b","wert":"A","leitende":2,"teilnehmende":10}]}"""))
        val summary = (runBlocking { api.attendanceSummary(camp) } as ApiResult.Ok).value
        assertEquals(12, summary.rows.single().total)
    }

    // ---- Tagesseiten und Heute ----

    @Test
    fun dayPageReadsLeadBlocksWeatherAndTasks() {
        server.enqueue(
            json(
                200,
                """{"lagertag":{"id":"$a","datum":"2027-07-19","label":null},
                    "seite":{"id":"$b","version":2,"lagertagId":"$a","tageschefPersonId":"$b","notiz":"Sonnencreme","tageschef":{"id":"$b","name":"Mara","ceviName":"Luchs"}},
                    "bloecke":[{"id":"$a","version":1,"seiteId":"$b","art":"wichtig","text":"Abfahrt 13:30","dateiId":null,"reihenfolge":0},
                               {"id":"$b","version":1,"seiteId":"$b","art":"programmbild","text":"Foto","dateiId":"$a","reihenfolge":1,"datei":{"id":"$a","version":1,"anzeigename":"foto.jpg","link":"https://example.org/f","typ":"image/jpeg","groesse":10,"hinzugefuegtVon":null,"hinzugefuegtAm":"2027-07-01T10:00:00Z"}}],
                    "wetter":{"datum":"2027-07-19","code":95,"tmax":24.4,"tmin":12.0,"regen":3.2,"boeen":40.0,"zustand":"Gewitter","warnungen":["gewitter"]},
                    "wetterStand":"2027-07-19T05:00:00Z","aufgaben":[]}""",
            ),
        )
        val page = (runBlocking { api.day(camp, a) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/tagesseiten/$a HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Mara (Luchs)", page.page!!.tageschef!!.display)
        assertEquals(BlockKind.IMPORTANT, page.blocks[0].kind)
        assertEquals("https://example.org/f", page.blocks[1].datei!!.link)
        assertEquals(listOf("gewitter"), page.weather!!.warnings)
        assertEquals(24.4, page.weather!!.high!!, 0.001)
    }

    @Test
    fun blockChangeAndDeleteNameTheVersion() {
        server.enqueue(json(200, """{"id":"$a","version":3,"seiteId":"$b","art":"idee","text":"neu","dateiId":null,"reihenfolge":0}"""))
        runBlocking { api.changeBlock(camp, a, b, buildJsonObject { put("version", 2); put("text", "neu") }) }
        assertEquals("PATCH /api/v1/lager/$camp/tagesseiten/$a/bloecke/$b HTTP/1.1", server.takeRequest().requestLine)
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.deleteBlock(camp, a, DayBlock(b, 3, "idee", "neu")) }
        assertEquals("DELETE /api/v1/lager/$camp/tagesseiten/$a/bloecke/$b?version=3 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun addingABlockSendsKindAndText() {
        server.enqueue(json(201, """{"id":"$a","version":1,"seiteId":"$b","art":"wichtig","text":"Hallo","dateiId":null,"reihenfolge":0}"""))
        runBlocking { api.addBlock(camp, a, BlockKind.IMPORTANT, " Hallo ") }
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/tagesseiten/$a/bloecke HTTP/1.1", request.requestLine)
        assertEquals("wichtig", bodyOf(request)["art"]!!.jsonPrimitive.content)
        assertEquals("Hallo", bodyOf(request)["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun todayReadsOnlyWhatTheRoleSees() {
        server.enqueue(json(200, """{"datum":"2027-07-19","lagertag":{"id":"$a","datum":"2027-07-19","label":null},"tagesseite":null,"wetter":null,"anwesenheit":null,"schichten":{"dienste":[{"id":"$a","version":1,"name":"Küche","beschreibung":null,"mindestbesetzung":2,"nacht":false,"tagesabschnittId":"$b","reihenfolge":0}],"schichten":[]},"hoecks":null}"""))
        val today = (runBlocking { api.today(camp) } as ApiResult.Ok).value
        assertNull(today.day)
        assertNull(today.weather)
        assertEquals("Küche", today.shifts!!.duties.single().name)
    }

    @Test
    fun myWeekReadsTheOwnShifts() {
        server.enqueue(json(200, """{"von":"2027-07-19","bis":"2027-07-25","aufgaben":null,"schichten":[{"id":"$a","version":1,"dienstId":"$b","lagertagId":"$a","datum":"2027-07-20","tagesabschnittId":"$b","personId":"$a","teilnehmerId":null,"name":"Lea","ceviName":null,"stufe":null,"dienst":{"id":"$b","name":"Küche","nacht":false}}],"hoecks":null}"""))
        val week = (runBlocking { api.myWeek(camp) } as ApiResult.Ok).value
        assertEquals("Küche", week.shifts!!.single().duty!!.name)
    }

    // ---- Einkauf ----

    @Test
    fun shoppingTotalsKeepTheEntriesBehindALine() {
        server.enqueue(json(200, """{"artikel":[{"name":"Reis","menge":12.0,"einheit":"kg","warengruppe":"vorrat","einkaeuferPersonId":null,"einkaeufer":null,"erledigt":false,"anzahl":3,"eintraege":[{"id":"$a","version":1},{"id":"$b","version":2}]}],"naechster":null}"""))
        val items = (runBlocking { api.shoppingTotals(camp, mine = true) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/einkauf/zusammen?limit=200&einkaeufer=ich HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(3, items.single().count)
        assertEquals(ShopGroup.PANTRY, items.single().group)
        assertEquals(2, items.single().entries.size)
    }

    @Test
    fun suggestionsEncodeTheTypedPrefix() {
        server.enqueue(json(200, """{"vorschlaege":[{"name":"Käse","haeufigkeit":4}]}"""))
        val result = (runBlocking { api.suggestions(camp, " Kä se") } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/einkauf/vorschlaege?limit=8&q=K%C3%A4+se HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Käse", result.suggestions.single().name)
    }

    @Test
    fun tickingAnArticleNamesTheVersion() {
        server.enqueue(json(200, """{"id":"$a","version":4,"name":"Reis","menge":null,"einheit":null,"warengruppe":"vorrat","einkaeuferPersonId":null,"einkaeufer":null,"erledigt":true}"""))
        val body = buildJsonObject { put("version", 3); put("erledigt", true) }
        val item = (runBlocking { api.changeShopItem(camp, a, body) } as ApiResult.Ok).value
        assertEquals("PATCH /api/v1/lager/$camp/einkauf/$a HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(item.done)
    }

    @Test
    fun shopVersionConflictCarriesTheCurrentState() {
        server.enqueue(json(409, """{"code":"VERSION_KONFLIKT","message":"x","aktuell":{"id":"$a","version":5,"name":"Reis neu"},"abweichend":["name"]}"""))
        val failure = runBlocking { api.changeShopItem(camp, a, buildJsonObject { put("version", 4); put("name", "Meiner") }) } as ApiResult.Failure
        assertEquals(ApiError.VERSION_CONFLICT, failure.error)
        assertEquals(5L, conflictVersion(failure.conflict!!))
    }

    // ---- Packliste ----

    @Test
    fun packListReadsStatesAndOwnItems() {
        server.enqueue(
            json(
                200,
                """{"punkte":[{"id":"$a","version":1,"abschnitt":"Kleidung","text":"Regenjacke","nurLagerleitung":false,"reihenfolge":0,"zustand":"abgehakt"},
                              {"id":"$b","version":1,"abschnitt":"Sonstiges","text":"Kasse","nurLagerleitung":true,"reihenfolge":1,"zustand":"entfernt"}],
                    "eigene":[{"id":"$a","abschnitt":"Eigene","text":"Buch","abgehakt":false,"reihenfolge":0}]}""",
            ),
        )
        val list = (runBlocking { api.packList(camp) } as ApiResult.Ok).value
        assertEquals(PackState.CHECKED, list.items[0].state)
        assertEquals(PackState.REMOVED, list.items[1].state)
        assertTrue(list.items[1].leadershipOnly)
        assertFalse(list.own.single().checked)
    }

    @Test
    fun packStateIsSetWithoutAVersion() {
        server.enqueue(json(200, """{"id":"$a","zustand":"entfernt"}"""))
        runBlocking { api.setPackState(camp, a, PackState.REMOVED) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/packliste/punkte/$a/zustand HTTP/1.1", request.requestLine)
        assertEquals("entfernt", bodyOf(request)["zustand"]!!.jsonPrimitive.content)
        assertEquals(setOf("zustand"), bodyOf(request).keys)
    }

    @Test
    fun ownPackItemsAreAddedChangedAndDeleted() {
        server.enqueue(json(201, """{"id":"$a","abschnitt":"Eigene","text":"Buch","abgehakt":false,"reihenfolge":0}"""))
        runBlocking { api.addOwnPackItem(camp, " Eigene ", " Buch ") }
        val add = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/packliste/eigene HTTP/1.1", add.requestLine)
        assertEquals("Buch", bodyOf(add)["text"]!!.jsonPrimitive.content)
        server.enqueue(json(200, """{"id":"$a","abschnitt":"Eigene","text":"Buch","abgehakt":true,"reihenfolge":0}"""))
        runBlocking { api.changeOwnPackItem(camp, a, buildJsonObject { put("abgehakt", true) }) }
        assertEquals("PATCH /api/v1/lager/$camp/packliste/eigene/$a HTTP/1.1", server.takeRequest().requestLine)
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.deleteOwnPackItem(camp, a) }
        assertEquals("DELETE /api/v1/lager/$camp/packliste/eigene/$a HTTP/1.1", server.takeRequest().requestLine)
    }

    // ---- Notfall und Infos ----

    @Test
    fun emergencyReadsAddressContactsAndTheEmergencyInfos() {
        server.enqueue(
            json(
                200,
                """{"adresse":{"ort":"Beispielberg","adresse":"Beispielweg 1","kartenLink":"https://maps.example.org/x"},
                    "kontakte":[{"quelle":"eintrag","id":"$a","version":1,"funktion":"Erste Hilfe","personId":null,"name":"Sina","ceviName":"Eule","telefon":"+41 00 000 00 00","reihenfolge":0},
                                {"quelle":"ressort","id":null,"version":null,"funktion":"Sicherheit","personId":"$b","name":"Jonas","ceviName":null,"telefon":null,"reihenfolge":null}],
                    "infos":[{"id":"$a","version":1,"titel":"Notfallplan","text":"Treffpunkt","links":[],"nurLagerleitung":false,"notfall":true,"reihenfolge":0,"dateien":[]}]}""",
            ),
        )
        val data = (runBlocking { api.emergency(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/notfall HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Sina (Eule)", data.contacts[0].display)
        assertEquals(1, LiveRules.callable(data.contacts).size)
        assertEquals("https://maps.example.org/x", data.address.kartenLink)
        assertTrue(data.infos.single().notfall)
    }

    @Test
    fun infosReadAllPagesAndReorderSendsEveryVersion() {
        server.enqueue(json(200, """{"abschnitte":[{"id":"$a","version":2,"titel":"Lagerhaus","text":null,"links":[{"titel":"Plan","url":"https://example.org/p"}],"nurLagerleitung":false,"notfall":false,"reihenfolge":0,"dateien":[]}],"naechster":null}"""))
        val infos = (runBlocking { api.infos(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/infos?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("https://example.org/p", infos.single().links.single().url)
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.reorderInfos(camp, listOf(infos.single(), infos.single().copy(id = b, version = 7))) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/infos/reihenfolge HTTP/1.1", request.requestLine)
        val entries = bodyOf(request)["eintraege"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(a, b), entries.map { it["id"]!!.jsonPrimitive.content })
        assertEquals("7", entries[1]["version"]!!.jsonPrimitive.content)
    }

    @Test
    fun infoDeleteNamesTheVersion() {
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.deleteInfo(camp, InfoSection(a, 6, "Regeln")) }
        assertEquals("DELETE /api/v1/lager/$camp/infos/$a?version=6 HTTP/1.1", server.takeRequest().requestLine)
    }

    // ---- To-dos ----

    @Test
    fun todosAreListedAddedChangedAndDeleted() {
        server.enqueue(json(200, """{"todos":[{"id":"$a","text":"Znüni vorbereiten","erledigt":true,"reihenfolge":0}]}"""))
        val list = (runBlocking { api.todos(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/todos HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(list.todos.single().done)
        server.enqueue(json(201, """{"id":"$b","text":"Brot","erledigt":false,"reihenfolge":1}"""))
        runBlocking { api.addTodo(camp, " Brot ") }
        assertEquals("Brot", bodyOf(server.takeRequest())["text"]!!.jsonPrimitive.content)
        server.enqueue(json(200, """{"id":"$b","text":"Brot","erledigt":true,"reihenfolge":1}"""))
        runBlocking { api.changeTodo(camp, b, buildJsonObject { put("erledigt", true) }) }
        assertEquals("PATCH /api/v1/lager/$camp/todos/$b HTTP/1.1", server.takeRequest().requestLine)
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.deleteTodo(camp, b) }
        assertEquals("DELETE /api/v1/lager/$camp/todos/$b HTTP/1.1", server.takeRequest().requestLine)
    }
}
