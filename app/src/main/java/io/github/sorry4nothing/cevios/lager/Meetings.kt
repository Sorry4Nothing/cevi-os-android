package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wer einen Höck sehen darf: die Lagerleitung (alle Rollen ausser Team) oder alle Mitglieder (`team`). */
enum class MeetingKind(val wire: String) {
    LEADERSHIP("lagerleitung"),
    TEAM("team"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: TEAM
    }
}

/**
 * Ein Höck in der Liste (`LagerHoeckListeneintrag`) oder im Detail. Das Team bekommt nur Datum, Zeit, Titel und Ort eines Team-Höcks
 * (`LagerHoeckTeam`): Die übrigen Felder fehlen dann und stehen auf ihrem Standard.
 */
@Serializable
data class Meeting(
    val id: String,
    val version: Long,
    @SerialName("art") val kindWire: String? = null,
    @SerialName("datum") val date: String,
    @SerialName("zeit") val time: String? = null,
    @SerialName("titel") val title: String,
    @SerialName("ort") val place: String? = null,
    @SerialName("gaeste") val guests: String? = null,
    @SerialName("protokollId") val minutesId: String? = null,
    @SerialName("anzahlTraktanden") val agendaCount: Int? = null,
    @SerialName("anzahlAnwesende") val presentCount: Int? = null,
    @SerialName("anwesende") val present: List<RessortPerson> = emptyList(),
    @SerialName("traktanden") val agenda: List<AgendaItem> = emptyList(),
) {
    val kind: MeetingKind get() = MeetingKind.from(kindWire)
}

@Serializable
data class MeetingList(@SerialName("hoecks") val meetings: List<Meeting>, @SerialName("naechster") val next: String? = null)

@Serializable
data class AgendaLink(@SerialName("titel") val title: String, val url: String)

enum class AgendaStatus(val wire: String) {
    OPEN("offen"),
    DONE("besprochen"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

/** Ein Traktandum (`LagerTraktandum`). `hoeckId` ist `null` bei Punkten für den nächsten Höck. */
@Serializable
data class AgendaItem(
    val id: String,
    val version: Long,
    @SerialName("hoeckId") val meetingId: String? = null,
    @SerialName("titel") val title: String,
    @SerialName("text") val text: String? = null,
    @SerialName("entscheid") val decision: String? = null,
    val links: List<AgendaLink> = emptyList(),
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("aufgabeId") val taskId: String? = null,
    @SerialName("status") val statusWire: String = "offen",
    @SerialName("verantwortliche") val responsible: List<RessortPerson> = emptyList(),
    /** Geplante Dauer in Minuten (1 bis 600); `null` ohne Angabe. */
    @SerialName("dauerMinuten") val minutes: Int? = null,
) {
    val status: AgendaStatus get() = AgendaStatus.from(statusWire)
}

@Serializable
data class NextMeetingAgenda(@SerialName("traktanden") val items: List<AgendaItem>)

@Serializable
data class TakeOverResult(@SerialName("uebernommen") val count: Int)

@Serializable
data class MemberPresence(
    val id: String,
    val name: String? = null,
    val ceviName: String? = null,
    @SerialName("rolle") val role: String,
    @SerialName("anwesend") val present: Boolean,
) {
    val display: String
        get() = when {
            !name.isNullOrBlank() && !ceviName.isNullOrBlank() -> "$name ($ceviName)"
            !name.isNullOrBlank() -> name
            else -> ceviName.orEmpty()
        }
}

@Serializable
data class RessortRoundCounts(
    @SerialName("gesamt") val total: Int,
    @SerialName("erledigt") val done: Int,
    @SerialName("offen") val open: Int,
    @SerialName("ueberfaellig") val overdue: Int,
)

/** Eine offene Aufgabe in der Ressort-Runde; der Server filtert sie nach dem Recht der Rolle. */
@Serializable
data class RoundTask(
    val id: String,
    @SerialName("titel") val title: String,
    @SerialName("status") val statusWire: String = "offen",
    @SerialName("verantwortlich") val responsible: RessortPerson? = null,
) {
    val status: TaskStatus get() = TaskStatus.from(statusWire)
}

@Serializable
data class RessortRound(
    val id: String,
    @SerialName("kuerzel") val abbreviation: String,
    val name: String,
    @SerialName("leitung") val leader: RessortPerson? = null,
    @SerialName("aufgaben") val tasks: RessortRoundCounts? = null,
    /** Höchstens 50 offene Aufgaben; `null`, wenn die Rolle keine Aufgaben lesen darf. */
    @SerialName("offeneAufgaben") val openTasks: List<RoundTask>? = null,
)

/** `GET …/hoecks/{id}/modus`: alles für die Sitzung. `ressortRunde` ist `null` ohne das Recht, Ressorts zu lesen. */
@Serializable
data class MeetingMode(
    @SerialName("hoeck") val meeting: Meeting,
    @SerialName("mitglieder") val members: List<MemberPresence>,
    @SerialName("ressortRunde") val ressortRound: List<RessortRound>? = null,
)

// ---- Protokolle und Beschlüsse ----

/** Eine Datei am Ablageort (SharePoint): Cevi OS kennt nur Name, Link, Typ und Grösse, nie den Inhalt. */
@Serializable
data class CampFile(
    val id: String,
    val version: Long = 1,
    @SerialName("anzeigename") val name: String,
    val link: String,
    @SerialName("typ") val type: String? = null,
    @SerialName("groesse") val size: Long? = null,
)

enum class MinutesKind(val wire: String) {
    SESSION("sitzung"),
    NOTE("notiz"),
    CONTACT("kontakt"),
    IDEA("idee"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: SESSION
    }
}

/** Ein Protokoll ist die Verknüpfung auf eine Datei: Cevi OS speichert keinen Protokolltext. */
@Serializable
data class Minutes(
    val id: String,
    val version: Long,
    @SerialName("art") val kindWire: String,
    @SerialName("titel") val title: String,
    @SerialName("datum") val date: String,
    @SerialName("hoeckId") val meetingId: String? = null,
    @SerialName("dateiId") val fileId: String? = null,
    @SerialName("datei") val file: CampFile? = null,
) {
    val kind: MinutesKind get() = MinutesKind.from(kindWire)
}

@Serializable
data class MinutesPage(@SerialName("protokolle") val minutes: List<Minutes>, @SerialName("naechster") val next: String? = null)

@Serializable
data class Decision(
    val id: String,
    val version: Long,
    @SerialName("text") val text: String,
    @SerialName("protokollId") val minutesId: String? = null,
    @SerialName("hoeckId") val meetingId: String? = null,
    @SerialName("erstelltAm") val createdAt: String? = null,
)

@Serializable
data class DecisionPage(@SerialName("beschluesse") val decisions: List<Decision>, @SerialName("naechster") val next: String? = null)

// ---- Erwähnungen ----

@Serializable
data class Mention(
    val id: String,
    @SerialName("quelleTyp") val sourceType: String,
    @SerialName("quelleId") val sourceId: String,
    @SerialName("titel") val title: String,
    @SerialName("erledigt") val done: Boolean = false,
    @SerialName("aufgabeId") val taskId: String? = null,
    @SerialName("erstelltAm") val createdAt: String,
)

@Serializable
data class MentionPage(@SerialName("erwaehnungen") val mentions: List<Mention>, @SerialName("naechster") val next: String? = null)

@Serializable
data class MentionTask(@SerialName("aufgabeId") val taskId: String)
