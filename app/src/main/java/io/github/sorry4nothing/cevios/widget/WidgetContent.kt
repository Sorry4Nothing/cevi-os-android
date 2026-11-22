package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.Timing
import io.github.sorry4nothing.cevios.programs.time
import java.time.ZonedDateTime

/** Die nächsten Programme (in der Reihenfolge des Servers), die weder abgesagt noch vorbei sind; `now` in Schweizer Zeit. */
fun upcoming(programs: List<Program>, now: ZonedDateTime, limit: Int = 2): List<Program> =
    programs.filter {
        !it.cancelled && when (val time = it.time()) {
            is Timing.AllDay -> !time.until.isBefore(now.toLocalDate())
            is Timing.Timed -> time.end.isAfter(now)
            null -> false
        }
    }.take(limit)
