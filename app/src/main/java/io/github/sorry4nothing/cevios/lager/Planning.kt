package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Die Modelle der Einteilung (Schnitt A3). Namen wie im Vertrag (`api-vertrag/openapi.yaml`); Bezeichner englisch.

// ---- Teilnehmende ----

/** Eine teilnehmende Person. Mit `teilnehmer.bearbeiten` kommt der Cevi-Name dazu; sonst fehlt er. */
@Serializable
data class Participant(
    val id: String,
    val version: Long,
    @SerialName("vorname") val firstName: String,
    @SerialName("nachname") val lastName: String,
    @SerialName("stufe") val stage: String? = null,
    @SerialName("abgewaehlt") val excluded: Boolean = false,
    val ceviName: String? = null,
) {
    val fullName: String get() = "$firstName $lastName"
}

/**
 * Ein Mitglied des Lagers für Auswahllisten (`GET /api/v1/lager/{id}/mitglieder`): `name` ist der Anzeigename des Lagers
 * (Cevi-Name, sonst Vorname). Keine Telefonnummer, keine E-Mail; nur anzeigen, nie loggen.
 */
@Serializable
data class CampMember(
    @SerialName("personId") val personId: String,
    val name: String,
    @SerialName("rolle") val role: String = "",
    @SerialName("ressorts") val ressorts: List<String> = emptyList(),
)

@Serializable
data class CampMemberList(@SerialName("mitglieder") val members: List<CampMember> = emptyList())

@Serializable
data class ParticipantPage(@SerialName("teilnehmer") val participants: List<Participant>, @SerialName("naechster") val next: String? = null)

@Serializable
data class ImportRow(
    @SerialName("zeile") val line: Int,
    @SerialName("vorname") val firstName: String,
    @SerialName("nachname") val lastName: String,
    @SerialName("stufe") val stage: String? = null,
    val ceviName: String? = null,
)

@Serializable
data class ImportFailure(@SerialName("zeile") val line: Int, val code: String, val message: String = "")

@Serializable
data class ImportDuplicate(@SerialName("zeile") val line: Int, @SerialName("grund") val reason: String)

@Serializable
data class ImportResult(
    @SerialName("vorschau") val preview: Boolean,
    @SerialName("angelegt") val created: Int,
    @SerialName("neu") val rows: List<ImportRow>,
    @SerialName("fehler") val failures: List<ImportFailure>,
    @SerialName("doppelt") val duplicates: List<ImportDuplicate>,
)

// ---- Zimmer und Zelte ----

enum class RoomKind(val wire: String) {
    ROOM("zimmer"),
    TENT("zelt"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: ROOM
    }
}

/** Wer in einem Zimmer liegt: Teilnehmende (`teilnehmer`) oder Mitglied des Lagers (`person`); beim Team nur der Name. */
@Serializable
data class RoomMember(@SerialName("typ") val kind: String = "", val id: String = "", val name: String, @SerialName("bildUrl") val imageUrl: String? = null) {
    val isParticipant: Boolean get() = kind == "teilnehmer"
}

/** Ein Zimmer. Das Team sieht nur das eigene Zimmer mit Namen: Plätze, Belegung und Notiz fehlen dann (`seats` ist `null`). */
@Serializable
data class Room(
    val id: String,
    val version: Long,
    val name: String,
    @SerialName("art") val kindWire: String,
    @SerialName("plaetze") val seats: Int? = null,
    @SerialName("notiz") val note: String? = null,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("belegt") val occupied: Int = 0,
    @SerialName("ueberbelegt") val overbooked: Boolean = false,
    @SerialName("mitglieder") val members: List<RoomMember> = emptyList(),
) {
    val kind: RoomKind get() = RoomKind.from(kindWire)

    /** Wahr, wenn die Rolle alle Zimmer mit Belegung sieht. */
    val full: Boolean get() = seats != null
}

@Serializable
data class OpenCounts(@SerialName("teilnehmer") val participants: Int, val team: Int)

@Serializable
data class RoomList(@SerialName("zimmer") val rooms: List<Room>, @SerialName("offen") val open: OpenCounts? = null)

/** Wer noch in keinem Zimmer liegt: Teilnehmende mit Vor- und Nachname, Mitglieder mit `name`. */
@Serializable
data class OpenPerson(
    val id: String,
    val name: String? = null,
    @SerialName("vorname") val firstName: String? = null,
    @SerialName("nachname") val lastName: String? = null,
    @SerialName("stufe") val stage: String? = null,
    val ceviName: String? = null,
) {
    val display: String
        get() = name?.takeIf { it.isNotBlank() } ?: listOfNotNull(firstName, lastName).joinToString(" ")
}

@Serializable
data class OpenPage(@SerialName("eintraege") val entries: List<OpenPerson>, @SerialName("naechster") val next: String? = null)

