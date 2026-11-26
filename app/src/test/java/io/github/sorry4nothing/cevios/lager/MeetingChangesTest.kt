package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

/** Der Haken «anwesend» im Höck-Modus über die Warteschlange und die Erwähnungen nach dem Senden einer Aufgabe. */
class MeetingChangesTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private lateinit var server: MockWebServer
    private lateinit var queue: OfflineQueue
    private val camp = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val meeting = "11111111-1111-4111-8111-111111111111"
    private val other = "99999999-9999-4999-8999-999999999999"
    private var counter = 0

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        val api = CampApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
        queue = OfflineQueue(camp, MemoryQueueStore(), api, newId = { "op-${++counter}" }, now = { 1_000L })
    }

    @After
    fun stop() = server.close()

    private val members = listOf(
        MemberPresence("p1", "Beispiel Person", "Spatz", "leitung", present = false),
        MemberPresence("p2", null, "Fuchs", "team", present = true),
    )

    private fun tick(person: String, present: Boolean) {
        routePresence(queue, presencePayload(meeting, person, present), "x") { queue.enqueue(PRESENCE_OP, presencePayload(meeting, person, present), "x") }
    }

    @Test
    fun aSecondTapOnTheSamePersonReplacesTheWaitingOne() {
        tick("p1", true)
        tick("p1", false)
        val waiting = queue.state.value.changes
        assertEquals(1, waiting.size)
        assertEquals("""{"hoeckId":"$meeting","personId":"p1","anwesend":false}""", waiting.single().payload.toString())
        // Anderer Inhalt: eine neue clientOpId (eine frühere Anfrage kann angekommen sein).
        assertEquals("op-2", waiting.single().clientOpId)
    }

    @Test
    fun tapsOnOtherPeopleAndOtherMeetingsStaySeparate() {
        tick("p1", true)
        tick("p2", false)
        routePresence(queue, presencePayload(other, "p1", true), "x") { queue.enqueue(PRESENCE_OP, presencePayload(other, "p1", true), "x") }
        assertEquals(3, queue.state.value.changes.size)
    }

    @Test
    fun overlayShowsWaitingTapsAndLeavesOtherMeetingsAlone() {
        tick("p1", true)
        tick("p2", false)
        val shown = applyPresence(members, meeting, queue.state.value.changes)
        assertTrue(shown[0].present)
        assertFalse(shown[1].present)
        assertEquals(members, applyPresence(members, other, queue.state.value.changes))
    }

    @Test
    fun sentTapsStayOverTheServerStateUntilCleared() {
        tick("p1", true)
        server.enqueue(MockResponse.Builder().code(200).body("""{"ergebnisse":[{"clientOpId":"op-1","status":"ok","ergebnis":{"id":"$meeting"}}]}""").build())
        runBlocking { queue.flush() }
        assertTrue(applyPresence(members, meeting, queue.state.value.changes)[0].present)
        queue.clearSent()
        assertFalse(applyPresence(members, meeting, queue.state.value.changes)[0].present)
        val request = server.takeRequest()
        assertEquals("POST /api/v1/lager/$camp/aenderungen HTTP/1.1", request.requestLine)
        assertTrue(request.body!!.utf8().contains(""""typ":"hoeck.anwesend""""))
    }

    @Test
    fun aTapThatIsAlreadyOnItsWayIsFollowedByANewOne() {
        tick("p1", true)
        // Eine gesendete Änderung lässt sich nicht mehr ändern: Der nächste Tipp wird eine eigene.
        server.enqueue(MockResponse.Builder().code(200).body("""{"ergebnisse":[{"clientOpId":"op-1","status":"ok"}]}""").build())
        runBlocking { queue.flush() }
        tick("p1", false)
        assertEquals(2, queue.state.value.changes.size)
        assertFalse(applyPresence(members, meeting, queue.state.value.changes)[0].present)
    }

    @Test
    fun taskMentionsAreSyncedOnlyForSentChangesToTitleOrNote() {
        fun change(type: String, payload: JsonObject, state: ChangeState, result: JsonObject? = null) = QueuedChange("c", type, payload, "s", 0, state = state, result = result)
        val note = buildJsonObject { put("id", "t1"); put("version", 2); put("notiz", "@{person:x}") }
        assertEquals("t1", mentionSyncTarget(change(TaskOps.CHANGE, note, ChangeState.SENT)))
        assertNull(mentionSyncTarget(change(TaskOps.CHANGE, note, ChangeState.WAITING)))
        assertNull(mentionSyncTarget(change(TaskOps.STATUS, note, ChangeState.SENT)))
        assertNull(mentionSyncTarget(change(TaskOps.CHANGE, buildJsonObject { put("id", "t1"); put("status", "offen") }, ChangeState.SENT)))
        // Bei der Anlage nennt die Antwort des Servers die ID.
        val created = change(TaskOps.CREATE, buildJsonObject { put("titel", "Zelte") }, ChangeState.SENT, buildJsonObject { put("id", "t9"); put("version", 1) })
        assertEquals("t9", mentionSyncTarget(created))
    }
}
