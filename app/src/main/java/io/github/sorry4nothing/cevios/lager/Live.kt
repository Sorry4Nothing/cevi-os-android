package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modelle des Schnitts «Im Lager»: Anwesenheit, Tagesseiten, Einkauf, Packliste, Notfall, Infos, To-dos. Die Namen der Felder
// bleiben über `@SerialName` die des Servers; Bezeichner im Code sind englisch.

// ---- Anwesenheit ----

/** Der Wert eines Feldes der Anwesenheit: `A` anwesend, `F` abwesend, `O` offen. Eine unbekannte Angabe gilt als offen. */
enum class Attendance(val wire: String) {
    PRESENT("A"),
    ABSENT("F"),
    OPEN("O"),
    ;

    /** Der nächste Wert beim Tippen auf ein Feld: Da, Frei, Offen, wieder Da. */
    fun next(): Attendance = when (this) {
        OPEN -> PRESENT
        PRESENT -> ABSENT
        ABSENT -> OPEN
    }

    companion object {
        fun from(wire: String?): Attendance = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

@Serializable
data class AttendanceEntry(
    val id: String,
    val version: Long,
    val lagertagId: String,
    val tagesabschnittId: String,
    @SerialName("wert") val valueWire: String,
) {
    val value: Attendance get() = Attendance.from(valueWire)
}

enum class RequestStatus(val wire: String) {
    OPEN("offen"),
    ACCEPTED("angenommen"),
    DECLINED("abgelehnt"),
    ;

    companion object {
        fun from(wire: String?): RequestStatus = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

@Serializable
data class AttendanceRequest(
    val id: String,
    val version: Long,
    val personId: String,
    val lagertagId: String,
    val tagesabschnittId: String,
    @SerialName("wert") val valueWire: String,
    @SerialName("notiz") val note: String? = null,
    @SerialName("status") val statusWire: String = "offen",
    @SerialName("erstelltAm") val createdAt: String = "",
) {
    val value: Attendance get() = Attendance.from(valueWire)
    val status: RequestStatus get() = RequestStatus.from(statusWire)
}

/** `GET …/anwesenheit/eigene`: die eigenen Felder, die eigenen Anfragen und die Frist (`null` ohne Frist). */
@Serializable
data class AttendanceOwn(
    @SerialName("eintraege") val entries: List<AttendanceEntry> = emptyList(),
    @SerialName("anfragen") val requests: List<AttendanceRequest> = emptyList(),
    @SerialName("frist") val deadline: String? = null,
)

@Serializable
data class AttendanceRequestPage(@SerialName("anfragen") val requests: List<AttendanceRequest> = emptyList(), @SerialName("naechster") val next: String? = null)

@Serializable
data class AttendanceRequests(@SerialName("anfragen") val requests: List<AttendanceRequest> = emptyList())

@Serializable
data class AttendanceCount(
    val lagertagId: String,
    val tagesabschnittId: String,
    @SerialName("wert") val valueWire: String,
    @SerialName("leitende") val leaders: Int = 0,
    @SerialName("teilnehmende") val participants: Int = 0,
) {
    val value: Attendance get() = Attendance.from(valueWire)
    val total: Int get() = leaders + participants
}

@Serializable
data class AttendanceSummary(@SerialName("zeilen") val rows: List<AttendanceCount> = emptyList())

@Serializable
data class AttendanceDeadline(@SerialName("frist") val deadline: String? = null)

// ---- Tagesseiten ----

/** Die Art eines Blocks der Tagesseite. */
enum class BlockKind(val wire: String) {
    IMPORTANT("wichtig"),
    IDEA("idee"),
    PICTURE("programmbild"),
    ;

    companion object {
        fun from(wire: String?): BlockKind? = entries.firstOrNull { it.wire == wire }
    }
}

@Serializable
data class DayBlock(
    val id: String,
    val version: Long,
    @SerialName("art") val kindWire: String,
    @SerialName("text") val text: String,
    val dateiId: String? = null,
    @SerialName("reihenfolge") val order: Int = 0,
    val datei: CampFile? = null,
) {
    val kind: BlockKind? get() = BlockKind.from(kindWire)
}

@Serializable
data class DayLead(val id: String, val name: String? = null, val ceviName: String? = null) {
    val display: String get() = RessortPerson(id, name, ceviName).display
}

@Serializable
data class DayPageRow(
    val id: String,
    val version: Long,
    val lagertagId: String,
    val tageschefPersonId: String? = null,
    @SerialName("notiz") val note: String? = null,
    val tageschef: DayLead? = null,
)

/** `GET …/tagesseiten/{lagertagId}`: die Seite, ihre Blöcke, das Wetter des Tages und die Aufgaben mit Frist an diesem Tag. */
@Serializable
data class DayPage(
    val lagertag: TodayLagertag,
    @SerialName("seite") val page: DayPageRow? = null,
    @SerialName("bloecke") val blocks: List<DayBlock> = emptyList(),
    @SerialName("wetter") val weather: WeatherDay? = null,
    @SerialName("wetterStand") val weatherUpdatedAt: String? = null,
    @SerialName("aufgaben") val tasks: List<CampTask> = emptyList(),
)

@Serializable
data class DayListEntry(val lagertagId: String, @SerialName("datum") val date: String, val label: String? = null, @SerialName("seite") val page: DayPageRow? = null)

@Serializable
data class DayList(@SerialName("tage") val days: List<DayListEntry> = emptyList())

// ---- Einkauf ----

/** Die Abteilungen der Einkaufsliste in der Reihenfolge, in der man durch den Laden geht. */
enum class ShopGroup(val wire: String) {
    PRODUCE("obst_gemuese"),
    BREAD("brot"),
    DAIRY("milch_kaese"),
    MEAT("fleisch_fisch"),
    PANTRY("vorrat"),
    DRINKS("getraenke"),
    HOUSEHOLD("haushalt"),
    OTHER("diverses"),
    ;

    companion object {
        fun from(wire: String?): ShopGroup = entries.firstOrNull { it.wire == wire } ?: OTHER
    }
}

@Serializable
data class ShopPerson(val id: String, val name: String? = null, val ceviName: String? = null) {
    val display: String get() = RessortPerson(id, name, ceviName).display
}

@Serializable
data class ShopItem(
    val id: String,
    val version: Long,
    val name: String,
    @SerialName("menge") val amount: Double? = null,
    @SerialName("einheit") val unit: String? = null,
    @SerialName("warengruppe") val groupWire: String = "diverses",
    @SerialName("einkaeuferPersonId") val buyerId: String? = null,
    @SerialName("einkaeufer") val buyer: ShopPerson? = null,
    @SerialName("erledigt") val done: Boolean = false,
) {
    val group: ShopGroup get() = ShopGroup.from(groupWire)
}

@Serializable
data class ShopPage(@SerialName("artikel") val items: List<ShopItem> = emptyList(), @SerialName("naechster") val next: String? = null)

@Serializable
data class ShopEntry(val id: String, val version: Long)

/** Eine Zeile der zusammengezählten Liste; `entries` sind die Artikel dahinter (nach dem Abhaken ändert die App jeden). */
@Serializable
data class ShopTotal(
    val name: String,
    @SerialName("menge") val amount: Double? = null,
    @SerialName("einheit") val unit: String? = null,
    @SerialName("warengruppe") val groupWire: String = "diverses",
    @SerialName("einkaeuferPersonId") val buyerId: String? = null,
    @SerialName("einkaeufer") val buyer: ShopPerson? = null,
    @SerialName("erledigt") val done: Boolean = false,
    @SerialName("anzahl") val count: Int = 1,
    @SerialName("eintraege") val entries: List<ShopEntry> = emptyList(),
) {
    val group: ShopGroup get() = ShopGroup.from(groupWire)
}

@Serializable
data class ShopTotalPage(@SerialName("artikel") val items: List<ShopTotal> = emptyList(), @SerialName("naechster") val next: String? = null)

@Serializable
data class ShopSuggestion(val name: String, @SerialName("haeufigkeit") val frequency: Int = 0)

@Serializable
data class ShopSuggestions(@SerialName("vorschlaege") val suggestions: List<ShopSuggestion> = emptyList())

// ---- Packliste ----

enum class PackState(val wire: String) {
    OPEN("offen"),
    CHECKED("abgehakt"),
    REMOVED("entfernt"),
    ;

    companion object {
        fun from(wire: String?): PackState = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

@Serializable
data class PackItem(
    val id: String,
    val version: Long,
    @SerialName("abschnitt") val section: String,
    @SerialName("text") val text: String,
    @SerialName("nurLagerleitung") val leadershipOnly: Boolean = false,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("zustand") val stateWire: String? = null,
) {
    val state: PackState get() = PackState.from(stateWire)
}

@Serializable
data class OwnPackItem(
    val id: String,
    @SerialName("abschnitt") val section: String,
    @SerialName("text") val text: String,
    @SerialName("abgehakt") val checked: Boolean = false,
    @SerialName("reihenfolge") val order: Int = 0,
)

@Serializable
data class PackList(
    @SerialName("punkte") val items: List<PackItem> = emptyList(),
    @SerialName("eigene") val own: List<OwnPackItem> = emptyList(),
)

// ---- Notfall und Infos ----

@Serializable
data class InfoLink(@SerialName("titel") val title: String, val url: String)

@Serializable
data class InfoSection(
    val id: String,
    val version: Long,
    @SerialName("titel") val title: String,
    @SerialName("text") val text: String? = null,
    val links: List<InfoLink> = emptyList(),
    @SerialName("nurLagerleitung") val leadershipOnly: Boolean = false,
    val notfall: Boolean = false,
    @SerialName("reihenfolge") val order: Int = 0,
    @SerialName("dateien") val files: List<CampFile> = emptyList(),
)

@Serializable
data class InfoPage(@SerialName("abschnitte") val sections: List<InfoSection> = emptyList(), @SerialName("naechster") val next: String? = null)

@Serializable
data class EmergencyAddress(
    @SerialName("ort") val place: String? = null,
    @SerialName("adresse") val address: String? = null,
    val kartenLink: String? = null,
)

@Serializable
data class EmergencyContact(
    @SerialName("quelle") val source: String,
    val id: String? = null,
    val version: Long? = null,
    @SerialName("funktion") val role: String,
    val personId: String? = null,
    val name: String,
    val ceviName: String? = null,
    @SerialName("telefon") val phone: String? = null,
    @SerialName("reihenfolge") val order: Int? = null,
) {
    /** Name mit Cevi-Name, so wie man die Person am Telefon nennt: «Sina (Eule)». */
    val display: String get() = if (!ceviName.isNullOrBlank() && ceviName != name) "$name ($ceviName)" else name
}

@Serializable
data class Emergency(
    @SerialName("adresse") val address: EmergencyAddress = EmergencyAddress(),
    @SerialName("kontakte") val contacts: List<EmergencyContact> = emptyList(),
    val infos: List<InfoSection> = emptyList(),
)

/** Der Stand des Notfall-Fensters mit dem Zeitpunkt, an dem er geladen wurde (für «Stand 18:02» ohne Netz). */
data class EmergencyState(val emergency: Emergency, val loadedAt: java.time.Instant)

// ---- To-dos ----

@Serializable
data class CampTodo(
    val id: String,
    @SerialName("text") val text: String,
    @SerialName("erledigt") val done: Boolean = false,
    @SerialName("reihenfolge") val order: Int = 0,
)

@Serializable
data class CampTodoList(@SerialName("todos") val todos: List<CampTodo> = emptyList())

// ---- Heute und Meine Woche ----

@Serializable
data class TodayWeather(@SerialName("tag") val day: WeatherDay? = null, @SerialName("stand") val updatedAt: String? = null)

/** `GET …/heute` im Umfang der Ansicht «Heute»: nur, was die Rolle lesen darf, der Rest ist `null`. */
@Serializable
data class TodayView(
    @SerialName("datum") val date: String,
    val lagertag: TodayLagertag? = null,
    @SerialName("tagesseite") val day: TodayDay? = null,
    @SerialName("wetter") val weather: TodayWeather? = null,
    @SerialName("anwesenheit") val attendance: AttendanceSummary? = null,
    @SerialName("schichten") val shifts: TodayShiftPlan? = null,
)

@Serializable
data class TodayDay(
    @SerialName("seite") val page: DayPageRow? = null,
    @SerialName("bloecke") val blocks: List<DayBlock> = emptyList(),
    @SerialName("aufgaben") val tasks: List<CampTask> = emptyList(),
)

@Serializable
data class TodayShiftPlan(
    @SerialName("dienste") val duties: List<Duty> = emptyList(),
    @SerialName("schichten") val shifts: List<Shift> = emptyList(),
)

/** Eine eigene Schicht in «Meine Woche»: Tag, Abschnitt und der Dienst. */
@Serializable
data class MyShift(
    val id: String,
    val dienstId: String,
    val lagertagId: String,
    @SerialName("datum") val date: String,
    val tagesabschnittId: String,
    @SerialName("dienst") val duty: MyDuty? = null,
)

@Serializable
data class MyDuty(val id: String, val name: String, @SerialName("nacht") val night: Boolean = false)

/** `GET …/meine-woche`: die sieben Tage ab heute; Teile, die die Rolle nicht lesen darf, sind `null`. Die App zeigt daraus nur die eigenen Dienste. */
@Serializable
data class MyWeek(
    @SerialName("von") val from: String,
    @SerialName("bis") val until: String,
    @SerialName("schichten") val shifts: List<MyShift>? = null,
)
