package io.github.sorry4nothing.cevios.info

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Höchste Grösse einer Seite; der Server liefert sie öffentlich, sie ist aber kein Grund, den Speicher zu füllen. */
internal const val MAX_PAGE_BYTES = 512 * 1024

/** Datenschutz, Support und «Konto löschen lassen»: öffentliche Seiten des Backends, die die App selbst darstellt. */
class InfoApi(private val api: ApiClient) {
    suspend fun page(page: InfoPage): ApiResult<InfoDocument> =
        when (val html = api.getPage(page.path, MAX_PAGE_BYTES)) {
            is ApiResult.Failure -> html
            // Das Umsetzen läuft nicht auf dem Hauptthread; der Text bleibt ohne Protokoll.
            is ApiResult.Ok -> withContext(Dispatchers.Default) {
                ApiResult.Ok(HtmlBlocks.parse(html.value, api.pageUrl(page.path).toHttpUrl()))
            }
        }
}
