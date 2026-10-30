package io.github.sorry4nothing.cevios.net

import io.github.sorry4nothing.cevios.chat.ChatApi
import io.github.sorry4nothing.cevios.chat.ChatMessage
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import io.github.sorry4nothing.cevios.programs.Timing
import io.github.sorry4nothing.cevios.programs.time
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DemoServerTest {
    private val storage = object : SessionStorage {
        override fun read(): String? = null
        override fun write(value: String?) = Unit
    }
    private val client = ApiClient(
        "https://api.example.org",
        ApiClient.httpClient(storage, "1.0.0", listOf(DemoServer(delayMs = 0))),
    )
    private val programs = ProgramsApi(client)
    private val chat = ChatApi(client)

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Ok).value

    private fun <T> ApiResult<T>.failure(): ApiError = (this as ApiResult.Failure).error

    private fun programList(): List<Program> = runBlocking {
        programs.list(LocalDate.now(), LocalDate.now().plusDays(30)).value().programs
    }

    private fun start(program: Program): Instant = when (val timing = program.time()) {
        is Timing.AllDay -> timing.from.atStartOfDay(SWISS_ZONE).toInstant()
        is Timing.Timed -> timing.start.toInstant()
        null -> error("Zeit nicht lesbar")
    }

    private fun page(chatId: String, before: String? = null) = runBlocking { chat.messages(chatId, before).value() }

    private fun groupChatId() = runBlocking { chat.chats().value().chats.single().id }

    @Test
    fun profileHasParentWithTwoChildren() {
        val profile = runBlocking { chat.profile().value() }
        assertTrue(profile.roles.isEmpty())
        assertEquals(2, profile.children.size)
        assertEquals("Jungschar (Demo)", profile.ageGroups.single().name)
    }

    @Test
    fun programListIsSortedAndHasAllKinds() {
        val list = programList()
        assertEquals(6, list.size)
        assertEquals(list.map(::start).sorted(), list.map(::start))
        assertTrue(list.any { it.status == "aktiv" && !it.allDay && it.meetingPoint != null && it.closingTime == "16:30:00" })
        assertTrue(list.any { it.cancelled })
        assertTrue(list.any { it.inReview })
        assertTrue(list.any { it.allDay && it.start == null && it.closingTime != null })
        assertTrue(list.any { it.meetingPoint == null && it.googleLocation == null && it.bringAlong == null && it.parentInfo == null })
        assertTrue(list.any { it.meetingPoint == null && it.googleLocation != null })
    }

    @Test
    fun detailHasTwoChildren() {
        val id = programList().first().id
        val detail = runBlocking { programs.read(id).value() }
        assertEquals(2, detail.children.size)
        assertTrue(detail.attendanceOpen)
        assertNull(detail.ownRegistration)
    }

    @Test
    fun attendanceChangesStatusInDetail() {
        val id = programList().first().id
        val child = runBlocking { programs.read(id).value() }.children.first()
        assertNull(child.status)
        runBlocking { programs.setAttendance(id, child.id, true).value() }
        assertEquals("ja", runBlocking { programs.read(id).value() }.children.first { it.id == child.id }.status)
        runBlocking { programs.setAttendance(id, child.id, false).value() }
        assertEquals("nein", runBlocking { programs.read(id).value() }.children.first { it.id == child.id }.status)
    }

    @Test
    fun attendanceOnClosedProgramIsRejected() {
        val cancelled = programList().first { it.cancelled }
        val detail = runBlocking { programs.read(cancelled.id).value() }
        assertFalse(detail.attendanceOpen)
        val result = runBlocking { programs.setAttendance(cancelled.id, detail.children.first().id, true) }
        assertEquals(ApiError.REGISTRATION_CLOSED, result.failure())
    }

    @Test
    fun leaderRsvpIsForbidden() {
        val result = runBlocking { programs.setLeaderRsvp(programList().first().id, true) }
        assertEquals(ApiError.FORBIDDEN, result.failure())
    }

    @Test
    fun directChatIsAddedAndIdempotent() {
        val chats = runBlocking { chat.chats().value().chats }
        assertEquals(1, chats.size)
        assertTrue(chats.single().isGroup)
        assertEquals(2, chats.single().groupLeaders.size)
        val ageGroup = chats.single().ageGroup.id
        val first = runBlocking { chat.directChat(ageGroup).value().id }
        val second = runBlocking { chat.directChat(ageGroup).value().id }
        assertEquals(first, second)
        val after = runBlocking { chat.chats().value().chats }
        assertEquals(2, after.size)
        assertEquals("Demo Eltern", after.first { !it.isGroup }.parent?.name)
        assertEquals(3, page(first).messages.size)
    }

    @Test
    fun messagesComeInTwoPagesNewestFirst() {
        val id = groupChatId()
        val first = page(id)
        assertEquals(50, first.messages.size)
        assertNotNull(first.next)
        assertEquals(first.messages.last().id, first.next)
        val second = page(id, first.next)
        assertEquals(5, second.messages.size)
        assertNull(second.next)
        val all: List<ChatMessage> = first.messages + second.messages
        assertEquals(55, all.map { it.id }.toSet().size)
        assertEquals(all.map { it.id }.sortedDescending(), all.map { it.id })
        assertTrue(all.any { it.hidden && it.own && it.text == null && it.program == null })
        assertTrue(all.any { it.program != null })
    }

    @Test
    fun sentMessageAppearsOnTop() {
        val id = groupChatId()
        val sent = runBlocking { chat.send(id, "Hallo zusammen").value().id }
        val top = page(id).messages.first()
        assertEquals(sent, top.id)
        assertEquals("Hallo zusammen", top.text)
        assertTrue(top.own)
        assertEquals(ApiError.UNKNOWN, runBlocking { chat.send(id, "  ") }.failure())
        assertEquals(ApiError.UNKNOWN, runBlocking { chat.send(id, "x".repeat(2001)) }.failure())
    }

    @Test
    fun hideIsForbiddenAndReportWorks() {
        val messages = page(groupChatId()).messages
        assertEquals(ApiError.FORBIDDEN, runBlocking { chat.hide(messages.first().id) }.failure())
        val foreign = messages.first { !it.own }
        assertEquals(Unit, runBlocking { chat.report(foreign.id) }.value())
        assertEquals(Unit, runBlocking { chat.report(foreign.id) }.value())
        val ownVisible = messages.first { it.own && !it.hidden }
        assertEquals(ApiError.UNKNOWN, runBlocking { chat.report(ownVisible.id) }.failure())
    }

    @Test
    fun helpIsReadable() {
        val text = runBlocking { chat.help().value() }.trustedPersons
        assertTrue(!text.isNullOrBlank())
    }

    @Test
    fun unknownPathAndIdAreNotFound() {
        assertEquals(ApiError.NOT_FOUND, runBlocking { programs.read("gibt-es-nicht").failure() })
        val result = runBlocking { client.get<Unit>("/api/v1/gibt-es-nicht") }
        assertEquals(ApiError.NOT_FOUND, result.failure())
    }

    @Test
    fun groupChatHasUnreadMessagesUntilMarkedRead() {
        val before = runBlocking { chat.chats().value().chats.single() }
        assertTrue("Die Demo zeigt einen Zähler", before.unread > 0)
        assertEquals(Unit, runBlocking { chat.markRead(before.id) }.value())
        assertEquals(0, runBlocking { chat.chats().value().chats.single().unread })
    }

    @Test
    fun markReadOfUnknownChatIsNotFound() {
        assertEquals(ApiError.NOT_FOUND, runBlocking { chat.markRead("gibt-es-nicht") }.failure())
    }
}
