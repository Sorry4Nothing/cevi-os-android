package io.github.sorry4nothing.cevios.programme

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Programme eines Kalendertages (Schweizer Zeit). `datum` ist `null`, wenn das Programm keine lesbare Zeit hat. */
data class Tag(val datum: LocalDate?, val programme: List<Programm>)

/** Der Tag, an dem das Programm beginnt: bei ganztägigen `tagVon`, sonst der Beginn in Schweizer Zeit. */
fun Programm.beginnTag(): LocalDate? = when (val zeit = zeit()) {
    is Zeit.Ganztaegig -> zeit.von
    is Zeit.Termin -> zeit.beginn.toLocalDate()
    null -> null
}

/**
 * Gruppiert die Liste nach Beginn-Tag. Der Server sortiert nach Beginn, deshalb liegen gleiche Tage hintereinander;
 * ein Datum erscheint in der Zeitleiste nur einmal, auch bei mehreren Programmen am selben Tag.
 */
fun gruppiereNachTag(programme: List<Programm>): List<Tag> {
    val tage = mutableListOf<Tag>()
    for (programm in programme) {
        val datum = programm.beginnTag()
        val letzter = tage.lastOrNull()
        if (letzter != null && letzter.datum == datum) {
            tage[tage.lastIndex] = letzter.copy(programme = letzter.programme + programm)
        } else {
            tage += Tag(datum, listOf(programm))
        }
    }
    return tage
}

/** «SA» für Samstag: die ersten zwei Buchstaben des deutschen Kürzels in Grossbuchstaben. */
fun wochentagKurz(datum: LocalDate): String =
    datum.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2).uppercase(Locale.GERMAN)

/** «14. November» */
fun tagUndMonat(datum: LocalDate): String =
    "${datum.dayOfMonth}. ${datum.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)}"
