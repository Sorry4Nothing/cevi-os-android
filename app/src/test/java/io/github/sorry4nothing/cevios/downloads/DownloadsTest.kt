package io.github.sorry4nothing.cevios.downloads

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

private const val CAMP_A = "11111111-1111-4111-8111-111111111111"
private const val CAMP_B = "22222222-2222-4222-8222-222222222222"

class DownloadsTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    @get:Rule val folder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var downloads: Downloads
    private var clock = 1_000L

    @Before
    fun setUp() {
        root = File(folder.newFolder("app"), "downloads")
        downloads = Downloads(root) { clock++ }
    }

    @Test
    fun savesAndListsNewestFirst() {
        downloads.saveBytes(CAMP_A, "Plan.pdf", "eins".toByteArray())
        clock += 5_000
        downloads.saveBytes(CAMP_A, "Liste.xlsx", "zwei".toByteArray())
        val list = downloads.list(CAMP_A)
        assertEquals(listOf("Liste.xlsx", "Plan.pdf"), list.map { it.name })
        assertEquals(4L, list.first().size)
        assertEquals("zwei", list.first().file.readText())
    }

    @Test
    fun filesLiveOnlyUnderTheCampFolderInTheirOwnFolder() {
        val saved = downloads.saveBytes(CAMP_A, "Plan.pdf", ByteArray(3))
        assertEquals(File(File(root, CAMP_A), saved.id), saved.file.parentFile)
    }

    @Test
    fun tooBigLeavesNothing() {
        try {
            downloads.save(CAMP_A, "gross.bin", ByteArray(100).inputStream(), 50)
            org.junit.Assert.fail("zu gross")
        } catch (e: TooBigException) {
            // erwartet
        }
        assertTrue(downloads.list().isEmpty())
        assertTrue(File(root, CAMP_A).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun anUnknownCampIsRefused() {
        try {
            downloads.saveBytes("../x", "a.pdf", ByteArray(1))
            org.junit.Assert.fail("kein Lager")
        } catch (e: StorageException) {
            // erwartet
        }
        assertFalse(File(root, "../x").exists())
    }

    @Test
    fun removeDeletesOnlyThatFile() {
        val one = downloads.saveBytes(CAMP_A, "a.pdf", ByteArray(1))
        downloads.saveBytes(CAMP_A, "b.pdf", ByteArray(1))
        downloads.remove(one)
        assertEquals(listOf("b.pdf"), downloads.list(CAMP_A).map { it.name })
        assertFalse(one.file.exists())
    }

    @Test
    fun removeNeverTouchesAnythingOutsideTheDownloads() {
        val outside = folder.newFolder("fremd")
        File(outside, "wichtig.txt").writeText("x")
        downloads.remove(DownloadedFile(CAMP_A, "id", "wichtig.txt", 1, 1, File(outside, "wichtig.txt")))
        assertTrue(File(outside, "wichtig.txt").exists())
        downloads.saveBytes(CAMP_A, "a.pdf", ByteArray(1))
        downloads.remove(DownloadedFile(CAMP_A, "id", "x", 1, 1, root))
        assertEquals(1, downloads.list().size)
    }

    @Test
    fun deletingACampKeepsTheOthers() {
        downloads.saveBytes(CAMP_A, "a.pdf", ByteArray(1))
        downloads.saveBytes(CAMP_B, "b.pdf", ByteArray(1))
        downloads.deleteCamp(CAMP_A)
        assertEquals(listOf("b.pdf"), downloads.list().map { it.name })
        downloads.deleteCamp("..")
        assertEquals(1, downloads.list().size)
    }

    @Test
    fun retainOnlyDeletesEveryOtherCamp() {
        downloads.saveBytes(CAMP_A, "a.pdf", ByteArray(1))
        downloads.saveBytes(CAMP_B, "b.pdf", ByteArray(1))
        downloads.retainOnly(setOf(CAMP_B))
        assertEquals(listOf("b.pdf"), downloads.list().map { it.name })
        downloads.retainOnly(emptySet())
        assertTrue(downloads.list().isEmpty())
    }

    @Test
    fun clearDeletesEverything() {
        downloads.saveBytes(CAMP_A, "a.pdf", ByteArray(1))
        downloads.clear()
        assertTrue(downloads.list().isEmpty())
        assertFalse(root.exists())
    }

    @Test
    fun namesAreCleaned() {
        assertEquals("Plan.pdf", Downloads.safeName("Plan.pdf"))
        assertEquals("Sola.pdf", Downloads.safeName("Plan: Küche/Sola?.pdf"))
        assertEquals("passwd", Downloads.safeName("../../etc/passwd"))
        assertEquals("Datei", Downloads.safeName("///"))
        assertEquals("Datei.pdf", Downloads.safeName("...", "pdf"))
        assertEquals("Liste.pdf", Downloads.safeName("Liste", "PDF"))
        assertEquals("Liste.xlsx", Downloads.safeName("Liste.xlsx", "pdf"))
        assertEquals("Küche Ä.pdf", Downloads.safeName("Küche Ä.pdf"))
        assertEquals("a_b.pdf", Downloads.safeName("a\u0000b.pdf"))
    }

    @Test
    fun namesAreShortAndKeepTheirEnding() {
        val name = Downloads.safeName("x".repeat(300) + ".pdf")
        assertTrue(name.length <= Downloads.MAX_NAME)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun filesThatCouldRunAreRenamedToText() {
        assertEquals("seite.html.txt", Downloads.safeName("seite.html"))
        assertEquals("app.apk.txt", Downloads.safeName("app.apk"))
        assertEquals("start.sh.txt", Downloads.safeName("start.sh"))
    }
}

class LinkDownloaderTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    @get:Rule val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloads: Downloads

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        downloads = Downloads(File(folder.newFolder("app"), "downloads"))
    }

    @After
    fun stop() = server.close()

    private fun downloader(httpsOnly: Boolean = false) = LinkDownloader(downloads, OkHttpClient(), httpsOnly)

    private fun url(path: String) = server.url(path).toString()

    @Test
    fun savesTheFileUnderTheGivenName() = runBlocking {
        server.enqueue(MockResponse.Builder().body("PDFDATEN").addHeader("Content-Type", "application/pdf").build())
        val result = downloader().download(CAMP_A, url("/f/abc"), "Zeltplan")
        val file = (result as DownloadResult.Done).file
        assertEquals("Zeltplan.pdf", file.name)
        assertEquals("PDFDATEN", file.file.readText())
    }

    @Test
    fun theSessionNeverLeavesTheApp() = runBlocking {
        server.enqueue(MockResponse.Builder().body("x").addHeader("Content-Type", "application/pdf").build())
        downloader().download(CAMP_A, url("/f/abc"), "Plan")
        val request = server.takeRequest()
        assertEquals(null, request.headers["Cookie"])
        assertEquals(null, request.headers["X-App-Version"])
        assertEquals(null, request.headers["Authorization"])
    }

    @Test
    fun aPageInsteadOfAFileIsNotSaved() = runBlocking {
        server.enqueue(MockResponse.Builder().body("<html>Anmelden</html>").addHeader("Content-Type", "text/html; charset=utf-8").build())
        assertEquals(DownloadResult.Failed(DownloadError.NOT_A_FILE), downloader().download(CAMP_A, url("/f/abc"), "Plan"))
        assertTrue(downloads.list().isEmpty())
    }

    @Test
    fun anErrorStatusIsNotSaved() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403).body("nein").build())
        assertEquals(DownloadResult.Failed(DownloadError.NOT_A_FILE), downloader().download(CAMP_A, url("/f/abc"), "Plan"))
    }

    @Test
    fun aRedirectToAnotherHostIsFollowedWithoutCookies() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            other.enqueue(MockResponse.Builder().body("DATEN").addHeader("Content-Type", "application/pdf").build())
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", other.url("/ziel.pdf").toString()).build())
            val result = downloader().download(CAMP_A, url("/f/abc"), "Plan")
            assertEquals("Plan.pdf", (result as DownloadResult.Done).file.name)
            assertEquals(null, other.takeRequest().headers["Cookie"])
        } finally {
            other.close()
        }
    }

    @Test
    fun onlyHttpsLinksAreLoaded() = runBlocking {
        assertEquals(DownloadResult.Failed(DownloadError.NOT_HTTPS), downloader(httpsOnly = true).download(CAMP_A, url("/f/abc"), "Plan"))
        assertEquals(DownloadResult.Failed(DownloadError.NOT_HTTPS), downloader(httpsOnly = true).download(CAMP_A, "javascript:alert(1)", "Plan"))
        assertEquals(DownloadResult.Failed(DownloadError.NOT_HTTPS), downloader(httpsOnly = true).download(CAMP_A, "file:///etc/passwd", "Plan"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun storageStopsAtTheLimitAndNothingStays() {
        try {
            downloads.save(CAMP_A, "a.pdf", ByteArray(64).inputStream(), 10)
            org.junit.Assert.fail("zu gross")
        } catch (e: TooBigException) {
            // erwartet
        }
        assertTrue(downloads.list().isEmpty())
    }

    @Test
    fun noNetworkIsReported() = runBlocking {
        val link = url("/f/abc")
        server.close()
        assertEquals(DownloadResult.Failed(DownloadError.NETWORK), downloader().download(CAMP_A, link, "Plan"))
    }
}
