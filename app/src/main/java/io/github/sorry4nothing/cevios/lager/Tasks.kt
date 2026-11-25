package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Status einer Aufgabe. `NEXT_MEETING` («Auf nächsten Höck») setzt der Schnitt A2; A1 zeigt ihn nur an. */
enum class TaskStatus(val wire: String) {
    OPEN("offen"),
    RUNNING("laeuft"),
    WAITING("wartet"),
    DONE("erledigt"),
    NEXT_MEETING("naechster_hoeck"),
    ;

    companion object {
        /** Die vier Status, die man wählen kann (Entwurf L7, L9). */
        val CHOOSABLE = listOf(OPEN, RUNNING, WAITING, DONE)

        /** Ein Status, den die App nicht kennt, gilt als offen. */
        fun from(wire: String?): TaskStatus = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

/** Eine Person in einer Aufgabe (Verantwortliche, Unterstützung, Autor). */
@Serializable
data class TaskPerson(
    val id: String,
    val name: String? = null,
    val ceviName: String? = null,
    /** Nur bei `verantwortlich`: wahr, wenn die Leitung des Ressorts einspringt, weil niemand eingetragen ist. */
    @SerialName("automatisch") val automatic: Boolean = false,
    /** Pfad des Profilbilds (`LagerBildUrl`), `null` ohne Bild: dann zeigt der Avatar die Initiale. */
    @SerialName("bildUrl") val imageUrl: String? = null,
) {
    /** «Name (Cevi-Name)» wie im Entwurf; fehlt einer von beiden, steht der andere allein da. */
    val display: String
        get() = when {
            !name.isNullOrBlank() && !ceviName.isNullOrBlank() -> "$name ($ceviName)"
            !name.isNullOrBlank() -> name
            !ceviName.isNullOrBlank() -> ceviName
            else -> ""
        }
}

@Serializable
data class TaskRessortRef(val id: String, @SerialName("kuerzel") val abbreviation: String, val name: String)

/**
 * Eine Aufgabe (`LagerAufgabe`). `pending` und `queuedId` sind nur lokal: Sie sagen, dass eine Änderung in der Warteschlange
 * wartet, und tragen bei einer Aufgabe, die noch nicht angelegt ist, die `clientOpId`.
 */
@Serializable
data class CampTask(
    val id: String,
    val version: Long,
    @SerialName("titel") val title: String,
    @SerialName("ressortId") val ressortId: String? = null,
    @SerialName("teil") val part: String? = null,
    @SerialName("verantwortlichPersonId") val assigneeId: String? = null,
    @SerialName("status") val statusWire: String = "offen",
    @SerialName("notiz") val note: String? = null,
    @SerialName("frist") val due: String? = null,
    val fristMeilensteinId: String? = null,
    val fristVersatzTage: Int? = null,
    @SerialName("stern") val starred: Boolean = false,
    @SerialName("laufend") val ongoing: Boolean = false,
    @SerialName("spaeterAb") val snoozedUntil: String? = null,
    @SerialName("wiederholung") val repeat: String? = null,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("erledigtAm") val doneAt: String? = null,
    @SerialName("verantwortlich") val assignee: TaskPerson? = null,
    val ressort: TaskRessortRef? = null,
    @SerialName("unterstuetzung") val supporters: List<TaskPerson> = emptyList(),
    @Transient val pending: Boolean = false,
    @Transient val queuedId: String? = null,
) {
    val status: TaskStatus get() = TaskStatus.from(statusWire)
    val done: Boolean get() = status == TaskStatus.DONE

    /** Ob die Person die Aufgabe trägt: als Verantwortliche (auch die einspringende Leitung des Ressorts) oder in der Unterstützung. */
    fun involves(personId: String): Boolean = assignee?.id == personId || assigneeId == personId || supporters.any { it.id == personId }
}

@Serializable
data class TaskPage(@SerialName("aufgaben") val tasks: List<CampTask>, @SerialName("naechster") val next: String? = null)

@Serializable
data class Checkpoint(
    val id: String,
    val version: Long,
    val text: String,
    @SerialName("erledigt") val done: Boolean = false,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("erledigtVon") val doneBy: TaskPerson? = null,
)

@Serializable
data class TaskLink(val id: String, val version: Long, @SerialName("titel") val title: String, val url: String)

@Serializable
data class TaskDetails(
    @SerialName("checkliste") val checklist: List<Checkpoint> = emptyList(),
    val links: List<TaskLink> = emptyList(),
    @SerialName("stichworte") val keywords: List<String> = emptyList(),
)

@Serializable
data class HistoryEntry(
    val seq: Long,
    @SerialName("objekttyp") val type: String,
    val id: String,
    @SerialName("art") val kind: String,
    val version: Long,
    @SerialName("zeitpunkt") val time: String,
)

/** Verlauf einer Aufgabe: wer sie angelegt und zuletzt geändert hat, dazu die Einträge des Feeds (nur Art und Zeit, nie Inhalte). */
@Serializable
data class TaskHistory(
    @SerialName("erstelltAm") val createdAt: String,
    @SerialName("erstelltVon") val createdBy: TaskPerson? = null,
    @SerialName("geaendertAm") val changedAt: String,
    @SerialName("geaendertVon") val changedBy: TaskPerson? = null,
    @SerialName("eintraege") val entries: List<HistoryEntry> = emptyList(),
)

@Serializable
data class DuplicateTask(val id: String, val version: Long, @SerialName("titel") val title: String, @SerialName("status") val statusWire: String = "offen")

@Serializable
data class DuplicateGroup(@SerialName("ressortId") val ressortId: String? = null, @SerialName("titel") val title: String, @SerialName("aufgaben") val tasks: List<DuplicateTask>)

@Serializable
data class Duplicates(@SerialName("gruppen") val groups: List<DuplicateGroup> = emptyList())

@Serializable
data class QuickText(val text: String)

/** Wie sicher der Server eine Angabe erkannt hat: `gefunden` (genau eine), `mehrdeutig` (mehrere) oder `unbekannt` (keine). */
enum class Recognition(val wire: String) {
    FOUND("gefunden"),
    AMBIGUOUS("mehrdeutig"),
    UNKNOWN("unbekannt"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

@Serializable
data class PersonCandidate(val id: String, val name: String, val ceviName: String? = null) {
    val display: String get() = if (ceviName.isNullOrBlank()) name else "$name ($ceviName)"
}

@Serializable
data class RessortCandidate(val id: String, @SerialName("kuerzel") val abbreviation: String, val name: String)

@Serializable
data class QuickPerson(
    @SerialName("status") val statusWire: String,
    val text: String,
    @SerialName("kandidaten") val candidates: List<PersonCandidate> = emptyList(),
) {
    val status: Recognition get() = Recognition.from(statusWire)
}

@Serializable
data class QuickRessort(
    @SerialName("status") val statusWire: String,
    val text: String,
    @SerialName("kandidaten") val candidates: List<RessortCandidate> = emptyList(),
) {
    val status: Recognition get() = Recognition.from(statusWire)
}

@Serializable
data class QuickDue(@SerialName("datum") val date: String, val text: String)

@Serializable
data class QuickSuggestion(
    @SerialName("zeile") val line: String,
    @SerialName("titel") val title: String,
    val person: QuickPerson? = null,
    val ressort: QuickRessort? = null,
    @SerialName("frist") val due: QuickDue? = null,
    @SerialName("hinweise") val hints: List<String> = emptyList(),
)

@Serializable
data class QuickSuggestions(@SerialName("vorschlaege") val suggestions: List<QuickSuggestion> = emptyList())

@Serializable
data class MergeBody(val version: Long, val mitId: String, val mitVersion: Long)

// ---- Ressorts ----

@Serializable
data class RessortPerson(val id: String, val name: String? = null, val ceviName: String? = null, @SerialName("bildUrl") val imageUrl: String? = null) {
    val display: String
        get() = when {
            !name.isNullOrBlank() && !ceviName.isNullOrBlank() -> "$name ($ceviName)"
            !name.isNullOrBlank() -> name
            else -> ceviName.orEmpty()
        }
}

@Serializable
data class Ressort(
    val id: String,
    val version: Long,
    @SerialName("kuerzel") val abbreviation: String,
    val name: String,
    @SerialName("leitungPersonId") val leaderId: String? = null,
    @SerialName("stvPersonId") val deputyId: String? = null,
    @SerialName("notiz") val tips: String? = null,
    val parentId: String? = null,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("leitung") val leader: RessortPerson? = null,
    @SerialName("stv") val deputy: RessortPerson? = null,
    /** «Ruhiges Ressort»: hat gerade nichts zu tun; die App zeigt es grau und im Zeitplan als Hinweis. */
    @SerialName("ruhig") val quiet: Boolean = false,
)

@Serializable
data class RessortPage(@SerialName("ressorts") val ressorts: List<Ressort>, @SerialName("naechster") val next: String? = null)

@Serializable
data class RessortLink(val id: String, val version: Long, @SerialName("titel") val title: String, val url: String)

@Serializable
data class RessortLinkPage(val links: List<RessortLink>, @SerialName("naechster") val next: String? = null)

@Serializable
data class RessortNote(
    val id: String,
    val version: Long,
    val text: String,
    @SerialName("erstelltAm") val createdAt: String,
    @SerialName("von") val author: RessortPerson? = null,
)

@Serializable
data class RessortNotePage(@SerialName("notizen") val notes: List<RessortNote>, @SerialName("naechster") val next: String? = null)

/** Alles, was die Seite eines Ressorts zeigt: das Ressort selbst, seine Links und Notizen. */
data class RessortBundle(val ressort: Ressort, val links: List<RessortLink>, val notes: List<RessortNote>)

// ---- Start ----

@Serializable
data class StartCountdown(
    @SerialName("von") val from: String,
    @SerialName("bis") val until: String,
    @SerialName("tageBisBeginn") val daysToStart: Int,
    @SerialName("tageBisEnde") val daysToEnd: Int,
)

@Serializable
data class TaskProgress(
    @SerialName("gesamt") val total: Int,
    @SerialName("erledigt") val done: Int,
    @SerialName("ueberfaellig") val overdue: Int,
)

@Serializable
data class Milestone(val id: String, val version: Long, @SerialName("titel") val title: String, @SerialName("datum") val date: String, @SerialName("art") val kind: String? = null)

@Serializable
data class MilestoneProgress(@SerialName("gesamt") val total: Int, @SerialName("vergangen") val past: Int, @SerialName("naechster") val next: Milestone? = null)

@Serializable
data class StartProgress(
    @SerialName("aufgaben") val tasks: TaskProgress? = null,
    @SerialName("meilensteine") val milestones: MilestoneProgress? = null,
)

/** `GET /api/v1/lager/{id}/start` für Lagerleitung und Leitung. `null` heisst: Die Rolle darf das nicht lesen. */
@Serializable
data class CampStart(
    @SerialName("heute") val today: String,
    val countdown: StartCountdown,
    @SerialName("fortschritt") val progress: StartProgress,
    @SerialName("ueberfaellig") val overdue: List<CampTask>? = null,
    @SerialName("eigene") val own: List<CampTask>? = null,
)

@Serializable
data class MeetingEntry(
    val id: String,
    @SerialName("datum") val date: String,
    @SerialName("zeit") val time: String? = null,
    @SerialName("titel") val title: String,
    @SerialName("ort") val place: String? = null,
    @SerialName("anzahlTraktanden") val agendaCount: Int? = null,
)

@Serializable
data class MeetingPage(@SerialName("hoecks") val meetings: List<MeetingEntry>)

@Serializable
data class WeatherDay(
    @SerialName("datum") val date: String,
    @SerialName("zustand") val condition: String? = null,
    @SerialName("warnungen") val warnings: List<String> = emptyList(),
    @SerialName("tmax") val high: Double? = null,
    @SerialName("tmin") val low: Double? = null,
    @SerialName("regen") val rain: Double? = null,
    @SerialName("boeen") val gusts: Double? = null,
)

@Serializable
data class Weather(@SerialName("stand") val updatedAt: String? = null, @SerialName("tage") val days: List<WeatherDay> = emptyList())

@Serializable
data class TodayLagertag(val id: String, @SerialName("datum") val date: String, val label: String? = null)

@Serializable
data class TodayDutyPerson(val id: String, val name: String? = null, val ceviName: String? = null)

@Serializable
data class TodayDayPage(@SerialName("seite") val page: TodayPage? = null)

@Serializable
data class TodayPage(@SerialName("tageschef") val dayLead: TodayDutyPerson? = null)

@Serializable
data class TodayShifts(@SerialName("dienste") val duties: List<TodayDuty> = emptyList())

@Serializable
data class TodayDuty(val id: String, val name: String)

/** `GET /api/v1/lager/{id}/heute`, nur was der Start zeigt: der Lagertag, der Tageschef und die Zahl der Dienste. */
@Serializable
data class CampToday(
    @SerialName("datum") val date: String,
    val lagertag: TodayLagertag? = null,
    @SerialName("tagesseite") val dayPage: TodayDayPage? = null,
    @SerialName("schichten") val shifts: TodayShifts? = null,
)

@Serializable
data class Notification(
    val id: String,
    @SerialName("art") val kind: String,
    @SerialName("objektTyp") val objectType: String,
    @SerialName("objektId") val objectId: String,
    @SerialName("gelesen") val read: Boolean = false,
    @SerialName("erstelltAm") val createdAt: String,
)

@Serializable
data class NotificationPage(@SerialName("eintraege") val entries: List<Notification>)
