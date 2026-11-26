package io.github.sorry4nothing.cevios.termine

import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

private val DEADLINE_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy, HH:mm")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy")

/** «12.11.2027, 18:00» in Schweizer Zeit; der Text selbst, wenn der Server etwas anderes liefert. */
fun eventDeadlineText(iso: String): String = try {
    DEADLINE_FORMAT.format(Instant.parse(iso).atZone(SWISS_ZONE))
} catch (e: DateTimeParseException) {
    iso
}

/** «17. bis 24. Juli 2027», «Samstag, 28. November», mit Uhrzeit bei Terminen, die eine haben. Leer ohne lesbare Zeit. */
fun eventDateLine(form: EventForm): String {
    fun day(date: LocalDate) = "${date.dayOfMonth}. ${date.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)}"
    return try {
        if (form.allDay) {
            val from = LocalDate.parse(form.dayFrom ?: return "")
            val until = LocalDate.parse(form.dayTo ?: form.dayFrom)
            if (from == until) "${day(from)} ${from.year}" else "${day(from)} bis ${day(until)} ${until.year}"
        } else {
            val start = Instant.parse(form.start ?: return "").atZone(SWISS_ZONE)
            val end = Instant.parse(form.end ?: return "").atZone(SWISS_ZONE)
            val time = DateTimeFormatter.ofPattern("HH:mm")
            val date = start.toLocalDate()
            "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)}, ${day(date)}, ${time.format(start)}–${time.format(end)}"
        }
    } catch (e: DateTimeParseException) {
        ""
    }
}

/** «28.11.2027» für ein Datum im Format `JJJJ-MM-TT`; leer ohne Datum. */
fun dayText(iso: String?): String = iso?.let { runCatching { DAY_FORMAT.format(LocalDate.parse(it)) }.getOrNull() }.orEmpty()

/** Die Antwort eines Feldes als Text, `null` ohne Antwort (nicht «false»: Ja/Nein ohne Antwort ist offen). */
fun JsonElement.asAnswerText(): String? = (this as? JsonPrimitive)?.contentOrNull

/** Ein Ja/Nein-Feld: `true`, `false` oder `null` für noch nicht beantwortet. */
fun JsonElement.asAnswerBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

/**
 * Die Schlüssel der Pflichtfelder, die noch keine Antwort haben. Text zählt leer als fehlend, Ja/Nein ohne Wahl und eine
 * Auswahl ohne Option ebenso. Der Server prüft dasselbe noch einmal; hier steht der Fehler beim Feld, bevor etwas gesendet wird.
 */
fun missingRequired(fields: List<EventField>, answers: Map<String, JsonElement>): Set<String> =
    fields.filter { it.required }.filter { field ->
        val answer = answers[field.key]
        when (field.type) {
            "ja_nein" -> answer?.asAnswerBoolean() == null
            else -> answer?.asAnswerText()?.isBlank() != false
        }
    }.map { it.key }.toSet()

/** Antworten für den Versand: Leere Texte entfallen (ein Pflichtfeld ist dann schon als fehlend gemeldet, ein freiwilliges bleibt weg). */
fun cleanAnswers(fields: List<EventField>, answers: Map<String, JsonElement>): Map<String, JsonElement> {
    val known = fields.associateBy { it.key }
    return answers.filter { (key, value) ->
        val field = known[key] ?: return@filter false
        if (field.type == "ja_nein") value.asAnswerBoolean() != null else value.asAnswerText()?.isNotBlank() == true
    }.mapValues { (key, value) ->
        if (known.getValue(key).type == "text") JsonPrimitive(value.asAnswerText().orEmpty().trim()) else value
    }
}

/** Die Eingabe des Formulars «Termin vorschlagen», wie die Person sie macht. */
data class ProposalDraft(
    val title: String = "",
    val allDay: Boolean = false,
    val day: LocalDate? = null,
    /** Letzter Tag bei ganztägigen Terminen; `null` heisst: derselbe Tag. */
    val lastDay: LocalDate? = null,
    val from: String = "",
    val to: String = "",
    val place: String = "",
    val description: String = "",
    val tagIds: Set<String> = emptySet(),
)

/** Was am Vorschlag noch nicht stimmt, bevor er gesendet wird. */
enum class ProposalProblem { TITLE, DAY, FROM, TO, ORDER, PAST, TAGS }

private val OFFSET_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

/** Tag und Zeit in der Zone der Abteilung als RFC 3339 mit Sekunden («2026-12-01T14:00:00+01:00»); `OffsetDateTime.toString()` lässt sie bei vollen Minuten weg. */
fun offsetText(day: LocalDate, time: LocalTime): String = OFFSET_FORMAT.format(day.atTime(time).atZone(SWISS_ZONE))

/** «14:00», «9:30» oder «14.00» als Zeit; `null`, wenn es keine ist. */
fun parseTime(text: String): LocalTime? {
    val match = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").matchEntire(text) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}

/** Alle Probleme der Eingabe; leer heisst: Der Vorschlag lässt sich senden. */
fun proposalProblems(draft: ProposalDraft, today: LocalDate, now: ZonedDateTime = ZonedDateTime.now(SWISS_ZONE)): Set<ProposalProblem> = buildSet {
    if (draft.title.isBlank()) add(ProposalProblem.TITLE)
    if (draft.tagIds.isEmpty()) add(ProposalProblem.TAGS)
    val day = draft.day
    if (day == null) {
        add(ProposalProblem.DAY)
        return@buildSet
    }
    if (draft.allDay) {
        val last = draft.lastDay ?: day
        if (day.isBefore(today)) add(ProposalProblem.PAST)
        if (last.isBefore(day)) add(ProposalProblem.ORDER)
    } else {
        val from = parseTime(draft.from)
        val to = parseTime(draft.to)
        if (from == null) add(ProposalProblem.FROM)
        if (to == null) add(ProposalProblem.TO)
        if (from != null && to != null) {
            if (!to.isAfter(from)) add(ProposalProblem.ORDER)
            if (day.atTime(from).atZone(SWISS_ZONE).isBefore(now)) add(ProposalProblem.PAST)
        }
    }
}

/** Der Vorschlag für den Versand, oder `null`, wenn die Eingabe Probleme hat. Zeiten gehen mit der Zone der Abteilung (mit Offset). */
fun proposalRequest(draft: ProposalDraft, today: LocalDate, now: ZonedDateTime = ZonedDateTime.now(SWISS_ZONE)): ProposalRequest? {
    if (proposalProblems(draft, today, now).isNotEmpty()) return null
    val day = draft.day ?: return null
    val place = draft.place.trim().ifEmpty { null }
    val description = draft.description.trim().ifEmpty { null }
    val tags = draft.tagIds.toList()
    return if (draft.allDay) {
        ProposalRequest(draft.title.trim(), true, dayFrom = day.toString(), dayTo = (draft.lastDay ?: day).toString(), place = place, description = description, tagIds = tags)
    } else {
        val from = parseTime(draft.from) ?: return null
        val to = parseTime(draft.to) ?: return null
        ProposalRequest(
            draft.title.trim(), false,
            start = offsetText(day, from),
            end = offsetText(day, to),
            place = place, description = description, tagIds = tags,
        )
    }
}
