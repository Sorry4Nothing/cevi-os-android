package io.github.sorry4nothing.cevios.auth

import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

private class ProofMemoryStorage(var value: String? = null) : SessionStorage {
    override fun read() = value
    override fun write(value: String?) {
        this.value = value
    }
}

class AppProofTest {
    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private val storage = ProofMemoryStorage()
    private val nonce = "mMn3wC0Rk9fVZ1Qp0eB8yq5H1b5d2xWw6tq3T8aJm0A"

    /** Die Bindungen, mit denen die App das Betriebssystem gefragt hat. */
    private val asked = mutableListOf<String>()
    private var integrityToken: String? = "integrity-token"

    private fun auth(integrity: IntegrityProvider? = IntegrityProvider { asked += it; integrityToken }) =
        AuthApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.2.3"), storage = storage), integrity)

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun stop() = server.close()

    private fun reply(code: Int, body: String = "{}", vararg headers: Pair<String, String>) {
        server.enqueue(MockResponse.Builder().code(code).body(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build())
    }

    private fun challenge() = reply(200, """{"nonce":"$nonce","laeuftAb":"2026-11-25T12:05:00.000Z","plattform":"android","paketname":"p"}""")
    private fun ticket() = reply(200, """{"ticket":"ticket-eins","laeuftAb":"2026-11-25T12:02:00.000Z"}""")

    @Test
    fun bindingIsTheHashOfNonceAndLowercaseUsername() {
        // Gegenprobe mit `crypto.createHash("sha256").update(nonce + "\n" + "anna").digest("base64url")`.
        val expected = "8lF-gjsfdImPfAE4uYC9BvIkGYsPbURfycdjr9nkMdA"
        assertEquals(expected, proofBinding(nonce, "anna"))
        assertEquals(expected, proofBinding(nonce, "ANNA"))
        assertEquals(43, expected.length)
        assertFalse(proofBinding(nonce, "anna").contains('=') || proofBinding(nonce, "anna").contains('+') || proofBinding(nonce, "anna").contains('/'))
        assertFalse(expected == proofBinding(nonce, "ann"))
    }

    @Test
    fun proofSwitchedOffSignsInWithoutTicket() {
        reply(404, """{"code":"NICHT_GEFUNDEN","message":"x"}""")
        reply(200)
        assertEquals(ApiResult.Ok(Unit), runBlocking { auth().signIn("anna", "geheim") })
        val challenge = server.takeRequest()
        assertEquals("/api/v1/app/nachweis/challenge", challenge.url.encodedPath)
        assertEquals("""{"plattform":"android"}""", challenge.body?.utf8())
        assertEquals("cevi-os://", challenge.headers["Origin"])
        assertEquals("1.2.3", challenge.headers["X-App-Version"])
        val signIn = server.takeRequest()
        assertEquals("/api/auth/sign-in/username", signIn.url.encodedPath)
        assertNull(signIn.headers["X-Cevi-Nachweis"])
        assertEquals("cevi-os://", signIn.headers["Origin"])
        assertTrue(asked.isEmpty())
    }

    @Test
    fun withoutAnIntegrityProviderTheAppNeverAsksForAChallenge() {
        reply(200)
        assertEquals(ApiResult.Ok(Unit), runBlocking { auth(integrity = null).signIn("anna", "geheim") })
        assertEquals("/api/auth/sign-in/username", server.takeRequest().url.encodedPath)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun proofSwitchedOnSendsTheTicketWithTheSignIn() {
        challenge()
        ticket()
        reply(200)
        assertEquals(ApiResult.Ok(Unit), runBlocking { auth().signIn("Anna", "geheim") })
        assertEquals("/api/v1/app/nachweis/challenge", server.takeRequest().url.encodedPath)
        val proof = server.takeRequest()
        assertEquals("/api/v1/app/nachweis", proof.url.encodedPath)
        assertEquals(
            """{"plattform":"android","nonce":"$nonce","benutzername":"Anna","integrityToken":"integrity-token"}""",
            proof.body?.utf8(),
        )
        assertEquals(listOf(proofBinding(nonce, "anna")), asked)
        val signIn = server.takeRequest()
        assertEquals("/api/auth/sign-in/username", signIn.url.encodedPath)
        assertEquals("ticket-eins", signIn.headers["X-Cevi-Nachweis"])
        assertEquals("cevi-os://", signIn.headers["Origin"])
        assertEquals("""{"username":"Anna","password":"geheim"}""", signIn.body?.utf8())
    }

    @Test
    fun everyAttemptStartsWithANewChallenge() {
        repeat(2) {
            challenge()
            ticket()
            reply(401, """{"code":"ANMELDUNG_FEHLGESCHLAGEN","message":"x"}""")
        }
        val api = auth()
        val results = List(2) { runBlocking { api.signIn("anna", "falsch") } as ApiResult.Failure }
        assertEquals(6, server.requestCount)
        results.forEach { assertEquals(R.string.login_failed, signInErrorText(it)) }
        assertEquals(2, asked.size)
    }

    @Test
    fun invalidProofStopsTheSignIn() {
        challenge()
        reply(401, """{"code":"APP_NACHWEIS_UNGUELTIG","message":"x"}""")
        val failure = runBlocking { auth().signIn("anna", "geheim") } as ApiResult.Failure
        assertEquals(ApiError.APP_PROOF_INVALID, failure.error)
        assertEquals(R.string.error_app_proof_invalid, signInErrorText(failure))
        assertEquals(2, server.requestCount) // keine Anmeldung ohne Ticket
    }

    @Test
    fun unreachableGoogleAsksForAnotherTry() {
        challenge()
        reply(503, """{"code":"APP_NACHWEIS_NICHT_ERREICHBAR","message":"x"}""")
        val failure = runBlocking { auth().signIn("anna", "geheim") } as ApiResult.Failure
        assertEquals(ApiError.APP_PROOF_UNREACHABLE, failure.error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun noIntegrityTokenStopsBeforeTheProofRoute() {
        integrityToken = null
        challenge()
        val failure = runBlocking { auth().signIn("anna", "geheim") } as ApiResult.Failure
        assertEquals(ApiError.INTEGRITY_UNAVAILABLE, failure.error)
        assertEquals(R.string.error_integrity_unavailable, signInErrorText(failure))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun tooManyChallengesKeepTheWaitTime() {
        reply(429, "{}", "X-Retry-After" to "60")
        val failure = runBlocking { auth().signIn("anna", "geheim") } as ApiResult.Failure
        assertEquals(ApiError.TOO_MANY_REQUESTS, failure.error)
        assertEquals(60, failure.retryAfterSeconds)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun networkErrorOnTheChallengeIsNotSuccess() {
        server.close()
        val failure = runBlocking { auth().signIn("anna", "geheim") } as ApiResult.Failure
        assertEquals(ApiError.NETWORK, failure.error)
    }

    @Test
    fun missingProofAtTheSignInHasTheSameTextAsAnInvalidOne() {
        reply(404, """{"code":"NICHT_GEFUNDEN","message":"x"}""")
        reply(401, """{"code":"APP_NACHWEIS_FEHLT","message":"x"}""")
        val failure = runBlocking { auth().signIn("anna", "geheim") } as ApiResult.Failure
        assertEquals(ApiError.APP_PROOF_INVALID, failure.error)
        assertEquals(R.string.error_app_proof_invalid, signInErrorText(failure))
    }

    @Test
    fun proofModelsNeverPrintTheirSecrets() {
        val text = listOf(
            ProofRequest("android", nonce, "anna", "integrity-token").toString(),
            ProofChallenge(nonce).toString(),
            ProofTicket("ticket-eins").toString(),
        ).joinToString()
        listOf("integrity-token", nonce, "anna", "ticket-eins").forEach { assertFalse(it, text.contains(it)) }
    }
}
