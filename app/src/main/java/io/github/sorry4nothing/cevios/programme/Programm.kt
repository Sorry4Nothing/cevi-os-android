package io.github.sorry4nothing.cevios.programme

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Die Abteilung rechnet in Schweizer Zeit, unabhängig von der Zone des Geräts. */
val SCHWEIZ: ZoneId = ZoneId.of("Europe/Zurich")

@Serializable
data class ProgrammStufe(val id: String, val name: String)

/** Listeneintrag und Detail der API. Zusatzfelder des Details (Kinder, Anmeldung) kommen mit der Anmeldung. */
@Serializable
data class Programm(
    val id: String,
    val titel: String,
    val status: String,
    val ganztaegig: Boolean,
    val beginn: String? = null,
    val ende: String? = null,
    val tagVon: String? = null,
    val tagBis: String? = null,
    val googleOrt: String? = null,
    val treffpunkt: String? = null,
    val schlussOrt: String? = null,
    val schlussZeit: String? = null,
    val mitnehmen: String? = null,
    val infoEltern: String? = null,
    val stufen: List<ProgrammStufe> = emptyList(),
) {
    val abgesagt get() = status == "abgesagt"
    val pruefen get() = status == "pruefen"

    /** Der Treffpunkt der Leitung, sonst der Ort aus dem Google-Kalender. */
    val ort get() = treffpunkt?.takeIf { it.isNotBlank() } ?: googleOrt?.takeIf { it.isNotBlank() }
}

@Serializable
data class ProgrammListe(val programme: List<Programm>)

sealed interface Zeit {
    /** Schweizer Kalendertage, `bis` ist inklusive. */
    data class Ganztaegig(val von: LocalDate, val bis: LocalDate) : Zeit

    data class Termin(val beginn: ZonedDateTime, val ende: ZonedDateTime) : Zeit
}

/** `null`, wenn die Angaben des Servers fehlen oder kein Datum sind. */
fun Programm.zeit(): Zeit? = try {
    if (ganztaegig) {
        Zeit.Ganztaegig(LocalDate.parse(tagVon ?: return null), LocalDate.parse(tagBis ?: return null))
    } else {
        Zeit.Termin(
            Instant.parse(beginn ?: return null).atZone(SCHWEIZ),
            Instant.parse(ende ?: return null).atZone(SCHWEIZ),
        )
    }
} catch (e: DateTimeParseException) {
    null
}
