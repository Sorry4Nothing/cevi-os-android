package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.lager.Camp
import io.github.sorry4nothing.cevios.lager.CampTask
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramChild
import io.github.sorry4nothing.cevios.programs.ProgramTag
import io.github.sorry4nothing.cevios.push.PushTarget
import io.github.sorry4nothing.cevios.push.WidgetArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Welcher Zustand bei welchen Daten, wie die Zeilen in die Grösse passen und wohin ein Tippen führt. */
class WidgetLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val now = Instant.parse("2026-11-21T17:02:00Z")
    private val programs = WidgetContent.Programs(listOf(ProgramRow("a", "Waldspiel", LocalDate.of(2026, 11, 28), "14:00", "Pfadiheim", listOf("Wölfe"), null)))
    private val snapshot = Snapshot(programs, now.minusSeconds(60 * 60))

    private fun resolve(kind: WidgetKind, outcome: LoadOutcome, previous: Snapshot? = null, locked: Boolean = false) =
        resolveScreen(kind, outcome, previous, now, locked)

    // ---- Zustände ----

    @Test
    fun loadedDataShowsFreshAndIsKept() {
        val (screen, kept) = resolve(WidgetKind.PROGRAM, LoadOutcome.Loaded(programs))
        assertEquals(Freshness.FRESH, (screen as WidgetScreen.Showing).stand.freshness)
        assertEquals(programs, kept?.content)
        assertEquals(now, kept?.loadedAt)
    }

    @Test
    fun emptyContentStillShowsWithStand() {
        val (screen, _) = resolve(WidgetKind.PROGRAM, LoadOutcome.Loaded(WidgetContent.Programs(emptyList())))
        assertTrue((screen as WidgetScreen.Showing).content.isEmpty)
        assertTrue(WidgetContent.Chat(0, 0).isEmpty)
        assertTrue(WidgetContent.Tasks("c", 0, 0, emptyList()).isEmpty)
        assertTrue(WidgetContent.CampToday("c", 3, 8, null, emptyList()).isEmpty)
        assertFalse(WidgetContent.Emergency(null).isEmpty)
    }

    @Test
    fun noNetworkKeepsLastStandWithItsTime() {
        val (screen, kept) = resolve(WidgetKind.PROGRAM, LoadOutcome.Offline, snapshot)
        screen as WidgetScreen.Showing
        assertEquals(Freshness.OFFLINE, screen.stand.freshness)
        assertEquals(snapshot.loadedAt.atZone(io.github.sorry4nothing.cevios.programs.SWISS_ZONE), screen.stand.at)
        assertEquals(snapshot, kept)
    }

    @Test
    fun standOlderThanTwoHoursIsStale() {
        val old = Snapshot(programs, now.minusSeconds(3 * 3600 + 120))
        val (screen, _) = resolve(WidgetKind.PROGRAM, LoadOutcome.Failed, old)
        screen as WidgetScreen.Showing
        assertEquals(Freshness.STALE, screen.stand.freshness)
        assertEquals(3, screen.stand.hours)
        // Genau zwei Stunden gelten schon als veraltet, knapp darunter nicht.
        assertEquals(Freshness.STALE, ((resolve(WidgetKind.PROGRAM, LoadOutcome.Offline, Snapshot(programs, now.minusSeconds(2 * 3600)))).first as WidgetScreen.Showing).stand.freshness)
        assertEquals(Freshness.OFFLINE, ((resolve(WidgetKind.PROGRAM, LoadOutcome.Offline, Snapshot(programs, now.minusSeconds(2 * 3600 - 1)))).first as WidgetScreen.Showing).stand.freshness)
    }

    @Test
    fun withoutMemoryNoNetworkShowsNoDataAndAnErrorShowsFailed() {
        assertEquals(WidgetScreen.NeverOpened(offline = true), resolve(WidgetKind.CALENDAR, LoadOutcome.Offline).first)
        assertEquals(WidgetScreen.Failed, resolve(WidgetKind.CALENDAR, LoadOutcome.Failed).first)
        assertNull(resolve(WidgetKind.CALENDAR, LoadOutcome.Offline).second)
    }

    @Test
    fun signedOutDropsEverything() {
        val (screen, kept) = resolve(WidgetKind.PROGRAM, LoadOutcome.NotSignedIn, snapshot)
        assertEquals(WidgetScreen.SignedOut, screen)
        assertNull(kept)
    }

    @Test
    fun noCampShowsNoCampForCampWidgets() {
        for (kind in WidgetKind.entries.filter { it.needsCamp }) {
            assertEquals(WidgetScreen.NoCamp, resolve(kind, LoadOutcome.NoCamp).first)
        }
    }

    @Test
    fun lockedAppShowsOnlyRedactedAndKeepsNothing() {
        val (screen, kept) = resolve(WidgetKind.PROGRAM, LoadOutcome.Loaded(programs), previous = null, locked = true)
        screen as WidgetScreen.Locked
        assertEquals(listOf(LocalDate.of(2026, 11, 28) to "14:00"), screen.redacted?.whens)
        assertNull(kept)
        assertEquals(WidgetScreen.Locked(null), resolve(WidgetKind.PROGRAM, LoadOutcome.Offline, snapshot, locked = true).first)
    }

    @Test
    fun emergencyButtonWorksLockedAndOffline() {
        val loaded = WidgetContent.Emergency("camp")
        assertEquals(WidgetScreen.Showing(loaded, Stand(Freshness.FRESH, now.atZone(io.github.sorry4nothing.cevios.programs.SWISS_ZONE))), resolve(WidgetKind.EMERGENCY, LoadOutcome.Loaded(loaded), locked = true).first)
        val offline = resolve(WidgetKind.EMERGENCY, LoadOutcome.Offline, Snapshot(loaded, now.minusSeconds(60))).first as WidgetScreen.Showing
        assertEquals(Freshness.OFFLINE, offline.stand.freshness)
        assertEquals("camp", (offline.content as WidgetContent.Emergency).campId)
        // Auch ohne jeden Stand bleibt der Knopf.
        assertEquals(WidgetContent.Emergency(null), ((resolve(WidgetKind.EMERGENCY, LoadOutcome.Offline).first) as WidgetScreen.Showing).content)
    }

    // ---- Redaktion ----

    @Test
    fun redactionKeepsKindTimeAndCounterOnly() {
        assertEquals(Redacted(WidgetKind.CHAT, counter = 3), redact(WidgetContent.Chat(3, 2)))
        assertEquals(Redacted(WidgetKind.TASKS, counter = 2), redact(WidgetContent.Tasks("c", 2, 7, listOf(TaskRow("t", "Offerte", null, Urgency.OVERDUE)))))
        assertEquals(
            Redacted(WidgetKind.CAMP_TODAY, day = 3, days = 8, dutyTime = "07:00–10:00"),
            redact(WidgetContent.CampToday("c", 3, 8, DutyLine("Küche", "Frühstück", "07:00–10:00"), listOf("Abfahrt"))),
        )
        val calendar = redact(WidgetContent.Calendar(2, listOf(CalendarRow("x", "Titel", LocalDate.of(2026, 11, 28), "14:00", listOf("Wölfe")))))
        assertEquals(listOf(LocalDate.of(2026, 11, 28) to "14:00"), calendar.whens)
        assertEquals(2, calendar.counter)
    }

    // ---- Zusagen ohne Namen ----

    private fun program(children: List<ProgramChild> = emptyList(), own: String? = null) =
        Program(id = "p", title = "T", status = "aktiv", allDay = false, children = children, ownRegistration = own)

    @Test
    fun statusCountsChildrenAndNeverNamesThem() {
        assertEquals(ProgramStatus(ProgramState.CHILDREN_OPEN, 1), programStatus(program(listOf(ProgramChild("1", "Vorname", null), ProgramChild("2", "Anderer", "ja")))))
        assertEquals(ProgramStatus(ProgramState.CHILDREN_NO, 1), programStatus(program(listOf(ProgramChild("1", "Vorname", "nein"), ProgramChild("2", "Anderer", "ja")))))
        assertEquals(ProgramStatus(ProgramState.ALL_YES, 2), programStatus(program(listOf(ProgramChild("1", "Vorname", "ja"), ProgramChild("2", "Anderer", "ja")))))
        assertEquals(ProgramStatus(ProgramState.CHILDREN_OPEN, 2), programStatus(program(listOf(ProgramChild("1", "A", null), ProgramChild("2", "B", "offen")))))
    }

    @Test
    fun leadersGetTheirOwnStatusAndOpenComesFirst() {
        assertEquals(ProgramStatus(ProgramState.YOU_YES), programStatus(program(own = "ja")))
        assertEquals(ProgramStatus(ProgramState.YOU_NO), programStatus(program(own = "nein")))
        assertEquals(ProgramStatus(ProgramState.YOU_OPEN), programStatus(program(own = "offen")))
        assertEquals(ProgramStatus(ProgramState.YOU_OPEN), programStatus(program(listOf(ProgramChild("1", "A", "ja")), own = "offen")))
        assertEquals(ProgramStatus(ProgramState.CHILDREN_OPEN, 1), programStatus(program(listOf(ProgramChild("1", "A", null)), own = "ja")))
        assertNull(programStatus(program()))
    }

    // ---- Daten zu Inhalt ----

    @Test
    fun calendarListsEachTermOnceEvenWithSeveralGroups() {
        val a = Program(id = "a", title = "A", status = "aktiv", allDay = true, dayFrom = "2026-11-28", dayTo = "2026-11-28", tags = listOf(ProgramTag("1", "Wölfe", "stufe"), ProgramTag("2", "Team Mat", "team")))
        val content = calendarContent(listOf(a, a, a.copy(id = "b")), limit = 1)
        assertEquals(2, content.total)
        assertEquals(1, content.rows.size)
        assertEquals(listOf("Wölfe", "Team Mat"), content.rows.single().groups)
        assertNull(content.rows.single().time)
    }

    private fun task(id: String, due: String?, status: String = "offen", assignee: String = "me") =
        CampTask(id = id, version = 1, title = id, due = due, statusWire = status, assigneeId = assignee)

    @Test
    fun tasksCountOverdueAndListNextByDueDate() {
        val today = LocalDate.of(2027, 3, 20)
        val tasks = listOf(
            task("spaet", "2027-04-30"),
            task("ueberfaellig", "2027-03-15"),
            task("bald", "2027-03-24"),
            task("erledigt", "2027-03-01", status = "erledigt"),
            task("fremd", "2027-03-02", assignee = "other"),
            task("ueberfaellig2", "2027-03-10"),
        )
        val content = tasksContent("c", tasks, "me", today, limit = 3)
        assertEquals(2, content.overdue)
        assertEquals(4, content.open)
        assertEquals(listOf("ueberfaellig2", "ueberfaellig", "bald"), content.rows.map { it.id })
        assertEquals(listOf(Urgency.OVERDUE, Urgency.OVERDUE, Urgency.SOON), content.rows.map { it.urgency })
        assertEquals(Urgency.LATER, urgencyOf(tasks[0], today))
        assertEquals(Urgency.NONE, urgencyOf(task("x", null), today))
    }

    private fun camp(id: String, status: String = "aktiv", from: String = "2027-07-17", until: String = "2027-07-24") =
        Camp(id = id, title = "Lager $id", from = from, until = until, status = status, roleName = "leitung")

    @Test
    fun campDayCountsFromOneAndIsNullOutsideTheCamp() {
        val camp = camp("a")
        assertEquals(3 to 8, campDay(camp, LocalDate.of(2027, 7, 19)))
        assertEquals(1 to 8, campDay(camp, LocalDate.of(2027, 7, 17)))
        assertEquals(8 to 8, campDay(camp, LocalDate.of(2027, 7, 24)))
        assertEquals(null to 8, campDay(camp, LocalDate.of(2027, 7, 25)))
        assertEquals(null to 8, campDay(camp, LocalDate.of(2027, 7, 16)))
    }

    @Test
    fun pickCampTakesTheChosenActiveCampElseTheFirstActive() {
        val camps = listOf(camp("alt", status = "abgeschlossen"), camp("a"), camp("b"))
        assertEquals("b", pickCamp(camps, "b")?.id)
        assertEquals("a", pickCamp(camps, "alt")?.id)
        assertEquals("a", pickCamp(camps, null)?.id)
        assertNull(pickCamp(listOf(camp("alt", status = "archiviert")), null))
    }

    @Test
    fun importantTitleTakesTheFirstLineAndShortensIt() {
        assertEquals("Abfahrt Ausflug 13:30", importantTitle("\n  Abfahrt Ausflug 13:30  \nWeitere Zeile"))
        assertEquals("a".repeat(59) + "…", importantTitle("a".repeat(80)))
        assertNull(importantTitle("  \n "))
    }

    @Test
    fun meetingPlaceIsTheFirstLineWithoutNumbers() {
        assertEquals("Pfadiheim, Eingang Nord", meetingPlace("Pfadiheim, Eingang Nord\nKontakt +41 00 000 00 00"))
        assertEquals("Bahnhof", meetingPlace("079 000 00 00\nBahnhof"))
        assertNull(meetingPlace("Anruf 044 000 00 00"))
        assertNull(meetingPlace(null))
        assertEquals("Treffpunkt 2. Stock", meetingPlace("Treffpunkt 2. Stock"))
        assertEquals("a".repeat(79) + "…", meetingPlace("a".repeat(100)))
    }

    @Test
    fun datesReadLikeTheDraft() {
        assertEquals("Sa 28. Nov, 14:00", whenLong(LocalDate.of(2026, 11, 28), "14:00", "ganztägig"))
        assertEquals("Sa 28. Nov, ganztägig", whenLong(LocalDate.of(2026, 11, 28), null, "ganztägig"))
        assertEquals("Fr 4.12.", whenShort(LocalDate.of(2026, 12, 4), "19:30", withTime = false))
        assertEquals("Fr 4.12., 19:30", whenShort(LocalDate.of(2026, 12, 4), "19:30", withTime = true))
        assertEquals("15. März", dueLong(LocalDate.of(2027, 3, 15)))
        assertEquals("17. bis 24. Juli 2027", campDates("2027-07-17", "2027-07-24"))
        assertEquals("30. Juni bis 3. Juli 2027", campDates("2027-06-30", "2027-07-03"))
        assertEquals("", campDates("kaputt", "2027-07-03"))
    }

    // ---- Zeilen und Grösse ----

    @Test
    fun sizeClassesFollowTheDesignSizes() {
        assertEquals(SizeClass.SMALL, sizeClassOf(172, 196))
        assertEquals(SizeClass.MEDIUM, sizeClassOf(360, 196))
        assertEquals(SizeClass.LARGE, sizeClassOf(360, 412))
    }

    @Test
    fun rowsFitTheDesignAtTheDesignSizes() {
        assertEquals(1, layoutPlan(WidgetKind.PROGRAM, 360, 196).rows)
        assertEquals(2, layoutPlan(WidgetKind.PROGRAM, 360, 412).rows)
        assertEquals(3, layoutPlan(WidgetKind.CALENDAR, 360, 196).rows)
        assertEquals(5, layoutPlan(WidgetKind.CALENDAR, 360, 412).rows)
        assertEquals(1, layoutPlan(WidgetKind.TASKS, 360, 196).rows)
        assertEquals(3, layoutPlan(WidgetKind.TASKS, 360, 412).rows)
        assertEquals(1, layoutPlan(WidgetKind.CAMP_TODAY, 360, 196).rows)
        assertEquals(2, layoutPlan(WidgetKind.CAMP_TODAY, 360, 412).rows)
    }

    @Test
    fun smallerOrLargerFontShowsFewerRowsAndLowHeightsDropPlaceFirst() {
        assertTrue(layoutPlan(WidgetKind.CALENDAR, 360, 196, fontScale = 2f).rows < 3)
        assertTrue(layoutPlan(WidgetKind.PROGRAM, 360, 412, fontScale = 2f).rows <= 2)
        val low = layoutPlan(WidgetKind.PROGRAM, 360, 100)
        assertEquals(1, low.rows)
        assertFalse(low.showPlace)
        assertFalse(layoutPlan(WidgetKind.PROGRAM, 360, 412, keyguard = true).showPlace)
        assertTrue(layoutPlan(WidgetKind.PROGRAM, 360, 412).showPlace)
    }

    // ---- Ziele ----

    @Test
    fun targetsBecomeTheSameExtrasAsPushes() {
        assertEquals("programm" to "id", intentExtras(WidgetTarget.Program("id")))
        assertEquals("lager_aufgabe" to "id", intentExtras(WidgetTarget.CampTask("id")))
        assertEquals("widget_chat" to null, intentExtras(WidgetTarget.Chat))
        assertEquals("widget_programme" to null, intentExtras(WidgetTarget.Programs))
        assertEquals("widget_lager_heute" to "c", intentExtras(WidgetTarget.CampToday("c")))
        assertEquals("widget_lager_aufgaben" to null, intentExtras(WidgetTarget.CampTasks(null)))
        assertEquals("widget_lager_notfall" to "c", intentExtras(WidgetTarget.CampEmergency("c")))
    }

    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun theAppBuildsTheTargetFromTypeAndIdAlsoOnColdStart() {
        for (target in listOf(WidgetTarget.Program(uuid), WidgetTarget.CampTask(uuid), WidgetTarget.CampToday(uuid), WidgetTarget.CampTasks(uuid), WidgetTarget.CampEmergency(uuid), WidgetTarget.Chat, WidgetTarget.Programs)) {
            val (type, id) = intentExtras(target)
            assertTrue("$target", PushTarget.from(type, id) != null)
        }
        assertEquals(PushTarget.Program(uuid), PushTarget.from("programm", uuid))
        assertEquals(PushTarget.Camp("lager_aufgabe", uuid), PushTarget.from("lager_aufgabe", uuid))
        assertEquals(PushTarget.Area(WidgetArea.CAMP_TASKS, uuid), PushTarget.from("widget_lager_aufgaben", uuid))
        assertEquals(PushTarget.Area(WidgetArea.CHAT, null), PushTarget.from("widget_chat", null))
        // Eine Kennung, die keine ist, führt zu keinem Lager, die Seite öffnet trotzdem.
        assertEquals(PushTarget.Area(WidgetArea.CAMP_TODAY, null), PushTarget.from("widget_lager_heute", "../x"))
        assertNull(PushTarget.from("programm", null))
    }

    @Test
    fun headerAndRowTargetsLeadToTheirOwnPages() {
        assertEquals(WidgetTarget.Program("a"), headerTarget(programs, WidgetKind.PROGRAM))
        assertEquals(WidgetTarget.Programs, headerTarget(WidgetContent.Programs(emptyList()), WidgetKind.PROGRAM))
        assertEquals(WidgetTarget.Programs, headerTarget(null, WidgetKind.CALENDAR))
        assertEquals(WidgetTarget.CampToday("c"), headerTarget(WidgetContent.CampToday("c", 1, 2, null, emptyList()), WidgetKind.CAMP_TODAY))
        assertEquals(WidgetTarget.CampTasks("c"), headerTarget(WidgetContent.Tasks("c", 0, 1, emptyList()), WidgetKind.TASKS))
        assertEquals(WidgetTarget.CampEmergency(null), headerTarget(null, WidgetKind.EMERGENCY))
        assertEquals(WidgetTarget.Chat, headerTarget(null, WidgetKind.CHAT))
        assertEquals(listOf(WidgetTarget.Program("a")), rowTargets(programs))
        assertEquals(listOf(WidgetTarget.CampTask("t")), rowTargets(WidgetContent.Tasks("c", 0, 1, listOf(TaskRow("t", "x", null, Urgency.NONE)))))
        assertEquals(emptyList<WidgetTarget>(), rowTargets(WidgetContent.Chat(1, 1)))
    }
}
