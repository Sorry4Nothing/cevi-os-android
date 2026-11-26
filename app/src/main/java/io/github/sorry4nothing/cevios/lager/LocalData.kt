package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.ResponseCache
import kotlinx.serialization.json.Json

/*
 * Lager-Daten liegen lokal nur, solange das Lager im Backend offen ist: Steht ein Lager nicht mehr in der Liste, ist es
 * abgeschlossen oder archiviert, oder antwortet es mit 404 oder 403, geht alles dazu vom Gerät.
 */

private val listJson = Json { ignoreUnknownKeys = true }

/** Die Lager, zu denen lokale Daten bleiben dürfen: offene und Übungslager, nicht abgeschlossene und nicht archivierte. */
fun List<Camp>.localIds(): Set<String> = filterNot { it.readOnly }.map { it.id }.toSet()

/**
 * Die frische Lagerliste ist da: Alles zu Lagern, die nicht mehr offen sind, wird gelöscht. Gespeichert wird nur die Liste der
 * offenen Lager (damit die Lagerwahl auch ohne Netz da ist), nie ein abgeschlossenes oder gelöschtes Lager.
 */
fun ResponseCache.applyList(camps: List<Camp>) {
    val open = camps.filterNot { it.readOnly }
    retainOnly(open.map { it.id }.toSet())
    store(CAMP_LIST_PATH, listJson.encodeToString(CampList(open)))
}

/** Die zuletzt gespeicherte Liste der offenen Lager; `null`, wenn es keine gibt. */
fun ResponseCache.loadList(): List<Camp>? = try {
    load(CAMP_LIST_PATH)?.let { listJson.decodeFromString<CampList>(it.text).camps }
} catch (e: IllegalArgumentException) { // auch SerializationException
    null
}

/** Ob die Antwort heisst: Das Lager gibt es für diese Person nicht (mehr). */
fun ApiResult<*>.campGone(): Boolean = this is ApiResult.Failure && (error == ApiError.NOT_FOUND || error == ApiError.FORBIDDEN)
