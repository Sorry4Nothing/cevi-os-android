package io.github.sorry4nothing.cevios.termine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Das zuständige Team eines Raums (Tag der Abteilung). */
@Serializable
data class RoomTeam(val id: String, val name: String)

/** Ein aktiver Raum der Abteilung (`GET /api/v1/raeume`). Ohne `team` entscheidet die Abteilungsleitung. */
@Serializable
data class Room(
    val id: String,
    val name: String,
    @SerialName("ort") val place: String? = null,
    @SerialName("beschreibung") val description: String? = null,
    @SerialName("kapazitaet") val capacity: Int? = null,
    @SerialName("aktiv") val active: Boolean = true,
    val team: RoomTeam? = null,
)

@Serializable
data class RoomPage(
    @SerialName("raeume") val rooms: List<Room>,
    @SerialName("weitere") val more: Boolean = false,
    @SerialName("naechste") val next: String? = null,
)

/** Die Anfrage, die die App sendet: Zeiten mit Zone, Zweck eine Zeile bis 200 Zeichen. */
@Serializable
data class RoomRequestBody(
    @SerialName("beginn") val start: String,
    @SerialName("ende") val end: String,
    @SerialName("zweck") val purpose: String,
)

/** Der Stand nach dem Anfragen oder nach dem Entscheid (`RaumAnfrageStand`). */
@Serializable
data class RoomRequestCreated(val id: String, val status: String)

/** Der Entscheid des Teams: `annehmen` oder `ablehnen`; die Begründung gehört nur zu `ablehnen` und ist dort Pflicht (1 bis 500 Zeichen). */
@Serializable
data class RoomDecisionBody(
    @SerialName("entscheid") val decision: String,
    @SerialName("begruendung") val reason: String? = null,
)

/** Höchstlänge der Begründung (Vertrag `RaumAnfrageEntscheid`). */
const val ROOM_REASON_MAX = 500

/** Eine eigene Anfrage; `status`: `angefragt`, `bestaetigt`, `abgelehnt` oder `storniert`. Bestätigt ist die Buchung. */
@Serializable
data class RoomRequest(
    val id: String,
    @SerialName("raumId") val roomId: String,
    @SerialName("raumName") val roomName: String,
    @SerialName("beginn") val start: String,
    @SerialName("ende") val end: String,
    @SerialName("zweck") val purpose: String,
    val status: String,
    /** Nur bei einer Ablehnung. */
    @SerialName("begruendung") val reason: String? = null,
    @SerialName("entschiedenAm") val decidedAt: String? = null,
    @SerialName("erstelltAm") val createdAt: String? = null,
    /** Nur in der Liste der offenen Anfragen: die anfragende Person. */
    @SerialName("vonName") val requesterName: String? = null,
)

@Serializable
data class RoomRequestPage(
    @SerialName("anfragen") val requests: List<RoomRequest>,
    @SerialName("weitere") val more: Boolean = false,
    @SerialName("naechste") val next: String? = null,
)
