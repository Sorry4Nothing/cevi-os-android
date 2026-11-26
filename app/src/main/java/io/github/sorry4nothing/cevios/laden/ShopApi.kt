package io.github.sorry4nothing.cevios.laden

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult

/** Das Lädeli aus Sicht einer angemeldeten Person. Keine Route unter `/admin`: Die Verwaltung gibt es nur im Web. */
class ShopApi(private val api: ApiClient) {
    suspend fun shop(): ApiResult<ShopView> = api.get("/api/v1/laden")

    suspend fun order(request: ShopOrderRequest): ApiResult<ShopOrder> = api.post("/api/v1/laden/bestellungen", request)

    /** Die eigenen Bestellungen in jedem Stand, neueste zuerst. shortcut: eine Seite mit höchstens 100, bei mehr braucht es `nach`. */
    suspend fun myOrders(): ApiResult<ShopOrderPage> = api.get("/api/v1/laden/bestellungen/meine?limit=100")

    /** Storniert die eigene Bestellung, solange sie `offen` ist (sonst 409 `STATUS_UNGUELTIG`). */
    suspend fun cancel(id: String): ApiResult<Unit> = api.delete("/api/v1/laden/bestellungen/$id")
}
