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
import java.net.URLEncoder

/**
 * Die Routen von «Im Lager»: Heute, Tagesseiten, Anwesenheit, Einkauf, Packliste, Notfall, Infos und eigene To-dos. Der Server kennt
 * für diese Objekte keine Operationen der Offline-Warteschlange: Änderungen gehen direkt hinaus und brauchen Netz. Zeilen mit Version
 * (Tagesseite, Block, Artikel, Info-Abschnitt, Anfrage) nennen die zuletzt gesehene `version`; stimmt sie nicht, antwortet der Server
 * mit `VERSION_KONFLIKT` (`ApiResult.Failure.conflict`). Anwesenheit, Zustand der Packliste, eigene Punkte und To-dos haben keine
 * Version: Der Server setzt genau den genannten Wert, eine Wiederholung ändert nichts.
 */
class LiveApi(private val api: ApiClient) {
    // ---- Heute ----

    suspend fun today(campId: String): ApiResult<TodayView> = api.get("/api/v1/lager/$campId/heute")

    suspend fun schedule(campId: String): ApiResult<Schedule> = api.get("/api/v1/lager/$campId/zeitplan")

    /** Nur für `ort`: den Grund, warum «Heute» oder die Tagesseite kein Wetter zeigen. */
    suspend fun weather(campId: String): ApiResult<Weather> = api.get("/api/v1/lager/$campId/wetter")

    suspend fun myWeek(campId: String): ApiResult<MyWeek> = api.get("/api/v1/lager/$campId/meine-woche")

    // ---- Anwesenheit ----

    suspend fun ownAttendance(campId: String): ApiResult<AttendanceOwn> = api.get("/api/v1/lager/$campId/anwesenheit/eigene")

    /** Trägt eigene offene Felder ein. Ist eines schon mit einem anderen Wert eingetragen oder die Frist vorbei, schreibt der Server nichts (409 `ANFRAGE_NOETIG`). */
    suspend fun setOwnAttendance(campId: String, fields: List<AttendanceField>): ApiResult<AttendanceOwn> =
        api.putJson("/api/v1/lager/$campId/anwesenheit/eigene", buildJsonObject { put("eintraege", fieldsJson(fields)) })

    /** Stellt je Feld eine Anfrage an die Lagerleitung (ein ganzer Tag sind die Felder seiner Abschnitte). */
    suspend fun requestAttendance(campId: String, fields: List<AttendanceField>, note: String?): ApiResult<AttendanceRequests> =
        api.post(
            "/api/v1/lager/$campId/anwesenheit/anfragen",
            buildJsonObject {
                put("eintraege", fieldsJson(fields))
                note?.takeIf { it.isNotBlank() }?.let { put("notiz", it.trim()) }
            },
        )

    suspend fun attendanceSummary(campId: String): ApiResult<AttendanceSummary> = api.get("/api/v1/lager/$campId/anwesenheit/zusammenfassung")

