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

/** Robustheit (D2): Wiederholen ohne doppelte Anlage, Nachziehen nach einer Unterbrechung, Rechte neu lesen. */
class RobustnessTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun file(id: String) = CampFile(id = id, name = "Plan", link = "https://example.org/$id")

    @Test
    fun retryingAPostLinksEachFileOnlyOnce() {
        val files = PostFiles()
        val draft = PostDraft(title = "Packliste", files = listOf("A" to "https://example.org/a", "B" to "https://example.org/b"))
        val added = mutableListOf<String>()
        var failSecond = true
        val add: suspend (String, String) -> ApiResult<CampFile> = { name, _ ->
            if (name == "B" && failSecond) ApiResult.Failure(ApiError.NETWORK)
            else ApiResult.Ok(file("id-$name")).also { added += name }
        }
        assertTrue(runBlocking { files.link(draft, add) } is ApiResult.Failure)
        failSecond = false
        val retry = runBlocking { files.link(draft, add) }
        assertEquals(listOf("id-A", "id-B"), (retry as ApiResult.Ok).value)
        assertEquals(listOf("A", "B"), added)
        // Nach dem Anlegen des Beitrags beginnt ein weiterer Entwurf mit gleichem Inhalt von vorn.
        files.clear()
        runBlocking { files.link(draft, add) }
        assertEquals(listOf("A", "B", "A", "B"), added)
    }

    @Test
    fun theSequenceNumberIsKeptOnceKnown() {
        assertEquals(ConnectStep.BASELINE, connectStep(null, interrupted = false))
        assertEquals(ConnectStep.BASELINE, connectStep(null, interrupted = true))
        assertEquals(ConnectStep.CATCH_UP, connectStep(42, interrupted = true))
        assertEquals(ConnectStep.RESUME, connectStep(42, interrupted = false))
    }

    @Test
    fun rightsAreReadAgainOnlyWhenTheViewResumes() {
        val reads = RightsReads()
        assertFalse("beim ersten Start hat `open` sie gelesen", reads.followStarted("a"))
        assertTrue("Rückkehr in die App", reads.followStarted("a"))
        assertTrue(reads.followStarted("a"))
        reads.reset()
        assertFalse("ein neu geöffnetes Lager beginnt von vorn", reads.followStarted("a"))
        assertFalse("ein anderes Lager auch", reads.followStarted("b"))
    }

    @Test
    fun aChangedDraftLinksAgain() {
        val files = PostFiles()
        var calls = 0
        val add: suspend (String, String) -> ApiResult<CampFile> = { _, _ -> calls++; ApiResult.Ok(file("f$calls")) }
        runBlocking { files.link(PostDraft(files = listOf("A" to "https://example.org/a")), add) }
        runBlocking { files.link(PostDraft(files = listOf("A" to "https://example.org/other")), add) }
        assertEquals(2, calls)
    }
}
