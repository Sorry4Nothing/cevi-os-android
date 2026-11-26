package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Ein Lager, das es nicht mehr gibt: Die wartenden Änderungen kommen nie an und dürfen nicht endlos wiederholt werden. */
class GoneCampQueueTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private var calls = 0

    private fun queue(store: QueueStore, error: ApiError, status: Int): OfflineQueue =
        OfflineQueue("lager-1", store, { _, _ ->
            calls++
            ApiResult.Failure(error).also { it.status = status }
        }, newId = { "op-${calls + 1}" })

    @Test
    fun notFoundRejectsTheChangeAndItIsNotSentAgain() = runBlocking {
        val queue = queue(MemoryQueueStore(), ApiError.NOT_FOUND, 404)
        queue.enqueue("aufgabe.aendern", JsonObject(emptyMap()), "Aufgabe geändert")
        queue.flush()
        queue.flush()
        assertEquals(1, calls)
        assertEquals(ChangeState.REJECTED, queue.state.value.changes.single().state)
    }

    @Test
    fun aClosedCampRejectsTheChangeAndItIsNotSentAgain() = runBlocking {
        val queue = queue(MemoryQueueStore(), ApiError.LAGER_CLOSED, 409)
        queue.enqueue("aufgabe.aendern", JsonObject(emptyMap()), "Aufgabe geändert")
        queue.flush()
        queue.flush()
        assertEquals(1, calls)
        assertEquals("LAGER_GESCHLOSSEN", queue.state.value.changes.single().code)
    }

    @Test
    fun clearingTheStoreDropsTheChangesOfThatCampOnly() {
        val store = MemoryQueueStore()
        val change = QueuedChange("op-1", "aufgabe.aendern", JsonObject(emptyMap()), "x", 1L)
        store.save("lager-1", listOf(change))
        store.save("lager-2", listOf(change))
        store.clear("lager-1")
        assertTrue(store.load("lager-1").isEmpty())
        assertEquals(1, store.load("lager-2").size)
    }
}
