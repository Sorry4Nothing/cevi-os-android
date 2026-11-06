package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.Timing
import io.github.sorry4nothing.cevios.programs.startDay
import io.github.sorry4nothing.cevios.programs.time
import io.github.sorry4nothing.cevios.programs.weekdayShort
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** Die nächsten Programme (in der Reihenfolge des Servers), die weder abgesagt noch vorbei sind; `now` in Schweizer Zeit. */
fun upcoming(programs: List<Program>, now: ZonedDateTime, limit: Int = 2): List<Program> =
    programs.filter {
        !it.cancelled && when (val time = it.time()) {
            is Timing.AllDay -> !time.until.isBefore(now.toLocalDate())
            is Timing.Timed -> time.end.isAfter(now)
            null -> false
        }
    }.take(limit)

/** «SA 14.11. · 14:00», bei ganztägigen Programmen statt der Zeit `allDayLabel`; leer ohne lesbare Zeit. */
fun dateLine(program: Program, allDayLabel: String): String {
    val date = program.startDay() ?: return ""
    val time = when (val timing = program.time()) {
        is Timing.AllDay -> allDayLabel
        is Timing.Timed -> TIME_FORMAT.format(timing.start)
        null -> return ""
    }
    return "${weekdayShort(date)} ${date.dayOfMonth}.${date.monthValue}. · $time"
}
