package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Teilnehmende, Zimmer, Fahrten, Gruppen, Schichtplan, Diensttausch und Abzählliste gegen einen Fake-Server: Pfade, Felder, Konflikt. */
class PlanningApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: PlanningApi
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
        api = PlanningApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun bodyOf(request: mockwebserver3.RecordedRequest): JsonObject = kotlinx.serialization.json.Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    @Test
    fun membersReadsTheDisplayNamesForThePickers() {
        server.enqueue(json(200, """{"mitglieder":[{"personId":"$a","name":"Biber","rolle":"leitung","ressorts":["KÜ","PR"]},{"personId":"$b","name":"Ben M.","rolle":"team","ressorts":[]}]}"""))
        val list = (runBlocking { api.members(camp) } as ApiResult.Ok).value.members
        assertEquals("GET /api/v1/lager/$camp/mitglieder HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(listOf("Biber", "Ben M."), list.map { it.name })
        assertEquals(listOf("KÜ", "PR"), list.first().ressorts)
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        assertEquals(ApiError.FORBIDDEN, (runBlocking { api.members(camp) } as ApiResult.Failure).error)
    }

    @Test
    fun participantsReadsAllPagesAndKeepsTheCeviNameOnlyWhenPresent() {
        server.enqueue(json(200, """{"teilnehmer":[{"id":"$a","version":2,"vorname":"Anna","nachname":"Beispiel","stufe":"Wölfe","abgewaehlt":false,"ceviName":"Biber"}],"naechster":"$b"}"""))
        server.enqueue(json(200, """{"teilnehmer":[{"id":"$b","version":1,"vorname":"Ben","nachname":"Muster","stufe":null,"abgewaehlt":true}],"naechster":null}"""))
        val all = (runBlocking { api.participants(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/teilnehmer?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/teilnehmer?limit=200&nach=$b HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Biber", all[0].ceviName)
        assertNull(all[1].ceviName)
        assertTrue(all[1].excluded)
        assertEquals("Anna Beispiel", all[0].fullName)
    }

    @Test
    fun importSendsTheTextAndWhetherToApply() {
        server.enqueue(json(200, """{"vorschau":true,"angelegt":0,"neu":[{"zeile":1,"vorname":"Anna","nachname":"Beispiel","stufe":null,"ceviName":null}],"fehler":[{"zeile":2,"code":"VORNAME_FEHLT","message":"x"}],"doppelt":[{"zeile":3,"grund":"vorhanden"}]}"""))
        val result = (runBlocking { api.importParticipants(camp, "Anna;Beispiel", apply = false) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/teilnehmer/import HTTP/1.1", request.requestLine)
        assertEquals("Anna;Beispiel", bodyOf(request)["text"]!!.jsonPrimitive.content)
        assertFalse(bodyOf(request)["uebernehmen"]!!.jsonPrimitive.boolean)
        assertTrue(result.preview)
        assertEquals("VORNAME_FEHLT", result.failures.single().code)
        assertEquals("vorhanden", result.duplicates.single().reason)
    }

    @Test
    fun excludingSendsTheIds() {
        server.enqueue(json(200, """{"geaendert":1}"""))
        runBlocking { api.setExcluded(camp, listOf(a), true) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/gruppen/abgewaehlt HTTP/1.1", request.requestLine)
        assertTrue(bodyOf(request)["abgewaehlt"]!!.jsonPrimitive.boolean)
        assertEquals(a, bodyOf(request)["teilnehmerIds"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun roomsFullViewAndTeamViewDifferByTheSeats() {
        server.enqueue(
            json(
                200,
                """{"zimmer":[{"id":"$a","version":3,"name":"Eiger","art":"zimmer","plaetze":6,"notiz":null,"reihenfolge":0,"belegt":7,"ueberbelegt":true,
                    "mitglieder":[{"typ":"teilnehmer","id":"$b","name":"Anna"}]}],"offen":{"teilnehmer":3,"team":1}}""",
            ),
        )
        val full = (runBlocking { api.rooms(camp) } as ApiResult.Ok).value
        assertTrue(full.rooms.single().full)
        assertTrue(full.rooms.single().overbooked)
        assertTrue(full.rooms.single().members.single().isParticipant)
        assertEquals(3, full.open!!.participants)

        server.enqueue(json(200, """{"zimmer":[{"id":"$a","version":3,"name":"Mönch","art":"zelt","mitglieder":[{"name":"Tim"}]}]}"""))
        val team = (runBlocking { api.rooms(camp) } as ApiResult.Ok).value
        assertFalse(team.rooms.single().full)
        assertEquals(RoomKind.TENT, team.rooms.single().kind)
        assertNull(team.open)
    }

    @Test
    fun assignToRoomSendsOnlyTheFilledLists() {
        server.enqueue(json(200, """{"eingeteilt":1,"belegt":2,"plaetze":6,"ueberbelegt":false}"""))
        runBlocking { api.assignToRoom(camp, a, listOf(b), emptyList()) }
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/zimmer/$a/zuordnung HTTP/1.1", request.requestLine)
        assertEquals(setOf("teilnehmerIds"), bodyOf(request).keys)
    }

    @Test
    fun alreadySeatedCarriesTheCode() {
        server.enqueue(json(409, """{"code":"SCHON_EINGETEILT","message":"x"}"""))
        val failure = runBlocking { api.assignToRoom(camp, a, listOf(b), emptyList()) } as ApiResult.Failure
        assertEquals("SCHON_EINGETEILT", failure.code)
    }

    @Test
    fun roomVersionConflictCarriesTheCurrentState() {
        server.enqueue(json(409, """{"code":"VERSION_KONFLIKT","message":"x","aktuell":{"id":"$a","version":5,"name":"Eiger neu"},"abweichend":["name"]}"""))
        val failure = runBlocking { api.changeRoom(camp, a, buildJsonObject { put("version", 4); put("name", "Meiner") }) } as ApiResult.Failure
        assertEquals(ApiError.VERSION_CONFLICT, failure.error)
        assertEquals(5L, conflictVersion(failure.conflict!!))
        assertEquals(listOf("name"), failure.conflict!!.differing)
    }

    @Test
    fun deleteRoomSendsTheVersionAsQuery() {
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking { api.deleteRoom(camp, Room(a, 7, "Eiger", "zimmer")) }
        assertEquals("DELETE /api/v1/lager/$camp/zimmer/$a?version=7 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun tripsReadDriverPassengersAndFreeSeats() {
        server.enqueue(
            json(
                200,
                """{"fahrten":[{"id":"$a","version":1,"richtung":"hin","art":"auto","name":null,"fahrerPersonId":"$b","plaetze":5,"datum":"2027-07-17","zeit":"07:30","von":null,"nach":null,
                    "treffpunkt":"Parkplatz","link":null,"zielBezeichnung":null,"zielAdresse":null,"zielLat":null,"zielLon":null,"zielLink":null,
                    "fahrer":{"personId":"$b","name":"Jonas"},"mitfahrer":[{"id":"$a","version":2,"personId":null,"teilnehmerId":"$b","name":"Anna","stufe":"Wölfe"}],
                    "belegt":1,"frei":4,"ueberbelegt":false}],"naechster":null}""",
            ),
        )
        val trip = (runBlocking { api.trips(camp) } as ApiResult.Ok).value.single()
        assertEquals("GET /api/v1/lager/$camp/fahrten?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertTrue(trip.isCar)
        assertEquals(TripDirection.THERE, trip.direction)
        assertEquals("Jonas", trip.driver!!.name)
        assertEquals(4, trip.free)
        assertEquals("Anna", trip.passengers.single().name)
    }

    @Test
    fun withoutTripAsksForTheDirectionAndAddPassengerNamesOneKind() {
        server.enqueue(json(200, """{"personen":[{"personId":"$a","teilnehmerId":null,"name":"Lea","stufe":null}],"gesamt":1,"naechster":null}"""))
        val people = (runBlocking { api.withoutTrip(camp, TripDirection.BACK) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/fahrten/ohne?richtung=rueck&limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("p:$a", people.single().key)

        server.enqueue(json(201, """{"id":"$a","version":1}"""))
        runBlocking { api.addPassenger(camp, b, people.single()) }
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/fahrten/$b/mitfahrer HTTP/1.1", request.requestLine)
        assertEquals(setOf("personId"), bodyOf(request).keys)
    }

    @Test
    fun groupsReadLeadersAndChildren() {
        server.enqueue(
            json(
                200,
                """{"gruppen":[{"id":"$a","version":1,"name":"Falken","reihenfolge":0,
                    "personen":[{"personId":"$b","name":"Lea","ceviName":"Biber","rolle":"leitung"}],
                    "teilnehmer":[{"id":"$b","vorname":"Anna","nachname":"Beispiel","stufe":"Wölfe"}]}],"ohneGruppe":3,"abgewaehlt":1}""",
            ),
        )
        val list = (runBlocking { api.groups(camp) } as ApiResult.Ok).value
        assertEquals(3, list.withoutGroup)
        assertEquals("leitung", list.groups.single().people.single().role)
        assertEquals(1, list.groups.single().participants.size)
    }

    @Test
    fun assignToGroupSendsNullAsNoGroupAndTheRoleOnlyWithPeople() {
        server.enqueue(json(200, """{"geaendert":1}"""))
        runBlocking { api.assignToGroup(camp, null, listOf(a), emptyList(), leader = false) }
        val first = bodyOf(server.takeRequest())
        assertTrue(first["gruppeId"] is kotlinx.serialization.json.JsonNull)
        assertFalse("rolle" in first)

        server.enqueue(json(200, """{"geaendert":1}"""))
        runBlocking { api.assignToGroup(camp, b, emptyList(), listOf(a), leader = true) }
        val second = bodyOf(server.takeRequest())
        assertEquals("leitung", second["rolle"]!!.jsonPrimitive.content)
        assertEquals(b, second["gruppeId"]!!.jsonPrimitive.content)
    }

    @Test
    fun mixSendsTheCountAndTheSeedForTheApply() {
        server.enqueue(json(200, """{"vorschau":true,"seed":4294967295,"gruppen":[{"nummer":1,"gruppeId":null,"name":"Gruppe 1","anzahl":1,"teilnehmer":[{"id":"$a","vorname":"Anna","nachname":"Beispiel","stufe":null}]}]}"""))
        val preview = (runBlocking { api.mixGroups(camp, 3, apply = false, seed = null) } as ApiResult.Ok).value
        val first = bodyOf(server.takeRequest())
        assertEquals(3, first["anzahl"]!!.jsonPrimitive.content.toInt())
        assertFalse("seed" in first)
        assertEquals(4294967295L, preview.seed)
        assertNull(preview.groups.single().gruppeId)

        server.enqueue(json(200, """{"vorschau":false,"seed":4294967295,"gruppen":[],"geaendert":5}"""))
        runBlocking { api.mixGroups(camp, 3, apply = true, seed = preview.seed) }
        val second = bodyOf(server.takeRequest())
        assertTrue(second["uebernehmen"]!!.jsonPrimitive.boolean)
        assertEquals("4294967295", second["seed"]!!.jsonPrimitive.content)
    }

    @Test
    fun shiftPlanReadsDutiesShiftsAndStatistics() {
        server.enqueue(
            json(
                200,
                """{"dienste":[{"id":"$a","version":1,"name":"Küche","beschreibung":null,"mindestbesetzung":2,"nacht":false,"tagesabschnittId":"$b","reihenfolge":0}],
                    "schichten":[{"id":"$b","version":1,"dienstId":"$a","lagertagId":"$a","datum":"2027-07-17","tagesabschnittId":"$b","personId":"$b","teilnehmerId":null,"name":"Jonas","ceviName":"Fuchs","stufe":null}],
                    "statistik":[{"personId":"$b","teilnehmerId":null,"name":"Jonas","anzahl":1,"nachtwachen":0}]}""",
            ),
        )
        val plan = (runBlocking { api.shiftPlan(camp) } as ApiResult.Ok).value
        assertEquals(2, plan.duties.single().minimum)
        assertEquals("Jonas", plan.shifts.single().name)
        assertEquals(1, plan.stats.single().count)
    }

    @Test
    fun scheduleReadsDaysSectionsAndMilestones() {
        server.enqueue(json(200, """{"meilensteine":[{"id":"$a","version":1,"titel":"Lagerhaus fest","datum":"2027-03-03","art":null}],"lagertage":[{"id":"$a","version":1,"datum":"2027-07-17","label":null}],"tagesabschnitte":[{"id":"$b","version":1,"name":"Vormittag","zeit":"08:00","reihenfolge":1}]}"""))
        val schedule = (runBlocking { api.schedule(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/zeitplan HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("2027-07-17", schedule.days.single().date)
        assertEquals("Vormittag", schedule.sections.single().name)
        assertEquals("Lagerhaus fest", schedule.milestones.single().title)
    }

    @Test
    fun staffDutySendsBothListsEvenWhenEmptyToClearTheDuty() {
        server.enqueue(json(200, """{"geaendert":2}"""))
        runBlocking { api.staffDuty(camp, a, b, emptyList(), emptyList()) }
        val request = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/schichtplan/schichten HTTP/1.1", request.requestLine)
        assertEquals(JsonArray(emptyList()), bodyOf(request)["personIds"])
        assertEquals(JsonArray(emptyList()), bodyOf(request)["teilnehmerIds"])
    }

    @Test
    fun suggestionPreviewHasNoSeedAndCarriesTheOptions() {
        server.enqueue(
            json(
                200,
                """{"vorschau":true,"seed":7,"gekuerzt":false,"schichten":[{"lagertagId":"$a","datum":"2027-07-17","dienstId":"$b","tagesabschnittId":"$a","personId":"$b","teilnehmerId":null,"name":"Tim"}],
                    "unbesetzt":[{"lagertagId":"$a","dienstId":"$b","fehlend":1}],"statistik":[{"personId":"$b","teilnehmerId":null,"name":"Tim","vorher":1,"neu":1,"gesamt":2,"nachtwachen":0}]}""",
            ),
        )
        val result = (runBlocking { api.suggestShifts(camp, apply = false, seed = null, withParticipants = true, onlyPresent = false) } as ApiResult.Ok).value
        val body = bodyOf(server.takeRequest())
        assertFalse("seed" in body)
        assertTrue(body["mitTeilnehmenden"]!!.jsonPrimitive.boolean)
        assertFalse(body["nurAnwesende"]!!.jsonPrimitive.boolean)
        assertEquals(1, result.unfilled.single().missing)
        assertEquals(2, result.stats.single().total)
    }

    private val swap = """{"id":"$a","version":3,"vonPersonId":"$b","schichtId":"$a","mitPersonId":null,"mitSchichtId":null,"notiz":null,"status":"offen","entschiedenVon":null,"entschiedenAm":null,"erstelltAm":"2027-07-16T19:40:00.000Z"}"""

    @Test
    fun requestSwapSendsOnlyTheGivenForm() {
        server.enqueue(json(201, swap))
        runBlocking { api.requestSwap(camp, a, null, b, " ") }
        val body = bodyOf(server.takeRequest())
        assertEquals(setOf("schichtId", "mitPersonId"), body.keys)
    }

    @Test
    fun decideAndWithdrawNameTheVersion() {
        server.enqueue(json(200, swap.replace("\"offen\"", "\"angenommen\"")))
        val decided = (runBlocking { api.decideSwap(camp, Swap(a, 3, b, a, null, null, null, "offen", null, null, "x"), 3, accept = true, takeOverBy = b) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/diensttausch/$a/entscheid HTTP/1.1", request.requestLine)
        assertEquals("angenommen", bodyOf(request)["entscheid"]!!.jsonPrimitive.content)
        assertEquals(b, bodyOf(request)["mitPersonId"]!!.jsonPrimitive.content)
        assertEquals(SwapStatus.ACCEPTED, decided.status)

        server.enqueue(json(409, """{"code":"BEREITS_ENTSCHIEDEN","message":"x"}"""))
        val failure = runBlocking { api.withdrawSwap(camp, decided, 3) } as ApiResult.Failure
        assertEquals("BEREITS_ENTSCHIEDEN", failure.code)
        assertEquals("POST /api/v1/lager/$camp/diensttausch/$a/zurueckziehen HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun ownSwapsUnwrapTheList() {
        server.enqueue(json(200, """{"tausche":[$swap]}"""))
        val own = (runBlocking { api.ownSwaps(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/diensttausch/eigene HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(SwapStatus.OPEN, own.single().status)
    }

    @Test
    fun countReadsAllPagesWithTheFilterAndTheSums() {
        server.enqueue(json(200, """{"eintraege":[{"id":"$a","teilnehmerId":"$a","vorname":"Anna","nachname":"Beispiel","stufe":"Wölfe","geprueft":true,"geprueftAm":"2027-07-17T08:00:00Z"}],"naechster":"$b","da":18,"gesamt":22}"""))
        server.enqueue(json(200, """{"eintraege":[{"id":"$b","personId":"$b","name":"Lea","ceviName":"Biber","geprueft":false,"geprueftAm":null}],"naechster":null,"da":18,"gesamt":22}"""))
        val page = (runBlocking { api.count(camp, team = false, stage = "Wölfe", groupId = a) } as ApiResult.Ok).value
        val first = server.takeRequest().requestLine
        assertTrue(first, first.startsWith("GET /api/v1/lager/$camp/abzaehlen?art=teilnehmer&limit=200&stufe=W%C3%B6lfe&gruppe=$a"))
        assertTrue(server.takeRequest().requestLine.endsWith("&nach=$b HTTP/1.1"))
        assertEquals(2, page.entries.size)
        assertEquals(18, page.present)
        assertEquals("Anna Beispiel", page.entries[0].display)
        assertEquals("Lea (Biber)", page.entries[1].display)
        assertNotNull(page.entries[0].checkedAt)
    }

    @Test
    fun setCheckedAndResetUseTheDocumentedBodies() {
        server.enqueue(json(200, """{"geaendert":1}"""))
        runBlocking { api.setChecked(camp, listOf(a), emptyList(), true) }
        val set = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/abzaehlen HTTP/1.1", set.requestLine)
        assertTrue(bodyOf(set)["geprueft"]!!.jsonPrimitive.boolean)
        assertEquals(setOf("geprueft", "teilnehmerIds"), bodyOf(set).keys)

        server.enqueue(json(200, """{"zurueckgesetzt":4}"""))
        val reset = (runBlocking { api.resetCount(camp) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/abzaehlen/zuruecksetzen HTTP/1.1", request.requestLine)
        assertTrue(bodyOf(request)["bestaetigt"]!!.jsonPrimitive.boolean)
        assertEquals(4, reset.reset)
    }

    @Test
    fun teamGetsForbiddenForTheListWithoutTrip() {
        server.enqueue(json(403, """{"code":"KEINE_BERECHTIGUNG","message":"x"}"""))
        val failure = runBlocking { api.withoutTrip(camp, TripDirection.THERE) } as ApiResult.Failure
        assertEquals(ApiError.FORBIDDEN, failure.error)
    }
}
