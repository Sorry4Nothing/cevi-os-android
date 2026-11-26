package io.github.sorry4nothing.cevios.formulare

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.FieldProblem
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class FormsApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var api: FormsApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = FormsApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val id = "11111111-1111-4111-8111-111111111111"

    @Test
    fun theListCarriesTitleDeadlineAndState() {
        server.enqueue(json(200, """{"formulare":[{"id":"$id","titel":null,"seitenTitel":"Sommerlager","schliesstAm":"2027-06-01","beantwortet":true}]}"""))
        val list = (runBlocking { api.list() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/formulare HTTP/1.1", server.takeRequest().requestLine)
        assertEquals("Sommerlager", list.forms.single().pageTitle)
        assertEquals("2027-06-01", list.forms.single().closesOn)
        assertTrue(list.forms.single().answered)
        assertEquals(0, FormRules.unanswered(list.forms))
    }

    @Test
    fun aDefinitionKeepsAnUnknownFieldTypeAsText() {
        server.enqueue(
            json(
                200,
                """{"id":"$id","titel":"Anmeldung","einleitung":null,"felder":[
                    {"id":"a","typ":"text","beschriftung":"Name","pflicht":true,"maxLaenge":40},
                    {"id":"b","typ":"unterschrift","beschriftung":"Unterschrift","pflicht":false}],
                  "datenschutz":"Nur die Leitung sieht die Antworten.","absenden":"Anmelden","danke":"Danke","schliesstAm":null,
                  "geschlossen":false,"eineProPerson":true,"beantwortet":false}""",
            ),
        )
        val form = (runBlocking { api.definition(id) } as ApiResult.Ok).value
        assertEquals("GET /api/v1/formulare/$id HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(40, form.fields[0].maxLength)
        assertEquals(listOf("unterschrift"), FormRules.unsupported(form).map { it.type })
        assertFalse(FormRules.canSend(form))
    }

    @Test
    fun sendingPostsTheAnswersWithAnEmptyTrapAndTheMeasuredDuration() {
        server.enqueue(json(201, """{"ok":true}"""))
        val result = runBlocking { api.send(id, FormAnswerRequest(mapOf("a" to JsonPrimitive("Beispiel")), "", durationMs = 4321)) }
        assertTrue(result is ApiResult.Ok)
        val request = server.takeRequest()
        assertEquals("POST /api/v1/formulare/$id/antworten HTTP/1.1", request.requestLine)
        val body = request.body!!.utf8()
        assertTrue(body, body.contains("\"homepage\":\"\""))
        assertTrue(body, body.contains("\"dauerMs\":4321"))
        assertTrue(body, body.contains("\"antworten\":{\"a\":\"Beispiel\"}"))
    }

    @Test
    fun theRequestNeverPrintsTheAnswers() {
        assertEquals("FormAnswerRequest(1 Felder)", FormAnswerRequest(mapOf("a" to JsonPrimitive("Geheim")), "", durationMs = 1).toString())
    }

    @Test
    fun invalidAnswersReturnTheFieldAndCodeButNoValue() {
        server.enqueue(json(422, """{"code":"ANTWORTEN_UNGUELTIG","message":"x","fehler":[{"feld":"a","code":"PFLICHT"},{"feld":"b","code":"ZU_LANG"}]}"""))
        val failure = runBlocking { api.send(id, FormAnswerRequest(emptyMap(), "", durationMs = 3000)) } as ApiResult.Failure
        assertEquals(422, failure.status)
        assertEquals("ANTWORTEN_UNGUELTIG", failure.code)
        assertEquals(listOf(FieldProblem("a", "PFLICHT"), FieldProblem("b", "ZU_LANG")), failure.fieldErrors)
        assertEquals(SendFailure.INVALID, sendFailureOf(failure))
        assertEquals(mapOf("a" to FormProblem.REQUIRED, "b" to FormProblem.TOO_LONG), failure.fieldErrors.associate { it.field to FormProblem.fromCode(it.code) })
    }

    private fun failureOf(status: Int, code: String?): SendFailure {
        server.enqueue(json(status, if (code == null) "{}" else """{"code":"$code","message":"x"}"""))
        return sendFailureOf(runBlocking { api.send(id, FormAnswerRequest(emptyMap(), "", durationMs = 3000)) } as ApiResult.Failure)
    }

    @Test
    fun everyStateOfTheServerHasItsOwnReason() {
        assertEquals(SendFailure.TOO_FAST, failureOf(422, "ZU_SCHNELL"))
        assertEquals(SendFailure.ALREADY, failureOf(409, "BEREITS_BEANTWORTET"))
        assertEquals(SendFailure.FULL, failureOf(409, "FORMULAR_VOLL"))
        assertEquals(SendFailure.CLOSED, failureOf(410, "FORMULAR_GESCHLOSSEN"))
        assertEquals(SendFailure.TOO_OFTEN, failureOf(429, "ZU_OFT"))
        assertEquals(SendFailure.LOCKED, failureOf(423, "GESPERRT"))
        assertEquals(SendFailure.MAINTENANCE, failureOf(503, "IN_WARTUNG"))
        assertEquals(SendFailure.FORBIDDEN, failureOf(403, null))
        assertEquals(SendFailure.NOT_FOUND, failureOf(404, null))
        assertEquals(SendFailure.OTHER, failureOf(500, null))
    }

    @Test
    fun onlyNetworkAndUnclearFailuresOfferAnotherTry() {
        assertTrue(SendFailure.NETWORK.canRetry())
        assertTrue(SendFailure.TOO_FAST.canRetry())
        assertFalse(SendFailure.ALREADY.canRetry())
        assertFalse(SendFailure.FULL.canRetry())
        assertFalse(SendFailure.CLOSED.canRetry())
        assertFalse(SendFailure.TOO_OFTEN.canRetry())
        assertEquals(ApiError.NETWORK, ApiResult.Failure(ApiError.NETWORK).error)
        assertEquals(SendFailure.NETWORK, sendFailureOf(ApiResult.Failure(ApiError.NETWORK)))
    }
}
