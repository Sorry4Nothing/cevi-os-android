package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Härtung der Warteschlange (A8): neue `clientOpId`, Versionen, ganz abgelehnte Anfragen. */
class HardeningQueueTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private val campId = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private var counter = 0
    private val newId = { "op-${++counter}" }

    private fun change(title: String, id: String = "t1", version: Long = 1): JsonObject = buildJsonObject {
        put("id", id)
        put("version", version)
        put("titel", title)
    }

    private fun ok(op: QueueOperation, version: Long? = null) =
        QueueResult(op.clientOpId, "ok", result = buildJsonObject { op.payload["id"]?.let { put("id", (it as JsonPrimitive).content) }; version?.let { put("version", it) } })

    private fun failure(status: Int, code: String? = null) =
        ApiResult.Failure(ApiError.UNKNOWN, code).also { it.status = status }


    // ---- 1: eine geänderte Änderung bekommt eine neue clientOpId ----

    @Test
    fun revisedContentGetsANewClientOpIdButKeepsItsRef() {
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, _ -> ApiResult.Failure(ApiError.NETWORK) }, newId)
        queue.enqueue(TaskOps.CHANGE, change("Alt"), "x")
        assertTrue(queue.revise("op-1", change("Neu"), "y"))
        val revised = queue.state.value.changes.single()
        assertNotEquals("op-1", revised.clientOpId)
        assertEquals("op-1", revised.ref)
        assertEquals("Neu", (revised.payload["titel"] as JsonPrimitive).content)
    }

    @Test
    fun unchangedContentKeepsTheClientOpIdForTheRetry() {
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, _ -> ApiResult.Failure(ApiError.NETWORK) }, newId)
        queue.enqueue(TaskOps.CHANGE, change("Gleich"), "x")
        assertTrue(queue.revise("op-1", change("Gleich"), "y"))
        assertEquals("op-1", queue.state.value.changes.single().clientOpId)
    }

    @Test
    fun aLostAnswerAndASecondEditSendTheSecondUnderANewId() {
        val sent = mutableListOf<List<String>>()
        var lost = true
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, ops ->
            sent += ops.map { it.clientOpId }
            if (lost) ApiResult.Failure(ApiError.NETWORK) else ApiResult.Ok(QueueResponse(ops.map { ok(it) }))
        }, newId)
        queue.enqueue(TaskOps.CHANGE, change("Erste"), "x")
        runBlocking { queue.flush() }
        // Die Antwort ging verloren, die Person ändert dasselbe Feld nochmals.
        queue.revise("op-1", change("Zweite"), "x")
        lost = false
        runBlocking { queue.flush() }
        assertEquals(listOf(listOf("op-1"), listOf("op-2")), sent)
        assertEquals(ChangeState.SENT, queue.state.value.changes.single().state)
    }

    @Test
    fun reviseAndCancelFailWhileTheBatchIsOnItsWay() {
        lateinit var queue: OfflineQueue
        var revised: Boolean? = null
        var cancelled: Boolean? = null
        queue = OfflineQueue(campId, MemoryQueueStore(), { _, ops ->
            revised = queue.revise(ops.single().clientOpId, change("Spät"), "x")
            cancelled = queue.cancel(ops.single().clientOpId)
            ApiResult.Ok(QueueResponse(ops.map { ok(it) }))
        }, newId)
        queue.enqueue(TaskOps.CHANGE, change("Früh"), "x")
        runBlocking { queue.flush() }
        assertEquals(false, revised)
        assertEquals(false, cancelled)
        assertEquals(1, queue.state.value.changes.size)
    }

    // ---- 4: zwei Änderungen an derselben Zeile ----

    @Test
    fun twoChangesToOneRowGoInSeparateBatches() {
        val a = QueuedChange("a", TaskOps.CHANGE, change("A"), "x", 0)
        val b = QueuedChange("b", TaskOps.CHANGE, change("B"), "x", 0)
        val c = QueuedChange("c", TaskOps.CHANGE, change("C", id = "t2"), "x", 0)
        assertEquals(listOf("a"), nextBatch(listOf(a, b, c), 100).map { it.clientOpId })
        // Auch eine Änderung an einer anderen Zeile wartet hinter der zweiten: Die Reihenfolge bleibt.
        assertEquals(listOf("a", "c"), nextBatch(listOf(a, c, b), 100).map { it.clientOpId })
        assertEquals(listOf("a"), nextBatch(listOf(a, c), 1).map { it.clientOpId })
    }

    @Test
    fun theSecondChangeTakesTheVersionTheFirstWrote() {
        val versions = mutableListOf<String>()
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, ops ->
            versions += ops.map { op -> (op.payload["version"] as JsonPrimitive).content }
            ApiResult.Ok(QueueResponse(ops.map { ok(it, version = 2) }))
        }, newId)
        queue.enqueue(TaskOps.STATUS, change("A", version = 1), "x")
        queue.enqueue(TaskOps.CHANGE, change("B", version = 1), "x")
        runBlocking { queue.flush() }
        assertEquals(listOf("1", "2"), versions)
        assertEquals(listOf(ChangeState.SENT, ChangeState.SENT), queue.state.value.changes.map { it.state })
    }

    // ---- 5: eine ganz abgelehnte Anfrage sperrt die anderen nicht ----

    @Test
    fun aBatchTheServerRejectsAsAWholeIsSplitUntilTheBadOneIsFound() {
        val requests = mutableListOf<Int>()
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, ops ->
            requests += ops.size
            if (ops.any { it.type == "kaputt" }) failure(400, "UNGUELTIGE_EINGABE") else ApiResult.Ok(QueueResponse(ops.map { ok(it) }))
        }, newId)
        queue.enqueue("a.b", change("1", id = "r1"), "eins")
        queue.enqueue("kaputt", change("2", id = "r2"), "zwei")
        queue.enqueue("a.b", change("3", id = "r3"), "drei")
        queue.enqueue("a.b", change("4", id = "r4"), "vier")
        assertEquals(FlushResult.Sent, runBlocking { queue.flush() })
        val states = queue.state.value.changes.associate { it.summary to it.state }
        assertEquals(ChangeState.REJECTED, states["zwei"])
        assertEquals(setOf(ChangeState.SENT), setOf(states["eins"], states["drei"], states["vier"]))
        assertEquals("UNGUELTIGE_EINGABE", queue.state.value.changes.first { it.summary == "zwei" }.code)
        // Nach dem Aufräumen wartet nichts mehr: Kein Sendeversuch alle 15 Sekunden.
        assertEquals(0, queue.state.value.waiting)
        assertTrue(requests.size < 10)
    }

    @Test
    fun aSingleTooLargeChangeIsRejectedVisibly() {
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, _ -> failure(413) }, newId)
        queue.enqueue("a.b", change("1"), "eins")
        assertEquals(FlushResult.Idle, runBlocking { queue.flush() })
        assertEquals(ChangeState.REJECTED, queue.state.value.changes.single().state)
    }

    @Test
    fun aServerErrorStillKeepsEverythingWaiting() {
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, _ -> failure(500) }, newId)
        queue.enqueue("a.b", change("1"), "eins")
        assertTrue(runBlocking { queue.flush() } is FlushResult.Failed)
        assertEquals(1, queue.state.value.waiting)
    }
}
