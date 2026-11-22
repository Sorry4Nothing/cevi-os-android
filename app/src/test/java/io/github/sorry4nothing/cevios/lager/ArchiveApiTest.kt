package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Zeitplan, Dokumente, Adressbuch, Suche und Aushänge gegen einen Fake-Server: Pfade, Felder, Seiten, Fehler. */
class ArchiveApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: ArchiveApi
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
        api = ArchiveApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun bodyOf(request: mockwebserver3.RecordedRequest): JsonObject = kotlinx.serialization.json.Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    // ---- Zeitplan ----

    @Test
    fun scheduleReadsMilestonesDaysAndSections() {
        server.enqueue(
            json(
                200,
                """{"meilensteine":[{"id":"$a","version":2,"titel":"Lagerhaus fest","datum":"2027-03-03","art":null}],
                    "lagertage":[{"id":"$b","version":1,"datum":"2027-07-17","label":null}],
                    "tagesabschnitte":[{"id":"$a","version":1,"name":"Vormittag","zeit":null,"reihenfolge":0}]}""",
            ),
        )
        val schedule = (runBlocking { api.schedule(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/zeitplan HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Lagerhaus fest", schedule.milestones.single().title)
        assertEquals("2027-07-17", schedule.days.single().date)
        assertEquals("Vormittag", schedule.sections.single().name)
    }

    @Test
    fun proposalAndAdoptionUseTheProposedValues() {
        server.enqueue(json(200, """{"lagertage":[{"datum":"2027-07-17","label":null},{"datum":"2027-07-18","label":null}],"tagesabschnitte":[{"name":"Vormittag","zeit":null,"reihenfolge":0}]}"""))
        val proposal = (runBlocking { api.proposal(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/zeitplan/vorschlag HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(2, proposal.days.size)
        server.enqueue(json(201, """{"lagertage":[],"tagesabschnitte":[]}"""))
        runBlocking { api.adoptProposal(camp, proposal) }
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/zeitplan/uebernehmen HTTP/1.1", request.requestLine)
        val body = bodyOf(request)
        assertEquals(listOf("2027-07-17", "2027-07-18"), body["lagertage"]!!.jsonArray.map { it.jsonObject["datum"]!!.jsonPrimitive.content })
        assertEquals("Vormittag", body["tagesabschnitte"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun adoptionOmitsWhatIsNotProposed() {
        server.enqueue(json(201, """{"lagertage":[],"tagesabschnitte":[]}"""))
        runBlocking { api.adoptProposal(camp, ScheduleProposal(days = listOf(ProposedDay("2027-07-17")))) }
        assertEquals(setOf("lagertage"), bodyOf(server.takeRequest()).keys)
    }

    @Test
    fun scheduleForbiddenForTheProposalIsAFailure() {
        server.enqueue(json(403, """{"code":"RECHT_FEHLT","message":"x"}"""))
        val failure = runBlocking { api.proposal(camp) } as ApiResult.Failure
        assertEquals(ApiError.FORBIDDEN, failure.error)
    }

    // ---- Dokumente ----

    private fun documentJson(id: String, title: String, link: String?) = """{"quelle":"dokument","id":"$id","version":2,"titel":"$title","stichworte":["programm"],"teamSichtbar":true,
        "datei":${if (link == null) "null" else """{"id":"$b","version":1,"anzeigename":"$title","link":"$link","typ":"application/pdf","groesse":1200,"hinzugefuegtVon":null,"hinzugefuegtAm":"2027-03-01T10:00:00Z"}"""},
        "art":${if (link == null) "null" else "\"pdf\""},"sharepoint":${if (link == null) "null" else "true"},"erstelltAm":"2027-03-01T10:00:00Z"}"""

    @Test
    fun documentsFollowTheCursorEvenThroughAnEmptyPage() {
        server.enqueue(json(200, """{"dokumente":[],"naechster":"$a"}"""))
        server.enqueue(json(200, """{"dokumente":[${documentJson(a, "Lagerprogramm.pdf", "https://beispiel.sharepoint.com/x")},${documentJson(b, "Weg", null)}],"naechster":null}"""))
        val documents = (runBlocking { api.documents(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/dokumente?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/dokumente?limit=200&nach=$a HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(2, documents.size)
        assertEquals("https://beispiel.sharepoint.com/x", documents[0].file!!.link)
        assertEquals(listOf("programm"), documents[0].keywords)
        assertNull(documents[1].file)
        assertTrue(documents[0].editable)
    }

    @Test
    fun documentsFailureForNoRightIsForbidden() {
        server.enqueue(json(403, """{"code":"RECHT_FEHLT","message":"x"}"""))
        assertEquals(ApiError.FORBIDDEN, (runBlocking { api.documents(camp) } as ApiResult.Failure).error)
    }

    @Test
    fun addingADocumentCreatesTheFileLinkFirstAndThenTheDocument() {
        server.enqueue(json(201, """{"id":"$b","version":1,"anzeigename":"Menüplan","link":"https://beispiel.sharepoint.com/m","typ":null,"groesse":null,"hinzugefuegtVon":null,"hinzugefuegtAm":"2027-03-01T10:00:00Z"}"""))
        val file = (runBlocking { api.addFile(camp, " Menüplan ", " https://beispiel.sharepoint.com/m ") } as ApiResult.Ok).value
        val fileRequest = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/dateien HTTP/1.1", fileRequest.requestLine)
        assertEquals("Menüplan", bodyOf(fileRequest)["anzeigename"]!!.jsonPrimitive.content)
        assertEquals("https://beispiel.sharepoint.com/m", bodyOf(fileRequest)["link"]!!.jsonPrimitive.content)
        server.enqueue(json(201, documentJson(a, "Menüplan", "https://beispiel.sharepoint.com/m")))
        runBlocking { api.addDocument(camp, "Menüplan", file.id, teamVisible = true) }
        val documentRequest = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/dokumente HTTP/1.1", documentRequest.requestLine)
        assertEquals(b, bodyOf(documentRequest)["dateiId"]!!.jsonPrimitive.content)
        assertEquals("true", bodyOf(documentRequest)["teamSichtbar"]!!.jsonPrimitive.content)
    }

    @Test
    fun anInvalidLinkIsRefusedByTheServerWithItsCode() {
        server.enqueue(json(400, """{"code":"LINK_UNGUELTIG","message":"x"}"""))
        val failure = runBlocking { api.addFile(camp, "x", "http://beispiel.ch") } as ApiResult.Failure
        assertEquals("LINK_UNGUELTIG", failure.code)
    }

    @Test
    fun deletingADocumentSendsTheSeenVersion() {
        server.enqueue(json(204, ""))
        runBlocking { api.deleteDocument(camp, a, 5) }
        assertEquals("DELETE /api/v1/lager/$camp/dokumente/$a?version=5 HTTP/1.1", server.takeRequest().requestLine)
    }

    // ---- Adressbuch ----

    private fun contactJson(id: String, name: String, keep: Boolean = false) =
        """{"id":"$id","version":1,"name":"$name","funktion":"Hauswart","gruppe":null,"telefon":"000 00 00","email":null,"notiz":null,"behalten":$keep,"aufraeumenAb":${if (keep) "null" else "\"2028-01-14\""},"erstelltAm":"2027-03-01T10:00:00Z"}"""

    @Test
    fun contactsReadAllPages() {
        server.enqueue(json(200, """{"kontakte":[${contactJson(a, "Eins")}],"naechster":"$a"}"""))
        server.enqueue(json(200, """{"kontakte":[${contactJson(b, "Zwei", keep = true)}],"naechster":null}"""))
        val contacts = (runBlocking { api.contacts(camp) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/adressbuch?limit=200 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$camp/adressbuch?limit=200&nach=$a HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(listOf("Eins", "Zwei"), contacts.map { it.name })
        assertTrue(contacts[1].keep)
        assertEquals("2028-01-14", contacts[0].cleanupFrom)
    }

    @Test
    fun theAddressBookIsForbiddenForEveryoneButTheLeadership() {
        server.enqueue(json(403, """{"code":"RECHT_FEHLT","message":"x"}"""))
        assertEquals(ApiError.FORBIDDEN, (runBlocking { api.contacts(camp) } as ApiResult.Failure).error)
    }

    @Test
    fun changingAContactSendsTheVersionAndOnlyTheChangedField() {
        server.enqueue(json(200, contactJson(a, "Eins", keep = true)))
        val body = buildJsonObject { put("version", 1); put("behalten", true) }
        val changed = (runBlocking { api.changeContact(camp, a, body) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("PATCH /api/v1/lager/$camp/adressbuch/$a HTTP/1.1", request.requestLine)
        assertEquals(setOf("version", "behalten"), bodyOf(request).keys)
        assertTrue(changed.keep)
    }

    @Test
    fun deletingAContactSendsTheSeenVersion() {
        server.enqueue(json(204, ""))
        runBlocking { api.deleteContact(camp, a, 3) }
        assertEquals("DELETE /api/v1/lager/$camp/adressbuch/$a?version=3 HTTP/1.1", server.takeRequest().requestLine)
    }

    // ---- Suche ----

    @Test
    fun searchEncodesTheQueryAndReadsTheHits() {
        server.enqueue(json(200, """{"treffer":[{"typ":"aufgabe","id":"$a","titel":"Zelte prüfen","auszug":null},{"typ":"protokoll","id":"$b","titel":"Höck","auszug":"… Zelt bis Freitag"}]}"""))
        val result = (runBlocking { api.search(camp, " Zelt Küche ") } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/suche?q=Zelt+K%C3%BCche HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(listOf("aufgabe", "protokoll"), result.hits.map { it.type })
        assertNull(result.hits[0].excerpt)
        assertEquals("… Zelt bis Freitag", result.hits[1].excerpt)
    }

    @Test
    fun tooManySearchesAreReported() {
        server.enqueue(json(429, """{"code":"ZU_VIELE_ANFRAGEN","message":"x"}"""))
        assertEquals(ApiError.TOO_MANY_REQUESTS, (runBlocking { api.search(camp, "Zelt") } as ApiResult.Failure).error)
    }

    // ---- Aushänge ----

    @Test
    fun aPrintoutIsDownloadedAsBytesFromTheNamedRoute() {
        val pdf = "%PDF-1.4".toByteArray()
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/pdf").body(String(pdf)).build())
        val bytes = (runBlocking { api.printout(camp, Printout.DOOR_SIGNS) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/lager/$camp/aushaenge/tuerschilder HTTP/1.1", server.takeRequest().requestLine)
        assertArrayEquals(pdf, bytes)
    }

    @Test
    fun everyPrintoutHasTheNameOfTheServer() {
        assertEquals(
            setOf("schichtplan", "zimmerliste", "tuerschilder", "gruppenlisten", "fahrtenliste", "organigramm"),
            Printout.entries.map { it.wire }.toSet(),
        )
        assertFalse(Printout.entries.any { it.wire.contains('/') })
    }

    @Test
    fun aPrintoutWithoutTheRightIsForbidden() {
        server.enqueue(json(403, """{"code":"RECHT_FEHLT","message":"x"}"""))
        assertEquals(ApiError.FORBIDDEN, (runBlocking { api.printout(camp, Printout.ROOMS) } as ApiResult.Failure).error)
    }
}
