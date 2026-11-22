package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Die Operationen der Warteschlange für Aufgaben (Namen und Felder bestimmt der Server, `aenderungen`). */
object TaskOps {
    const val CREATE = "aufgabe.anlegen"
    const val CHANGE = "aufgabe.aendern"
    const val STATUS = "aufgabe.status"
    const val DELETE = "aufgabe.loeschen"
}

/** Die Nutzlasten der Operationen. Jede Änderung nennt die zuletzt gesehene `version` der Aufgabe (sonst 409 `VERSION_KONFLIKT`). */
object TaskPayloads {
    fun create(
        title: String,
        ressortId: String? = null,
        assigneeId: String? = null,
        due: String? = null,
    ): JsonObject = buildJsonObject {
        put("titel", title)
        ressortId?.let { put("ressortId", it) }
        assigneeId?.let { put("verantwortlichPersonId", it) }
        due?.let { put("frist", it) }
    }

    fun status(task: CampTask, status: TaskStatus): JsonObject = buildJsonObject {
        put("id", task.id)
        put("version", task.version)
        put("status", status.wire)
    }

    /** Ändert Felder; `null` als Wert leert das Feld (der Server liest `null` als «leeren»). */
    fun change(task: CampTask, fields: Map<String, JsonElement>): JsonObject = JsonObject(
        mapOf("id" to JsonPrimitive(task.id), "version" to JsonPrimitive(task.version)) + fields,
    )

    fun delete(task: CampTask): JsonObject = buildJsonObject {
        put("id", task.id)
        put("version", task.version)
    }

    /** Die Felder `id` und `version` stehen in jeder Änderung; der Rest ist das, was sich ändert. */
    fun fieldsOf(payload: JsonObject): Map<String, JsonElement> = payload.filterKeys { it != "id" && it != "version" }
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

/**
 * Legt die wartenden und eben gesendeten Änderungen über den Stand des Servers, damit die Liste sofort zeigt, was die Person
 * getan hat (auch ohne Netz): neue Aufgaben, geänderte Felder, neuer Status, gelöschte Aufgaben. Betroffene Aufgaben tragen
 * `pending`. Eine gesendete Änderung bleibt sichtbar, bis die Liste neu geladen hat; dann bringt der Server dasselbe mit.
 * Konflikte und Ablehnungen legen nichts darüber: Die Person entscheidet erst, dann gilt der Stand des Servers.
 */
fun applyQueued(
    base: List<CampTask>,
    changes: List<QueuedChange>,
    people: Map<String, TaskPerson> = emptyMap(),
    ressorts: Map<String, TaskRessortRef> = emptyMap(),
): List<CampTask> {
    val shown = changes.filter { it.state == ChangeState.WAITING || it.state == ChangeState.SENT }
    if (shown.isEmpty()) return base
    val tasks = LinkedHashMap<String, CampTask>().apply { base.forEach { put(it.id, it) } }
    val created = mutableListOf<CampTask>()
    for (change in shown) {
        val payload = change.payload
        val pending = change.state == ChangeState.WAITING
        val id = payload["id"].text()
        when (change.type) {
            TaskOps.CREATE -> {
                val realId = change.result?.get("id").text()
                // Hat der Server die Aufgabe schon in der Liste, steht sie dort; sonst zeigt die Liste die eigene.
                if (realId == null || realId !in tasks) {
                    created += payload.toTask("queued:${change.clientOpId}", people, ressorts).copy(pending = pending, queuedId = change.clientOpId)
                }
            }
            TaskOps.STATUS -> id?.let { key ->
                tasks[key]?.let { task ->
                    tasks[key] = task.copy(statusWire = payload["status"].text() ?: task.statusWire, version = change.newVersion(task), pending = pending)
                }
            }
            TaskOps.CHANGE -> id?.let { key ->
                tasks[key]?.let { task -> tasks[key] = task.withFields(payload, people, ressorts).copy(version = change.newVersion(task), pending = pending) }
            }
            TaskOps.DELETE -> id?.let { tasks.remove(it) }
        }
    }
    return tasks.values.toList() + created
}

/** Die Version nach einer gesendeten Änderung (der Server nennt sie im Ergebnis); eine wartende ändert die Version nicht. */
private fun QueuedChange.newVersion(task: CampTask): Long =
    if (state == ChangeState.SENT) result?.get("version")?.jsonPrimitive?.longOrNull ?: task.version else task.version

private fun JsonObject.toTask(id: String, people: Map<String, TaskPerson>, ressorts: Map<String, TaskRessortRef>): CampTask {
    val base = CampTask(
        id = id,
        version = 0,
        title = this["titel"].text().orEmpty(),
        statusWire = this["status"].text() ?: TaskStatus.OPEN.wire,
    )
    return base.withFields(this, people, ressorts)
}

/** Übernimmt die Felder einer Nutzlast in die Aufgabe, so wie der Server sie nach der Änderung zeigen würde. */
fun CampTask.withFields(payload: JsonObject, people: Map<String, TaskPerson>, ressorts: Map<String, TaskRessortRef>): CampTask {
    var task = this
    for ((key, value) in payload) {
        val text = value.text()
        task = when (key) {
            "titel" -> text?.let { task.copy(title = it) } ?: task
            "status" -> text?.let { task.copy(statusWire = it) } ?: task
            "notiz" -> task.copy(note = text)
            "frist" -> task.copy(due = text, fristMeilensteinId = null, fristVersatzTage = null)
            "spaeterAb" -> task.copy(snoozedUntil = text)
            "teil" -> task.copy(part = text)
            "wiederholung" -> task.copy(repeat = text)
            "stern" -> (value as? JsonPrimitive)?.booleanOrNull?.let { task.copy(starred = it) } ?: task
            "laufend" -> (value as? JsonPrimitive)?.booleanOrNull?.let { task.copy(ongoing = it) } ?: task
            "reihenfolge" -> (value as? JsonPrimitive)?.intOrNull?.let { task.copy(order = it) } ?: task
            "ressortId" -> task.copy(ressortId = text, ressort = text?.let { ressorts[it] })
            "verantwortlichPersonId" -> task.copy(assigneeId = text, assignee = text?.let { people[it] })
            "unterstuetzungPersonIds" -> (value as? JsonArray)?.let { ids ->
                task.copy(supporters = ids.mapNotNull { people[it.text()] })
            } ?: task
            else -> task
        }
    }
    return task
}
