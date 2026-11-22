package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.ui.GroupChip
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChipsTest {
    private fun program(vararg groups: ProgramAgeGroup, tags: List<ProgramTag> = emptyList()) =
        Program(id = "p", title = "Programm", status = "aktiv", allDay = false, ageGroups = groups.toList(), tags = tags)

    @Test
    fun eachCategoryIsAChipAndOnlyAStageKeepsItsIdForTheLogo() {
        val chips = groupChips(program(tags = listOf(ProgramTag("t1", "Wölfe", "stufe"), ProgramTag("t2", "Mat", "team"))))
        assertEquals(listOf(GroupChip("Wölfe", "t1"), GroupChip("Mat", null)), chips)
    }

    @Test
    fun theOlderStateNamesStagesInsteadOfCategories() {
        val chips = groupChips(program(ProgramAgeGroup("a", "Stufe A"), ProgramAgeGroup("b", "Stufe B", logoUrl = "/x")))
        assertEquals(listOf(GroupChip("Stufe A", "a"), GroupChip("Stufe B", "b")), chips)
    }

    @Test
    fun withoutAnyGroupThereIsNoChipAtAll() {
        assertEquals(emptyList<GroupChip>(), groupChips(program()))
    }
}
