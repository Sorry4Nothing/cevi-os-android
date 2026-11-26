package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Die Routen des Lager-Checks «Sind wir bereit?». Der Server rechnet den Check bei jeder Anfrage und speichert nichts. Die
 * eigenen Punkte schreiben über den Sync-Kern, aber die Warteschlange kennt dafür keine Operation und der Vertrag keine
 * Idempotenz-Kennung: Sie gehen darum direkt hinaus und brauchen Netz.
 */
class CheckApi(private val api: ApiClient) {
    suspend fun check(campId: String): ApiResult<CampCheck> = api.get("/api/v1/lager/$campId/check")

    suspend fun points(campId: String): ApiResult<CheckItems> = api.get("/api/v1/lager/$campId/check/punkte")

    suspend fun addPoint(campId: String, title: String): ApiResult<CheckItem> =
        api.post("/api/v1/lager/$campId/check/punkte", buildJsonObject { put("titel", title.trim()) })

    /** Ändert Titel und/oder Haken; `version` ist die zuletzt gesehene. */
    suspend fun changePoint(campId: String, pointId: String, version: Long, title: String? = null, done: Boolean? = null): ApiResult<CheckItem> =
        api.patchJson(
            "/api/v1/lager/$campId/check/punkte/$pointId",
            buildJsonObject {
                put("version", version)
                title?.let { put("titel", it.trim()) }
                done?.let { put("erledigt", it) }
            },
        )

    suspend fun deletePoint(campId: String, pointId: String, version: Long): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/check/punkte/$pointId?version=$version")
}
