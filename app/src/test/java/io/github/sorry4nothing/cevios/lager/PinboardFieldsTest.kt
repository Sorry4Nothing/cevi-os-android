package io.github.sorry4nothing.cevios.lager

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

/** Empfänger nach Ressort und Gruppe sowie «gesehen» der Pinnwand: Felder, Anfragen und die Entscheidung, ob sich Empfänger geändert haben. */
class PinboardFieldsTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var api: CommsApi
    private val camp = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val postId = "11111111-1111-4111-8111-111111111111"
    private val ressort = "22222222-2222-4222-8222-222222222222"
    private val group = "33333333-3333-4333-8333-333333333333"

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = CommsApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private fun post(recipients: String, seen: String) = """{"beitraege":[{"id":"$postId","version":1,"titel":"Abfahrt","text":"","wichtig":false,"angeheftet":false,
        "sichtbarAb":null,"sichtbarBis":null,"vonPersonId":null,"erstelltAm":"2027-07-01T08:00:00.000Z","von":null,"empfaenger":$recipients,"dateien":[],"umfrage":null,
        "reaktionen":{"zaehler":{},"eigene":null},"gesehen":$seen}],"naechster":null}"""

    @Test
    fun postReadsRessortGroupRecipientsAndSeenWithNames() {
        server.enqueue(
            json(
                200,
                post(
                    """{"anAlle":false,"rollen":["leitung"],"personen":[],"ressortIds":["$ressort"],"gruppeIds":["$group"]}""",
                    """{"anzahl":18,"eigene":false,"personen":[{"id":"p1","name":"Beispiel Person"}]}""",
                ),
            ),
        )
        val first = (runBlocking { api.posts(camp, all = true) } as ApiResult.Ok).value.single()
        assertEquals(listOf(ressort), first.recipients.ressortIds)
        assertEquals(listOf(group), first.recipients.groupIds)
        assertEquals(18, first.seen.count)
        assertEquals("Beispiel Person", first.seen.people!!.single().display)
    }

    @Test
    fun readersWithoutTheRightGetOnlyTheCountAndNoRecipientLists() {
        server.enqueue(json(200, post("""{"anAlle":true,"rollen":[],"personen":[],"ressortIds":[],"gruppeIds":[]}""", """{"anzahl":3,"eigene":true,"personen":null}""")))
        val first = (runBlocking { api.posts(camp, all = false) } as ApiResult.Ok).value.single()
        assertTrue(first.recipients.everyone)
        assertEquals(3, first.seen.count)
        assertTrue(first.seen.own)
        assertNull(first.seen.people)
    }

    @Test
    fun postWithoutSeenFieldKeepsWorking() {
        server.enqueue(json(200, post("""{"anAlle":true,"rollen":[],"personen":[]}""", "null").replace(""","gesehen":null""", "")))
        val first = (runBlocking { api.posts(camp, all = false) } as ApiResult.Ok).value.single()
        assertEquals(0, first.seen.count)
        assertTrue(first.recipients.ressortIds.isEmpty())
    }

    @Test
    fun markSeenSendsOneBatchAndSingleMarksUseTheirOwnRoute() {
        server.enqueue(json(204, ""))
        server.enqueue(json(204, ""))
        assertTrue(runBlocking { api.markSeen(camp, listOf(postId, ressort)) } is ApiResult.Ok)
        assertTrue(runBlocking { api.markOneSeen(camp, postId) } is ApiResult.Ok)
        val batch = server.takeRequest()
        assertEquals("PUT /api/v1/lager/$camp/pinnwand/gesehen HTTP/1.1", batch.requestLine)
        assertEquals("""{"beitragIds":["$postId","$ressort"]}""", batch.body!!.utf8())
        assertEquals("PUT /api/v1/lager/$camp/pinnwand/$postId/gesehen HTTP/1.1", server.takeRequest().requestLine)
    }

    @Test
    fun markSeenReportsAnUnreadablePostAs404() {
        server.enqueue(json(404, """{"code":"NICHT_GEFUNDEN","message":"nicht gefunden"}"""))
        val result = runBlocking { api.markSeen(camp, listOf(postId)) }
        assertEquals(ApiError.NOT_FOUND, (result as ApiResult.Failure).error)
    }

    @Test
    fun recipientsBodyIsNullForEveryoneAndCarriesAllFourLists() {
        assertNull(recipientsBody(PostDraft()))
        val body = recipientsBody(PostDraft(roles = setOf("leitung"), ressortIds = setOf(ressort), groupIds = setOf(group), peopleIds = setOf("p1")))!!
        assertEquals("""{"rollen":["leitung"],"personen":["p1"],"ressortIds":["$ressort"],"gruppeIds":["$group"]}""", body.toString())
    }

    @Test
    fun recipientsChangedComparesAllFourListsAndTreatsEveryoneAsEmpty() {
        val everyone = PostRecipients()
        assertFalse(recipientsChanged(everyone, PostDraft()))
        assertTrue(recipientsChanged(everyone, PostDraft(ressortIds = setOf(ressort))))
        val targeted = PostRecipients(everyone = false, roles = listOf("team"), ressortIds = listOf(ressort), groupIds = listOf(group), people = listOf("p1"))
        val same = PostDraft(roles = setOf("team"), ressortIds = setOf(ressort), groupIds = setOf(group), peopleIds = setOf("p1"))
        assertFalse(recipientsChanged(targeted, same))
        assertTrue(recipientsChanged(targeted, same.copy(groupIds = emptySet())))
        assertTrue(recipientsChanged(targeted, PostDraft()))
    }

    @Test
    fun choicesNameRessortsAndGroupsOrReturnNull() {
        val choices = RecipientChoices(listOf(ressort to "Küche"), listOf(group to "Falken"))
        assertEquals("Küche", choices.ressortName(ressort))
        assertEquals("Falken", choices.groupName(group))
        assertNull(choices.groupName(ressort))
    }
}
