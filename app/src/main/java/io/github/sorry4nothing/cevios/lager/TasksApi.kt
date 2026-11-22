package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Die Routen von Aufgaben, Ressorts und Start. Änderungen an Aufgaben (anlegen, ändern, Status, löschen) gehen über die
 * Warteschlange (`CampViewModel.enqueue`), damit sie auch ohne Netz entstehen; hier stehen nur die Aufrufe, die Netz brauchen.
 */
class TasksApi(private val api: ApiClient) {
    /** Alle Aufgaben, die die Rolle sehen darf: Seite für Seite (200 je Seite), bis es keine nächste mehr gibt. */
    suspend fun listAll(campId: String): ApiResult<List<CampTask>> {
        val all = mutableListOf<CampTask>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/aufgaben?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<TaskPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.tasks
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun read(campId: String, taskId: String): ApiResult<CampTask> = api.get("/api/v1/lager/$campId/aufgaben/$taskId")

    suspend fun details(campId: String, taskId: String): ApiResult<TaskDetails> = api.get("/api/v1/lager/$campId/aufgaben/$taskId/details")

    /** Verlauf: wer die Aufgabe angelegt und zuletzt geändert hat, dazu die Einträge der letzten 30 Tage. Braucht das Recht «Verlauf lesen». */
    suspend fun history(campId: String, taskId: String): ApiResult<TaskHistory> = api.get("/api/v1/lager/$campId/aufgaben/$taskId/verlauf")

    /** Die offenen Aufgaben im selben Ressort mit gleichem Titel, für den Hinweis beim Erfassen. */
    suspend fun duplicates(campId: String, title: String, ressortId: String?): ApiResult<Duplicates> {
        val query = "titel=" + URLEncoder.encode(title, "UTF-8") + (ressortId?.let { "&ressortId=$it" } ?: "")
        return api.get("/api/v1/lager/$campId/aufgaben/duplikate?$query")
    }

    /** Schnelleingabe: Der Server liest den Text und schlägt Aufgaben vor. Er schreibt nichts. */
    suspend fun quick(campId: String, text: String): ApiResult<QuickSuggestions> =
        api.post("/api/v1/lager/$campId/aufgaben/schnell", QuickText(text))

    /** Legt eine Aufgabe sofort an (nur für «Zusammenführen», das die ID der neuen Aufgabe braucht). */
    suspend fun create(campId: String, body: JsonObject): ApiResult<CampTask> = api.post("/api/v1/lager/$campId/aufgaben", body)

    /** Führt die Aufgabe `withId` in die Aufgabe `taskId` zusammen. */
    suspend fun merge(campId: String, taskId: String, body: MergeBody): ApiResult<CampTask> =
        api.post("/api/v1/lager/$campId/aufgaben/$taskId/zusammenfuehren", body)

    /** Neue Reihenfolge: die Stelle in der Liste ist die neue `reihenfolge`. */
    suspend fun reorder(campId: String, entries: List<Pair<String, Long>>): ApiResult<Unit> =
        api.put("/api/v1/lager/$campId/aufgaben/reihenfolge", reorderBody(entries))

    suspend fun addCheckpoint(campId: String, taskId: String, text: String): ApiResult<Checkpoint> =
        api.post("/api/v1/lager/$campId/aufgaben/$taskId/checkliste", buildJsonObject { put("text", text) })

    suspend fun setCheckpoint(campId: String, taskId: String, checkpoint: Checkpoint, done: Boolean): ApiResult<Checkpoint> =
        api.patchJson("/api/v1/lager/$campId/aufgaben/$taskId/checkliste/${checkpoint.id}", buildJsonObject {
            put("version", checkpoint.version)
            put("erledigt", done)
        })

    suspend fun deleteCheckpoint(campId: String, taskId: String, checkpoint: Checkpoint): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/aufgaben/$taskId/checkliste/${checkpoint.id}?version=${checkpoint.version}")

    suspend fun reorderCheckpoints(campId: String, taskId: String, entries: List<Pair<String, Long>>): ApiResult<Unit> =
        api.put("/api/v1/lager/$campId/aufgaben/$taskId/checkliste/reihenfolge", reorderBody(entries))

    suspend fun addLink(campId: String, taskId: String, title: String, url: String): ApiResult<TaskLink> =
        api.post("/api/v1/lager/$campId/aufgaben/$taskId/links", buildJsonObject {
            put("titel", title)
            put("url", url)
        })

    suspend fun deleteLink(campId: String, taskId: String, link: TaskLink): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/aufgaben/$taskId/links/${link.id}?version=${link.version}")

    // ---- Ressorts ----

    suspend fun ressorts(campId: String): ApiResult<List<Ressort>> {
        val all = mutableListOf<Ressort>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/ressorts?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<RessortPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.ressorts
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun ressort(campId: String, ressortId: String): ApiResult<Ressort> = api.get("/api/v1/lager/$campId/ressorts/$ressortId")

    suspend fun ressortLinks(campId: String, ressortId: String): ApiResult<RessortLinkPage> =
        api.get("/api/v1/lager/$campId/ressorts/$ressortId/links?limit=$PAGE_SIZE")

    suspend fun ressortNotes(campId: String, ressortId: String): ApiResult<RessortNotePage> =
        api.get("/api/v1/lager/$campId/ressorts/$ressortId/notizen?limit=$PAGE_SIZE")

    suspend fun addRessortNote(campId: String, ressortId: String, text: String): ApiResult<RessortNote> =
        api.post("/api/v1/lager/$campId/ressorts/$ressortId/notizen", buildJsonObject { put("text", text) })

    // ---- Start ----

    /** Start der Lagerleitung und der Leitung; Team und Lesen bekommen 403. */
    suspend fun start(campId: String): ApiResult<CampStart> = api.get("/api/v1/lager/$campId/start")

    /** Die nächsten Höcks ab `from` (Datum). */
    suspend fun meetings(campId: String, from: String): ApiResult<MeetingPage> = api.get("/api/v1/lager/$campId/hoecks?von=$from&limit=5")

    suspend fun weather(campId: String): ApiResult<Weather> = api.get("/api/v1/lager/$campId/wetter")

    suspend fun today(campId: String): ApiResult<CampToday> = api.get("/api/v1/lager/$campId/heute")

    suspend fun notifications(campId: String): ApiResult<NotificationPage> = api.get("/api/v1/lager/$campId/benachrichtigungen?limit=10")

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 10

        fun reorderBody(entries: List<Pair<String, Long>>): JsonObject = buildJsonObject {
            put("eintraege", kotlinx.serialization.json.JsonArray(entries.map { (id, version) -> buildJsonObject { put("id", id); put("version", version) } }))
        }
    }
}
