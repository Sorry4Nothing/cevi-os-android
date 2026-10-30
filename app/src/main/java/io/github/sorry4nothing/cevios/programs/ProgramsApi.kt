package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
private data class RsvpRequest(val status: String)

class ProgramsApi(private val api: ApiClient) {
    /** Programme, die das Fenster `from` bis `until` (Schweizer Kalendertage, inklusive) berühren. */
    suspend fun list(from: LocalDate, until: LocalDate): ApiResult<ProgramList> =
        api.get("/api/v1/programme?von=$from&bis=$until")

    suspend fun read(id: String): ApiResult<Program> = api.get("/api/v1/programme/$id")

    /** Meldet ein eigenes Kind an («ja») oder ab («nein»); eine frühere Antwort wird überschrieben. */
    suspend fun setAttendance(id: String, childId: String, attending: Boolean): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/teilnahmen/$childId", rsvpBody(attending))

    /** Dasselbe für die eigene Anmeldung als Leitung. */
    suspend fun setLeaderRsvp(id: String, attending: Boolean): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/leitung", rsvpBody(attending))

    /** Setzt Stufen und Sichtbarkeit; die App schickt den vollen neuen Stand, wie die API es verlangt. */
    suspend fun assign(id: String, request: AssignmentRequest): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/zuordnung", request)

    suspend fun ageGroups(): ApiResult<AgeGroupList> = api.get("/api/v1/stufen")

    suspend fun profile(): ApiResult<Profile> = api.get("/api/v1/ich")

    private fun rsvpBody(attending: Boolean) = RsvpRequest(if (attending) "ja" else "nein")
}
