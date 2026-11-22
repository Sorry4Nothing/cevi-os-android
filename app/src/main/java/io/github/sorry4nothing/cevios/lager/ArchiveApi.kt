package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Die Routen von «Ablage und Querschnitt»: Zeitplan, Dokumente, Adressbuch, Suche und Aushänge. Dateien liegen am Ablageort
 * (SharePoint): Der Server liefert nur den Link, nie den Inhalt, und die App öffnet genau diesen Link. Der Server kennt für diese
 * Objekte keine Operationen der Offline-Warteschlange: Änderungen gehen direkt hinaus und brauchen Netz.
 */
class ArchiveApi(private val api: ApiClient) {
    // ---- Zeitplan ----

    suspend fun schedule(campId: String): ApiResult<Schedule> = api.get("/api/v1/lager/$campId/zeitplan")

    /** Nur Lagerleitung: Tage von Beginn bis Ende, die es noch nicht gibt, und die Standard-Abschnitte. Ändert nichts. */
    suspend fun proposal(campId: String): ApiResult<ScheduleProposal> = api.get("/api/v1/lager/$campId/zeitplan/vorschlag")

    /** Legt die Tage und Abschnitte des Vorschlags an; was es schon gibt, überspringt der Server. */
    suspend fun adoptProposal(campId: String, proposal: ScheduleProposal): ApiResult<Unit> =
        api.postAction(
            "/api/v1/lager/$campId/zeitplan/uebernehmen",
            buildJsonObject {
                if (proposal.days.isNotEmpty()) put("lagertage", JsonArray(proposal.days.map { buildJsonObject { put("datum", it.date) } }))
                if (proposal.sections.isNotEmpty()) put("tagesabschnitte", JsonArray(proposal.sections.map { buildJsonObject { put("name", it.name); put("reihenfolge", it.order) } }))
            },
        )

    // ---- Dokumente ----

    /** Alle Dokumente des Lagers, neueste zuerst. Eine Seite kann leer sein und trotzdem einen Cursor haben; die App fragt weiter. */
    suspend fun documents(campId: String): ApiResult<List<CampDocument>> {
        val all = mutableListOf<CampDocument>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/dokumente?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<DocumentPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.documents
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Legt die Verknüpfung auf die Datei an (der Server prüft den Link streng) und gibt ihre ID zurück. */
    suspend fun addFile(campId: String, name: String, link: String): ApiResult<CampFile> =
        api.post("/api/v1/lager/$campId/dateien", buildJsonObject { put("anzeigename", name.trim()); put("link", link.trim()) })

    suspend fun addDocument(campId: String, title: String, fileId: String, teamVisible: Boolean): ApiResult<CampDocument> =
        api.post("/api/v1/lager/$campId/dokumente", buildJsonObject { put("titel", title.trim()); put("dateiId", fileId); put("teamSichtbar", teamVisible) })

    suspend fun deleteDocument(campId: String, documentId: String, version: Long): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/dokumente/$documentId?version=$version")

    // ---- Adressbuch ----

    suspend fun contacts(campId: String): ApiResult<List<Contact>> {
        val all = mutableListOf<Contact>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/adressbuch?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<ContactPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.contacts
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun addContact(campId: String, body: JsonObject): ApiResult<Contact> = api.post("/api/v1/lager/$campId/adressbuch", body)

    suspend fun changeContact(campId: String, contactId: String, body: JsonObject): ApiResult<Contact> =
        api.patchJson("/api/v1/lager/$campId/adressbuch/$contactId", body)

    suspend fun deleteContact(campId: String, contactId: String, version: Long): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/adressbuch/$contactId?version=$version")

    // ---- Suche ----

    /** Der Server filtert nach Rolle und versteht Wortstämme und kleine Tippfehler (höchstens 50 Treffer, die besten zuerst). */
    suspend fun search(campId: String, query: String): ApiResult<SearchResult> =
        api.get("/api/v1/lager/$campId/suche?q=" + URLEncoder.encode(query.trim(), "UTF-8"))

    // ---- Aushänge ----

    /** Das PDF eines Aushangs; der Server erzeugt es beim Aufruf und speichert es nirgends. */
    suspend fun printout(campId: String, printout: Printout): ApiResult<ByteArray> =
        api.getBytes("/api/v1/lager/$campId/aushaenge/${printout.wire}", MAX_PDF_BYTES)

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 10
        const val MAX_PDF_BYTES = 10_000_000
    }
}
