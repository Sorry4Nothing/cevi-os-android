package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.VersionConflict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Rechte, Sortierung, Marken, Umfragen, Zeitfenster und die Konfliktlösung bei Änderungen ohne Warteschlange. */
class MeetingLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun item(id: String, order: Int, status: String = "offen") = AgendaItem(id, 1, "h1", "Punkt $id", order = order, statusWire = status)

    private fun meeting(id: String, date: String, time: String? = null) = Meeting(id, 1, "lagerleitung", date, time, "Höck $id")

    @Test
    fun durationIsAWholeNumberOfMinutesFromOneToSixHundred() {
        assertEquals(15, MeetingRules.minutesOf(" 15 "))
        assertNull(MeetingRules.minutesOf(""))
        assertNull(MeetingRules.minutesOf("0"))
        assertNull(MeetingRules.minutesOf("601"))
        assertNull(MeetingRules.minutesOf("1,5"))
        assertTrue(MeetingRules.isValidMinutes("") && MeetingRules.isValidMinutes("600"))
        assertFalse(MeetingRules.isValidMinutes("0") || MeetingRules.isValidMinutes("abc"))
    }

    @Test
    fun rightsFollowTheRoleAndTheClosedCamp() {
        val leadership = MeetingRights.of(defaultPermissions(CampRole.LEADERSHIP, false))
        assertTrue(leadership.editMeetings && leadership.readInbox && leadership.answerInbox && leadership.editPinboard)
        val leader = MeetingRights.of(defaultPermissions(CampRole.LEADER, false))
        assertTrue(leader.editMeetings && leader.readAll)
        assertFalse(leader.readInbox)
        val team = MeetingRights.of(defaultPermissions(CampRole.TEAM, false))
        assertFalse(team.readAll || team.editMeetings || team.editPinboard)
        assertTrue(team.interact && team.sendInbox && team.readPinboard)
        val reader = MeetingRights.of(defaultPermissions(CampRole.READ_ONLY, false))
        assertFalse(reader.interact || reader.sendInbox || reader.mentionToTask)
        assertTrue(reader.readAll)
        val closed = MeetingRights.of(defaultPermissions(CampRole.LEADERSHIP, true))
        assertFalse(closed.editMeetings || closed.editPinboard || closed.interact || closed.sendInbox || closed.answerInbox)
        assertTrue(closed.readInbox)
        assertTrue(closed.readOwnInbox)
    }

    @Test
    fun upcomingStartsWithTheNextAndPastWithTheLatest() {
        val today = LocalDate.parse("2027-03-17")
        val all = listOf(meeting("a", "2027-03-24"), meeting("b", "2027-03-10"), meeting("c", "2027-03-17", "19:30"), meeting("d", "2027-02-01"), meeting("e", "2027-03-17", "08:00"))
        assertEquals(listOf("e", "c", "a"), MeetingRules.upcoming(all, today).map { it.id })
        assertEquals(listOf("b", "d"), MeetingRules.past(all, today).map { it.id })
    }

    @Test
    fun moveItemShiftsAndStopsAtTheEdges() {
        val items = listOf(item("a", 0), item("b", 1), item("c", 2))
        assertEquals(listOf("b", "a", "c"), MeetingRules.move(items, 1, -1)!!.map { it.id })
        assertEquals(listOf("a", "c", "b"), MeetingRules.move(items, 1, 1)!!.map { it.id })
        assertNull(MeetingRules.move(items, 0, -1))
        assertNull(MeetingRules.move(items, 2, 1))
        assertNull(MeetingRules.move(items, 5, 1))
    }

    @Test
    fun agendaIsSortedByOrderThenId() {
        val sorted = MeetingRules.sorted(listOf(item("b", 1), item("a", 1), item("c", 0)))
        assertEquals(listOf("c", "a", "b"), sorted.map { it.id })
    }

    @Test
    fun modeStartsAtTheFirstOpenItem() {
        assertEquals(2, MeetingRules.firstOpen(listOf(item("a", 0, "besprochen"), item("b", 1, "besprochen"), item("c", 2))))
        assertEquals(0, MeetingRules.firstOpen(listOf(item("a", 0, "besprochen"))))
        assertEquals(2 to 5, MeetingRules.position(1, 5))
    }

    @Test
    fun mentionMarksBecomeNamesAndUnknownOnesAStandInWord() {
        val names = mapOf("11111111-1111-4111-8111-111111111111" to "Tim")
        val text = "@{person:11111111-1111-4111-8111-111111111111} klärt die Offerte, @{person:22222222-2222-4222-8222-222222222222} auch."
        assertEquals("@Tim klärt die Offerte, @Person auch.", renderMentions(text, names, "Person"))
        assertEquals("@{person:abc}", mentionMark("abc"))
        assertTrue(hasMentionMarks("kein", "Hallo ${mentionMark("11111111-1111-4111-8111-111111111111")}"))
        assertFalse(hasMentionMarks(null, "ohne Marken", "@alle"))
    }

    @Test
    fun pollPercentAndVotesFollowTheRules() {
        fun option(id: String, votes: Int, own: Boolean) = PollOption(id, id, votes, own)
        val single = Poll("p", 1, multiple = false, options = listOf(option("a", 9, true), option("b", 5, false)))
        assertEquals(64, pollPercent(single.options[0], single))
        assertEquals(36, pollPercent(single.options[1], single))
        assertEquals(listOf("b"), votesAfterTap(single, single.options[1]))
        val multi = single.copy(multiple = true)
        assertEquals(listOf("a", "b"), votesAfterTap(multi, multi.options[1]))
        assertEquals(emptyList<String>(), votesAfterTap(multi, multi.options[0]))
        assertEquals(0, pollPercent(option("x", 0, false), single.copy(options = listOf(option("x", 0, false)))))
    }

    @Test
    fun postWindowStatesAreRelativeToNow() {
        val now = Instant.parse("2027-07-10T10:00:00Z")
        fun post(from: String?, until: String?) = Post("p", 1, "T", visibleFrom = from, visibleUntil = until)
        assertEquals(PostWindow.UPCOMING, postState(post("2027-07-11T00:00:00Z", null), now))
        assertEquals(PostWindow.EXPIRED, postState(post(null, "2027-07-10T10:00:00Z"), now))
        assertEquals(PostWindow.VISIBLE, postState(post("2027-07-01T00:00:00Z", "2027-07-20T00:00:00Z"), now))
        assertEquals(PostWindow.VISIBLE, postState(post(null, null), now))
    }

    @Test
    fun dayBoundsUseTheCampTimeAndEndAtTheEndOfTheDay() {
        assertEquals("2027-07-11T22:00:00Z", dayStartInstant("2027-07-12"))
        assertEquals("2027-07-18T21:59:00Z", dayEndInstant("2027-07-18"))
        assertEquals("2027-07-12", instantToDay("2027-07-11T22:00:00Z"))
        assertNull(instantToDay(null))
        assertNull(dayStartInstant("kein Datum"))
    }

    @Test
    fun copyTextIsTitleAndBodyOnly() {
        assertEquals("Abfahrt\n\nTreffpunkt Bahnhof", copyText(Post("p", 1, "Abfahrt", "Treffpunkt Bahnhof")))
        assertEquals("Abfahrt", copyText(Post("p", 1, "Abfahrt", " ")))
    }

    @Test
    fun codesIgnoreCaseDashesAndSpaces() {
        assertEquals("ABCDEFGH", normalizeCode(" abcd-efgh "))
    }

    // ---- Konflikt ohne Warteschlange ----

    private fun conflict(version: Long, differing: List<String>, title: String = "Offerte neu") =
        VersionConflict(buildJsonObject { put("id", "x"); put("version", version); put("titel", title) }, differing)

    @Test
    fun conflictScreenShowsMineAgainstCurrentWithTheDifferingField() {
        val sent = mapOf<String, JsonElement>("titel" to JsonPrimitive("Meine Offerte"), "version" to JsonPrimitive(4))
        val change = directConflict("Traktandum geändert", sent, conflict(5, listOf("titel", "text")), mapOf("titel" to "Titel"))
        assertEquals(ChangeState.CONFLICT, change.state)
        val rows = conflictRows(change)
        assertEquals(1, rows.size)
        assertEquals("Titel", rows[0].label)
        assertEquals("Meine Offerte", rows[0].mine)
        assertEquals("Offerte neu", rows[0].current)
        assertTrue(rows[0].differs)
        assertEquals(5L, conflictVersion(conflict(5, emptyList())))
    }

    @Test
    fun writerResendsSilentlyWhenNothingDiffers() {
        // Unconfined: Die Aufrufe unterbrechen nie, alles läuft sofort im Test.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val writer = DirectWriter(scope) { emptyMap() }
        val versions = mutableListOf<Long>()
        var done: String? = null
        writer.submit<String>(
            "Höck geändert", mapOf("titel" to JsonPrimitive("Neu")), 4,
            call = { v ->
                versions += v
                if (v == 4L) ApiResult.Failure(ApiError.VERSION_CONFLICT, "VERSION_KONFLIKT", conflict = conflict(5, emptyList())) else ApiResult.Ok("fertig")
            },
            onCurrent = {},
        ) { done = it }
        assertEquals(listOf(4L, 5L), versions)
        assertEquals("fertig", done)
        assertNull(writer.conflict.value)
    }

    @Test
    fun writerShowsTheConflictAndKeepMineSendsWithTheNewVersion() {
        // Unconfined: Die Aufrufe unterbrechen nie, alles läuft sofort im Test.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val writer = DirectWriter(scope) { mapOf("titel" to "Titel") }
        val versions = mutableListOf<Long>()
        var done: String? = null
        var reloaded = 0
        writer.submit<String>(
            "Traktandum geändert", mapOf("titel" to JsonPrimitive("Meine Offerte")), 4,
            call = { v ->
                versions += v
                if (v == 4L) ApiResult.Failure(ApiError.VERSION_CONFLICT, "VERSION_KONFLIKT", conflict = conflict(5, listOf("titel"))) else ApiResult.Ok("fertig")
            },
            onCurrent = { reloaded++ },
        ) { done = it }
        // Der Konflikt verschwindet nie still: Er steht da, bis die Person entscheidet.
        val shown = writer.conflict.value
        assertNotNull(shown)
        assertNull(done)
        assertEquals(listOf(4L), versions)
        shown!!.keepMine()
        assertEquals(listOf(4L, 5L), versions)
        assertEquals("fertig", done)
        assertNull(writer.conflict.value)
        assertEquals(0, reloaded)
    }

    @Test
    fun writerTakeCurrentReloadsAndSendsNothing() {
        // Unconfined: Die Aufrufe unterbrechen nie, alles läuft sofort im Test.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val writer = DirectWriter(scope) { emptyMap() }
        var calls = 0
        var reloaded = 0
        writer.submit<String>(
            "Protokoll geändert", mapOf("titel" to JsonPrimitive("Meins")), 2,
            call = { calls++; ApiResult.Failure(ApiError.VERSION_CONFLICT, "VERSION_KONFLIKT", conflict = conflict(3, listOf("titel"))) },
            onCurrent = { reloaded++ },
        ) { }
        writer.conflict.value!!.takeCurrent()
        assertEquals(1, calls)
        assertEquals(1, reloaded)
        assertNull(writer.conflict.value)
    }

    @Test
    fun otherFailuresOfferToTryAgainAndKeepTheCode() {
        // Unconfined: Die Aufrufe unterbrechen nie, alles läuft sofort im Test.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val writer = DirectWriter(scope) { emptyMap() }
        var attempts = 0
        writer.submit<String>(
            "Beitrag geändert", mapOf("wichtig" to JsonPrimitive(true)), 1,
            call = { attempts++; if (attempts == 1) ApiResult.Failure(ApiError.NETWORK) else ApiResult.Ok("ok") },
            onCurrent = {},
        ) { }
        val error = writer.error.value
        assertEquals(ApiError.NETWORK, error!!.error)
        error.retry!!.invoke()
        assertEquals(2, attempts)
    }

    @Test
    fun aDeletionWithAConflictReloadsAndExplainsWithoutAComparison() {
        // Unconfined: Die Aufrufe unterbrechen nie, alles läuft sofort im Test.
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val writer = DirectWriter(scope) { emptyMap() }
        var reloaded = 0
        writer.submit<Unit>("", emptyMap(), 2, { ApiResult.Failure(ApiError.VERSION_CONFLICT, "VERSION_KONFLIKT", conflict = conflict(3, emptyList())) }, { reloaded++ }) { }
        assertNull(writer.conflict.value)
        assertEquals(ApiError.VERSION_CONFLICT, writer.error.value!!.error)
        assertEquals(1, reloaded)
    }
}
