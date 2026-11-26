package io.github.sorry4nothing.cevios.lager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class CheckLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun checker(id: String, level: String, vararg notes: CheckNote) = CheckChecker(id, id, level, notes.toList())
    private fun note(severity: String) = CheckNote("X", severity, "Text")
    private fun point(id: String, done: Boolean = false, order: Int = 0, title: String = id) = CheckItem(id, 1, title, done, order)

    // ---- Rechte ----

    @Test
    fun leadershipReadsAndEdits() {
        val rights = CheckRights.of(defaultPermissions(CampRole.LEADERSHIP))
        assertTrue(rights.read)
        assertTrue(rights.edit)
    }

    @Test
    fun leaderAndReadOnlyReadButDoNotEdit() {
        listOf(CampRole.LEADER, CampRole.READ_ONLY).forEach {
            val rights = CheckRights.of(defaultPermissions(it))
            assertTrue(it.name, rights.read)
            assertFalse(it.name, rights.edit)
        }
    }

    @Test
    fun theTeamSeesNothingByDefault() {
        assertEquals(CheckRights(read = false, edit = false), CheckRights.of(defaultPermissions(CampRole.TEAM)))
    }

    @Test
    fun theTeamReadsWhenTheLeadershipAllowsIt() {
        val rights = CheckRights.of(defaultPermissions(CampRole.TEAM, extra = setOf("check.lesen")))
        assertTrue(rights.read)
        assertFalse(rights.edit)
    }

    @Test
    fun editingNeedsBothRights() {
        assertFalse(CheckRights.of(defaultPermissions(CampRole.LEADERSHIP, without = setOf("check.lesen"))).edit)
        assertFalse(CheckRights.of(defaultPermissions(CampRole.LEADERSHIP, without = setOf("zeitplan.bearbeiten"))).edit)
    }

    @Test
    fun aClosedCampKeepsReadingButNotEditing() {
        val rights = CheckRights.of(defaultPermissions(CampRole.LEADERSHIP, readOnly = true))
        assertTrue(rights.read)
        assertFalse(rights.edit)
    }

    @Test
    fun withoutDetailNothingIsAllowed() {
        assertEquals(CheckRights(read = false, edit = false), CheckRights.of(NO_PERMISSIONS))
    }

    // ---- Anzeige ----

    @Test
    fun checkersWithOpenPointsComeFirst() {
        val check = CampCheck("offen", "2027-03-01", listOf(checker("a", "bereit"), checker("b", "hinweise"), checker("c", "offen")))
        assertEquals(listOf("c", "b", "a"), CheckRules.sortedCheckers(check).map { it.id })
    }

    @Test
    fun theOrderOfEqualCheckersIsKept() {
        val check = CampCheck("hinweise", "2027-03-01", listOf(checker("x", "hinweise"), checker("y", "hinweise")))
        assertEquals(listOf("x", "y"), CheckRules.sortedCheckers(check).map { it.id })
    }

    @Test
    fun onlyNotesWithSeverityOpenBlock() {
        val check = CampCheck("offen", "2027-03-01", listOf(checker("a", "offen", note("offen"), note("hinweis")), checker("b", "offen", note("offen"))))
        assertEquals(2, CheckRules.blockingCount(check))
    }

    @Test
    fun anUnknownLevelIsOpen() {
        assertEquals(CheckLevel.OPEN, CheckLevel.from(null))
        assertEquals(CheckLevel.OPEN, CheckLevel.from("neu"))
        assertEquals(CheckLevel.READY, CheckLevel.from("bereit"))
    }

    // ---- Eigene Punkte ----

    @Test
    fun openPointsComeBeforeDoneOnesInOrder() {
        val sorted = CheckRules.sortedPoints(listOf(point("d1", done = true, order = 0), point("o2", order = 2), point("o1", order = 1)))
        assertEquals(listOf("o1", "o2", "d1"), sorted.map { it.id })
    }

    @Test
    fun aTitleIsTrimmedAndBounded() {
        assertEquals("Elternbrief", CheckRules.validTitle("  Elternbrief "))
        assertNull(CheckRules.validTitle("   "))
        assertNull(CheckRules.validTitle("x".repeat(CheckRules.TITLE_MAX + 1)))
        assertEquals(CheckRules.TITLE_MAX, CheckRules.validTitle("x".repeat(CheckRules.TITLE_MAX))?.length)
    }

    @Test
    fun theLimitStopsAdding() {
        assertTrue(CheckRules.canAdd(List(CheckRules.MAX_POINTS - 1) { point("p$it") }))
        assertFalse(CheckRules.canAdd(List(CheckRules.MAX_POINTS) { point("p$it") }))
    }

    @Test
    fun replacingTouchesOnlyTheChangedPoint() {
        val list = listOf(point("a"), point("b"))
        val result = CheckRules.replace(list, point("b", done = true))
        assertFalse(result[0].done)
        assertTrue(result[1].done)
    }
}
