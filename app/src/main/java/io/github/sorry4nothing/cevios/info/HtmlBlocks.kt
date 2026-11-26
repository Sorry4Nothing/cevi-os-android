package io.github.sorry4nothing.cevios.info

import okhttp3.HttpUrl

/**
 * Macht aus der HTML-Seite des Backends lesbare Blöcke. Kein Browser, keine Bibliothek: Der Umsetzer kennt nur, was die Seiten
 * des Backends brauchen (Überschriften, Absätze, Listen, fett, kursiv, Zeilenumbruch, Links, Tabellenzeilen als Absatz) und lässt
 * alles andere weg: Skripte, Stile, Kopf, Navigation, Fuss, Formulare, Bilder und eingebettete Inhalte. Kaputtes HTML ergibt
 * höchstens weniger Text, nie einen Absturz.
 *
 * shortcut: Tabellen, Zitate und Aufklapplisten erscheinen als gewöhnliche Absätze; Bilder fehlen. Bei Bedarf hier ein Blockart ergänzen.
 */
object HtmlBlocks {
    /** `base` ist die Adresse der Seite: Gegen sie lösen sich Links auf und sie entscheidet, welche Links in der App bleiben. */
    fun parse(html: String, base: HttpUrl): InfoDocument {
        val root = contentRoot(buildTree(html))
        val walker = Walker(base)
        walker.walkChildren(root, Style())
        walker.flush()
        // Der Titel steht schon im Kopf des Bildschirms.
        val blocks = walker.blocks.let { if ((it.firstOrNull() as? Block.Heading)?.level == 1) it.drop(1) else it }
        return InfoDocument(blocks)
    }

    // --- Links ---

    private val MAIL = Regex("""[^\s@<>"',;]{1,64}@[^\s@<>"',;]{1,190}""")
    private val PHONE = Regex("""\+?[0-9 ()./-]{3,30}""")

    internal fun classifyLink(raw: String, base: HttpUrl): LinkTarget? {
        val href = raw.trim()
        if (href.isEmpty() || href.length > 2048 || href.startsWith("#")) return null
        val lower = href.lowercase()
        if (lower.startsWith("mailto:")) {
            val address = href.substring(7).substringBefore('?').trim()
            return if (MAIL.matches(address)) LinkTarget.Mail(address) else null
        }
        if (lower.startsWith("tel:")) {
            val number = href.substring(4).trim()
            if (!PHONE.matches(number)) return null
            val dialable = number.filter { it.isDigit() || it == '+' }
            return if (dialable.count { it.isDigit() } >= 3) LinkTarget.Phone(dialable) else null
        }
        // Alles ausser http und https (javascript:, data:, intent: …) löst sich nicht auf und bleibt Text.
        val url = base.resolve(href) ?: return null
        if (url.host == base.host && url.port == base.port) InfoPage.forPath(url.encodedPath)?.let { return LinkTarget.Page(it) }
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        return LinkTarget.Web(url.toString())
    }

    // --- Baum ---

    private sealed interface Node
    private class TextNode(val text: String) : Node
    private class Element(val tag: String, val href: String?) : Node {
        val children = ArrayList<Node>()
    }

    private const val MAX_ELEMENTS = 100_000
    private const val MAX_DEPTH = 100

    private val VOID = setOf("br", "hr", "img", "input", "meta", "link", "source", "wbr", "col", "area", "base", "embed", "track", "param")
    private val RAW_TEXT = setOf("script", "style")
    private val P_CLOSERS = setOf(
        "p", "div", "ul", "ol", "dl", "h1", "h2", "h3", "h4", "h5", "h6", "table", "blockquote", "section", "article", "aside",
        "header", "footer", "nav", "main", "form", "pre", "hr", "figure", "details", "address", "fieldset",
    )
    private val P_STOPS = setOf("td", "th", "li", "dt", "dd", "table", "button", "details")

    private fun isNameChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == ':'
    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C'

