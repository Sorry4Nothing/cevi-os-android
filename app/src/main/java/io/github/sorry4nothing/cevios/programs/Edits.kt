package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.chat.Profile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Die Stufen der Abteilung (`GET /api/v1/stufen`); gelesen werden nur ID und Name. */
@Serializable
data class AgeGroupList(@SerialName("stufen") val ageGroups: List<ProgramAgeGroup>)

/** Der volle neue Stand der Zuordnung (`PUT /api/v1/programme/{id}/zuordnung`), kein Hinzufügen einzelner Einträge. */
@Serializable
data class AssignmentRequest(
    @SerialName("stufen") val ageGroupIds: List<String>,
    @SerialName("sichtbarkeit") val visibility: String,
)

/** Wer ein Programm ausser Admins und Stufenleitungen sieht. Die Reihenfolge ist die der Stufen im Dialog. */
enum class Visibility(val api: String) {
    INTERN("intern"),
    LEADERS("leitende"),
    PARENTS("eltern"),
}

/** Die fünf Felder, die Leitende ergänzen. Leer heisst: keine Angabe, dann gilt wieder der Ort aus Google. */
data class Extras(
    val meetingPoint: String = "",
    val closingLocation: String = "",
    /** `HH:MM`. */
    val closingTime: String = "",
    val bringAlong: String = "",
    val parentInfo: String = "",
) {
    companion object {
        fun of(program: Program) = Extras(
            meetingPoint = program.meetingPoint.orEmpty(),
            closingLocation = program.closingLocation.orEmpty(),
            // Der Server liefert «16:30:00», nimmt aber nur «16:30» an.
            closingTime = program.closingTime.orEmpty().take(TIME_LENGTH),
            bringAlong = program.bringAlong.orEmpty(),
            parentInfo = program.parentInfo.orEmpty(),
        )

        private const val TIME_LENGTH = 5
    }
}

/** Was die App prüft, bevor sie sendet. Die Grenzen sind die der API; der Server prüft trotzdem. */
object ExtrasRules {
    const val SHORT_MAX = 200
    const val LONG_MAX = 2000
    private val TIME = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")

    /** Leer (keine Angabe) oder `HH:MM`. */
    fun validTime(text: String) = text.isBlank() || TIME.matches(text.trim())

    /**
     * Nur die geänderten Felder, an den Rändern von Leerzeichen befreit, als Body für `PATCH /api/v1/programme/{id}`.
     * Ein leerer Text entfernt die Angabe. Leer heisst: nichts zu speichern.
     */
    fun changes(original: Extras, edited: Extras): Map<String, String> = buildMap {
        fun add(key: String, before: String, after: String) {
            if (before.trim() != after.trim()) put(key, after.trim())
        }
        add("treffpunkt", original.meetingPoint, edited.meetingPoint)
        add("schlussOrt", original.closingLocation, edited.closingLocation)
        add("schlussZeit", original.closingTime, edited.closingTime)
        add("mitnehmen", original.bringAlong, edited.bringAlong)
        add("infoEltern", original.parentInfo, edited.parentInfo)
    }
}

/**
 * Zuordnung von Stufen und Sichtbarkeit. Admins dürfen alles, Stufenleitungen nur Stufen, in denen sie selbst Stufenleitung
 * sind. Leitende und Eltern nie. Die Entscheidung trifft der Server; eine Aktion, die er ablehnt, zeigt die App als Fehler.
 */
object AssignmentRules {
    fun mayAssign(profile: Profile?) = profile != null && (profile.admin || profile.roles.any { it.type == "stufenleitung" })

    /** Die Stufen, die die Person zuordnen darf. */
    fun choices(all: List<ProgramAgeGroup>, profile: Profile): List<ProgramAgeGroup> =
        if (profile.admin) all else all.filter { profile.isGroupLeader(it.id) }

    /** Stufen, die das Programm schon hat und die die Person nicht ändern darf: Sie bleiben im neuen Stand. */
    fun fixed(program: Program, choices: List<ProgramAgeGroup>): List<ProgramAgeGroup> =
        program.ageGroups.filter { group -> choices.none { it.id == group.id } }

    /** Die Sichtbarkeit ändern darf nur, wem alle bisherigen und neuen Stufen gehören: Es darf keine fremde Stufe bleiben. */
    fun mayChangeVisibility(fixed: List<ProgramAgeGroup>) = fixed.isEmpty()

    /** Ohne Stufe ist nur «intern» erlaubt. */
    fun valid(selected: Set<String>, fixed: List<ProgramAgeGroup>, visibility: Visibility) =
        selected.isNotEmpty() || fixed.isNotEmpty() || visibility == Visibility.INTERN

    fun request(selected: Set<String>, fixed: List<ProgramAgeGroup>, visibility: Visibility) =
        AssignmentRequest((selected + fixed.map { it.id }).toList(), visibility.api)
}