    /** Die offenen Anfragen des Teams, die ältesten zuerst (nur die Lagerleitung). */
    suspend fun openRequests(campId: String): ApiResult<List<AttendanceRequest>> {
        val all = mutableListOf<AttendanceRequest>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/anwesenheit/anfragen?status=offen&limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<AttendanceRequestPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.requests
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Nimmt Anfragen an oder lehnt sie ab (höchstens 50 je Aufruf, alles oder nichts, je Anfrage mit der gesehenen `version`). */
    suspend fun decideRequests(campId: String, accept: Boolean, requests: List<AttendanceRequest>): ApiResult<AttendanceRequests> =
        api.post(
            "/api/v1/lager/$campId/anwesenheit/anfragen/entscheid",
            buildJsonObject {
                put("entscheid", if (accept) "angenommen" else "abgelehnt")
                put("anfragen", JsonArray(requests.map { buildJsonObject { put("id", it.id); put("version", it.version) } }))
            },
        )

    /** Setzt die Frist, bis zu der das Team direkt einträgt; `null` hebt sie auf. */
    suspend fun setDeadline(campId: String, deadline: String?): ApiResult<AttendanceDeadline> =
        api.putJson("/api/v1/lager/$campId/anwesenheit/frist", buildJsonObject { put("frist", deadline?.let { JsonPrimitive(it) } ?: JsonNull) })

    // ---- Tagesseiten ----

    suspend fun days(campId: String): ApiResult<DayList> = api.get("/api/v1/lager/$campId/tagesseiten")

    suspend fun day(campId: String, dayId: String): ApiResult<DayPage> = api.get("/api/v1/lager/$campId/tagesseiten/$dayId")

    suspend fun createDayPage(campId: String, dayId: String, body: JsonObject): ApiResult<DayPageRow> = api.post("/api/v1/lager/$campId/tagesseiten/$dayId", body)

    suspend fun changeDayPage(campId: String, dayId: String, body: JsonObject): ApiResult<DayPageRow> = api.patchJson("/api/v1/lager/$campId/tagesseiten/$dayId", body)

    suspend fun addBlock(campId: String, dayId: String, kind: BlockKind, text: String): ApiResult<DayBlock> =
        api.post("/api/v1/lager/$campId/tagesseiten/$dayId/bloecke", buildJsonObject { put("art", kind.wire); put("text", text.trim()) })

    suspend fun changeBlock(campId: String, dayId: String, blockId: String, body: JsonObject): ApiResult<DayBlock> =
        api.patchJson("/api/v1/lager/$campId/tagesseiten/$dayId/bloecke/$blockId", body)

    suspend fun deleteBlock(campId: String, dayId: String, block: DayBlock): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/tagesseiten/$dayId/bloecke/${block.id}?version=${block.version}")

    // ---- Einkauf ----

    /** Alle Artikel (ohne gelöschte); mit `mine` nur die, die die Person selbst einkauft. */
    suspend fun shopping(campId: String, mine: Boolean): ApiResult<List<ShopItem>> {
        val all = mutableListOf<ShopItem>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/einkauf?limit=$PAGE_SIZE" + (if (mine) "&einkaeufer=ich" else "") + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<ShopPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.items
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Gleiche Artikel in einer Zeile, die Mengen summiert (nie über verschiedene Einheiten). */
    suspend fun shoppingTotals(campId: String, mine: Boolean): ApiResult<List<ShopTotal>> {
        val all = mutableListOf<ShopTotal>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/einkauf/zusammen?limit=$PAGE_SIZE" + (if (mine) "&einkaeufer=ich" else "") + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<ShopTotalPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.items
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Die häufigsten Artikelnamen des Lagers, die so beginnen (höchstens 50, eine Abfrage). */
    suspend fun suggestions(campId: String, prefix: String): ApiResult<ShopSuggestions> =
        api.get("/api/v1/lager/$campId/einkauf/vorschlaege?limit=$SUGGESTION_LIMIT&q=" + URLEncoder.encode(prefix.trim(), "UTF-8"))

    suspend fun addShopItem(campId: String, body: JsonObject): ApiResult<ShopItem> = api.post("/api/v1/lager/$campId/einkauf", body)

    suspend fun changeShopItem(campId: String, itemId: String, body: JsonObject): ApiResult<ShopItem> = api.patchJson("/api/v1/lager/$campId/einkauf/$itemId", body)

    suspend fun deleteShopItem(campId: String, itemId: String, version: Long): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/einkauf/$itemId?version=$version")

    // ---- Vorlagen ----

    /** Die Vorlagen der Abteilung zum Anwenden (Recht `vorlagen.anwenden`, Standard nur die Lagerleitung). */
    suspend fun templates(campId: String): ApiResult<TemplateList> = api.get("/api/v1/lager/$campId/vorlagen")

    /** Legt die Einträge der Vorlage im Lager an. Wer dieselbe Vorlage zweimal anwendet, hat jeden Eintrag zweimal. */
    suspend fun applyTemplate(campId: String, templateId: String): ApiResult<TemplateApplied> =
        api.postWithoutBodyJson("/api/v1/lager/$campId/vorlagen/$templateId/anwenden")

    // ---- Packliste ----

    suspend fun packList(campId: String): ApiResult<PackList> = api.get("/api/v1/lager/$campId/packliste")

    suspend fun setPackState(campId: String, itemId: String, state: PackState): ApiResult<Unit> =
        api.put("/api/v1/lager/$campId/packliste/punkte/$itemId/zustand", buildJsonObject { put("zustand", state.wire) })

    suspend fun addOwnPackItem(campId: String, section: String, text: String): ApiResult<OwnPackItem> =
        api.post("/api/v1/lager/$campId/packliste/eigene", buildJsonObject { put("abschnitt", section.trim()); put("text", text.trim()) })

    suspend fun changeOwnPackItem(campId: String, itemId: String, body: JsonObject): ApiResult<OwnPackItem> =
        api.patchJson("/api/v1/lager/$campId/packliste/eigene/$itemId", body)

    suspend fun deleteOwnPackItem(campId: String, itemId: String): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/packliste/eigene/$itemId")

    // ---- Notfall und Infos ----

    suspend fun emergency(campId: String): ApiResult<Emergency> = api.get("/api/v1/lager/$campId/notfall")

    suspend fun infos(campId: String): ApiResult<List<InfoSection>> {
        val all = mutableListOf<InfoSection>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/infos?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<InfoPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.sections
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun addInfo(campId: String, body: JsonObject): ApiResult<InfoSection> = api.post("/api/v1/lager/$campId/infos", body)

    suspend fun changeInfo(campId: String, sectionId: String, body: JsonObject): ApiResult<InfoSection> = api.patchJson("/api/v1/lager/$campId/infos/$sectionId", body)

    suspend fun deleteInfo(campId: String, section: InfoSection): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/infos/${section.id}?version=${section.version}")

    /** Neue Reihenfolge: die Stelle in der Liste ist die neue `reihenfolge` (alles oder nichts, je Eintrag mit der gesehenen `version`). */
    suspend fun reorderInfos(campId: String, sections: List<InfoSection>): ApiResult<Unit> =
        api.put(
            "/api/v1/lager/$campId/infos/reihenfolge",
            buildJsonObject { put("eintraege", JsonArray(sections.map { buildJsonObject { put("id", it.id); put("version", it.version) } })) },
        )

    // ---- Eigene To-dos ----

    suspend fun todos(campId: String): ApiResult<CampTodoList> = api.get("/api/v1/lager/$campId/todos")

    suspend fun addTodo(campId: String, text: String): ApiResult<CampTodo> = api.post("/api/v1/lager/$campId/todos", buildJsonObject { put("text", text.trim()) })

    suspend fun changeTodo(campId: String, todoId: String, body: JsonObject): ApiResult<CampTodo> = api.patchJson("/api/v1/lager/$campId/todos/$todoId", body)

    suspend fun deleteTodo(campId: String, todoId: String): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/todos/$todoId")

    private fun fieldsJson(fields: List<AttendanceField>): JsonElement = JsonArray(
        fields.map { buildJsonObject { put("lagertagId", it.dayId); put("tagesabschnittId", it.sectionId); put("wert", it.value.wire) } },
    )

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 10
        const val SUGGESTION_LIMIT = 8
    }
}

/** Ein Feld der Anwesenheit: Lagertag, Abschnitt des Tages und der Wert. */
data class AttendanceField(val dayId: String, val sectionId: String, val value: Attendance)
