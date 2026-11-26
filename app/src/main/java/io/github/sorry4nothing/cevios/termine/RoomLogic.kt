package io.github.sorry4nothing.cevios.termine

import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/** Der Zweck: eine Zeile, höchstens so viele Zeichen (Vertrag `RaumAnfrageNeu`). */
const val ROOM_PURPOSE_MAX = 200

private val ROOM_TIME = DateTimeFormatter.ofPattern("HH:mm")
private val SHORT_DAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val LONG_DAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

/** Die Eingabe des Formulars «Raum anfragen», wie die Person sie macht. */
data class RoomDraft(
    val roomId: String? = null,
    val day: LocalDate? = null,
    val from: String = "",
    val to: String = "",
    val purpose: String = "",
)

/** Was an der Anfrage noch nicht stimmt, bevor sie gesendet wird. */
enum class RoomProblem { ROOM, DAY, FROM, TO, ORDER, PAST, PURPOSE }

/** Alle Probleme der Eingabe; leer heisst: Die Anfrage lässt sich senden. Beginn und Ende liegen am selben Tag. */
fun roomProblems(draft: RoomDraft, now: ZonedDateTime = ZonedDateTime.now(SWISS_ZONE)): Set<RoomProblem> = buildSet {
    if (draft.roomId == null) add(RoomProblem.ROOM)
    if (draft.purpose.isBlank() || draft.purpose.trim().length > ROOM_PURPOSE_MAX || '\n' in draft.purpose) add(RoomProblem.PURPOSE)
    val day = draft.day
    if (day == null) add(RoomProblem.DAY)
    val from = parseTime(draft.from)
    val to = parseTime(draft.to)
    if (from == null) add(RoomProblem.FROM)
    if (to == null) add(RoomProblem.TO)
    if (day == null || from == null || to == null) return@buildSet
    if (!to.isAfter(from)) add(RoomProblem.ORDER)
    if (!day.atTime(from).atZone(SWISS_ZONE).isAfter(now)) add(RoomProblem.PAST)
}

/** Die Anfrage für den Versand, oder `null`, wenn die Eingabe Probleme hat. Zeiten gehen mit der Zone der Abteilung (mit Offset). */
fun roomRequestBody(draft: RoomDraft, now: ZonedDateTime = ZonedDateTime.now(SWISS_ZONE)): RoomRequestBody? {
    if (roomProblems(draft, now).isNotEmpty()) return null
    val day = draft.day ?: return null
    val from = parseTime(draft.from) ?: return null
    val to = parseTime(draft.to) ?: return null
    return RoomRequestBody(
        day.atTime(from).atZone(SWISS_ZONE).toOffsetDateTime().toString(),
        day.atTime(to).atZone(SWISS_ZONE).toOffsetDateTime().toString(),
        draft.purpose.trim(),
    )
}

/** Der Zweck als eine Zeile: Zeilenumbrüche (auch aus dem Einfügen) werden zu Leerzeichen. */
fun oneLine(text: String): String = text.replace(Regex("""\s*[\r\n]+\s*"""), " ")

/** Die Zustände einer Anfrage, wie die App sie zeigt. `storniert` zeigt sie nicht (die Person hat die Anfrage selbst zurückgezogen). */
enum class RoomStatus { OPEN, CONFIRMED, REJECTED, CANCELLED, UNKNOWN }

fun roomStatus(status: String): RoomStatus = when (status) {
    "angefragt" -> RoomStatus.OPEN
    "bestaetigt" -> RoomStatus.CONFIRMED
    "abgelehnt" -> RoomStatus.REJECTED
    "storniert" -> RoomStatus.CANCELLED
    else -> RoomStatus.UNKNOWN
}

/** Zurückziehen oder absagen geht nur für offene und bestätigte Anfragen, solange ihre Zeit nicht vorbei ist (sonst 409). */
fun canCancelRequest(request: RoomRequest, now: Instant = Instant.now()): Boolean {
    val status = roomStatus(request.status)
    if (status != RoomStatus.OPEN && status != RoomStatus.CONFIRMED) return false
    return runCatching { Instant.parse(request.end).isAfter(now) }.getOrDefault(false)
}

fun dayNameShort(date: LocalDate): String = SHORT_DAYS[date.dayOfWeek.value - 1]

fun dayNameLong(day: DayOfWeek): String = LONG_DAYS[day.value - 1]

/** «Sa 12.12., 14:00–18:00»; über mehrere Tage «Sa 12.12., 14:00 – So 13.12., 10:00». Leer ohne lesbare Zeit. */
fun roomWhen(startIso: String, endIso: String): String = roomWhenText(startIso, endIso, spoken = false)

/** Wie [roomWhen], aber zum Vorlesen: «Samstag, 12. Dezember, 14:00 bis 18:00». TalkBack liest «Sa» und «12.12.» sonst Zeichen für Zeichen. */
fun roomWhenSpoken(startIso: String, endIso: String): String = roomWhenText(startIso, endIso, spoken = true)

private fun roomWhenText(startIso: String, endIso: String, spoken: Boolean): String = try {
    val start = Instant.parse(startIso).atZone(SWISS_ZONE)
    val end = Instant.parse(endIso).atZone(SWISS_ZONE)
    fun day(zoned: ZonedDateTime): String {
        val date = zoned.toLocalDate()
        return if (spoken) "${dayNameLong(date.dayOfWeek)}, ${date.dayOfMonth}. ${date.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)}"
        else "${dayNameShort(date)} ${zoned.dayOfMonth}.${zoned.monthValue}."
    }
    if (start.toLocalDate() == end.toLocalDate()) {
        "${day(start)}, ${ROOM_TIME.format(start)}${if (spoken) " bis " else "–"}${ROOM_TIME.format(end)}"
    } else {
        "${day(start)}, ${ROOM_TIME.format(start)}${if (spoken) " bis " else " – "}${day(end)}, ${ROOM_TIME.format(end)}"
    }
} catch (e: DateTimeParseException) {
    ""
}

/** «Samstag, 12.12.» für den Satz bei einer Überschneidung. */
fun roomDayLong(date: LocalDate): String = "${dayNameLong(date.dayOfWeek)}, ${date.dayOfMonth}.${date.monthValue}."
