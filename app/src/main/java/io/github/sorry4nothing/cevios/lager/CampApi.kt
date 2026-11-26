package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.ResponseCache

/** Die Lager-Routen des Grundgerüsts. Jedes Modul (Aufgaben, Pinnwand, …) bringt seine eigenen Aufrufe mit. */
class CampApi(private val api: ApiClient) : QueueSender {
    /** Der Zwischenspeicher mit dem zuletzt geladenen Stand der Lager; `null`, wenn der Client keinen hat. */
    val cache: ResponseCache? get() = api.cache

    /** Meine Lager mit meiner Rolle, neueste zuerst. */
    suspend fun list(): ApiResult<CampList> = api.get("/api/v1/lager")

    suspend fun read(id: String): ApiResult<Camp> = api.get("/api/v1/lager/$id")

    /** Nur Lagerleitung: Katalog der Rechte mit Standard und aktuellem Wert je Rolle. */
    suspend fun rights(id: String): ApiResult<CampRights> = api.get("/api/v1/lager/$id/rechte")

    /** Aktuelle Folgenummer und lesbare Objekttypen; der Ausgangspunkt für den Feed. */
    suspend fun feedState(id: String): ApiResult<FeedState> = api.get("/api/v1/lager/$id/aenderungen")

    /** Einträge nach der Folgenummer `since`. 410 (`ApiError.FEED_RELOAD`) heisst: Listen neu laden. */
    suspend fun feed(id: String, since: Long): ApiResult<FeedPage> = api.get("/api/v1/lager/$id/aenderungen?seit=$since")

    /** Schickt gesammelte Änderungen (höchstens 100 je Anfrage); eine Wiederholung derselben `clientOpId` schreibt nichts doppelt. */
    override suspend fun sendQueue(id: String, operations: List<QueueOperation>): ApiResult<QueueResponse> =
        api.post("/api/v1/lager/$id/aenderungen", QueueRequest(operations))

    /** Offener Ereignisstrom; `onLine` bekommt jede Zeile. Endet mit der Verbindung. */
    suspend fun events(id: String, lastEventId: Long?, onLine: suspend (String) -> Unit): ApiResult<Unit> =
        api.readStream("/api/v1/lager/$id/ereignisse", lastEventId, onLine)

    /** Ein Punkt je Bereich, ohne Inhalte; die Person muss nichts mitgeben. */
    suspend fun news(): ApiResult<CampNews> = api.get("/api/v1/lager/neuigkeiten")

    suspend fun unread(id: String): ApiResult<UnreadCount> = api.get("/api/v1/lager/$id/benachrichtigungen/ungelesen")

    /** In welchem Lager liegt das Objekt, das ein Push nennt? 404, wenn die Person es nicht (mehr) sehen darf. */
    suspend fun resolve(type: String, id: String): ApiResult<PushResolution> = api.get("/api/v1/lager/aufloesen/$type/$id")
}
