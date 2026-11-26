package io.github.sorry4nothing.cevios.info

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Seiten und Adressen in diesen Tests sind erfunden. */
class InfoApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var api: InfoApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = "better-auth.session_token=geheim"
            override fun write(value: String?) = Unit
        }
        api = InfoApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun html(body: String, code: Int = 200, type: String = "text/html; charset=utf-8") =
        MockResponse.Builder().code(code).addHeader("Content-Type", type).body(body).build()

    private fun redirect(to: String, code: Int = 308) = MockResponse.Builder().code(code).addHeader("Location", to).build()

    private fun page(page: InfoPage) = runBlocking { api.page(page) }

    @Test
    fun aPageComesAsBlocks() {
        server.enqueue(html("<html><body><main><h1>Support</h1><h2>Kontakt</h2><p>Schreib an <a href=\"mailto:leitung@example.org\">leitung@example.org</a>.</p></main></body></html>"))
        val document = (page(InfoPage.SUPPORT) as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("GET /support HTTP/1.1", request.requestLine)
        assertEquals("1.0.0", request.headers["X-App-Version"])
        assertEquals(2, document.blocks.size)
        val paragraph = document.blocks[1] as Block.Paragraph
        assertEquals(LinkTarget.Mail("leitung@example.org"), paragraph.spans.single { it.link != null }.link)
    }

    @Test
    fun theOldPrivacyAddressRedirectsOnTheSameHost() {
        server.enqueue(redirect("/datenschutz-website"))
        server.enqueue(html("<main><h2>Verantwortlich</h2><p>Text</p></main>"))
        val document = (page(InfoPage.PRIVACY) as ApiResult.Ok).value
        assertEquals("GET /datenschutz HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("GET /datenschutz-website HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(2, document.blocks.size)
    }

    @Test
    fun aMissingPageIsNotFound() {
        server.enqueue(html("<p>weg</p>", code = 404))
        assertEquals(ApiError.NOT_FOUND, (page(InfoPage.SUPPORT) as ApiResult.Failure).error)
    }

    @Test
    fun aServerErrorIsAnError() {
        server.enqueue(html("", code = 500))
        assertEquals(ApiError.UNKNOWN, (page(InfoPage.SUPPORT) as ApiResult.Failure).error)
    }

    @Test
    fun aRedirectToAnotherHostIsNotFollowed() {
        server.enqueue(redirect("https://other.example/datenschutz"))
        server.enqueue(html("<main><p>falsch</p></main>"))
        assertEquals(ApiError.UNKNOWN, (page(InfoPage.PRIVACY) as ApiResult.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aRedirectToAnotherPortOrSchemeIsNotFollowed() {
        server.enqueue(redirect("http://localhost:1/x"))
        assertTrue(page(InfoPage.PRIVACY) is ApiResult.Failure)
        server.enqueue(redirect("https://${server.hostName}:${server.port}/x"))
        assertTrue(page(InfoPage.PRIVACY) is ApiResult.Failure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aRedirectWithoutTargetOrInALoopFails() {
        server.enqueue(MockResponse.Builder().code(302).build())
        assertTrue(page(InfoPage.SUPPORT) is ApiResult.Failure)
        repeat(6) { server.enqueue(redirect("/support")) }
        assertTrue(page(InfoPage.SUPPORT) is ApiResult.Failure)
        // Eine Anfrage ohne Ziel und vier im Kreis (die erste und drei Weiterleitungen).
        assertEquals(5, server.requestCount)
    }

    @Test
    fun aTooLargePageIsAnError() {
        server.enqueue(html("<main><p>" + "x".repeat(MAX_PAGE_BYTES) + "</p></main>"))
        assertEquals(ApiError.UNKNOWN, (page(InfoPage.SUPPORT) as ApiResult.Failure).error)
    }

    @Test
    fun aPageJustUnderTheLimitIsRead() {
        val body = "<main><p>" + "x".repeat(MAX_PAGE_BYTES - 100) + "</p></main>"
        server.enqueue(html(body))
        assertEquals(1, (page(InfoPage.SUPPORT) as ApiResult.Ok).value.blocks.size)
    }

    @Test
    fun somethingThatIsNotHtmlIsAnError() {
        server.enqueue(html("{\"a\":1}", type = "application/json"))
        assertEquals(ApiError.UNKNOWN, (page(InfoPage.SUPPORT) as ApiResult.Failure).error)
    }

    @Test
    fun withoutConnectionItIsANetworkError() {
        server.close()
        assertEquals(ApiError.NETWORK, (page(InfoPage.SUPPORT) as ApiResult.Failure).error)
    }
}