@Serializable
data class RoomFill(
    @SerialName("belegt") val occupied: Int,
    @SerialName("plaetze") val seats: Int,
    @SerialName("ueberbelegt") val overbooked: Boolean,
)

// ---- Fahrten ----

enum class TripDirection(val wire: String) {
    THERE("hin"),
    BACK("rueck"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: THERE
    }
}

@Serializable
data class TripPerson(val id: String, val version: Long, val personId: String? = null, val teilnehmerId: String? = null, val name: String, @SerialName("stufe") val stage: String? = null, @SerialName("bildUrl") val imageUrl: String? = null)

@Serializable
data class TripDriver(val personId: String, val name: String? = null)

@Serializable
data class Trip(
    val id: String,
    val version: Long,
    @SerialName("richtung") val directionWire: String,
    @SerialName("art") val kindWire: String,
    val name: String? = null,
    val fahrerPersonId: String? = null,
    @SerialName("plaetze") val seats: Int? = null,
    @SerialName("datum") val date: String? = null,
    @SerialName("zeit") val time: String? = null,
    @SerialName("von") val from: String? = null,
    @SerialName("nach") val to: String? = null,
    @SerialName("treffpunkt") val meetingPoint: String? = null,
    val link: String? = null,
    @SerialName("fahrer") val driver: TripDriver? = null,
    @SerialName("mitfahrer") val passengers: List<TripPerson> = emptyList(),
    @SerialName("belegt") val occupied: Int = 0,
    @SerialName("frei") val free: Int? = null,
    @SerialName("ueberbelegt") val overbooked: Boolean = false,
) {
    val direction: TripDirection get() = TripDirection.from(directionWire)
    val isCar: Boolean get() = kindWire == "auto"
}

@Serializable
data class TripPage(@SerialName("fahrten") val trips: List<Trip>, @SerialName("naechster") val next: String? = null)

/** Eine Person ohne Fahrt in einer Richtung: genau eines von `personId` und `teilnehmerId` ist gesetzt. */
@Serializable
data class TripCandidate(val personId: String? = null, val teilnehmerId: String? = null, val name: String, @SerialName("stufe") val stage: String? = null) {
    val key: String get() = personId?.let { "p:$it" } ?: "t:$teilnehmerId"
}

@Serializable
data class TripCandidatePage(@SerialName("personen") val people: List<TripCandidate>, @SerialName("gesamt") val total: Int, @SerialName("naechster") val next: String? = null)

// ---- Gruppen ----

@Serializable
data class GroupPerson(val personId: String, val name: String, val ceviName: String? = null, @SerialName("rolle") val role: String)

@Serializable
data class GroupParticipant(
    val id: String,
    @SerialName("vorname") val firstName: String,
    @SerialName("nachname") val lastName: String,
    @SerialName("stufe") val stage: String? = null,
    val ceviName: String? = null,
)

@Serializable
data class Group(
    val id: String,
    val version: Long,
    val name: String,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("personen") val people: List<GroupPerson> = emptyList(),
    @SerialName("teilnehmer") val participants: List<GroupParticipant> = emptyList(),
)

@Serializable
data class GroupList(
    @SerialName("gruppen") val groups: List<Group>,
    @SerialName("ohneGruppe") val withoutGroup: Int,
    @SerialName("abgewaehlt") val excluded: Int,
)

@Serializable
data class MixParticipant(val id: String, @SerialName("vorname") val firstName: String, @SerialName("nachname") val lastName: String, @SerialName("stufe") val stage: String? = null)

@Serializable
data class MixGroup(
    @SerialName("nummer") val number: Int,
    val gruppeId: String? = null,
    val name: String,
    @SerialName("anzahl") val count: Int,
    @SerialName("teilnehmer") val participants: List<MixParticipant>,
)

@Serializable
data class MixResult(
    @SerialName("vorschau") val preview: Boolean,
    val seed: Long,
    @SerialName("gruppen") val groups: List<MixGroup>,
    @SerialName("geaendert") val changed: Int? = null,
)

@Serializable
data class ChangedCount(@SerialName("geaendert") val changed: Int)

// ---- Schichtplan ----

@Serializable
data class Duty(
    val id: String,
    val version: Long,
    val name: String,
    @SerialName("beschreibung") val description: String? = null,
    @SerialName("mindestbesetzung") val minimum: Int = 1,
    @SerialName("nacht") val night: Boolean = false,
    val tagesabschnittId: String,
    @SerialName("reihenfolge") val order: Int = 0,
)

/** Eine Person oder ein Teilnehmender in einem Dienst an einem Lagertag: genau eines von `personId` und `teilnehmerId` ist gesetzt. */
@Serializable
data class Shift(
    val id: String,
    val version: Long,
    val dienstId: String,
    val lagertagId: String,
    @SerialName("datum") val date: String,
    val tagesabschnittId: String,
    val personId: String? = null,
    val teilnehmerId: String? = null,
    val name: String,
    val ceviName: String? = null,
    @SerialName("stufe") val stage: String? = null,
)

