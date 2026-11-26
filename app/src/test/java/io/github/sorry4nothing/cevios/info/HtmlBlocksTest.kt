package io.github.sorry4nothing.cevios.info

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Alle Seiten in diesen Tests sind erfunden. */
class HtmlBlocksTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val base = "https://app.example/".toHttpUrl()

    private fun parse(html: String) = HtmlBlocks.parse(html, base).blocks

    private fun text(block: Block): String = when (block) {
        is Block.Heading -> block.spans
        is Block.Paragraph -> block.spans
        is Block.ListItem -> block.spans
    }.joinToString("") { it.text }

    private fun spans(block: Block): List<Span> = when (block) {
        is Block.Heading -> block.spans
        is Block.Paragraph -> block.spans
        is Block.ListItem -> block.spans
    }

    private fun linkIn(html: String): LinkTarget? = spans(parse("<main><p>$html</p></main>").single()).single().link

    @Test
    fun headingsParagraphsAndListsBecomeBlocks() {
        val blocks = parse(
            """<main><h1>Titel</h1><h2>Kontakt</h2><p>Schreib an <strong>uns</strong> oder <em>ruf an</em>.</p>
            <ul><li>erstens</li><li>zweitens</li></ul><ol><li>eins</li><li>zwei</li></ol><h3>Mehr</h3><h4>Noch mehr</h4></main>""",
        )
        // Der erste Titel steht schon im Kopf des Bildschirms.
        assertEquals(
            listOf("Kontakt", "Schreib an uns oder ruf an.", "erstens", "zweitens", "eins", "zwei", "Mehr", "Noch mehr"),
            blocks.map(::text),
        )
        assertEquals(2, (blocks[0] as Block.Heading).level)
        assertEquals(listOf("•", "•", "1.", "2."), blocks.filterIsInstance<Block.ListItem>().map { it.marker })
        assertEquals(listOf(3, 4), blocks.filterIsInstance<Block.Heading>().drop(1).map { it.level })
        val paragraph = (blocks[1] as Block.Paragraph).spans
        assertTrue(paragraph.any { it.text == "uns" && it.bold })
        assertTrue(paragraph.any { it.text == "ruf an" && it.italic })
    }

    @Test
    fun onlyTheFirstTopHeadingIsDropped() {
        val blocks = parse("<main><h1>Titel</h1><p>a</p><h1>Zweiter</h1></main>")
        assertEquals(listOf("a", "Zweiter"), blocks.map(::text))
    }

    @Test
    fun navigationHeaderFooterScriptsAndStylesAreLeftOut() {
        val html = """<!doctype html><html><head><title>Seite</title><style>p{color:red}</style><script>var a = "<main>";</script></head>
            <body><a class="sprunglink" href="#inhalt">Zum Inhalt</a><header><nav><a href="/x">Menü</a></nav></header>
            <main id="inhalt"><nav><p>Auf dieser Seite</p></nav><p>Text</p><script>alert(1)</script><form><button>Senden</button></form>
            <img src="https://tracker.example/p.png" alt="Bild"><iframe src="https://tracker.example"></iframe></main>
            <footer><p>Fuss</p></footer></body></html>"""
        assertEquals(listOf("Text"), parse(html).map(::text))
    }

    @Test
    fun withoutMainTheBodyCountsButWithoutHeaderNavigationAndFooter() {
        val html = "<html><head><title>T</title></head><body><header><p>Kopf</p></header><p>Inhalt</p><nav><p>Menü</p></nav><footer>Fuss</footer></body></html>"
        assertEquals(listOf("Inhalt"), parse(html).map(::text))
    }

    @Test
    fun linksKeepOnlyWhatTheAppMayOpen() {
        assertEquals(LinkTarget.Web("https://example.org/a"), linkIn("""<a href="https://example.org/a">x</a>"""))
        assertEquals(LinkTarget.Web("https://other.example/x"), linkIn("""<a href="//other.example/x">x</a>"""))
        assertEquals(LinkTarget.Mail("leitung@example.org"), linkIn("""<a href="mailto:leitung@example.org?subject=Hallo">x</a>"""))
        assertEquals(LinkTarget.Phone("+41441234567"), linkIn("""<a href="tel:+41 44 123 45 67">x</a>"""))
        assertEquals(LinkTarget.Page(InfoPage.SUPPORT), linkIn("""<a href="/support">x</a>"""))
        assertEquals(LinkTarget.Page(InfoPage.PRIVACY), linkIn("""<a href="/datenschutz-website">x</a>"""))
        assertEquals(LinkTarget.Page(InfoPage.PRIVACY_APP), linkIn("""<a href="/datenschutz-cevi-os">x</a>"""))
        assertEquals(LinkTarget.Page(InfoPage.DELETE_ACCOUNT), linkIn("""<a href="https://app.example/konto-loeschen/">x</a>"""))
        // Eine unbekannte Seite auf demselben Host geht über https in den Browser.
        assertEquals(LinkTarget.Web("https://app.example/impressum"), linkIn("""<a href="/impressum">x</a>"""))
    }

    @Test
    fun dangerousOrUnknownLinksStayText() {
        for (href in listOf(
            "javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,x", "intent://x#Intent;end", "http://example.org/a", "ftp://example.org/a",
            "#abschnitt-11-1", "", "mailto:kein-mail", "mailto:a@b.c d", "tel:abc", "tel:12", "https://user:pw@example.org/",
        )) {
            val block = parse("""<main><p><a href="$href">Text</a></p></main>""").single()
            assertEquals("Text", text(block))
            assertNull("href=$href", spans(block).single().link)
        }
        // Ein Link ohne href ist kein Link.
        assertNull(linkIn("<a>x</a>"))
    }

    @Test
    fun brokenHtmlGivesWhatIsReadable() {
        val blocks = parse("""<main><p>Eins<p>Zwei <b>fett <i>beides</p></b> 3 < 4 und <div class="x>Rest</main></p></ul>""")
        assertEquals("Eins", text(blocks[0]))
        assertEquals("Zwei fett beides", text(blocks[1]))
        assertEquals("3 < 4 und", text(blocks[2]))
        // Abgeschnittene Tags und Attribute dürfen nicht abstürzen.
        parse("<main><p>a<a href=\"https://example.org")
        parse("<main><p>a<")
        parse("<")
        parse("<!-- offen")
        parse("<main><script>offen")
    }

    @Test
    fun emptyOrTextlessPagesAreEmpty() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   \n ").isEmpty())
        assertTrue(parse("<html><body></body></html>").isEmpty())
        assertTrue(parse("<main><div> </div><p> </p></main>").isEmpty())
    }

    @Test
    fun entitiesAreDecodedAndUnknownOnesStay() {
        val block = parse("<main><p>A &amp; B &auml; &#228; &#xE4; &unbekannt; &#0; x&nbsp;y</p></main>").single()
        assertEquals("A & B ä ä ä &unbekannt; � x y", text(block))
    }

    @Test
    fun lineBreaksStayAndWhitespaceCollapses() {
        val block = parse("<main><p>  Zeile  eins <br>\n  Zeile zwei<br><br> </p></main>").single()
        assertEquals("Zeile eins\nZeile zwei", text(block))
    }

    @Test
    fun implicitlyClosedItemsAndParagraphs() {
        val blocks = parse("<main><ul><li>a<li>b<ul><li>b1</ul><li>c</ul><p>x<p>y</main>")
        assertEquals(listOf("a", "b", "b1", "c", "x", "y"), blocks.map(::text))
        assertEquals(listOf(1, 1, 2, 1), blocks.filterIsInstance<Block.ListItem>().map { it.depth })
    }

    @Test
    fun aSecondParagraphInAnItemIsIndentedWithoutMarker() {
        val blocks = parse("<main><ul><li><p>eins</p><p>weiter</p></li></ul></main>").filterIsInstance<Block.ListItem>()
        assertEquals(listOf("•", null), blocks.map { it.marker })
    }

    @Test
    fun tableRowsAndDetailsBecomeParagraphs() {
        val blocks = parse("<main><table><tr><th>Art<th>Frist</tr><tr><td>Konto<td>30 Tage</table><details><summary>Frage</summary><p>Antwort</p></details></main>")
        assertEquals(listOf("Art – Frist", "Konto – 30 Tage", "Frage", "Antwort"), blocks.map(::text))
        assertEquals(listOf("Art", "Frist"), spans(blocks[0]).filter { it.bold }.map { it.text })
        assertTrue(spans(blocks[2]).all { it.bold })
    }

    @Test
    fun veryDeepNestingDoesNotCrash() {
        val html = "<main>" + "<div>".repeat(20_000) + "Tief" + "</div>".repeat(20_000) + "</main>"
        assertEquals(listOf("Tief"), parse(html).map(::text))
    }

    @Test
    fun aLinkKeepsItsEmphasis() {
        val span = spans(parse("""<main><p><a href="/support"><strong>Support</strong></a></p></main>""").single()).single()
        assertTrue(span.bold)
        assertEquals(LinkTarget.Page(InfoPage.SUPPORT), span.link)
    }

    @Test
    fun theFixedPagesKnowTheirPaths() {
        assertEquals(InfoPage.PRIVACY, InfoPage.forPath("/datenschutz"))
        assertEquals(InfoPage.PRIVACY, InfoPage.forPath("/datenschutz-website/"))
        assertNull(InfoPage.forPath("/datenschutz-webseite"))
        assertNull(InfoPage.forPath("/"))
    }
}
