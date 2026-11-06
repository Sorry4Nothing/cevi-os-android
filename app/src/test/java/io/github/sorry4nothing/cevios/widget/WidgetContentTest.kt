package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class WidgetContentTest {
    private val now = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, SWISS_ZONE)

    private fun timed(id: String, start: String, end: String, status: String = "aktiv") =
        Program(id = id, title = id, status = status, allDay = false, start = start, end = end)

    private fun allDay(id: String, from: String, until: String) =
        Program(id = id, title = id, status = "aktiv", allDay = true, dayFrom = from, dayTo = until)

    @Test
    fun skipsPastAndCancelledAndKeepsServerOrder() {
        val programs = listOf(
            timed("vorbei", "2026-11-14T08:00:00Z", "2026-11-14T09:00:00Z"),
            timed("abgesagt", "2026-11-14T13:00:00Z", "2026-11-14T15:00:00Z", status = "abgesagt"),
            timed("laeuft", "2026-11-14T10:00:00Z", "2026-11-14T12:00:00Z"),
            allDay("lager", "2026-11-20", "2026-11-22"),
            timed("spaeter", "2026-11-28T13:00:00Z", "2026-11-28T15:00:00Z"),
        )
        assertEquals(listOf("laeuft", "lager"), upcoming(programs, now).map { it.id })
        assertEquals(listOf("laeuft", "lager", "spaeter"), upcoming(programs, now, limit = 5).map { it.id })
    }

    @Test
    fun allDayProgramEndingTodayStillCounts() {
        assertEquals(listOf("heute"), upcoming(listOf(allDay("heute", "2026-11-13", "2026-11-14")), now).map { it.id })
        assertEquals(emptyList<Program>(), upcoming(listOf(allDay("gestern", "2026-11-12", "2026-11-13")), now))
    }

    @Test
    fun dateLineShowsWeekdayDateAndSwissTime() {
        val line = dateLine(timed("a", "2026-11-14T13:00:00Z", "2026-11-14T15:00:00Z"), "ganztägig")
        assertEquals("SA 14.11. · 14:00", line)
    }

    @Test
    fun dateLineUsesLabelForAllDayAndIsEmptyWithoutTime() {
        assertEquals("SA 14.11. · ganztägig", dateLine(allDay("a", "2026-11-14", "2026-11-14"), "ganztägig"))
        assertEquals("", dateLine(Program(id = "x", title = "x", status = "aktiv", allDay = false), "ganztägig"))
    }
}
