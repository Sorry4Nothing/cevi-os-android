package io.github.sorry4nothing.cevios.termine

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult

class TermineApi(private val api: ApiClient) {
    suspend fun linkStatus(): ApiResult<CalendarLinkStatus> = api.get("/api/v1/ich/kalender-link")

    /** Erzeugt den Link neu; ein aktiver wird sofort ungültig. Der Token steht nur in dieser Antwort. */
    suspend fun createLink(): ApiResult<CalendarLinkNew> = api.postWithoutBodyJson("/api/v1/ich/kalender-link")

    suspend fun revokeLink(): ApiResult<Unit> = api.delete("/api/v1/ich/kalender-link")

    /** Die ganze Adresse zum Abonnieren: Basisadresse der API vor den Pfad. */
    fun linkUrl(path: String): String = api.pageUrl(path)

    suspend fun proposalOptions(): ApiResult<ProposalOptions> = api.get("/api/v1/termin-vorschlaege/optionen")

    /** Die eigenen Vorschläge, neueste zuerst (eine Seite, höchstens 50). */
    suspend fun myProposals(): ApiResult<ProposalPage> = api.get("/api/v1/termin-vorschlaege/meine?limit=50")

    suspend fun propose(request: ProposalRequest): ApiResult<ProposalCreated> = api.post("/api/v1/termin-vorschlaege", request)

    /** Zieht den eigenen Vorschlag zurück, solange er offen ist. */
    suspend fun withdraw(id: String): ApiResult<Unit> = api.delete("/api/v1/termin-vorschlaege/$id")

    suspend fun event(id: String): ApiResult<EventForm> = api.get("/api/v1/anlaesse/$id")

    suspend fun register(id: String, request: EventRegistrationRequest): ApiResult<EventRegistration> =
        api.putJson("/api/v1/anlaesse/$id/anmeldung", request)

    suspend fun unregister(id: String): ApiResult<Unit> = api.delete("/api/v1/anlaesse/$id/anmeldung")

    suspend fun unregisterChild(id: String, childId: String): ApiResult<Unit> =
        api.delete("/api/v1/anlaesse/$id/anmeldung?kindId=$childId")

    /** Die aktiven Räume, nach Name. shortcut: eine Seite mit höchstens 100 Räumen, bei mehr braucht es `nach`. */
    suspend fun rooms(): ApiResult<RoomPage> = api.get("/api/v1/raeume?limit=100")

    suspend fun requestRoom(roomId: String, request: RoomRequestBody): ApiResult<RoomRequestCreated> =
        api.post("/api/v1/raeume/$roomId/anfragen", request)

    /** Die eigenen Anfragen in jedem Stand, neueste zuerst (eine Seite, höchstens 50). */
    suspend fun myRoomRequests(): ApiResult<RoomRequestPage> = api.get("/api/v1/raeume/anfragen/meine?limit=50")

    /** Zieht eine offene Anfrage zurück oder sagt eine bestätigte Buchung ab, solange die Zeit nicht vorbei ist. */
    suspend fun cancelRoomRequest(id: String): ApiResult<Unit> = api.delete("/api/v1/raeume/anfragen/$id")
}
