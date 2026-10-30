package io.github.sorry4nothing.cevios.programme

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
private data class Antwort(val status: String)

class ProgrammeApi(private val api: ApiClient) {
    /** Programme, die das Fenster `von` bis `bis` (Schweizer Kalendertage, inklusive) berühren. */
    suspend fun liste(von: LocalDate, bis: LocalDate): ApiResult<ProgrammListe> =
        api.get("/api/v1/programme?von=$von&bis=$bis")

    suspend fun lesen(id: String): ApiResult<Programm> = api.get("/api/v1/programme/$id")

    /** Meldet ein eigenes Kind an («ja») oder ab («nein»); eine frühere Antwort wird überschrieben. */
    suspend fun teilnahmeSetzen(id: String, kindId: String, ja: Boolean): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/teilnahmen/$kindId", antwort(ja))

    /** Dasselbe für die eigene Anmeldung als Leitung. */
    suspend fun leitungSetzen(id: String, ja: Boolean): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/leitung", antwort(ja))

    private fun antwort(ja: Boolean) = Antwort(if (ja) "ja" else "nein")
}
