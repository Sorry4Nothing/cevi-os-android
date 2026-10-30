package io.github.sorry4nothing.cevios.attendance

import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.programs.Program
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ein Anlass der Stufe. Der Server liefert die vergangenen aufsteigend, den kommenden zuletzt. */
@Serializable
data class Occasion(
    /** Gleich der ID des Programms. */
    val id: String,
    /** Wahr, wenn der Anlass begonnen hat. */
    @SerialName("vergangen") val past: Boolean,
    /** Wahr, solange der Anlass begonnen hat und höchstens 14 Tage her ist: Nur dann nimmt der Server einen Stand an. */
    @SerialName("erfassbar") val recordable: Boolean,
)

/**
 * Ein Kind (`vorname`, `nachname`) oder eine Leitungsperson (`name`, `rolle`). `marks` hat einen Eintrag pro Anlass in der
 * Reihenfolge von `anlaesse`: «da», «nicht_da» oder `null` für nicht erfasst.
 */
@Serializable
data class AttendancePerson(
    val id: String,
    @SerialName("vorname") val firstName: String? = null,
    @SerialName("nachname") val lastName: String? = null,
    val name: String? = null,
    @SerialName("rolle") val role: String? = null,
    @SerialName("anwesenheit") val marks: List<String?>,
) {
    val displayName get() = name ?: "${firstName.orEmpty()} ${lastName.orEmpty()}".trim()
    val isGroupLeader get() = role == "stufenleitung"
}

/** `GET /api/v1/stufen/{id}/anwesenheit`: je Person der Stand pro Anlass. Die App zeigt davon die Spalte eines Programms. */
@Serializable
data class AttendanceOverview(
    @SerialName("anlaesse") val occasions: List<Occasion>,
    @SerialName("kinder") val children: List<AttendancePerson>,
    @SerialName("leitende") val leaders: List<AttendancePerson>,
) {
    /** Die Position des Programms in `anlaesse` (und damit in `marks`); -1, wenn die Übersicht es nicht enthält. */
    fun indexOf(programId: String) = occasions.indexOfFirst { it.id == programId }
}

object AttendanceRules {
    /**
     * Die Stufe, in der die Person bei diesem Programm erfasst: die erste Stufe des Programms, die sie als Leitung oder
     * Stufenleitung führt, bei Admins sonst die erste des Programms. `null`, wenn sie nichts erfassen darf; entschieden wird
     * trotzdem beim Server (403).
     */
    fun ageGroupFor(program: Program, profile: Profile?): String? {
        if (profile == null) return null
        val led = program.ageGroups.firstOrNull { group ->
            profile.roles.any { it.ageGroupId == group.id && (it.type == "leitung" || it.type == "stufenleitung") }
        }
        return (led ?: program.ageGroups.firstOrNull()?.takeIf { profile.admin })?.id
    }
}
