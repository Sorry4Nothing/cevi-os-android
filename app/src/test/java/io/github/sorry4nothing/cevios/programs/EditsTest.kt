package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.chat.ProfileRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    private val pfadi = ProgramAgeGroup("s1", "Pfadi")
    private val woelfe = ProgramAgeGroup("s2", "Wölfe")

    private fun program(ageGroups: List<ProgramAgeGroup> = listOf(pfadi), visibility: String = "eltern") = Program(
        id = "p1", title = "Hock", status = "aktiv", visibility = visibility, allDay = false,
        meetingPoint = "Pfadiheim", closingLocation = null, closingTime = "16:30:00", bringAlong = "Zvieri",
        ageGroups = ageGroups,
    )

    private val admin = Profile("a", admin = true)
    private val groupLeader = Profile("l", roles = listOf(ProfileRole("s1", "stufenleitung")))
    private val leader = Profile("m", roles = listOf(ProfileRole("s1", "leitung")))
    private val parent = Profile("e")

    @Test
    fun extrasCutSecondsFromClosingTime() {
        val extras = Extras.of(program())
        assertEquals("16:30", extras.closingTime)
        assertEquals("", extras.closingLocation)
        assertEquals("Pfadiheim", extras.meetingPoint)
    }

    @Test
    fun nothingChangedMeansNothingToSend() {
        val original = Extras.of(program())
        assertTrue(ExtrasRules.changes(original, original).isEmpty())
        assertTrue("Leerzeichen am Rand ändern nichts", ExtrasRules.changes(original, original.copy(meetingPoint = " Pfadiheim ")).isEmpty())
    }

    @Test
    fun onlyChangedFieldsAreSentAndTrimmed() {
        val original = Extras.of(program())
        val edited = original.copy(meetingPoint = " Bahnhof ", parentInfo = "Bitte pünktlich")
        assertEquals(mapOf("treffpunkt" to "Bahnhof", "infoEltern" to "Bitte pünktlich"), ExtrasRules.changes(original, edited))
    }

    @Test
    fun clearedFieldIsSentEmptyToRemoveIt() {
        val original = Extras.of(program())
        assertEquals(mapOf("treffpunkt" to ""), ExtrasRules.changes(original, original.copy(meetingPoint = "  ")))
    }

    @Test
    fun timeMustBeEmptyOrHoursAndMinutes() {
        listOf("", "  ", "16:30", "00:00", "23:59", "09:05").forEach { assertTrue(it, ExtrasRules.validTime(it)) }
        listOf("24:00", "16:60", "9:30", "16.30", "1630", "16:30:00", "abc").forEach { assertFalse(it, ExtrasRules.validTime(it)) }
    }

    @Test
    fun onlyAdminsAndGroupLeadersMayAssign() {
        assertTrue(AssignmentRules.mayAssign(admin))
        assertTrue(AssignmentRules.mayAssign(groupLeader))
        assertFalse("Leitende nie", AssignmentRules.mayAssign(leader))
        assertFalse(AssignmentRules.mayAssign(parent))
        assertFalse("ohne Profil", AssignmentRules.mayAssign(null))
    }

    @Test
    fun adminsChooseAllGroupLeadersOnlyTheirOwn() {
        val all = listOf(pfadi, woelfe)
        assertEquals(all, AssignmentRules.choices(all, admin))
        assertEquals(listOf(pfadi), AssignmentRules.choices(all, groupLeader))
        assertTrue(AssignmentRules.choices(all, leader).isEmpty())
    }

    @Test
    fun foreignAgeGroupsStayAndLockVisibility() {
        val program = program(ageGroups = listOf(pfadi, woelfe))
        val choices = AssignmentRules.choices(listOf(pfadi, woelfe), groupLeader)
        val fixed = AssignmentRules.fixed(program, choices)
        assertEquals(listOf(woelfe), fixed)
        assertFalse(AssignmentRules.mayChangeVisibility(fixed))
        assertTrue(AssignmentRules.mayChangeVisibility(AssignmentRules.fixed(program, listOf(pfadi, woelfe))))
        val request = AssignmentRules.request(setOf("s1"), fixed, Visibility.PARENTS)
        assertEquals(setOf("s1", "s2"), request.ageGroupIds.toSet())
        assertEquals("eltern", request.visibility)
    }

    @Test
    fun withoutAgeGroupOnlyInternIsValid() {
        assertTrue(AssignmentRules.valid(emptySet(), emptyList(), Visibility.INTERN))
        assertFalse(AssignmentRules.valid(emptySet(), emptyList(), Visibility.LEADERS))
        assertFalse(AssignmentRules.valid(emptySet(), emptyList(), Visibility.PARENTS))
        assertTrue(AssignmentRules.valid(setOf("s1"), emptyList(), Visibility.PARENTS))
        assertTrue("eine fremde Stufe bleibt", AssignmentRules.valid(emptySet(), listOf(woelfe), Visibility.PARENTS))
    }
}
