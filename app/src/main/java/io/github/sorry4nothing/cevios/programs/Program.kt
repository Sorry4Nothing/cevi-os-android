package io.github.sorry4nothing.cevios.programs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Die Abteilung rechnet in Schweizer Zeit, unabhängig von der Zone des Geräts. */
val SWISS_ZONE: ZoneId = ZoneId.of("Europe/Zurich")

@Serializable
data class ProgramAgeGroup(val id: String, val name: String)

/** Eigenes Kind in einer Stufe des Programms. `status` ist «ja», «nein» oder `null` für noch offen. */
@Serializable
data class ProgramChild(val id: String, @SerialName("vorname") val firstName: String, val status: String? = null)

/** Listeneintrag und Detail der API. Die Felder ab `attendanceOpen` liefert nur das Detail. */
@Serializable
data class Program(
    val id: String,
    @SerialName("titel") val title: String,
    val status: String,
    /** «intern», «leitende» oder «eltern»: wer das Programm ausser Admins und Stufenleitungen sieht. */
    @SerialName("sichtbarkeit") val visibility: String = "intern",
    @SerialName("ganztaegig") val allDay: Boolean,
    @SerialName("beginn") val start: String? = null,
    @SerialName("ende") val end: String? = null,
    @SerialName("tagVon") val dayFrom: String? = null,
    @SerialName("tagBis") val dayTo: String? = null,
    @SerialName("googleOrt") val googleLocation: String? = null,
    @SerialName("treffpunkt") val meetingPoint: String? = null,
    @SerialName("schlussOrt") val closingLocation: String? = null,
    @SerialName("schlussZeit") val closingTime: String? = null,
    @SerialName("mitnehmen") val bringAlong: String? = null,
    @SerialName("infoEltern") val parentInfo: String? = null,
    @SerialName("stufen") val ageGroups: List<ProgramAgeGroup> = emptyList(),
    /** Wahr, solange das Programm aktiv ist und nicht begonnen hat: nur dann lässt sich antworten. */
    @SerialName("teilnahmeOffen") val attendanceOpen: Boolean = false,
    @SerialName("kinder") val children: List<ProgramChild> = emptyList(),
    /** «ja», «nein» oder «offen»; `null`, wenn die Person keine Leitung dieses Programms ist. */
    @SerialName("eigeneAnmeldung") val ownRegistration: String? = null,
    /** Admin oder Leitung und Stufenleitung einer zugeordneten Stufe: darf Treffpunkt, Schluss, Mitnehmen und Info ändern. */
    @SerialName("darfErgaenzen") val mayEdit: Boolean = false,
) {
    val cancelled get() = status == "abgesagt"
    val inReview get() = status == "pruefen"
    val unassigned get() = ageGroups.isEmpty()

    /** Der Treffpunkt der Leitung, sonst der Ort aus dem Google-Kalender. */
    val location get() = meetingPoint?.takeIf { it.isNotBlank() } ?: googleLocation?.takeIf { it.isNotBlank() }
}

@Serializable
data class ProgramList(@SerialName("programme") val programs: List<Program>)

sealed interface Timing {
    /** Schweizer Kalendertage, `until` ist inklusive. */
    data class AllDay(val from: LocalDate, val until: LocalDate) : Timing

    data class Timed(val start: ZonedDateTime, val end: ZonedDateTime) : Timing
}

/** `null`, wenn die Angaben des Servers fehlen oder kein Datum sind. */
fun Program.time(): Timing? = try {
    if (allDay) {
        Timing.AllDay(LocalDate.parse(dayFrom ?: return null), LocalDate.parse(dayTo ?: return null))
    } else {
        Timing.Timed(
            Instant.parse(start ?: return null).atZone(SWISS_ZONE),
            Instant.parse(end ?: return null).atZone(SWISS_ZONE),
        )
    }
} catch (e: DateTimeParseException) {
    null
}
