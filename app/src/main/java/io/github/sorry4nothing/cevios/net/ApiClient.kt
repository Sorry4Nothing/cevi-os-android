package io.github.sorry4nothing.cevios.net

import android.content.Context
import io.github.sorry4nothing.cevios.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit
import java.security.GeneralSecurityException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Marke für Anfragen im Hintergrund (regelmässiges Nachladen, Bilder, «gelesen» melden): Sie zählen nicht in
 * [ApiClient.pending] und sperren die App nicht. Aufruf: `launch(Background()) { … }`.
 */
class Background : AbstractCoroutineContextElement(Background) {
    companion object Key : CoroutineContext.Key<Background>
}

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

    private val _forbidden = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Meldet jedes 403 unter `/api/v1`: Es kann «kein Recht» heissen oder «Passwortwechsel offen» (der Server sagt es nicht im Status). */
    val forbidden: SharedFlow<Unit> = _forbidden.asSharedFlow()

    private val _pending = MutableStateFlow(0)

    /** Anzahl der laufenden Anfragen, die eine Person ausgelöst hat (ohne [Background]); über 0 sperrt die App und zeigt den Ladekreis. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** Ob ein Sitzungs-Cookie da ist; ohne eins gibt es nichts zu entsperren. */
    fun hasSession(): Boolean = storage?.read() != null

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

    /** PUT mit JSON-Body, die Antwort hat einen Inhalt (200). */
    suspend inline fun <reified B, reified T> putJson(path: String, body: B): ApiResult<T> =
        send(request(path).put(jsonBody(json.encodeToString(body))).build()) { json.decodeFromString<T>(it) }

    /** PATCH mit JSON-Body, die Antwort hat einen Inhalt (200). */
    suspend inline fun <reified B, reified T> patchJson(path: String, body: B): ApiResult<T> =
        send(request(path).patch(jsonBody(json.encodeToString(body))).build()) { json.decodeFromString<T>(it) }

    /** POST mit JSON-Body, ohne Interesse an der Antwort. */
    suspend inline fun <reified B> postAction(path: String, body: B): ApiResult<Unit> =
        send(request(path).post(jsonBody(json.encodeToString(body))).build()) { }

    /** POST ohne Body, die Antwort hat einen Inhalt (201). */
    suspend inline fun <reified T> postWithoutBodyJson(path: String): ApiResult<T> =
        send(request(path).post(ByteArray(0).toRequestBody()).build()) { json.decodeFromString<T>(it) }

    /** PUT ohne Body; die Antwort hat keinen Inhalt (204). */
    suspend fun putWithoutBody(path: String): ApiResult<Unit> =
        send(request(path).put(ByteArray(0).toRequestBody()).build()) { }

    /** POST ohne Body; die Antwort hat keinen Inhalt (204). */
    suspend fun postWithoutBody(path: String): ApiResult<Unit> =
        send(request(path).post(ByteArray(0).toRequestBody()).build()) { }

    /** PUT mit den Bytes eines Bildes (der Typ ergibt sich beim Server aus den ersten Bytes); die Antwort hat keinen Inhalt (204). */
    suspend fun putBytes(path: String, bytes: ByteArray, type: String): ApiResult<Unit> =
        send(request(path).put(bytes.toRequestBody(type.toMediaType())).build()) { }

    /** DELETE ohne Body; die Antwort hat keinen Inhalt (204). */
    suspend fun delete(path: String): ApiResult<Unit> =
        send(request(path).delete().build()) { }

    /** GET einer Datei (Bild) als Bytes; mehr als `maxBytes` gilt als Fehler. */
    suspend fun getBytes(path: String, maxBytes: Int): ApiResult<ByteArray> =
        withContext(Background()) {
            sendRaw(request(path).get().build()) { response ->
                val source = response.body.source()
                source.request(maxBytes + 1L)
                if (source.buffer.size > maxBytes) throw IllegalArgumentException("Antwort zu gross")
                source.readByteArray()
            }
        }

    /** Für Antworten ohne Inhalt oder ohne Interesse am Inhalt: `decode = { }`. */
    suspend fun <T> send(request: Request, decode: (String) -> T): ApiResult<T> =
        sendRaw(request) { decode(it.body.string()) }

    /**
     * Liest einen offenen Textstrom (Server-Sent Events) Zeile für Zeile, bis er endet oder die Coroutine abbricht. Er zählt nicht
     * in [pending] (er ist offen, solange die Ansicht es ist). `lastEventId` geht als `Last-Event-ID` mit. Eine Antwort ohne
     * Erfolg gibt denselben Fehler wie jede andere Anfrage; ein abgebrochener oder getrennter Strom ist ein Netzfehler.
     */
    suspend fun readStream(path: String, lastEventId: Long?, onLine: suspend (String) -> Unit): ApiResult<Unit> = coroutineScope {
        val request = request(path).header("Accept", "text/event-stream")
            .apply { lastEventId?.let { header("Last-Event-ID", it.toString()) } }
            .get().build()
        // Der Server schickt alle 25 Sekunden einen Herzschlag; ohne Zeile in 70 Sekunden ist die Verbindung tot.
        val call = http.newBuilder().readTimeout(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS).build().newCall(request)
        val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
        try {
            withContext(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) return@withContext failure(response)
                        val source = response.body.source()
                        while (true) onLine(source.readUtf8Line() ?: break)
                        ApiResult.Ok(Unit)
                    }
                } catch (e: IOException) {
                    ensureActive()
                    ApiResult.Failure(ApiError.NETWORK)
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun <T> sendRaw(request: Request, decode: (Response) -> T): ApiResult<T> {
        val counted = coroutineContext[Background] == null
        if (counted) _pending.update { it + 1 }
        try {
            return suspendCancellableCoroutine { continuation ->
                val httpCall = http.newCall(request)
                continuation.invokeOnCancellation { httpCall.cancel() }
                httpCall.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val error = if (e.cause is GeneralSecurityException) ApiError.UNKNOWN else ApiError.NETWORK
                        continuation.resume(ApiResult.Failure(error))
                    }

                    // Darf nie ohne Antwort enden, sonst bleibt die Fortsetzung hängen: Ein Fehler beim Auswerten (auch ein Error)
                    // kommt als Ausnahme beim Aufrufer an, statt dass die Anfrage ewig wartet.
                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use { evaluate(it, decode) }
                        } catch (t: Throwable) {
                            continuation.resumeWithException(t)
                            return
                        }
                        continuation.resume(result)
                    }
                })
            }
        } finally {
            if (counted) _pending.update { it - 1 }
        }
    }

    private fun failure(response: Response): ApiResult.Failure {
        if (response.code == 401 && !response.request.url.encodedPath.startsWith(SIGN_IN_PATH)) _unauthorized.tryEmit(Unit)
        if (response.code == 403 && response.request.url.encodedPath.startsWith(API_PATH)) _forbidden.tryEmit(Unit)
        val body = if (response.code in CODE_STATUSES) errorBody(response) else null
        val read = body?.code
        // Bei 403 zählt nur der zweite Faktor; sonst heisst 403 schlicht «kein Recht», ohne Code.
        val code = if (response.code == 403 && read != SECOND_FACTOR_CODE) null else read
        val retryAfter = if (response.code == 429) response.header(RETRY_AFTER)?.toIntOrNull()?.coerceIn(1, MAX_RETRY_AFTER) else null
        val conflict = if (code == VERSION_CONFLICT_CODE && body?.current != null) VersionConflict(body.current, body.differing) else null
        return ApiResult.Failure(ApiError.from(response.code, code), code, retryAfter, conflict)
    }

    private fun <T> evaluate(response: Response, decode: (Response) -> T): ApiResult<T> {
        if (!response.isSuccessful) return failure(response)
        return try {
            ApiResult.Ok(decode(response))
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.NETWORK)
        } catch (e: IllegalArgumentException) { // auch SerializationException
            ApiResult.Failure(ApiError.UNKNOWN)
        } catch (e: RuntimeException) { // eine Antwort, die sich nicht auswerten lässt, ist ein unbekannter Fehler
            ApiResult.Failure(ApiError.UNKNOWN)
        }
    }

    /** Der Fehler `{code, message}` des Servers (bei Versionskonflikt mit `aktuell` und `abweichend`); sonst `null`. `message` zeigt die App nie. */
    private fun errorBody(response: Response): ErrorBody? = try {
        json.decodeFromString<ErrorBody>(response.body.string())
    } catch (e: IOException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    @Serializable
    private class ErrorBody(
        val code: String? = null,
        @kotlinx.serialization.SerialName("aktuell") val current: kotlinx.serialization.json.JsonObject? = null,
        @kotlinx.serialization.SerialName("abweichend") val differing: List<String> = emptyList(),
    )

    companion object {
        private const val SIGN_IN_PATH = "/api/auth/sign-in"
        /** Antworten, deren Fehlercode die App liest: 400, 403 (zweiter Faktor), 409, 410 (Feed) und 503 (Wartung). */
        private val CODE_STATUSES = setOf(400, 403, 409, 410, 503)
        private const val SECOND_FACTOR_CODE = "ZWEITER_FAKTOR_FEHLT"
        private const val VERSION_CONFLICT_CODE = "VERSION_KONFLIKT"
        private const val STREAM_TIMEOUT_SECONDS = 70L
        private const val API_PATH = "/api/v1/"

        /** Wartezeit in Sekunden bei 429; eine Stunde ist die Obergrenze, was der Server schickt, ist nicht vertrauenswürdig. */
        private const val RETRY_AFTER = "X-Retry-After"
        private const val MAX_RETRY_AFTER = 3600
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
