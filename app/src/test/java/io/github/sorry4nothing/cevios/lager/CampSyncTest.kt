package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Änderungsfeed und Ereignisstrom gegen einen Fake-Server. */
class CampSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var sync: CampSync
    private val id = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        sync = CampSync(CampApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0"))), id)
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun entry(seq: Int, type: String) = """{"seq":$seq,"objekttyp":"$type","id":"$id","art":"geaendert","version":2,"zeitpunkt":"2027-07-17T10:00:00Z"}"""

    @Test
    fun parserCollectsMessagesAndIgnoresHeartbeats() {
        val parser = SseParser()
        val lines = listOf("retry: 5000", "id: 42", "event: bereit", """data: {"bis":42}""", "", ": herzschlag", "", "id: 43", "event: aenderung", """data: {"bis":43,"objekttypen":["aufgabe"]}""", "")
        val messages = lines.mapNotNull(parser::feed)
        assertEquals(listOf("bereit", "aenderung"), messages.map { it.event })
        assertEquals(listOf(42L, 43L), messages.map { it.id })
        assertTrue(messages[1].data.contains("aufgabe"))
    }

    @Test
    fun pullFollowsAllPagesAndKeepsTheLastSequence() {
        server.enqueue(json(200, """{"seq":10,"objekttypen":["aufgabe"]}"""))
        server.enqueue(json(200, """{"eintraege":[${entry(11, "aufgabe")}],"bis":11,"weitere":true}"""))
        server.enqueue(json(200, """{"eintraege":[${entry(12, "ressort")}],"bis":12,"weitere":false}"""))
        runBlocking { sync.baseline() }
        val done = runBlocking { sync.pull() } as PullResult.Done
        assertEquals(setOf("aufgabe", "ressort"), done.change!!.types)
        assertEquals(2, done.change!!.count)
        assertEquals(12L, sync.seq)
        server.takeRequest()
        assertEquals("GET /api/v1/lager/$id/aenderungen?seit=10 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /api/v1/lager/$id/aenderungen?seit=11 HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun anEmptyPageIsNoChangeButMovesOn() {
        server.enqueue(json(200, """{"seq":10,"objekttypen":[]}"""))
        server.enqueue(json(200, """{"eintraege":[],"bis":15,"weitere":false}"""))
        runBlocking { sync.baseline() }
        assertNull((runBlocking { sync.pull() } as PullResult.Done).change)
        assertEquals(15L, sync.seq)
    }

    @Test
    fun goneReloadsTheBaseline() {
        server.enqueue(json(200, """{"seq":10,"objekttypen":[]}"""))
        server.enqueue(json(410, """{"code":"FEED_NEU_LADEN","message":"zu alt","seq":500}"""))
        server.enqueue(json(200, """{"seq":500,"objekttypen":["aufgabe"]}"""))
        runBlocking { sync.baseline() }
        val reload = runBlocking { sync.pull() } as PullResult.Reload
        assertEquals(500L, reload.seq)
        assertEquals(500L, sync.seq)
    }

    @Test
    fun pullWithoutBaselineLoadsItFirst() {
        server.enqueue(json(200, """{"seq":7,"objekttypen":[]}"""))
        assertEquals(PullResult.Reload(7), runBlocking { sync.pull() })
    }

    @Test
    fun failuresAreReported() {
        server.enqueue(json(200, """{"seq":10,"objekttypen":[]}"""))
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"x"}"""))
        runBlocking { sync.baseline() }
        assertEquals(PullResult.Failed(ApiError.NOT_FOUND), runBlocking { sync.pull() })
    }

    @Test
    fun streamTellsThereIsNewsAndSendsLastEventId() {
        server.enqueue(json(200, """{"seq":10,"objekttypen":["aufgabe"]}"""))
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "text/event-stream")
                .body("retry: 5000\nid: 10\nevent: bereit\ndata: {\"bis\":10}\n\n: herzschlag\n\nid: 11\nevent: aenderung\ndata: {\"bis\":11,\"objekttypen\":[\"aufgabe\"]}\n\n")
                .build(),
        )
        runBlocking { sync.baseline() }
        var news = 0
        val result = runBlocking { sync.listen(onNews = { news++ }, onReload = {}) }
        assertEquals(ListenResult.Ended, result)
        // «bereit» mit der eigenen Folgenummer ist nichts Neues, die Änderung danach schon.
        assertEquals(1, news)
        server.takeRequest()
        val stream = server.takeRequest()
        assertEquals("GET /api/v1/lager/$id/ereignisse HTTP/1.1", stream.requestLine)
        assertEquals("10", stream.headers["Last-Event-ID"])
        assertEquals("text/event-stream", stream.headers["Accept"])
    }

    @Test
    fun streamRefusedWithTooManyConnectionsReportsFailure() {
        server.enqueue(MockResponse.Builder().code(429).body("""{"code":"ZU_VIELE_VERBINDUNGEN","message":"x"}""").build())
        val result = runBlocking { sync.listen(onNews = {}, onReload = {}) } as ListenResult.Failed
        assertEquals(ApiError.TOO_MANY_REQUESTS, result.error)
    }

    @Test
    fun streamOnADeadServerIsANetworkError() {
        val dead = MockWebServer().apply { start() }
        val url = dead.url("/").toString()
        dead.close()
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        val offline = CampSync(CampApi(ApiClient(url, ApiClient.httpClient(storage, "1.0.0"))), id)
        assertEquals(ListenResult.Failed(ApiError.NETWORK), runBlocking { offline.listen(onNews = {}, onReload = {}) })
    }
}
