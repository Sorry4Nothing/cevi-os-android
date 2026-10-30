package io.github.sorry4nothing.cevios.net

import android.content.Context
import io.github.sorry4nothing.cevios.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
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
    baseUrl: String,
    private val http: OkHttpClient,
    val json: Json = Json { ignoreUnknownKeys = true },
    private val storage: SessionStorage? = null,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Meldet jedes 401 ausser dem der Anmeldung: Die Sitzung ist abgelaufen oder beendet. */
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    /** Vergisst das gespeicherte Cookie, auch ein halbes (`two_factor`). */
    fun forgetSession() {
        storage?.write(null)
    }

    /** Adresse einer Seite des Backends (z. B. `/datenschutz`), zum Öffnen im Browser. */
    fun pageUrl(path: String): String = request(path).build().url.toString()

    /** Anfrage an `path` (mit führendem `/`, Query erlaubt). Header und Cookie setzt der [SessionInterceptor]. */
    fun request(path: String): Request.Builder =
        Request.Builder().url(requireNotNull(base.resolve(path)) { "Ungültiger Pfad" })

    fun jsonBody(text: String): RequestBody = text.toRequestBody(JSON_TYPE)

    suspend inline fun <reified T> get(path: String): ApiResult<T> =
        send(request(path).get().build()) { json.decodeFromString<T>(it) }

    suspend inline fun <reified B, reified T> post(path: String, body: B): ApiResult<T> =
        send(request(path).post(jsonBody(json.encodeToString(body))).build()) { json.decodeFromString<T>(it) }

    /** PUT mit JSON-Body; die Antwort hat keinen Inhalt (204). */
    suspend inline fun <reified B> put(path: String, body: B): ApiResult<Unit> =
        send(request(path).put(jsonBody(json.encodeToString(body))).build()) { }

    /** PATCH mit JSON-Body; die Antwort hat keinen Inhalt (204). */
    suspend inline fun <reified B> patch(path: String, body: B): ApiResult<Unit> =
        send(request(path).patch(jsonBody(json.encodeToString(body))).build()) { }

    /** POST mit JSON-Body, ohne Interesse an der Antwort. */
    suspend inline fun <reified B> postAction(path: String, body: B): ApiResult<Unit> =
        send(request(path).post(jsonBody(json.encodeToString(body))).build()) { }

    /** PUT ohne Body; die Antwort hat keinen Inhalt (204). */
    suspend fun putWithoutBody(path: String): ApiResult<Unit> =
        send(request(path).put(ByteArray(0).toRequestBody()).build()) { }

    /** POST ohne Body; die Antwort hat keinen Inhalt (204). */
    suspend fun postWithoutBody(path: String): ApiResult<Unit> =
        send(request(path).post(ByteArray(0).toRequestBody()).build()) { }

    /** Für Antworten ohne Inhalt oder ohne Interesse am Inhalt: `decode = { }`. */
    suspend fun <T> send(request: Request, decode: (String) -> T): ApiResult<T> =
        suspendCancellableCoroutine { continuation ->
            val httpCall = http.newCall(request)
            continuation.invokeOnCancellation { httpCall.cancel() }
            httpCall.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val error = if (e.cause is GeneralSecurityException) ApiError.UNKNOWN else ApiError.NETWORK
                    continuation.resume(ApiResult.Failure(error))
                }

                // Darf nie werfen, sonst bleibt die Fortsetzung hängen.
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response.use { evaluate(it, decode) })
                }
            })
        }

    private fun <T> evaluate(response: Response, decode: (String) -> T): ApiResult<T> {
        if (!response.isSuccessful) {
            if (response.code == 401 && !response.request.url.encodedPath.startsWith(SIGN_IN_PATH)) _unauthorized.tryEmit(Unit)
            return ApiResult.Failure(ApiError.fromStatus(response.code), if (response.code == 400) errorCode(response) else null)
        }
        return try {
            ApiResult.Ok(decode(response.body.string()))
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.NETWORK)
        } catch (e: IllegalArgumentException) { // auch SerializationException
            ApiResult.Failure(ApiError.UNKNOWN)
        }
    }

    /** Der Fehlercode `{code, message}` des Servers; sonst `null`. Nur der Code zählt, `message` zeigt die App nie. */
    private fun errorCode(response: Response): String? = try {
        json.decodeFromString<ErrorBody>(response.body.string()).code
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    @Serializable
    private class ErrorBody(val code: String? = null)

    companion object {
        private const val SIGN_IN_PATH = "/api/auth/sign-in"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Ohne Weiterleitungen: OkHttp würde den Cookie-Header sonst auch an fremde Hosts mitschicken. */
        fun httpClient(
            storage: SessionStorage,
            appVersion: String,
        ): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(SessionInterceptor(storage, appVersion))
                .build()

        fun create(context: Context): ApiClient {
            val storage = KeystoreSessionStorage(context)
            return ApiClient(BuildConfig.API_BASE_URL, httpClient(storage, BuildConfig.VERSION_NAME), storage = storage)
        }
    }
}
