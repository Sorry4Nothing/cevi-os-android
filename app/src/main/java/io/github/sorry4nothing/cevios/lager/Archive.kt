package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Dokumente ----

/**
 * Ein Eintrag der Sammelabfrage `GET …/dokumente`: ein Dokument oder die Datei eines anderen Moduls (`source` ist dann nicht
 * «dokument», `version`, `teamVisible` und `keywords` fehlen). Es gibt nie den Inhalt: Die Datei liegt am Ablageort, `file.link`
 * ist die Adresse, die der Server liefert.
 */
@Serializable
data class CampDocument(
    @SerialName("quelle") val source: String,
    val id: String,
    val version: Long? = null,
    @SerialName("titel") val title: String,
    @SerialName("stichworte") val keywords: List<String> = emptyList(),
    @SerialName("teamSichtbar") val teamVisible: Boolean? = null,
    @SerialName("datei") val file: CampFile? = null,
    @SerialName("art") val kindWire: String? = null,
    val sharepoint: Boolean? = null,
    @SerialName("erstelltAm") val createdAt: String = "",
) {
    /** Nur ein Dokument des Lagers selbst lässt sich löschen, nicht die Datei eines anderen Moduls. */
    val editable: Boolean get() = source == DOCUMENT_SOURCE && version != null
}

const val DOCUMENT_SOURCE = "dokument"

@Serializable
data class DocumentPage(@SerialName("dokumente") val documents: List<CampDocument>, @SerialName("naechster") val next: String? = null)

/** Die Art einer Datei nach dem Server: Sie bestimmt Wort und Zeichen in der Liste. */
enum class DocumentKind(val wire: String) {
    PDF("pdf"),
    WORD("word"),
    EXCEL("excel"),
    IMAGE("bild"),
    LINK("link"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: LINK
    }
}

// ---- Adressbuch ----

/** Ein Kontakt des Adressbuchs: Personendaten Dritter, nur für die Lagerleitung. Er steht nie in einem Log. */
@Serializable
data class Contact(
    val id: String,
    val version: Long,
    val name: String,
    @SerialName("funktion") val role: String? = null,
    @SerialName("gruppe") val group: String? = null,
    @SerialName("telefon") val phone: String? = null,
    val email: String? = null,
    @SerialName("notiz") val note: String? = null,
    @SerialName("behalten") val keep: Boolean = false,
    @SerialName("aufraeumenAb") val cleanupFrom: String? = null,
) {
    /** «Brot und Zopf, Küche»: Funktion und Gruppe in einer Zeile. */
    val subtitle: String? get() = listOfNotNull(role?.takeIf { it.isNotBlank() }, group?.takeIf { it.isNotBlank() }).joinToString(", ").ifEmpty { null }

    override fun toString(): String = "Contact(id=$id)"
}

@Serializable
data class ContactPage(@SerialName("kontakte") val contacts: List<Contact>, @SerialName("naechster") val next: String? = null)

// ---- Suche ----

/** Ein Treffer: Typ und ID des Objekts (das Modul öffnet es), sein Titel und ein kurzer Ausschnitt. */
@Serializable
data class SearchHit(
    @SerialName("typ") val type: String,
    val id: String,
    @SerialName("titel") val title: String,
    @SerialName("auszug") val excerpt: String? = null,
)

@Serializable
data class SearchResult(@SerialName("treffer") val hits: List<SearchHit>)

// ---- Zeitplan ----

/** Der Vorschlag des Servers für Lagertage und Abschnitte, die es noch nicht gibt. */
@Serializable
data class ScheduleProposal(
    @SerialName("lagertage") val days: List<ProposedDay> = emptyList(),
    @SerialName("tagesabschnitte") val sections: List<ProposedSection> = emptyList(),
) {
    val isEmpty: Boolean get() = days.isEmpty() && sections.isEmpty()
}

@Serializable
data class ProposedDay(@SerialName("datum") val date: String)

@Serializable
data class ProposedSection(val name: String, @SerialName("reihenfolge") val order: Int = 0)

/** Alles, was der Zeitplan zeigt: der Zeitplan des Servers, dazu Höcks und Aufgaben mit Frist (fällt eines aus, fehlt nur es). */
data class TimelineData(val schedule: Schedule, val meetings: List<Meeting>, val tasks: List<CampTask>)

/** Die Ausdrucke, die der Server als PDF erzeugt (`GET …/aushaenge/{art}`). */
enum class Printout(val wire: String) {
    SHIFTS("schichtplan"),
    ROOMS("zimmerliste"),
    DOOR_SIGNS("tuerschilder"),
    GROUPS("gruppenlisten"),
    TRIPS("fahrtenliste"),
    ORGCHART("organigramm"),
}
