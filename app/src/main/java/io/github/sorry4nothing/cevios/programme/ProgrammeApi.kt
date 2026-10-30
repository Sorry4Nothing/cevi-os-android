package io.github.sorry4nothing.cevios.programme

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import java.time.LocalDate

class ProgrammeApi(private val api: ApiClient) {
    /** Programme, die das Fenster `von` bis `bis` (Schweizer Kalendertage, inklusive) berühren. */
    suspend fun liste(von: LocalDate, bis: LocalDate): ApiResult<ProgrammListe> =
        api.get("/api/v1/programme?von=$von&bis=$bis")

    suspend fun lesen(id: String): ApiResult<Programm> = api.get("/api/v1/programme/$id")
}
