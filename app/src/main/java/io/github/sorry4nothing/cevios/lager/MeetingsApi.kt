package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Die Routen der Sitzungen: Höcks, Traktanden, Protokolle, Beschlüsse und Erwähnungen. Der Server kennt für diese Objekte keine
 * Operationen der Offline-Warteschlange: Sie gehen direkt hinaus und brauchen Netz. Jede Änderung nennt die zuletzt gesehene
 * `version`; stimmt sie nicht, antwortet der Server mit `VERSION_KONFLIKT` und dem aktuellen Stand (`ApiResult.Failure.conflict`).
 */
class MeetingsApi(private val api: ApiClient) {
    suspend fun meetings(campId: String): ApiResult<List<Meeting>> {
        val all = mutableListOf<Meeting>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/hoecks?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<MeetingList>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.meetings
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun meeting(campId: String, meetingId: String): ApiResult<Meeting> = api.get("/api/v1/lager/$campId/hoecks/$meetingId")

    suspend fun createMeeting(campId: String, body: JsonObject): ApiResult<Meeting> = api.post("/api/v1/lager/$campId/hoecks", body)

    suspend fun changeMeeting(campId: String, meetingId: String, body: JsonObject): ApiResult<Meeting> =
        api.patchJson("/api/v1/lager/$campId/hoecks/$meetingId", body)

    suspend fun deleteMeeting(campId: String, meeting: Meeting): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/hoecks/${meeting.id}?version=${meeting.version}")

    /** Der Kalendereintrag eines Höcks (`text/calendar`); der Server erzeugt ihn, er enthält nur Titel, Ort, Datum und Zeit. */
    suspend fun calendarFile(campId: String, meetingId: String): ApiResult<ByteArray> =
        api.getBytes("/api/v1/lager/$campId/hoecks/$meetingId.ics", MAX_ICS_BYTES)

    suspend fun mode(campId: String, meetingId: String): ApiResult<MeetingMode> = api.get("/api/v1/lager/$campId/hoecks/$meetingId/modus")

    suspend fun setPresent(campId: String, meetingId: String, personId: String, present: Boolean): ApiResult<Unit> {
        val path = "/api/v1/lager/$campId/hoecks/$meetingId/anwesende/$personId"
        return if (present) api.putWithoutBody(path) else api.delete(path)
    }

    /** Übernimmt die Punkte, die auf den nächsten Höck warten, in diesen Höck (mit `ids` nur diese). */
    suspend fun takeOver(campId: String, meetingId: String, ids: List<String>? = null): ApiResult<TakeOverResult> =
        api.post(
            "/api/v1/lager/$campId/hoecks/$meetingId/traktanden/uebernehmen",
            buildJsonObject { ids?.let { put("traktandumIds", JsonArray(it.map(::JsonPrimitive))) } },
        )

    suspend fun nextMeetingItems(campId: String): ApiResult<NextMeetingAgenda> = api.get("/api/v1/lager/$campId/traktanden/naechster")

    suspend fun createItem(campId: String, body: JsonObject): ApiResult<AgendaItem> = api.post("/api/v1/lager/$campId/traktanden", body)

    suspend fun changeItem(campId: String, itemId: String, body: JsonObject): ApiResult<AgendaItem> =
        api.patchJson("/api/v1/lager/$campId/traktanden/$itemId", body)

    suspend fun deleteItem(campId: String, item: AgendaItem): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/traktanden/${item.id}?version=${item.version}")

    /** Neue Reihenfolge der Traktanden eines Höcks (`meetingId` null: die Punkte für den nächsten Höck). */
    suspend fun reorderItems(campId: String, meetingId: String?, items: List<AgendaItem>): ApiResult<Unit> =
        api.put(
            "/api/v1/lager/$campId/traktanden/reihenfolge",
            buildJsonObject {
                put("hoeckId", meetingId?.let(::JsonPrimitive) ?: JsonNull)
                put("eintraege", JsonArray(items.map { buildJsonObject { put("id", it.id); put("version", it.version) } }))
            },
        )

    // ---- Protokolle und Beschlüsse ----

    suspend fun minutes(campId: String): ApiResult<List<Minutes>> {
        val all = mutableListOf<Minutes>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/protokolle?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<MinutesPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.minutes
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Verknüpft eine Datei am Ablageort (nur https); der Server prüft den Link streng und lädt ihn nie. */
    suspend fun addFile(campId: String, name: String, link: String): ApiResult<CampFile> =
        api.post("/api/v1/lager/$campId/dateien", buildJsonObject { put("anzeigename", name); put("link", link) })

    suspend fun createMinutes(campId: String, body: JsonObject): ApiResult<Minutes> = api.post("/api/v1/lager/$campId/protokolle", body)

    suspend fun changeMinutes(campId: String, minutesId: String, body: JsonObject): ApiResult<Minutes> =
        api.patchJson("/api/v1/lager/$campId/protokolle/$minutesId", body)

    suspend fun decisions(campId: String): ApiResult<List<Decision>> {
        val all = mutableListOf<Decision>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/beschluesse?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<DecisionPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.decisions
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun createDecision(campId: String, body: JsonObject): ApiResult<Decision> = api.post("/api/v1/lager/$campId/beschluesse", body)

    suspend fun changeDecision(campId: String, decisionId: String, body: JsonObject): ApiResult<Decision> =
        api.patchJson("/api/v1/lager/$campId/beschluesse/$decisionId", body)

    suspend fun deleteDecision(campId: String, decision: Decision): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/beschluesse/${decision.id}?version=${decision.version}")

    // ---- Erwähnungen ----

    suspend fun mentions(campId: String): ApiResult<List<Mention>> {
        val all = mutableListOf<Mention>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/erwaehnungen?limit=$MENTION_PAGE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<MentionPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.mentions
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Gleicht die Erwähnungen eines Inhalts ab, nachdem er gespeichert ist (`type`: `traktandum`, `beschluss`, `pinnwand`, `aufgabe`). */
    suspend fun syncMentions(campId: String, type: String, sourceId: String): ApiResult<Unit> =
        api.putWithoutBody("/api/v1/lager/$campId/erwaehnungen/$type/$sourceId")

    suspend fun setMentionDone(campId: String, mentionId: String, done: Boolean): ApiResult<Mention> =
        api.patchJson("/api/v1/lager/$campId/erwaehnungen/$mentionId", buildJsonObject { put("erledigt", done) })

    suspend fun mentionToTask(campId: String, mentionId: String): ApiResult<MentionTask> =
        api.post("/api/v1/lager/$campId/erwaehnungen/$mentionId/als-aufgabe", buildJsonObject { })

    private companion object {
        const val PAGE_SIZE = 200
        const val MENTION_PAGE = 100
        const val MAX_PAGES = 10
        const val MAX_ICS_BYTES = 64 * 1024
    }
}

/** Ein Feld einer Nutzlast mit einem Wert, der leer sein darf (`null` leert das Feld beim Server). */
fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
