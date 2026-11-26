package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.AesGcmSealer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

private const val CAMP = "11111111-1111-4111-8111-111111111111"
private const val OTHER_CAMP = "22222222-2222-4222-8222-222222222222"
private const val P1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
private const val P2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
private const val P3 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
private const val GROUP = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"

class CountLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    @get:Rule val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private lateinit var dir: File
    private lateinit var cache: CampCache

    private fun setUp() {
        dir = folder.newFolder("lagercache")
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        cache = CampCache(dir, AesGcmSealer { key }) { clock }
    }

    private fun entry(id: String, name: String = "Name $id", checked: Boolean = false) = CountEntry(id = id, teilnehmerId = id, firstName = name, checked = checked)

    // ---- Auswahl ----

    @Test
    fun theDefaultSelectionIsTheParticipantsAndSeveralCanBeChosenAtOnce() {
        var selection = CountSelection()
        assertTrue(selection.has(CountSource.Participants))
        selection = selection.toggled(CountSource.Team).toggled(CountSource.Group(GROUP)).toggled(CountSource.Stage("Wölfe"))
        assertEquals(setOf(CountSource.Participants, CountSource.Team, CountSource.Group(GROUP), CountSource.Stage("Wölfe")), selection.sources)
    }

    @Test
    fun theLastChosenSourceCannotBeRemoved() {
        val selection = CountSelection(setOf(CountSource.Team))
        assertEquals(selection, selection.toggled(CountSource.Team))
        assertEquals(setOf(CountSource.Team), selection.toggled(CountSource.Team).sources)
        assertEquals(setOf(CountSource.Team), CountSelection().toggled(CountSource.Team).toggled(CountSource.Participants).sources)
    }

    @Test
    fun theQueriesAskOnlyForWhatIsNotAlreadyCovered() {
        assertEquals(listOf(CountQuery(team = false)), CountRules.queries(CountSelection()))
        assertEquals(listOf(CountQuery(team = false), CountQuery(team = true)), CountRules.queries(CountSelection(setOf(CountSource.Participants, CountSource.Team))))
        // Eine Gruppe sind ihre Teilnehmenden und ihre Leitenden; alle Teilnehmenden decken die ersten schon ab.
        assertEquals(
            listOf(CountQuery(team = false, groupId = GROUP), CountQuery(team = true, groupId = GROUP)),
            CountRules.queries(CountSelection(setOf(CountSource.Group(GROUP)))),
        )
        assertEquals(
            listOf(CountQuery(team = false), CountQuery(team = true, groupId = GROUP)),
            CountRules.queries(CountSelection(setOf(CountSource.Participants, CountSource.Group(GROUP)))),
        )
        assertEquals(
            listOf(CountQuery(team = true), CountQuery(team = false, stage = "Wölfe")),
            CountRules.queries(CountSelection(setOf(CountSource.Team, CountSource.Stage("Wölfe")))),
        )
    }

    // ---- Zählen ----

    @Test
    fun aPersonInTwoSourcesIsCountedOnce() {
        val a = CountPage(listOf(entry(P1), entry(P2)), null, 0, 2)
        val b = CountPage(listOf(entry(P2), entry(P3)), null, 0, 2)
        assertEquals(listOf(P1, P2, P3), CountRules.merge(listOf(a, b)).map { it.id })
    }

    @Test
    fun theOwnTicksDecideAndTheServerTickNever() {
        val entries = listOf(entry(P1, checked = true), entry(P2), entry(P3))
        val page = CountRules.page(entries, setOf(P2, "nicht-in-der-liste"))
        assertEquals(listOf(false, true, false), page.entries.map { it.checked })
        assertEquals(1, page.present)
        assertEquals(3, page.total)
        assertEquals(0, CountRules.page(entries, emptySet()).present)
    }

    @Test
    fun togglingAddsAndRemovesTheId() {
        val one = CountRules.toggle(emptySet(), P1)
        assertEquals(setOf(P1), one)
        assertEquals(setOf(P1, P2), CountRules.toggle(one, P2))
        assertEquals(emptySet<String>(), CountRules.toggle(one, P1))
    }

    @Test
    fun onlyIdsAreWrittenAndOnlyIdsAreRead() {
        val text = CountRules.encode(setOf(P2, P1))
        assertEquals("$P1\n$P2", text)
        assertEquals(setOf(P1, P2), CountRules.decode(text))
        assertEquals(setOf(P1), CountRules.decode("$P1\nKind Beispiel\n../../x\n\n"))
        assertEquals(emptySet<String>(), CountRules.decode(""))
    }

    // ---- Speicher auf dem Gerät ----

    @Test
    fun theTicksSurviveARestartAsLongAsTheCampExists() {
        setUp()
        CountStore(cache).save(CAMP, setOf(P1, P2))
        assertEquals(setOf(P1, P2), CountStore(cache).load(CAMP))
        assertEquals(emptySet<String>(), CountStore(cache).load(OTHER_CAMP))
    }

    @Test
    fun theFileOnDiskIsSealedAndHoldsNoNames() {
        setUp()
        CountStore(cache).save(CAMP, setOf(P1))
        val file = dir.walkTopDown().single { it.isFile }
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(raw.contains(P1))
        assertFalse(raw.contains("abzaehlen"))
        assertTrue(file.parentFile!!.name == CAMP)
    }

    @Test
    fun theTicksGoWhenTheCampIsNotInTheListAnyMoreOrIsClosed() {
        setUp()
        CountStore(cache).save(CAMP, setOf(P1))
        CountStore(cache).save(OTHER_CAMP, setOf(P2))
        // Die Liste kennt nur noch das offene Lager: das abgeschlossene oder gelöschte verliert seine Haken.
        cache.retainOnly(setOf(OTHER_CAMP))
        assertEquals(emptySet<String>(), CountStore(cache).load(CAMP))
        assertEquals(setOf(P2), CountStore(cache).load(OTHER_CAMP))
    }

    @Test
    fun theTicksGoWithAGoneCampAndWithSignOut() {
        setUp()
        CountStore(cache).save(CAMP, setOf(P1))
        CountStore(cache).save(OTHER_CAMP, setOf(P2))
        cache.deleteCamp(CAMP)
        assertEquals(emptySet<String>(), CountStore(cache).load(CAMP))
        assertEquals(setOf(P2), CountStore(cache).load(OTHER_CAMP))
        cache.clear()
        assertEquals(emptySet<String>(), CountStore(cache).load(OTHER_CAMP))
        assertTrue(dir.walkTopDown().none { it.isFile })
    }

    @Test
    fun oldTicksAreGoneAfterThirtyDays() {
        setUp()
        CountStore(cache).save(CAMP, setOf(P1))
        clock += 31L * 24 * 60 * 60 * 1000
        assertEquals(emptySet<String>(), CountStore(cache).load(CAMP))
        assertTrue(dir.walkTopDown().none { it.isFile })
    }

    @Test
    fun aFileMovedToAnotherCampDoesNotOpenAndBadKeysTouchNothing() {
        setUp()
        CountStore(cache).save(CAMP, setOf(P1))
        CountStore(cache).save(OTHER_CAMP, setOf(P2))
        val a = File(File(dir, CAMP), "local-abzaehlen.bin")
        val b = File(File(dir, OTHER_CAMP), "local-abzaehlen.bin")
        b.writeBytes(a.readBytes())
        assertEquals(emptySet<String>(), CountStore(cache).load(OTHER_CAMP))
        cache.storeLocal("..", "abzaehlen", "x")
        cache.storeLocal(CAMP, "../x", "x")
        assertNull(cache.loadLocal("..", "abzaehlen"))
        assertNull(cache.loadLocal(CAMP, "A B"))
    }

    @Test
    fun withoutACacheTheTicksStayInMemoryOnly() {
        CountStore(null).save(CAMP, setOf(P1))
        assertEquals(emptySet<String>(), CountStore(null).load(CAMP))
    }
}
