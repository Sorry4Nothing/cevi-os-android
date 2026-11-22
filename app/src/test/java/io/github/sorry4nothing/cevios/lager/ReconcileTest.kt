package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Wann eine stille Aktualisierung den Stand ersetzt und wie gelöschte Zeilen aus einer Liste fallen. */
class ReconcileTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    @Test
    fun aSilentRefreshKeepsTheOldStateOnNetworkAndServerErrors() {
        assertFalse(replacesState(ApiResult.Failure(ApiError.NETWORK), silent = true, hasCurrent = true))
        assertFalse(replacesState(ApiResult.Failure(ApiError.UNKNOWN), silent = true, hasCurrent = true))
    }

    @Test
    fun aWithdrawnReleaseOrAGoneThingReplacesTheOldState() {
        assertTrue(replacesState(ApiResult.Failure(ApiError.FORBIDDEN), silent = true, hasCurrent = true))
        assertTrue(replacesState(ApiResult.Failure(ApiError.NOT_FOUND), silent = true, hasCurrent = true))
    }

    @Test
    fun successAnAudibleLoadAndAnEmptyStateAlwaysReplace() {
        assertTrue(replacesState(ApiResult.Ok(1), silent = true, hasCurrent = true))
        assertTrue(replacesState(ApiResult.Failure(ApiError.NETWORK), silent = false, hasCurrent = true))
        assertTrue(replacesState(ApiResult.Failure(ApiError.NETWORK), silent = true, hasCurrent = false))
    }

    @Test
    fun deletedRowsFallOutOfAListAndNothingElseChanges() {
        val list: ApiResult<List<String>>? = ApiResult.Ok(listOf("a", "b", "c"))
        assertEquals(ApiResult.Ok(listOf("a", "c")), list.withoutIds(setOf("b")) { it })
        assertSame(list, list.withoutIds(setOf("x")) { it })
        assertSame(list, list.withoutIds(emptySet()) { it })
        val failure: ApiResult<List<String>> = ApiResult.Failure(ApiError.NETWORK)
        assertSame(failure, failure.withoutIds(setOf("a")) { it })
        assertNull((null as ApiResult<List<String>>?).withoutIds(setOf("a")) { it })
    }
}
