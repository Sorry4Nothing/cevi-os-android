package io.github.sorry4nothing.cevios.net

import io.github.sorry4nothing.cevios.programs.AssignmentRequest
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Der Leitungs-Modus des DemoServers: Die Zuordnung wirkt im Speicher, mit den Regeln der API. */
class LeaderDemoServerTest {
    private val storage = object : SessionStorage {
        override fun read(): String? = null
        override fun write(value: String?) = Unit
    }

    private fun programsApi(leader: Boolean) = ProgramsApi(
        ApiClient("https://api.example.org", ApiClient.httpClient(storage, "1.0.0", listOf(DemoServer(delayMs = 0, leader = leader)))),
    )

    private val api = programsApi(leader = true)

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Ok).value

    private fun <T> ApiResult<T>.failure(): ApiError = (this as ApiResult.Failure).error

    private fun list(api: ProgramsApi = this.api): List<Program> =
        runBlocking { api.list(LocalDate.now(), LocalDate.now().plusDays(30)).value().programs }

    private fun unassigned() = list().single { it.unassigned }

    private fun read(id: String) = runBlocking { api.read(id).value() }

    @Test
    fun leaderHasOneUnassignedProgramInReview() {
        assertEquals("pruefen", unassigned().status)
        assertTrue(runBlocking { api.profile().value() }.roles.any { it.type == "stufenleitung" })
        assertTrue("Eltern sehen alle Programme zugeordnet", list(programsApi(leader = false)).none { it.unassigned })
    }

    @Test
    fun parentsCannotAssign() {
        val parent = programsApi(leader = false)
        val id = list(parent).first().id
        assertEquals(ApiError.FORBIDDEN, runBlocking { parent.assign(id, AssignmentRequest(listOf(DemoData.AGE_GROUP_ID), "eltern")) }.failure())
    }

    @Test
    fun assignmentIsReplacedAndFollowsTheRules() {
        val id = unassigned().id
        val own = DemoData.AGE_GROUP_ID
        assertEquals(ApiError.UNKNOWN, runBlocking { api.assign(id, AssignmentRequest(emptyList(), "eltern")) }.failure())
        runBlocking { api.assign(id, AssignmentRequest(listOf(own), "eltern")) }.value()
        val now = read(id)
        assertEquals(listOf(own), now.ageGroups.map { it.id })
        assertEquals("eltern", now.visibility)
        val other = AssignmentRequest(listOf(own, DemoData.OTHER_AGE_GROUP_ID), "eltern")
        assertEquals(ApiError.FORBIDDEN, runBlocking { api.assign(id, other) }.failure())
    }

    @Test
    fun ageGroupsListsAllOfThem() {
        val groups = runBlocking { api.ageGroups().value() }.ageGroups
        assertEquals(listOf(DemoData.AGE_GROUP_ID, DemoData.OTHER_AGE_GROUP_ID), groups.map { it.id })
    }
}
