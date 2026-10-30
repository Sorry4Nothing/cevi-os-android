package io.github.sorry4nothing.cevios.net

import android.content.Context
import io.github.sorry4nothing.cevios.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.coroutines.resume

/** Dünner Zugang zur Cevi-OS-API. Antworten kommen als [ApiResult], nie als Exception. */
class ApiClient(
    basisUrl: String,
    private val http: OkHttpClient,
    val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val basis: HttpUrl = basisUrl.toHttpUrl()

    /** Anfrage an `pfad` (mit führendem `/`, Query erlaubt). Header und Cookie setzt der [SitzungInterceptor]. */
    fun anfrage(pfad: String): Request.Builder =
        Request.Builder().url(requireNotNull(basis.resolve(pfad)) { "Ungültiger Pfad" })

    fun jsonBody(text: String): RequestBody = text.toRequestBody(JSON_TYP)

    suspend inline fun <reified T> get(pfad: String): ApiResult<T> =
        senden(anfrage(pfad).get().build()) { json.decodeFromString<T>(it) }

    suspend inline fun <reified B, reified T> post(pfad: String, body: B): ApiResult<T> =
        senden(anfrage(pfad).post(jsonBody(json.encodeToString(body))).build()) { json.decodeFromString<T>(it) }

    /** Für Antworten ohne Inhalt oder ohne Interesse am Inhalt: `decode = { }`. */
    suspend fun <T> senden(anfrage: Request, decode: (String) -> T): ApiResult<T> =
        suspendCancellableCoroutine { fortsetzung ->
            val aufruf = http.newCall(anfrage)
            fortsetzung.invokeOnCancellation { aufruf.cancel() }
            aufruf.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val fehler = if (e.cause is GeneralSecurityException) ApiFehler.UNBEKANNT else ApiFehler.NETZWERK
                    fortsetzung.resume(ApiResult.Fehler(fehler))
                }

                // Darf nie werfen, sonst bleibt die Fortsetzung hängen.
                override fun onResponse(call: Call, response: Response) {
                    fortsetzung.resume(response.use { auswerten(it, decode) })
                }
            })
        }

    private fun <T> auswerten(antwort: Response, decode: (String) -> T): ApiResult<T> {
        if (!antwort.isSuccessful) return ApiResult.Fehler(ApiFehler.vonStatus(antwort.code))
        return try {
            ApiResult.Ok(decode(antwort.body.string()))
        } catch (e: IOException) {
            ApiResult.Fehler(ApiFehler.NETZWERK)
        } catch (e: IllegalArgumentException) { // auch SerializationException
            ApiResult.Fehler(ApiFehler.UNBEKANNT)
        }
    }

    companion object {
        private val JSON_TYP = "application/json; charset=utf-8".toMediaType()

        /** Ohne Weiterleitungen: OkHttp würde den Cookie-Header sonst auch an fremde Hosts mitschicken. */
        fun httpClient(speicher: SitzungSpeicher, appVersion: String): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(SitzungInterceptor(speicher, appVersion))
                .build()

        fun erstellen(context: Context): ApiClient = ApiClient(
            BuildConfig.API_BASE_URL,
            httpClient(KeystoreSitzungSpeicher(context), BuildConfig.VERSION_NAME),
        )
    }
}
