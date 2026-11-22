package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.ui.GroupChip
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChipsTest {
    private fun program(vararg groups: ProgramAgeGroup) =
        Program(id = "p", title = "Programm", status = "aktiv", allDay = false, ageGroups = groups.toList())

    @Test
    fun eachStageKeepsItsIdForTheLogo() {
        val chips = groupChips(program(ProgramAgeGroup("a", "Stufe A"), ProgramAgeGroup("b", "Stufe B", logoUrl = "/x")), "Alle Stufen")
        assertEquals(listOf(GroupChip("Stufe A", "a"), GroupChip("Stufe B", "b")), chips)
    }

    @Test
    fun aProgramWithoutStageIsForAllAndHasNoLogo() {
        assertEquals(listOf(GroupChip("Alle Stufen", null)), groupChips(program(), "Alle Stufen"))
    }
}
