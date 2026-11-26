package io.github.sorry4nothing.cevios.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Warum ein Download nicht ging; die Oberfläche nennt es in einem Satz. */
enum class DownloadError {
    /** Der Link ist kein https-Link. */
    NOT_HTTPS,

    /** Kein Netz oder die Verbindung brach ab. */
    NETWORK,

    /** Der Server liefert eine Seite statt der Datei (zum Beispiel die Anmeldung des Ablageorts) oder verweigert sie. */
    NOT_A_FILE,

    /** Die Datei ist grösser als [LinkDownloader.MAX_BYTES]. */
    TOO_BIG,

    /** Die Datei liess sich auf dem Gerät nicht speichern. */
    STORAGE,

    /** Keine App auf dem Gerät öffnet diese Datei (kommt erst beim Öffnen). */
    NO_APP,
}

sealed interface DownloadResult {
    data class Done(val file: DownloadedFile) : DownloadResult
    data class Failed(val error: DownloadError) : DownloadResult
}

/**
 * Lädt eine Datei von dem Link, den die API für sie nennt (Verknüpfung auf den Ablageort, der Server liefert nie den Inhalt). Das ist
 * kein Aufruf der Cevi-OS-API: Der Client hier hat **keinen** Cookie und keinen Kopf der App, die Sitzung verlässt die App nie. Nur
 * https, Weiterleitungen nur auf https, höchstens [MAX_BYTES], keine Seite (`text/html`): Verlangt der Ablageort eine Anmeldung, geht
 * der Download nicht und die Person öffnet die Datei im Browser. Es wird nichts im Hintergrund geladen: nur auf Tipp.
 */
class LinkDownloader(
    private val downloads: Downloads,
    private val http: OkHttpClient = defaultClient(),
    /** Nur für Tests mit einem Server ohne TLS. */
    private val httpsOnly: Boolean = true,
) {
    suspend fun download(campId: String, link: String, name: String): DownloadResult = withContext(Dispatchers.IO) {
        val url = link.trim().toHttpUrlOrNull()
        if (url == null || (httpsOnly && !url.isHttps)) return@withContext DownloadResult.Failed(DownloadError.NOT_HTTPS)
        val call = http.newCall(Request.Builder().url(url).get().build())
        coroutineScope {
            val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { response ->
                    if (httpsOnly && !response.request.url.isHttps) return@use DownloadResult.Failed(DownloadError.NOT_HTTPS)
                    val type = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                    if (!response.isSuccessful || type == "text/html" || type == "application/xhtml+xml") return@use DownloadResult.Failed(DownloadError.NOT_A_FILE)
                    val length = response.body.contentLength()
                    if (length > MAX_BYTES) return@use DownloadResult.Failed(DownloadError.TOO_BIG)
                    try {
                        DownloadResult.Done(downloads.save(campId, Downloads.safeName(name, extensionOf(type, url.encodedPath)), response.body.byteStream(), MAX_BYTES))
                    } catch (e: TooBigException) {
                        DownloadResult.Failed(DownloadError.TOO_BIG)
                    } catch (e: StorageException) {
                        DownloadResult.Failed(DownloadError.STORAGE)
                    }
                }
            } catch (e: IOException) {
                ensureActive()
                DownloadResult.Failed(DownloadError.NETWORK)
            } finally {
                watcher.cancel()
            }
        }
    }

    companion object {
        /** 25 MB: mehr braucht kein Plan und keine Liste, und das Handy füllt sich nicht unbemerkt. */
        const val MAX_BYTES = 25L * 1024 * 1024

        private val TYPES = mapOf(
            "application/pdf" to "pdf", "text/calendar" to "ics", "image/jpeg" to "jpg", "image/png" to "png", "text/plain" to "txt", "text/csv" to "csv",
            "application/msword" to "doc", "application/vnd.ms-excel" to "xls", "application/vnd.ms-powerpoint" to "ppt",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
        )

        /** Die Endung für einen Namen ohne eigene: aus dem Typ der Antwort, sonst aus dem Pfad des Links. */
        internal fun extensionOf(type: String, path: String): String? =
            TYPES[type] ?: path.substringAfterLast('/').substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }

        /** Ohne Cookie-Speicher und ohne Kopf der App; Weiterleitungen von https auf http folgt er nicht. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
