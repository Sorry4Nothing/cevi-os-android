package io.github.sorry4nothing.cevios.termine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Stand des persönlichen Kalenderlinks (`GET /api/v1/ich/kalender-link`). Der Token steht nie darin. */
@Serializable
data class CalendarLinkStatus(
    @SerialName("aktiv") val active: Boolean,
    @SerialName("erstelltAm") val createdAt: String? = null,
    @SerialName("zuletztAbgerufenAm") val lastFetchedAt: String? = null,
    /** Die letzten vier Zeichen des Tokens, damit die Person ihren Link erkennt. */
    @SerialName("endung") val ending: String? = null,
)

/**
 * Ein frisch erzeugter Link: Der Token steht nur in dieser Antwort. Er ist ein Geheimnis und gehört in kein Log, auch nicht in
 * `toString` (Fehlerberichte, Debug-Ausgaben); darum keine `data class`.
 */
@Serializable
class CalendarLinkNew(
    @SerialName("token") val token: String,
    @SerialName("pfad") val path: String,
    @SerialName("erstelltAm") val createdAt: String,
    @SerialName("endung") val ending: String,
) {
    override fun toString() = "CalendarLinkNew(…$ending)"
}

/** Eine Gruppe, die sich als Kategorie eines Vorschlags wählen lässt. `type`: `stufe`, `funktion`, `team` oder `lager`. */
@Serializable
data class ProposalTag(val id: String, val name: String, @SerialName("typ") val type: String = "")

@Serializable
data class ProposalOptions(
    @SerialName("darfVorschlagen") val mayPropose: Boolean,
    val tags: List<ProposalTag> = emptyList(),
)

/** Der Vorschlag, den die App sendet. Ganztägig mit `dayFrom` und `dayTo`, sonst mit `start` und `end` (mit Zeitzone). */
@Serializable
data class ProposalRequest(
    @SerialName("titel") val title: String,
    @SerialName("ganztaegig") val allDay: Boolean,
    @SerialName("beginn") val start: String? = null,
    @SerialName("ende") val end: String? = null,
    @SerialName("tagVon") val dayFrom: String? = null,
    @SerialName("tagBis") val dayTo: String? = null,
    @SerialName("ort") val place: String? = null,
    @SerialName("beschreibung") val description: String? = null,
    @SerialName("tagIds") val tagIds: List<String>,
)

@Serializable
data class ProposalCreated(val id: String, val status: String)

/** Ein eigener Vorschlag mit seinem Stand: `offen`, `freigegeben`, `abgelehnt` oder `in_outlook_eingetragen`. */
@Serializable
data class Proposal(
    val id: String,
    val status: String,
    @SerialName("titel") val title: String,
    @SerialName("ganztaegig") val allDay: Boolean,
    @SerialName("beginn") val start: String? = null,
    @SerialName("ende") val end: String? = null,
    @SerialName("tagVon") val dayFrom: String? = null,
    @SerialName("tagBis") val dayTo: String? = null,
    @SerialName("ort") val place: String? = null,
    val tags: List<ProposalTag> = emptyList(),
    /** Nur bei einer Ablehnung. */
    @SerialName("begruendung") val reason: String? = null,
)

@Serializable
data class ProposalPage(
    @SerialName("vorschlaege") val proposals: List<Proposal>,
    @SerialName("weitere") val more: Boolean = false,
    @SerialName("naechste") val next: String? = null,
)

/** Ein Zusatzfeld des Anlasses. `type`: `text`, `ja_nein` oder `auswahl` (dann mit `options`). */
@Serializable
data class EventField(
    @SerialName("schluessel") val key: String,
    @SerialName("beschriftung") val label: String,
    @SerialName("typ") val type: String,
    @SerialName("optionen") val options: List<String> = emptyList(),
    @SerialName("pflicht") val required: Boolean = false,
)

/** Eine Angabe, die das Konto schon kennt («aus deinem Konto»); die App zeigt sie nur an. */
@Serializable
data class AccountValue(
    @SerialName("schluessel") val key: String,
    @SerialName("beschriftung") val label: String,
    @SerialName("wert") val value: String,
)

@Serializable
data class EventRegistration(
    val id: String,
    /** `angemeldet` oder `abgemeldet`. */
    val status: String,
    /** Schlüssel des Feldes auf den Wert: Text, `true`/`false` oder die gewählte Option. */
    @SerialName("antworten") val answers: Map<String, JsonElement> = emptyMap(),
    @SerialName("geaendertAm") val changedAt: String? = null,
) {
    val registered get() = status == "angemeldet"
}

@Serializable
data class EventParticipant(
    /** Das Kind; `null`, wenn die Person sich selbst anmeldet. */
    @SerialName("kindId") val childId: String? = null,
    val name: String,
    @SerialName("konto") val account: List<AccountValue> = emptyList(),
    @SerialName("anmeldung") val registration: EventRegistration? = null,
)

/** Der Anlass mit Formular und eigener Anmeldung (`GET /api/v1/anlaesse/{id}`). */
@Serializable
data class EventForm(
    val id: String,
    @SerialName("terminId") val programId: String,
    @SerialName("titel") val title: String,
    @SerialName("ganztaegig") val allDay: Boolean,
    @SerialName("beginn") val start: String? = null,
    @SerialName("ende") val end: String? = null,
    @SerialName("tagVon") val dayFrom: String? = null,
    @SerialName("tagBis") val dayTo: String? = null,
    @SerialName("anmeldungOffen") val open: Boolean,
    @SerialName("frist") val deadline: String? = null,
    /** `kind` (ein Elternteil meldet sein Kind an) oder `person` (man meldet sich selbst an). */
    @SerialName("anmeldungFuer") val registerFor: String,
    @SerialName("felder") val fields: List<EventField> = emptyList(),
    @SerialName("teilnehmende") val participants: List<EventParticipant> = emptyList(),
)

/** Die Antworten einer Anmeldung; `childId` nur bei `kind`, bei `person` wäre es ein Fehler (400). */
@Serializable
data class EventRegistrationRequest(
    @SerialName("kindId") val childId: String? = null,
    @SerialName("antworten") val answers: Map<String, JsonElement>,
)
