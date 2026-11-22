package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Warteschlange und Konfliktlogik: gegen einen Fake-Server und gegen einen Ersatz, der das Netz verliert. */
class OfflineQueueTest {
    // Ein hängender Test soll mit Namen scheitern, nicht die CI stundenlang blockieren.
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var queue: OfflineQueue
    private val id = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private var counter = 0

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        val api = CampApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
        queue = OfflineQueue(id, MemoryQueueStore(), api, newId = { "op-${++counter}" }, now = { 1_000L })
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun payload(title: String, version: Int = 1): JsonObject = buildJsonObject {
        put("titel", title)
        put("version", version)
    }

    private fun state(clientOpId: String) = queue.state.value.changes.first { it.clientOpId == clientOpId }

    @Test
    fun enqueuedChangeWaitsUntilFlushed() {
        queue.enqueue("aufgabe.aendern", payload("Zelte"), "Aufgabe «Zelte» geändert")
        assertEquals(1, queue.state.value.waiting)
        assertEquals(ChangeState.WAITING, state("op-1").state)
    }

    @Test
    fun okMarksTheChangeAsSent() {
        queue.enqueue("aufgabe.aendern", payload("Zelte"), "Zelte")
        server.enqueue(json(200, """{"ergebnisse":[{"clientOpId":"op-1","status":"ok","ergebnis":{"version":2}}]}"""))
        assertEquals(FlushResult.Sent, runBlocking { queue.flush() })
        assertEquals(ChangeState.SENT, state("op-1").state)
        assertEquals(0, queue.state.value.waiting)
        queue.clearSent()
        assertTrue(queue.state.value.changes.isEmpty())
    }

