package io.github.sorry4nothing.cevios.formulare

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult

/** Die Formulare der Website aus Sicht einer angemeldeten Person. Keine Route unter `/admin`: Antworten ansehen gibt es nur im Web. */
class FormsApi(private val api: ApiClient) {
    suspend fun list(): ApiResult<FormList> = api.get("/api/v1/formulare")

    suspend fun definition(id: String): ApiResult<FormDefinition> = api.get("/api/v1/formulare/$id")

    /** Sendet genau einmal; die App wiederholt nie von selbst. */
    suspend fun send(id: String, request: FormAnswerRequest): ApiResult<FormSent> = api.post("/api/v1/formulare/$id/antworten", request)
}