@Serializable
data class ShiftStat(val personId: String? = null, val teilnehmerId: String? = null, val name: String, @SerialName("anzahl") val count: Int, @SerialName("nachtwachen") val nights: Int)

@Serializable
data class ShiftPlan(
    @SerialName("dienste") val duties: List<Duty>,
    @SerialName("schichten") val shifts: List<Shift>,
    @SerialName("statistik") val stats: List<ShiftStat>,
)

@Serializable
data class ShiftSuggestionShift(
    val lagertagId: String,
    @SerialName("datum") val date: String,
    val dienstId: String,
    val tagesabschnittId: String,
    val personId: String? = null,
    val teilnehmerId: String? = null,
    val name: String,
)

@Serializable
data class ShiftMissing(val lagertagId: String, val dienstId: String, @SerialName("fehlend") val missing: Int)

@Serializable
data class ShiftSuggestionStat(val personId: String? = null, val teilnehmerId: String? = null, val name: String, @SerialName("vorher") val before: Int, @SerialName("neu") val added: Int, @SerialName("gesamt") val total: Int, @SerialName("nachtwachen") val nights: Int)

@Serializable
data class ShiftSuggestion(
    @SerialName("vorschau") val preview: Boolean,
    val seed: Long,
    @SerialName("gekuerzt") val truncated: Boolean,
    @SerialName("schichten") val shifts: List<ShiftSuggestionShift>,
    @SerialName("unbesetzt") val unfilled: List<ShiftMissing>,
    @SerialName("statistik") val stats: List<ShiftSuggestionStat>,
    @SerialName("geaendert") val changed: Int? = null,
)

/** Ein Tag des Lagers aus dem Zeitplan (`GET …/zeitplan`); der Schichtplan braucht Tage und Abschnitte, der Zeitplan auch die Meilensteine. */
@Serializable
data class CampDay(val id: String, @SerialName("datum") val date: String, val label: String? = null)

@Serializable
data class DaySection(val id: String, val name: String, @SerialName("zeit") val time: String? = null, @SerialName("reihenfolge") val order: Int = 0)

@Serializable
data class Schedule(
    @SerialName("lagertage") val days: List<CampDay>,
    @SerialName("tagesabschnitte") val sections: List<DaySection>,
    @SerialName("meilensteine") val milestones: List<Milestone> = emptyList(),
)

// ---- Diensttausch ----

enum class SwapStatus(val wire: String) {
    OPEN("offen"),
    ACCEPTED("angenommen"),
    DECLINED("abgelehnt"),
    WITHDRAWN("zurueckgezogen"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

@Serializable
data class Swap(
    val id: String,
    val version: Long,
    val vonPersonId: String,
    val schichtId: String,
    val mitPersonId: String? = null,
    val mitSchichtId: String? = null,
    @SerialName("notiz") val note: String? = null,
    @SerialName("status") val statusWire: String,
    val entschiedenVon: String? = null,
    @SerialName("entschiedenAm") val decidedAt: String? = null,
    @SerialName("erstelltAm") val createdAt: String,
    /** Anzeigename der anfragenden Person; `null`, wenn sie nicht mehr Mitglied ist (und bei älteren Antworten). */
    val vonName: String? = null,
    /** Anzeigename der Wunschperson oder dessen, der übernommen hat; `null` ohne Person. */
    val mitName: String? = null,
) {
    val status: SwapStatus get() = SwapStatus.from(statusWire)
}

@Serializable
data class SwapPage(@SerialName("tausche") val swaps: List<Swap>, @SerialName("naechster") val next: String? = null)

@Serializable
data class SwapList(@SerialName("tausche") val swaps: List<Swap>)

// ---- Abzählen ----

@Serializable
data class CountEntry(
    val id: String,
    val teilnehmerId: String? = null,
    val personId: String? = null,
    @SerialName("vorname") val firstName: String? = null,
    @SerialName("nachname") val lastName: String? = null,
    @SerialName("stufe") val stage: String? = null,
    val name: String? = null,
    val ceviName: String? = null,
    /** Der gemeinsame Haken des Servers: Die App liest ihn nicht (die Haken sind lokal, siehe [CountRules.page]). */
    @SerialName("geprueft") val checked: Boolean = false,
    @SerialName("geprueftAm") val checkedAt: String? = null,
) {
    val display: String
        get() = name?.takeIf { it.isNotBlank() }?.let { n -> ceviName?.takeIf { it.isNotBlank() }?.let { "$n ($it)" } ?: n }
            ?: listOfNotNull(firstName, lastName).joinToString(" ")
}

@Serializable
data class CountPage(
    @SerialName("eintraege") val entries: List<CountEntry>,
    @SerialName("naechster") val next: String? = null,
    @SerialName("da") val present: Int,
    @SerialName("gesamt") val total: Int,
)
