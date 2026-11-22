package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Härtung bei Aufgaben (A8): Änderung während der Anlage, Anlage einmal, «Zusammenführen». */
class HardeningTaskTest {
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


    // ---- 2: Änderungen an einer Aufgabe, deren Anlage unterwegs ist ----

    private fun task(id: String, queuedId: String? = null) = CampTask(id = id, version = 1, title = "T", queuedId = queuedId)

    @Test
    fun anEditOfATaskBeingCreatedIsBlockedNotLost() {
        lateinit var queue: OfflineQueue
        var route: ChangeRoute? = null
        queue = OfflineQueue(campId, MemoryQueueStore(), { _, ops ->
            route = routeTaskChange(queue, task("queued:op-1", "op-1"), TaskOps.CHANGE, change("Neu", id = "queued:op-1"), "x") { error("nicht erwartet") }
            ApiResult.Ok(QueueResponse(ops.map { ok(it, version = 1) }))
        }, newId)
        queue.enqueue(TaskOps.CREATE, buildJsonObject { put("titel", "Zelte") }, "x")
        runBlocking { queue.flush() }
        assertEquals(ChangeRoute.BLOCKED, route)
    }

    @Test
    fun anEditOfAWaitingCreationChangesTheCreationAndADeleteRemovesIt() {
        val queue = OfflineQueue(campId, MemoryQueueStore(), { _, _ -> ApiResult.Failure(ApiError.NETWORK) }, newId)
        queue.enqueue(TaskOps.CREATE, buildJsonObject { put("titel", "Zelte") }, "x")
        val shown = task("queued:op-1", "op-1")
        assertEquals(ChangeRoute.MERGED, routeTaskChange(queue, shown, TaskOps.CHANGE, change("Zelte 2", id = shown.id), "y") { error("nicht erwartet") })
        assertEquals("Zelte 2", (queue.state.value.changes.single().payload["titel"] as JsonPrimitive).content)
        // Die Anzeige findet «ihre» Anlage auch nach der neuen clientOpId wieder.
        assertEquals("op-1", queue.state.value.changes.single().ref)
        assertEquals(ChangeRoute.MERGED, routeTaskChange(queue, shown, TaskOps.DELETE, change("", id = shown.id), "y") { error("nicht erwartet") })
        assertTrue(queue.state.value.changes.isEmpty())
    }

    @Test
    fun aChangeOnTheSameTaskWhileSendingIsQueuedAfterwards() {
        lateinit var queue: OfflineQueue
        var route: ChangeRoute? = null
        var enqueued = false
        queue = OfflineQueue(campId, MemoryQueueStore(), { _, ops ->
            route = routeTaskChange(queue, task("t1"), TaskOps.CHANGE, change("Zweite"), "x") { enqueued = true }
            ApiResult.Ok(QueueResponse(ops.map { ok(it, version = 2) }))
        }, newId)
        queue.enqueue(TaskOps.CHANGE, change("Erste"), "x")
        runBlocking { queue.flush() }
        assertEquals(ChangeRoute.ENQUEUED, route)
        assertTrue(enqueued)
    }

    @Test
    fun aConfirmedCreationShowsTheRealIdAndVersion() {
        val sent = QueuedChange(
            "op-1", TaskOps.CREATE, buildJsonObject { put("titel", "Zelte") }, "x", 0, state = ChangeState.SENT,
            result = buildJsonObject { put("id", "t9"); put("version", 3) },
        )
        val shown = applyQueued(emptyList(), listOf(sent)).single()
        assertEquals("t9", shown.id)
        assertEquals(3L, shown.version)
        assertEquals("op-1", shown.queuedId)
    }

    // ---- 7: Zusammenführen legt die Aufgabe nur einmal an ----

    @Test
    fun retryingAFailedMergeDoesNotCreateASecondTask() {
        val merge = DuplicateMerge()
        val item = QuickItem("Zelte", "Zelte", QuickChoice.None, QuickChoice.None, null, null, false)
        var created = 0
        var mergeCalls = 0
        val create: suspend () -> ApiResult<CampTask> = { created++; ApiResult.Ok(CampTask("neu", 1, "Zelte")) }
        val failingMerge: suspend (CampTask) -> ApiResult<CampTask> = { mergeCalls++; ApiResult.Failure(ApiError.NETWORK) }
        assertTrue(runBlocking { merge.merge(item, create, failingMerge) } is ApiResult.Failure)
        assertTrue(runBlocking { merge.merge(item, create, failingMerge) } is ApiResult.Failure)
        val done = runBlocking { merge.merge(item, create) { mergeCalls++; ApiResult.Ok(CampTask("alt", 2, "Zelte")) } }
        assertTrue(done is ApiResult.Ok)
        assertEquals(1, created)
        assertEquals(3, mergeCalls)
        assertEquals("neu", merge.createdFor(item)?.id)
        merge.clear()
        assertNull(merge.createdFor(item))
    }

    @Test
    fun aFailedCreateIsTriedAgainInFull() {
        val merge = DuplicateMerge()
        val item = QuickItem("Zelte", "Zelte", QuickChoice.None, QuickChoice.None, null, null, false)
        var created = 0
        val result = runBlocking { merge.merge(item, { created++; ApiResult.Failure(ApiError.NETWORK) }) { ApiResult.Ok(it) } }
        assertTrue(result is ApiResult.Failure)
        assertNull(merge.createdFor(item))
        runBlocking { merge.merge(item, { created++; ApiResult.Ok(CampTask("neu", 1, "Zelte")) }) { ApiResult.Ok(it) } }
        assertEquals(2, created)
    }
}
