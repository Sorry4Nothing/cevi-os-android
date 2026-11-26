package io.github.sorry4nothing.cevios.net

/** Eine zwischengespeicherte Antwort und der Zeitpunkt (Millisekunden seit 1970), zu dem sie vom Server kam. */
data class CachedBody(val text: String, val savedAt: Long)

/**
 * Zwischenspeicher für Antworten, die ohne Netz noch einmal gezeigt werden (zuletzt geladener Stand). Er gilt nur für Lager-Daten
 * und nur, solange es das Lager gibt: [deleteCamp], [retainOnly] und [clear] sind die Löschwege. Nichts davon wirft eine Ausnahme.
 */
interface ResponseCache {
    /** Ob die Antwort dieses Pfades gespeichert werden darf (nur Lese-Routen mit Arbeitsinhalten, keine Personenlisten). */
    fun accepts(path: String): Boolean

    fun store(path: String, body: String)

    fun load(path: String): CachedBody?

    /** Löscht alles zu diesem Lager. */
    fun deleteCamp(campId: String)

    /** Löscht alles zu Lagern, die nicht in `campIds` stehen. */
    fun retainOnly(campIds: Set<String>)

    /** Löscht alles (Abmelden, Sitzungsende). */
    fun clear()
}
