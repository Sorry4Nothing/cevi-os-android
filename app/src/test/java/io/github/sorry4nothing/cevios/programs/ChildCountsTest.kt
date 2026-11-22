package io.github.sorry4nothing.cevios.programs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChildCountsTest {
    private fun child(status: String?) = ProgramChild("k", "Name", status)

    @Test
    fun countsAnswersAndTreatsAnythingElseAsOpen() {
        assertEquals(ChildCounts(0, 0, 0), emptyList<ProgramChild>().counts())
        assertEquals(ChildCounts(2, 1, 2), listOf("ja", "ja", "nein", null, "offen").map(::child).counts())
    }

    @Test
    fun weekdayAndDateReadAsInTheDesign() {
        val date = LocalDate.of(2026, 11, 28)
        assertEquals("Samstag", weekdayLong(date))
        assertEquals("Samstag, 28. November", "${weekdayLong(date)}, ${dayAndMonth(date)}")
    }
}
