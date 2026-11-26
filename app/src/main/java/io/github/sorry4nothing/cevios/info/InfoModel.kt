package io.github.sorry4nothing.cevios.info

/** Die Seiten des Backends, die die App selbst zeigt (öffentlich, ohne Sitzung). Der Pfad ist die Adresse beim Server. */
enum class InfoPage(val path: String) {
    PRIVACY("/datenschutz"),
    PRIVACY_APP("/datenschutz-cevi-os"),
    SUPPORT("/support"),
    DELETE_ACCOUNT("/konto-loeschen"),
    ;

    companion object {
        /** Die Seite zu einem Pfad im Text einer anderen Seite; `/datenschutz-website` ist das Ziel der Weiterleitung von `/datenschutz`. */
        fun forPath(path: String): InfoPage? = when (path.trimEnd('/')) {
            "/datenschutz", "/datenschutz-website" -> PRIVACY
            "/datenschutz-cevi-os" -> PRIVACY_APP
            "/support" -> SUPPORT
            "/konto-loeschen" -> DELETE_ACCOUNT
            else -> null
        }
    }
}

/** Wohin ein Link im Text führt. Alles andere als diese vier Ziele bleibt Text ohne Link. */
sealed interface LinkTarget {
    /** Nur https, ohne Benutzerangabe in der Adresse. */
    data class Web(val url: String) : LinkTarget
    data class Mail(val address: String) : LinkTarget
    data class Phone(val number: String) : LinkTarget
    data class Page(val page: InfoPage) : LinkTarget
}

/** Ein Stück Text mit gleicher Auszeichnung. `"\n"` ist ein Zeilenumbruch. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val link: LinkTarget? = null)

sealed interface Block {
    /** `level` 1 bis 4. */
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>) : Block

    /** `marker` ist `null` bei einem weiteren Absatz im selben Listenpunkt. `depth` ab 1. */
    data class ListItem(val marker: String?, val spans: List<Span>, val depth: Int) : Block
}

/** Der Inhalt einer Seite als Blöcke. Leer, wenn die Seite keinen lesbaren Text hat. */
data class InfoDocument(val blocks: List<Block>)
