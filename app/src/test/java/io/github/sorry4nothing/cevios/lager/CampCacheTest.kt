package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.AesGcmSealer
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

private const val CAMP_A = "11111111-1111-4111-8111-111111111111"
private const val CAMP_B = "22222222-2222-4222-8222-222222222222"

@Serializable
private data class Row(val title: String)

private class NoSession : SessionStorage {
    override fun read(): String? = null
    override fun write(value: String?) = Unit
}

class CampCacheTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    @get:Rule val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private lateinit var dir: File
    private lateinit var cache: CampCache

    @Before
    fun setUp() {
        dir = folder.newFolder("lagercache")
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        cache = CampCache(dir, AesGcmSealer { key }) { clock }
    }

    private fun tasks(camp: String) = "/api/v1/lager/$camp/aufgaben?limit=50"

    private fun files(): List<File> = dir.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun savesAndReadsBackWithTime() {
        cache.store(tasks(CAMP_A), """{"titel":"Zelte zählen"}""")
        val loaded = cache.load(tasks(CAMP_A))
        assertEquals("""{"titel":"Zelte zählen"}""", loaded?.text)
        assertEquals(clock, loaded?.savedAt)
    }

    @Test
    fun fileOnDiskHasNoReadableContent() {
        cache.store(tasks(CAMP_A), "GEHEIMER-TITEL")
        val bytes = files().single().readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("GEHEIMER-TITEL"))
        assertFalse(files().single().name.contains("aufgaben"))
    }

    @Test
    fun brokenFileIsTreatedAsMissingAndRemoved() {
        cache.store(tasks(CAMP_A), "x")
        val file = files().single()
        file.writeBytes(file.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })
        assertNull(cache.load(tasks(CAMP_A)))
        assertFalse(file.exists())
    }

    @Test
    fun fileMovedToAnotherPathDoesNotOpen() {
        cache.store(tasks(CAMP_A), "x")
        cache.store(tasks(CAMP_B), "y")
        val fileA = File(dir, CAMP_A).listFiles()!!.single()
        val fileB = File(dir, CAMP_B).listFiles()!!.single()
        fileB.writeBytes(fileA.readBytes())
        assertNull(cache.load(tasks(CAMP_B)))
    }

    @Test
    fun oldAnswersAreGone() {
        cache.store(tasks(CAMP_A), "x")
        clock += 31L * 24 * 60 * 60 * 1000
        assertNull(cache.load(tasks(CAMP_A)))
        assertTrue(files().isEmpty())
    }

    @Test
    fun deletingACampLeavesTheOthers() {
        cache.store(tasks(CAMP_A), "a")
        cache.store(tasks(CAMP_B), "b")
        cache.deleteCamp(CAMP_A)
        assertNull(cache.load(tasks(CAMP_A)))
        assertEquals("b", cache.load(tasks(CAMP_B))?.text)
        assertFalse(File(dir, CAMP_A).exists())
    }

    @Test
    fun deletingWithAnInvalidIdTouchesNothing() {
        cache.store(tasks(CAMP_A), "a")
        cache.deleteCamp("..")
        cache.deleteCamp("")
        assertNotNull(cache.load(tasks(CAMP_A)))
    }

    @Test
    fun retainOnlyDeletesEveryOtherCampButKeepsTheList() {
        cache.store(tasks(CAMP_A), "a")
        cache.store(tasks(CAMP_B), "b")
        cache.store(CAMP_LIST_PATH, "[]")
        cache.retainOnly(setOf(CAMP_B))
        assertNull(cache.load(tasks(CAMP_A)))
        assertEquals("b", cache.load(tasks(CAMP_B))?.text)
        assertEquals("[]", cache.load(CAMP_LIST_PATH)?.text)
    }

    @Test
    fun clearDeletesEverything() {
        cache.store(tasks(CAMP_A), "a")
        cache.store(CAMP_LIST_PATH, "[]")
        cache.clear()
        assertTrue(files().isEmpty())
        assertNull(cache.load(tasks(CAMP_A)))
    }

    @Test
    fun onlyWorkContentIsCacheable() {
        listOf("aufgaben?limit=50", "aufgaben/abc", "aufgaben/abc/details", "start", "heute", "hoecks/abc/modus", "pinnwand?limit=20", "einkauf", "dokumente?limit=5", "")
            .forEach { assertTrue(it, CampCache.cacheable("/api/v1/lager/$CAMP_A/$it".trimEnd('/'))) }
        listOf(
            "teilnehmer", "zimmer", "fahrten", "gruppen", "anwesenheit/eigene", "abzaehlen?x=1", "diensttausch", "schichtplan", "mitglieder",
            "adressbuch", "eingang", "eingang/eigene", "suche?q=x", "benachrichtigungen", "aenderungen", "aenderungen?seit=3", "rechte",
            "notfall", "ereignisse", "einkauf/vorschlaege?q=x", "aufgaben/duplikate?titel=x", "hoecks/abc.ics",
        ).forEach { assertFalse(it, CampCache.cacheable("/api/v1/lager/$CAMP_A/$it")) }
        listOf(
            "/api/v1/lager", "/api/v1/lager/neuigkeiten", "/api/v1/lager/aufloesen/aufgabe/x", "/api/v1/ich", "/api/v1/lager/$CAMP_A?x=1",
            "/api/v1/lager/not-a-uuid/aufgaben", "/api/v1/lager/$CAMP_A/../$CAMP_B/aufgaben", "/api/v1/admin/lager/$CAMP_A",
        ).forEach { assertFalse(it, CampCache.cacheable(it)) }
    }

    @Test
    fun anUnsafePathIsNeverWritten() {
        cache.store("/api/v1/lager/../../x", "a")
        cache.store("/api/v1/lager/$CAMP_A/../x", "a")
        assertTrue(files().isEmpty())
    }

    @Test
    fun listKeepsOnlyOpenCampsAndDeletesTheRest() {
        cache.store(tasks(CAMP_A), "a")
        cache.store(tasks(CAMP_B), "b")
        val open = Camp(CAMP_A, "Sola", "2026-07-01", "2026-07-10", null, "aktiv", "leitung")
        val closed = Camp(CAMP_B, "Pfila", "2026-04-01", "2026-04-03", null, "abgeschlossen", "leitung")
        cache.applyList(listOf(open, closed))
        assertNotNull(cache.load(tasks(CAMP_A)))
        assertNull(cache.load(tasks(CAMP_B)))
        assertEquals(listOf(CAMP_A), cache.loadList()?.map { it.id })
        assertEquals(setOf(CAMP_A), listOf(open, closed).localIds())
    }

    @Test
    fun aCampMissingFromTheListIsDeleted() {
        cache.store(tasks(CAMP_A), "a")
        cache.applyList(emptyList())
        assertNull(cache.load(tasks(CAMP_A)))
        assertEquals(emptyList<Camp>(), cache.loadList())
    }

    @Test
    fun goneMeansNotFoundOrForbidden() {
        assertTrue(ApiResult.Failure(ApiError.NOT_FOUND).campGone())
        assertTrue(ApiResult.Failure(ApiError.FORBIDDEN).campGone())
        assertFalse(ApiResult.Failure(ApiError.NETWORK).campGone())
        assertFalse(ApiResult.Ok(Unit).campGone())
    }

    // Der Weg durch den Client: speichern, ohne Netz aus dem Speicher, sonst Fehler.
    private val server = MockWebServer().apply { start() }

    @After
    fun stop() = server.close()

    private fun client() = ApiClient(server.url("/").toString(), ApiClient.httpClient(NoSession(), "1.0.0"), storage = NoSession(), cache = cache)

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    @Test
    fun withoutNetworkTheLastAnswerComesBackWithItsTime() = runBlocking {
        val api = client()
        server.enqueue(ok("""{"title":"Zelte"}"""))
        assertEquals(ApiResult.Ok(Row("Zelte")), api.get<Row>(tasks(CAMP_A)))
        assertNull(api.staleSince.value)
        server.close()
        assertEquals(ApiResult.Ok(Row("Zelte")), api.get<Row>(tasks(CAMP_A)))
        assertEquals(clock, api.staleSince.value)
    }

    @Test
    fun withoutNetworkAndWithoutSavedAnswerItStaysAnError() = runBlocking {
        val api = client()
        server.close()
        assertEquals(ApiResult.Failure(ApiError.NETWORK), api.get<Row>(tasks(CAMP_A)))
        assertNull(api.staleSince.value)
    }

    @Test
    fun aNotFoundIsNeverAnsweredFromTheSavedStand() = runBlocking {
        val api = client()
        server.enqueue(ok("""{"title":"Zelte"}"""))
        api.get<Row>(tasks(CAMP_A))
        server.enqueue(MockResponse.Builder().code(404).body("{}").build())
        assertEquals(ApiError.NOT_FOUND, (api.get<Row>(tasks(CAMP_A)) as ApiResult.Failure).error)
    }

    @Test
    fun pathsOutsideTheAllowedAreasAreNotStored() = runBlocking {
        val api = client()
        server.enqueue(ok("""{"title":"Kind"}"""))
        api.get<Row>("/api/v1/lager/$CAMP_A/teilnehmer")
        assertTrue(files().isEmpty())
    }

    @Test
    fun anAnswerThatCannotBeReadIsNotStored() = runBlocking {
        val api = client()
        server.enqueue(ok("""{"unbekannt":1}"""))
        assertTrue(api.get<Row>(tasks(CAMP_A)) is ApiResult.Failure)
        assertTrue(files().isEmpty())
    }

    @Test
    fun forgettingTheSessionDeletesTheSavedStand() = runBlocking {
        val api = client()
        server.enqueue(ok("""{"title":"Zelte"}"""))
        api.get<Row>(tasks(CAMP_A))
        assertEquals(1, files().size)
        api.forgetSession()
        assertTrue(files().isEmpty())
        assertNull(api.staleSince.value)
    }
}
