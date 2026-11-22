package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiResult
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

/** Die Operation der Warteschlange für «Ruhiges Ressort» (Felder `id`, `version`, `ruhig`). */
const val RESSORT_QUIET_OP = "ressort.ruhig"

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

/** Wohin eine Änderung an einer Aufgabe gegangen ist; `BLOCKED`: Die Anlage ist gerade unterwegs, die Änderung ist noch nirgends aufgenommen. */
enum class ChangeRoute { ENQUEUED, REVISED, MERGED, BLOCKED }

/**
 * Schickt eine Änderung an einer Aufgabe in die Warteschlange. Wartet dort schon eine gleicher Art an derselben Aufgabe (zum
 * Beispiel ein zweiter Tipp auf den Haken), wird sie angepasst statt verdoppelt; eine Aufgabe, die noch gar nicht angelegt ist,
 * ändert die Anlage selbst. Ist die Anlage oder die gleiche Änderung gerade unterwegs, geht sie als eigene Änderung hinterher
 * (`enqueue`); nur die Anlage selbst lässt sich dann nicht mehr ändern (`BLOCKED`, die Person bekommt es gesagt).
 */
fun routeTaskChange(queue: OfflineQueue, task: CampTask, type: String, payload: JsonObject, summary: String, enqueue: () -> Unit): ChangeRoute {
    val waiting = queue.state.value.changes
    val creating = task.queuedId?.let { ref -> waiting.firstOrNull { it.ref == ref && it.state == ChangeState.WAITING } }
    if (creating != null) {
        val done = if (type == TaskOps.DELETE) queue.cancel(creating.clientOpId)
        else queue.revise(creating.clientOpId, JsonObject(creating.payload + TaskPayloads.fieldsOf(payload)), creating.summary)
        return if (done) ChangeRoute.MERGED else ChangeRoute.BLOCKED
    }
    val same = waiting.firstOrNull { it.state == ChangeState.WAITING && it.type == type && it.payload["id"].text() == task.id }
    if (same != null && queue.revise(same.clientOpId, JsonObject(same.payload + TaskPayloads.fieldsOf(payload)), summary)) return ChangeRoute.REVISED
    enqueue()
    return ChangeRoute.ENQUEUED
}

/**
 * «Zusammenführen» legt die neue Zeile an und führt sie mit der vorhandenen Aufgabe zusammen. Scheitert der zweite Schritt, merkt
 * sich diese Klasse die angelegte Aufgabe: Ein neuer Versuch führt nur noch zusammen, statt eine weitere Aufgabe anzulegen.
 */
class DuplicateMerge {
    private var created: Pair<QuickItem, CampTask>? = null

    /** Die für diese Zeile schon angelegte Aufgabe, falls ein früherer Versuch sie angelegt hat. */
    fun createdFor(item: QuickItem): CampTask? = created?.takeIf { it.first == item }?.second

    fun clear() {
        created = null
    }

    suspend fun merge(
        item: QuickItem,
        create: suspend () -> ApiResult<CampTask>,
        merge: suspend (CampTask) -> ApiResult<CampTask>,
    ): ApiResult<CampTask> {
        val task = createdFor(item) ?: when (val result = create()) {
            is ApiResult.Failure -> return result
            is ApiResult.Ok -> result.value.also { created = item to it }
        }
        return merge(task)
    }
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
                    // Hat der Server die Anlage bestätigt, gilt die echte ID und Version: Eine weitere Änderung geht sonst an «queued:…» und scheitert.
                    val version = change.result?.get("version")?.jsonPrimitive?.longOrNull ?: 0L
                    created += payload.toTask(realId ?: "queued:${change.ref}", people, ressorts).copy(version = version, pending = pending, queuedId = change.ref)
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
