package io.github.sorry4nothing.cevios.net

import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Ort, an dem die Sitzung als `name=wert; name2=wert2` liegt (auf dem Gerät verschlüsselt). */
interface SitzungSpeicher {
    fun lesen(): String?
    fun schreiben(wert: String?)
}

/**
 * Setzt `X-App-Version`, `Origin` und das gespeicherte Cookie bei jedem Aufruf und merkt sich die Cookies
 * aus den Antworten. Wirft nur IOException, sonst stürzt OkHttp auf seinem Thread ab.
 */
class SitzungInterceptor(
    private val speicher: SitzungSpeicher,
    private val appVersion: String,
) : Interceptor {
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val anfrage = chain.request().newBuilder()
            .header("X-App-Version", appVersion)
            .header("Origin", HERKUNFT)
        synchronized(lock) { speicher.lesen() }?.let { anfrage.header("Cookie", it) }

        val antwort = chain.proceed(anfrage.build())

        val gesetzt = antwort.headers("Set-Cookie")
        if (gesetzt.isNotEmpty()) {
            synchronized(lock) {
                speicher.schreiben(zusammenfuehren(speicher.lesen(), antwort.request.url, gesetzt))
            }
        }
        return antwort
    }

    companion object {
        /** Das App-Schema, dem das Backend für POSTs mit Cookie vertraut. */
        const val HERKUNFT = "cevi-os://"
    }
}

/** Übernimmt `name=wert` aus den `Set-Cookie`-Headern, ohne Attribute. Abgelaufene Cookies (`Max-Age=0`) fallen weg. */
internal fun zusammenfuehren(aktuell: String?, url: HttpUrl, setCookies: List<String>): String? {
    val paare = linkedMapOf<String, String>()
    aktuell?.split("; ")?.forEach { paar ->
        val teile = paar.split("=", limit = 2)
        if (teile.size == 2) paare[teile[0]] = teile[1]
    }
    for (header in setCookies) {
        val cookie = Cookie.parse(url, header) ?: continue
        if (cookie.expiresAt < System.currentTimeMillis()) paare.remove(cookie.name) else paare[cookie.name] = cookie.value
    }
    return paare.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifEmpty { null }
}