    private fun buildTree(html: String): Element {
        val root = Element("#root", null)
        val stack = ArrayList<Element>().apply { add(root) }
        var elements = 0
        var i = 0
        val n = html.length
        while (i < n) {
            if (html[i] != '<') {
                val end = html.indexOf('<', i).let { if (it < 0) n else it }
                addText(stack.last(), html.substring(i, end))
                i = end
                continue
            }
            val next = if (i + 1 < n) html[i + 1] else ' '
            when {
                html.startsWith("<!--", i) -> i = html.indexOf("-->", i + 4).let { if (it < 0) n else it + 3 }
                next == '!' || next == '?' -> i = html.indexOf('>', i).let { if (it < 0) n else it + 1 }
                next == '/' -> {
                    var j = i + 2
                    while (j < n && isNameChar(html[j])) j++
                    val name = html.substring(i + 2, j).lowercase()
                    i = html.indexOf('>', j).let { if (it < 0) n else it + 1 }
                    if (name.isNotEmpty()) closeElement(stack, name)
                }
                next in 'a'..'z' || next in 'A'..'Z' -> {
                    var j = i + 1
                    while (j < n && isNameChar(html[j])) j++
                    val name = html.substring(i + 1, j).lowercase()
                    var href: String? = null
                    var selfClosing = false
                    while (j < n) {
                        while (j < n && isSpace(html[j])) j++
                        if (j >= n) break
                        val c = html[j]
                        if (c == '>') { j++; break }
                        if (c == '/') {
                            if (j + 1 < n && html[j + 1] == '>') selfClosing = true
                            j++
                            continue
                        }
                        val nameStart = j
                        while (j < n && !isSpace(html[j]) && html[j] != '=' && html[j] != '>' && html[j] != '/') j++
                        if (j == nameStart) { j++; continue }
                        val attr = html.substring(nameStart, j).lowercase()
                        while (j < n && isSpace(html[j])) j++
                        var value = ""
                        if (j < n && html[j] == '=') {
                            j++
                            while (j < n && isSpace(html[j])) j++
                            if (j < n && (html[j] == '"' || html[j] == '\'')) {
                                val quote = html[j]
                                val close = html.indexOf(quote, j + 1).let { if (it < 0) n else it }
                                value = html.substring(j + 1, close)
                                j = minOf(close + 1, n)
                            } else {
                                val start = j
                                while (j < n && !isSpace(html[j]) && html[j] != '>') j++
                                value = html.substring(start, j)
                            }
                        }
                        if (attr == "href" && href == null) href = decodeEntities(value)
                    }
                    i = j
                    if (name in RAW_TEXT && !selfClosing) {
                        // Inhalt von script und style ist kein Text und kein Markup: überspringen.
                        val close = html.indexOf("</$name", i, ignoreCase = true)
                        i = if (close < 0) n else html.indexOf('>', close).let { if (it < 0) n else it + 1 }
                        continue
                    }
                    if (++elements > MAX_ELEMENTS) { i = n; continue }
                    closeImplied(stack, name)
                    val element = Element(name, href)
                    stack.last().children += element
                    if (name !in VOID && !selfClosing && stack.size < MAX_DEPTH) stack += element
                }
                else -> { addText(stack.last(), "<"); i++ }
            }
        }
        return root
    }

    private fun addText(parent: Element, raw: String) {
        val text = collapse(decodeEntities(raw))
        if (text.isNotEmpty()) parent.children += TextNode(text)
    }

    /** Folgen von Leerraum (nicht das geschützte Leerzeichen) werden zu einem Leerzeichen. */
    private fun collapse(text: String): String {
        val out = StringBuilder(text.length)
        var space = false
        for (c in text) {
            if (isSpace(c)) {
                if (!space) out.append(' ')
                space = true
            } else {
                out.append(c)
                space = false
            }
        }
        return out.toString()
    }

    private fun closeElement(stack: ArrayList<Element>, name: String) {
        val index = stack.indexOfLast { it.tag == name }
        if (index >= 1) while (stack.size > index) stack.removeAt(stack.lastIndex)
    }

    /** Ein neues `li`, `tr`, `td` oder ein Block schliesst, was HTML nach den Regeln der Sprache selbst schliesst. */
    private fun closeImplied(stack: ArrayList<Element>, name: String) {
        val (closes, stops) = when (name) {
            "li" -> setOf("li") to setOf("ul", "ol")
            "dt", "dd" -> setOf("dt", "dd") to setOf("dl")
            "tr" -> setOf("tr") to setOf("table")
            "td", "th" -> setOf("td", "th") to setOf("tr", "table")
            in P_CLOSERS -> setOf("p") to P_STOPS
            else -> return
        }
        for (k in stack.lastIndex downTo 1) {
            val tag = stack[k].tag
            if (tag in closes) {
                while (stack.size > k) stack.removeAt(stack.lastIndex)
                return
            }
            if (tag in stops) return
        }
    }

    private fun contentRoot(root: Element): Element {
        for (tag in listOf("main", "article", "body")) find(root, tag)?.let { return it }
        return root
    }

    private fun find(element: Element, tag: String): Element? {
        for (child in element.children) {
            if (child !is Element) continue
            if (child.tag == tag) return child
            find(child, tag)?.let { return it }
        }
        return null
    }

