package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SWISS = Locale.forLanguageTag("de-CH")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d. MMMM", SWISS)
private val FULL = DateTimeFormatter.ofPattern("d. MMMM yyyy", SWISS)

/**
 * Der Zeitraum eines Lagers in Worten: «17. bis 24. Juli 2027», «28. Juli bis 3. August 2027», über den Jahreswechsel mit beiden
 * Jahren. Ist ein Datum unlesbar, steht es unverändert da.
 */
fun campDates(from: String, until: String): String {
    val start = runCatching { LocalDate.parse(from) }.getOrNull()
    val end = runCatching { LocalDate.parse(until) }.getOrNull()
    if (start == null || end == null) return "$from bis $until"
    return when {
        start == end -> FULL.format(start)
        start.year != end.year -> "${FULL.format(start)} bis ${FULL.format(end)}"
        start.month != end.month -> "${DAY_MONTH.format(start)} bis ${FULL.format(end)}"
        else -> "${start.dayOfMonth}. bis ${FULL.format(end)}"
    }
}

/** Eine Zeile des Konfliktdialogs: Feld in Worten, deine und die aktuelle Fassung; `differs` markiert den Unterschied. */
data class ConflictRow(val label: String, val mine: String?, val current: String?, val differs: Boolean)

/** Felder, die nie als Zeile erscheinen: Sie steuern den Abgleich, die Person sieht sie nicht. */
private val HIDDEN_FIELDS = setOf("id", "version")

private fun JsonElement?.display(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content
    else -> toString()
}

/**
 * Stellt «Deine Fassung» und «Aktueller Stand» einer Änderung mit Konflikt gegenüber. Gezeigt werden die Felder, die der Server
 * als abweichend meldet (`abweichend`); meldet er keine, alle Felder der Nutzlast. Namen in Worten kommen aus `fieldLabels`, sonst
 * steht der Feldname da.
 */
fun conflictRows(change: QueuedChange): List<ConflictRow> {
    val keys = (change.differing.ifEmpty { change.payload.keys.toList() }).filter { it !in HIDDEN_FIELDS }
    return keys.map { key ->
        val mine = change.payload[key].display()
        val current = change.current?.get(key).display()
        ConflictRow(change.fieldLabels[key] ?: key, mine, current, differs = key in change.differing || mine != current)
    }
}
