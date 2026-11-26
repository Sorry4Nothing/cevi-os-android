package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Die Operation der Warteschlange für den Haken «anwesend» im Höck-Modus (Felder `hoeckId`, `personId`, `anwesend`; ohne Version). */
const val PRESENCE_OP = "hoeck.anwesend"

fun presencePayload(meetingId: String, personId: String, present: Boolean): JsonObject = buildJsonObject {
    put("hoeckId", meetingId)
    put("personId", personId)
    put("anwesend", present)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/**
 * Schickt den Haken in die Warteschlange. Wartet dort schon einer für dieselbe Person im selben Höck (zweiter Tipp), ersetzt der
 * neue den alten: Die Schlange hält nie zwei Haken für dieselbe Zeile. Ist der alte schon unterwegs, geht der neue hinterher.
 */
fun routePresence(queue: OfflineQueue, payload: JsonObject, summary: String, enqueue: () -> Unit) {
    val same = queue.state.value.changes.firstOrNull {
        it.state == ChangeState.WAITING && it.type == PRESENCE_OP && it.payload.text("hoeckId") == payload.text("hoeckId") && it.payload.text("personId") == payload.text("personId")
    }
    if (same == null || !queue.revise(same.clientOpId, payload, summary)) enqueue()
}

/**
 * Legt die wartenden und eben gesendeten Haken über den Stand des Servers, damit der Haken sofort stimmt (auch ohne Netz). Die
 * Reihenfolge der Schlange gilt: Der letzte Haken je Person gewinnt. Konflikte und Ablehnungen legen nichts darüber.
 */
fun applyPresence(members: List<MemberPresence>, meetingId: String, changes: List<QueuedChange>): List<MemberPresence> {
    val relevant = changes.filter {
        it.type == PRESENCE_OP && (it.state == ChangeState.WAITING || it.state == ChangeState.SENT) && it.payload.text("hoeckId") == meetingId
    }
    if (relevant.isEmpty()) return members
    val latest = relevant.mapNotNull { change -> change.payload.text("personId")?.let { person -> (change.payload["anwesend"] as? JsonPrimitive)?.booleanOrNull?.let { person to it } } }.toMap()
    return members.map { member -> latest[member.id]?.let { member.copy(present = it) } ?: member }
}