    @Test
    fun flushWithoutChangesDoesNotCallTheServer() {
        assertEquals(FlushResult.Idle, runBlocking { queue.flush() })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun conflictStaysVisibleWithBothVersions() {
        queue.enqueue("aufgabe.aendern", payload("Meine Fassung"), "Aufgabe geändert", mapOf("titel" to "Titel"))
        server.enqueue(
            json(200, """{"ergebnisse":[{"clientOpId":"op-1","status":"konflikt","code":"VERSION_KONFLIKT","aktuell":{"titel":"Andere Fassung","version":5},"abweichend":["titel"]}]}"""),
        )
        runBlocking { queue.flush() }
        val change = state("op-1")
        assertEquals(ChangeState.CONFLICT, change.state)
        assertEquals(1, queue.state.value.needsDecision.size)
        val rows = conflictRows(change)
        assertEquals(1, rows.size)
        assertEquals(ConflictRow("Titel", "Meine Fassung", "Andere Fassung", differs = true, key = "titel"), rows.single())
    }

    @Test
    fun keepMineSendsAgainWithNewIdAndCurrentVersion() {
        queue.enqueue("aufgabe.aendern", payload("Meine Fassung", version = 1), "Aufgabe geändert")
        server.enqueue(json(200, """{"ergebnisse":[{"clientOpId":"op-1","status":"konflikt","code":"VERSION_KONFLIKT","aktuell":{"titel":"Andere","version":5},"abweichend":["titel"]}]}"""))
        runBlocking { queue.flush() }
        queue.keepMine("op-1")
        val change = queue.state.value.changes.single()
        assertEquals(ChangeState.WAITING, change.state)
        assertNotEquals("op-1", change.clientOpId)
        assertEquals("5", change.payload["version"].toString())
        server.enqueue(json(200, """{"ergebnisse":[{"clientOpId":"${change.clientOpId}","status":"ok"}]}"""))
        runBlocking { queue.flush() }
        assertTrue(server.takeRequest().body!!.utf8().contains("\"clientOpId\":\"op-1\""))
        val second = server.takeRequest().body!!.utf8()
        assertTrue(second.contains("\"clientOpId\":\"${change.clientOpId}\""))
        assertTrue(second.contains("\"version\":5"))
        assertEquals(ChangeState.SENT, queue.state.value.changes.single().state)
    }

    @Test
    fun takeCurrentDropsTheChange() {
        queue.enqueue("aufgabe.aendern", payload("Meine"), "x")
        server.enqueue(json(200, """{"ergebnisse":[{"clientOpId":"op-1","status":"konflikt","aktuell":{"titel":"Andere","version":5},"abweichend":["titel"]}]}"""))
        runBlocking { queue.flush() }
        queue.discard("op-1")
        assertTrue(queue.state.value.changes.isEmpty())
    }

    @Test
    fun rejectedChangeStaysUntilDiscarded() {
        queue.enqueue("aufgabe.aendern", payload("Zelte"), "x")
        server.enqueue(json(200, """{"ergebnisse":[{"clientOpId":"op-1","status":"abgelehnt","code":"KEINE_BERECHTIGUNG"}]}"""))
        runBlocking { queue.flush() }
        assertEquals(ChangeState.REJECTED, state("op-1").state)
        assertEquals("KEINE_BERECHTIGUNG", state("op-1").code)
        // Ein weiterer Sendeversuch schickt nichts mehr: abgelehnt ist erledigt.
        assertEquals(FlushResult.Idle, runBlocking { queue.flush() })
        assertEquals(1, server.requestCount)
        queue.discard("op-1")
        assertTrue(queue.state.value.changes.isEmpty())
    }

    @Test
    fun discardNeverDropsAWaitingChange() {
        queue.enqueue("aufgabe.aendern", payload("Zelte"), "x")
        queue.discard("op-1")
        assertEquals(1, queue.state.value.waiting)
    }

    @Test
    fun closedCampRejectsAllWaitingChanges() {
        queue.enqueue("a.b", payload("1"), "eins")
        queue.enqueue("a.b", payload("2"), "zwei")
        server.enqueue(json(409, """{"code":"LAGER_GESCHLOSSEN","message":"zu"}"""))
        runBlocking { queue.flush() }
        assertEquals(listOf(ChangeState.REJECTED, ChangeState.REJECTED), queue.state.value.changes.map { it.state })
        assertEquals("LAGER_GESCHLOSSEN", state("op-1").code)
    }

    @Test
    fun noNetworkKeepsTheChangesAndTheSameClientOpId() {
        var attempts = 0
        val sent = mutableListOf<String>()
        val flaky = OfflineQueue(
            id, MemoryQueueStore(),
            { _, operations ->
                attempts++
                sent += operations.map { it.clientOpId }
                if (attempts == 1) {
                    ApiResult.Failure(ApiError.NETWORK)
                } else {
                    ApiResult.Ok(QueueResponse(operations.map { QueueResult(it.clientOpId, "ok") }))
                }
            },
            newId = { "op-${++counter}" },
        )
        flaky.enqueue("a.b", payload("1"), "eins")
        val first = runBlocking { flaky.flush() }
        assertEquals(FlushResult.Failed(ApiError.NETWORK), first)
        assertTrue(flaky.state.value.offline)
        assertEquals(1, flaky.state.value.waiting)
        runBlocking { flaky.flush() }
        assertFalse(flaky.state.value.offline)
        // Die Wiederholung trägt dieselbe clientOpId: Der Server schreibt sie nur einmal.
        assertEquals(listOf("op-1", "op-1"), sent)
        assertEquals(ChangeState.SENT, flaky.state.value.changes.single().state)
    }

    @Test
    fun serverErrorKeepsTheChangesWaiting() {
        queue.enqueue("a.b", payload("1"), "eins")
        server.enqueue(json(500, """{"code":"FEHLER","message":"x"}"""))
        assertTrue(runBlocking { queue.flush() } is FlushResult.Failed)
        assertEquals(1, queue.state.value.waiting)
    }

    @Test
    fun answerWithoutResultDoesNotLoopForever() {
        queue.enqueue("a.b", payload("1"), "eins")
        server.enqueue(json(200, """{"ergebnisse":[]}"""))
        assertEquals(FlushResult.Failed(ApiError.UNKNOWN), runBlocking { queue.flush() })
        assertEquals(1, server.requestCount)
        assertEquals(1, queue.state.value.waiting)
    }

    @Test
    fun moreThanHundredChangesGoInBatches() {
        val batches = mutableListOf<Int>()
        val big = OfflineQueue(
            id, MemoryQueueStore(),
            { _, operations ->
                batches += operations.size
                ApiResult.Ok(QueueResponse(operations.map { QueueResult(it.clientOpId, "ok") }))
            },
        )
        repeat(250) { big.enqueue("a.b", payload("$it"), "n$it") }
        runBlocking { big.flush() }
        assertEquals(listOf(100, 100, 50), batches)
        assertEquals(0, big.state.value.waiting)
    }

    @Test
    fun theStoreHoldsTheChangesBehindTheInterface() {
        val store = MemoryQueueStore()
        val api = QueueSender { _, _ -> ApiResult.Failure(ApiError.NETWORK) }
        OfflineQueue(id, store, api).enqueue("a.b", payload("1"), "eins")
        // Eine zweite Warteschlange auf demselben Speicher sieht die Änderung (heute nur im Arbeitsspeicher).
        assertEquals(1, OfflineQueue(id, store, api).state.value.waiting)
        assertEquals(0, OfflineQueue("anderes-lager", store, api).state.value.waiting)
    }

    @Test
    fun conflictRowsWithoutDifferingFieldsShowAllPayloadFieldsButNotTheVersion() {
        val change = QueuedChange("x", "a.b", payload("Meine"), "s", 0, state = ChangeState.CONFLICT, current = buildJsonObject { put("titel", "Meine") })
        val rows = conflictRows(change)
        assertEquals(listOf("titel"), rows.map { it.label })
        assertFalse(rows.single().differs)
    }
}
