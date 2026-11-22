package io.github.sorry4nothing.cevios.programs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.ZoneOffset

/** Beginn, Ende und Art des Termins in der Form, die der Kalender des Geräts erwartet. */
data class CalendarEntry(val title: String, val place: String?, val beginMillis: Long, val endMillis: Long, val allDay: Boolean)

/**
 * Der Termin als Eintrag für die Kalender-App des Geräts, `null` ohne lesbare Zeit. Ganztägige Termine beginnen und enden
 * um Mitternacht UTC (so verlangt es der Kalender des Systems), Termine mit Uhrzeit sind Zeitpunkte.
 */
fun Program.calendarEntry(): CalendarEntry? = when (val timing = time()) {
    null -> null
    is Timing.AllDay -> CalendarEntry(
        title, calendarLocation,
        timing.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        timing.until.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true,
    )
    is Timing.Timed -> CalendarEntry(title, calendarLocation, timing.start.toInstant().toEpochMilli(), timing.end.toInstant().toEpochMilli(), allDay = false)
}

/**
 * Öffnet den Eintrag in der Kalender-App des Geräts, ohne Berechtigung: Die Person speichert ihn dort selbst. `false`, wenn der
 * Termin keine lesbare Zeit hat oder keine Kalender-App da ist.
 */
fun addToCalendar(context: Context, program: Program): Boolean {
    val entry = program.calendarEntry() ?: return false
    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, entry.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.endMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, entry.allDay)
        .apply { entry.place?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) } }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
