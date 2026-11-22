package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Rolle in einem Lager. Die Abteilungsleitung gilt in Lagern der eigenen Abteilung immer als [LEADERSHIP]. */
enum class CampRole(val wire: String) {
    LEADERSHIP("lagerleitung"),
    LEADER("leitung"),
    TEAM("team"),
    READ_ONLY("lesen"),
    ;

    companion object {
        /** Eine Rolle, die die App nicht kennt, bekommt die kleinste: lesen. */
        fun from(wire: String?): CampRole = entries.firstOrNull { it.wire == wire } ?: READ_ONLY
    }
}

/** Ein Lager in der Liste (`GET /api/v1/lager`) oder im Detail (`GET /api/v1/lager/{id}`, dort mit `readOnlyFlag`). */
@Serializable
data class Camp(
    val id: String,
    @SerialName("titel") val title: String,
    @SerialName("von") val from: String,
    @SerialName("bis") val until: String,
    @SerialName("ort") val place: String? = null,
    val status: String,
    @SerialName("rolle") val roleName: String,
    /** Nur im Detail: wahr bei abgeschlossen und archiviert, schreibende Anfragen antworten dann mit 409. */
    @SerialName("schreibgeschuetzt") val readOnlyFlag: Boolean? = null,
    /** Wahr bei einem Übungslager (erfundene Daten). */
    @SerialName("uebung") val practice: Boolean = false,
    /** Nur im Detail: die Aktionen, die die eigene Rolle hier darf (Rechtekatalog des Vertrags, `<bereich>.<aktion>`); `null` in der Liste. */
    @SerialName("rechte") val grantedRights: List<String>? = null,
) {
    val role: CampRole get() = CampRole.from(roleName)

    /** Abgeschlossen und archiviert lassen nur noch lesen; steht das Detail noch aus, entscheidet der Status. */
    val readOnly: Boolean get() = readOnlyFlag ?: (status == STATUS_CLOSED || status == STATUS_ARCHIVED)

    val active: Boolean get() = status == STATUS_ACTIVE
}

// Auf Dateiebene, nicht in einem privaten `companion object` der Klasse: Dessen Feld ist für den erzeugten Serialisierer
// ausserhalb der Klasse nicht erreichbar (IllegalAccessError, der Aufruf bliebe hängen).
private const val STATUS_ACTIVE = "aktiv"
private const val STATUS_CLOSED = "abgeschlossen"
private const val STATUS_ARCHIVED = "archiviert"

@Serializable
data class CampList(@SerialName("lager") val camps: List<Camp>)

/**
 * Was die Person im Lager tun darf: die Aktionen aus `GET /api/v1/lager/{id}` (`rechte`, Einstellungen des Lagers
 * eingerechnet), der Schreibschutz und die Objekttypen, die sie lesen darf (aus dem Änderungsfeed). Die App zeigt eine Aktion
 * nur, wenn ihr Recht in der Liste steht; sonst fehlt der Knopf. Die Rolle ist nur die Beschriftung. Der Server prüft trotzdem
 * bei jeder Anfrage. Solange das Detail fehlt, ist nichts erlaubt.
 */
data class CampPermissions(
    val role: CampRole,
    val readOnly: Boolean,
    val readableTypes: Set<String>? = null,
    val granted: Set<String> = emptySet(),
) {
    /** Lesen: eines der Rechte steht in der Liste. */
    fun may(vararg rights: String): Boolean = rights.any { it in granted }

    /** Schreiben: eines der Rechte steht in der Liste, und das Lager ist nicht abgeschlossen. */
    fun mayWrite(vararg rights: String): Boolean = !readOnly && may(*rights)

    /** Der Start der Lagerleitung ist laut Vertrag für Lagerleitung und Leitung (kein Recht im Katalog, der Server entscheidet nach Rolle). */
    val seesLeadershipStart: Boolean get() = role == CampRole.LEADERSHIP || role == CampRole.LEADER

    /** Ob die Rolle den Objekttyp lesen darf; solange die Liste fehlt, gilt «ja», der Server filtert ohnehin. */
    fun canRead(type: String): Boolean = readableTypes?.contains(type) ?: true
}

fun Camp.permissions(readableTypes: Set<String>? = null) =
    CampPermissions(role, readOnly, readableTypes, grantedRights.orEmpty().toSet())

/** Ohne geöffnetes Lager (oder solange das Detail lädt): nichts erlaubt, nichts zu ändern. */
val NO_PERMISSIONS = CampPermissions(CampRole.READ_ONLY, readOnly = true)

/** Folgenummer und lesbare Objekttypen (Antwort ohne `seit`). */
@Serializable
data class FeedState(val seq: Long, @SerialName("objekttypen") val types: List<String>)

@Serializable
data class FeedEntry(
    val seq: Long,
    @SerialName("objekttyp") val type: String,
    val id: String,
    @SerialName("art") val kind: String,
    val version: Long,
)

@Serializable
data class FeedPage(
    @SerialName("eintraege") val entries: List<FeedEntry>,
    @SerialName("bis") val until: Long,
    @SerialName("weitere") val more: Boolean,
)

@Serializable
data class UnreadCount(@SerialName("anzahl") val count: Int)

/** Ziel eines Lager-Pushes (`GET /api/v1/lager/aufloesen/{typ}/{id}`): das Lager und das Objekt darin. */
@Serializable
data class PushResolution(
    @SerialName("lagerId") val campId: String,
    @SerialName("objektTyp") val objectType: String,
    @SerialName("objektId") val objectId: String,
)

@Serializable
data class RoleFlags(val leitung: Boolean, val team: Boolean, val lesen: Boolean)

@Serializable
data class RightEntry(
    @SerialName("recht") val right: String,
    @SerialName("bedeutung") val meaning: String,
    val standard: RoleFlags,
    @SerialName("aktuell") val current: RoleFlags,
)

@Serializable
data class CampRights(@SerialName("rechte") val rights: List<RightEntry>)

@Serializable
data class QueueOperation(val clientOpId: String, @SerialName("typ") val type: String, @SerialName("nutzlast") val payload: JsonObject)

@Serializable
data class QueueRequest(@SerialName("operationen") val operations: List<QueueOperation>)

/** Ergebnis einer Operation: `ok`, `konflikt` (mit `aktuell` und `abweichend`) oder `abgelehnt` (mit `code`). */
@Serializable
data class QueueResult(
    val clientOpId: String,
    val status: String,
    val code: String? = null,
    /** Nur bei `ok`: IDs und Versionen der geschriebenen Zeilen (keine Inhalte). */
    @SerialName("ergebnis") val result: JsonObject? = null,
    @SerialName("aktuell") val current: JsonObject? = null,
    @SerialName("abweichend") val differing: List<String> = emptyList(),
)

@Serializable
data class QueueResponse(@SerialName("ergebnisse") val results: List<QueueResult>)
