package io.github.sorry4nothing.cevios.attendance

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.Serializable

/** `da`: war da (`true`), war nicht da (`false`) oder zurückgenommen, also nicht erfasst (`null`, wird als `null` gesendet). */
@Serializable
private data class RecordRequest(val da: Boolean?)

class AttendanceApi(private val api: ApiClient) {
    /** Die letzten fünf vergangenen Anlässe der Stufe und der kommende; nur Admins sowie Leitende und Stufenleitungen der Stufe. */
    suspend fun overview(ageGroupId: String): ApiResult<AttendanceOverview> = api.get("/api/v1/stufen/$ageGroupId/anwesenheit")

    /** Hält fest, ob ein Kind da war; ein früherer Stand wird überschrieben. Der Server nimmt es von Beginn bis 14 Tage danach an. */
    suspend fun recordChild(ageGroupId: String, programId: String, childId: String, present: Boolean?): ApiResult<Unit> =
        api.put("/api/v1/stufen/$ageGroupId/anwesenheit/$programId/kinder/$childId", RecordRequest(present))

    /** Dasselbe für eine Leitungsperson der Stufe. */
    suspend fun recordLeader(ageGroupId: String, programId: String, userId: String, present: Boolean?): ApiResult<Unit> =
        api.put("/api/v1/stufen/$ageGroupId/anwesenheit/$programId/leitende/$userId", RecordRequest(present))
}
