package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Verschieben in zwei Schritten (A8): nach einem Fehler wiederholbar. */
class PlanningMoveTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    // ---- 3: Verschieben in zwei Schritten ----

    @Test
    fun movingStopsWhenTheRemovalFails() {
        var added = false
        var removedNotified = false
        val result = runBlocking {
            moveInTwoSteps(false, { ApiResult.Failure(ApiError.NETWORK) }, { added = true; ApiResult.Ok(1) }) { removedNotified = true }
        }
        assertTrue(result is ApiResult.Failure)
        assertFalse(added || removedNotified)
    }

    @Test
    fun movingRemembersTheRemovalAndTheRetryOnlyAdds() {
        var removals = 0
        var adds = 0
        var removed = false
        val remove: suspend () -> ApiResult<*> = { removals++; ApiResult.Ok(Unit) }
        val failing: suspend () -> ApiResult<Int> = { adds++; ApiResult.Failure(ApiError.NETWORK) }
        assertTrue(runBlocking { moveInTwoSteps(removed, remove, failing) { removed = true } } is ApiResult.Failure)
        assertTrue(removed)
        // Der zweite Versuch entfernt nicht noch einmal (mit der alten Version scheiterte er).
        assertTrue(runBlocking { moveInTwoSteps(removed, remove, { adds++; ApiResult.Ok(1) }) { removed = true } } is ApiResult.Ok)
        assertEquals(1, removals)
        assertEquals(2, adds)
    }
}