    // --- Zeichen ---

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "shy" to "",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›",
        "bdquo" to "„", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’", "copy" to "©", "reg" to "®",
        "times" to "×", "middot" to "·", "bull" to "•", "euro" to "€",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß",
    )

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val end = text.indexOf(';', i + 1)
                if (end in (i + 2)..(i + 10)) {
                    val name = text.substring(i + 1, end)
                    val decoded = if (name.startsWith("#")) numeric(name) else ENTITIES[name]
                    if (decoded != null) {
                        out.append(decoded)
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun numeric(name: String): String? {
        val hex = name.startsWith("#x") || name.startsWith("#X")
        val code = name.substring(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10) ?: return null
        return if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else "�"
    }

    // --- Blöcke ---

    private data class Style(val bold: Boolean = false, val italic: Boolean = false, val link: LinkTarget? = null)

    private val SKIP = setOf(
        "head", "script", "style", "noscript", "template", "nav", "header", "footer", "form", "button", "select", "textarea",
        "input", "svg", "math", "iframe", "object", "embed", "canvas", "audio", "video", "picture", "img", "dialog", "title", "meta", "link",
    )
    private val BLOCK = setOf(
        "p", "div", "section", "article", "aside", "main", "body", "blockquote", "details", "dl", "dt", "dd", "table", "thead", "tbody",
        "tfoot", "caption", "figure", "figcaption", "address", "fieldset", "legend", "pre", "hgroup", "center", "html", "#root",
    )

    private class Walker(val base: HttpUrl) {
        val blocks = ArrayList<Block>()
        private val run = ArrayList<Span>()
        private val lists = ArrayList<IntArray?>() // je offene Liste: Zähler bei `ol`, sonst `null`
        private var pendingMarker: String? = null

        fun add(text: String, style: Style) {
            if (text.isNotEmpty()) run += Span(text, style.bold, style.italic, style.link)
        }

        fun flush(level: Int? = null) {
            val spans = tidy(run)
            run.clear()
            if (spans.isEmpty()) return
            blocks += when {
                level != null -> Block.Heading(level, spans)
                lists.isNotEmpty() -> Block.ListItem(pendingMarker.also { pendingMarker = null }, spans, lists.size)
                else -> Block.Paragraph(spans)
            }
        }

        fun walkChildren(element: Element, style: Style) {
            for (child in element.children) walk(child, style)
        }

        private fun walk(node: Node, style: Style) {
            if (node is TextNode) {
                add(node.text, style)
                return
            }
            val element = node as Element
            val tag = element.tag
            when {
                tag in SKIP -> Unit
                tag == "br" -> add("\n", style)
                tag == "hr" -> flush()
                tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' -> {
                    flush()
                    walkChildren(element, style)
                    flush(minOf(tag[1] - '0', 4))
                }
                tag == "ul" || tag == "ol" -> {
                    flush()
                    lists += if (tag == "ol") IntArray(1) else null
                    walkChildren(element, style)
                    flush()
                    lists.removeAt(lists.lastIndex)
                    pendingMarker = null
                }
                tag == "li" -> {
                    flush()
                    val counter = lists.lastOrNull()
                    pendingMarker = if (counter != null) "${++counter[0]}." else "•"
                    walkChildren(element, style)
                    flush()
                    pendingMarker = null
                }
                tag == "tr" -> {
                    flush()
                    var first = true
                    for (child in element.children) {
                        if (child is Element && (child.tag == "td" || child.tag == "th")) {
                            if (!first) add(" – ", style)
                            first = false
                            walkChildren(child, if (child.tag == "th") style.copy(bold = true) else style)
                        } else {
                            walk(child, style)
                        }
                    }
                    flush()
                }
                tag == "summary" -> {
                    flush()
                    walkChildren(element, style.copy(bold = true))
                    flush()
                }
                tag in BLOCK -> {
                    flush()
                    walkChildren(element, style)
                    flush()
                }
                tag == "strong" || tag == "b" -> walkChildren(element, style.copy(bold = true))
                tag == "em" || tag == "i" -> walkChildren(element, style.copy(italic = true))
                tag == "a" -> {
                    val target = element.href?.let { classifyLink(it, base) }
                    walkChildren(element, if (target != null) style.copy(link = target) else style)
                }
                else -> walkChildren(element, style)
            }
        }

        /** Kein Leerraum am Rand und doppelt, Zeilenumbrüche ohne Leerzeichen daneben, gleiche Auszeichnung zusammengefasst. */
        private fun tidy(spans: List<Span>): List<Span> {
            val out = ArrayList<Span>()
            var atStart = true
            for (span in spans) {
                if (span.text == "\n") {
                    trimEnd(out)
                    if (out.isNotEmpty() && out.last().text != "\n") out += span
                    atStart = true
                    continue
                }
                var text = span.text
                if (atStart || out.last().text.endsWith(' ')) text = text.trimStart(' ')
                if (text.isEmpty()) continue
                out += span.copy(text = text)
                atStart = false
            }
            trimEnd(out)
            val merged = ArrayList<Span>()
            for (span in out) {
                val last = merged.lastOrNull()
                if (last != null && last.bold == span.bold && last.italic == span.italic && last.link == span.link && last.text != "\n" && span.text != "\n") {
                    merged[merged.lastIndex] = last.copy(text = last.text + span.text)
                } else {
                    merged += span
                }
            }
            return merged
        }

        private fun trimEnd(out: ArrayList<Span>) {
            while (out.isNotEmpty()) {
                val last = out.last()
                val text = if (last.text == "\n") "" else last.text.trimEnd(' ')
                if (text.isEmpty()) out.removeAt(out.lastIndex) else { out[out.lastIndex] = last.copy(text = text); return }
            }
        }
    }
}
