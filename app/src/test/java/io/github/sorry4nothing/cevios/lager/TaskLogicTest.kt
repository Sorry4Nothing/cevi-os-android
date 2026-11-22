package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Liste, Ansichten, Start und die Sicht auf wartende Änderungen: reine Logik, ohne Gerät und Netz. */
class TaskLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    private val today = LocalDate.of(2027, 3, 1)
    private val me = TaskPerson("p1", "Beispiel Person", "Spatz")
    private val other = TaskPerson("p2", "Muster Person", "Luchs")

    private fun task(
        id: String,
        title: String = id,
        due: String? = null,
        status: String = "offen",
        assignee: TaskPerson? = null,
        order: Int = 0,
        snoozed: String? = null,
        ressort: TaskRessortRef? = null,
        support: List<TaskPerson> = emptyList(),
    ) = CampTask(
        id = id, version = 1, title = title, due = due, statusWire = status, assignee = assignee, assigneeId = assignee?.id,
        order = order, snoozedUntil = snoozed, ressort = ressort, supporters = support,
    )

    @Test
    fun sectionsFollowTheDraft() {
        val tasks = listOf(
            task("late", due = "2027-02-20"),
            task("soon", due = "2027-03-10"),
            task("far", due = "2027-06-01"),
            task("none"),
            task("done", due = "2027-02-01", status = "erledigt"),
            task("snoozed", due = "2027-02-20", snoozed = "2027-03-05"),
        )
        val sections = TaskRules.sections(tasks, today).toMap()
        assertEquals(listOf("late"), sections[TaskSection.OVERDUE]!!.map { it.id })
        assertEquals(listOf("soon"), sections[TaskSection.SOON]!!.map { it.id })
        assertEquals(setOf("far", "snoozed"), sections[TaskSection.LATER]!!.map { it.id }.toSet())
        assertEquals(listOf("none"), sections[TaskSection.NO_DUE]!!.map { it.id })
        assertEquals(listOf("done"), sections[TaskSection.DONE]!!.map { it.id })
        // Die Reihenfolge der Abschnitte ist fest, erledigte stehen zuletzt.
        assertEquals(TaskSection.DONE, TaskRules.sections(tasks, today).last().first)
    }

    @Test
    fun doneTaskIsNeverOverdue() {
        assertNull(TaskRules.overdueDays(task("a", due = "2027-01-01", status = "erledigt"), today))
        assertEquals(2, TaskRules.overdueDays(task("b", due = "2027-02-27"), today))
        assertNull(TaskRules.overdueDays(task("c", due = "2027-03-01"), today))
    }

    @Test
    fun mineIncludesSupportAndFallbackLeadAndPutsDoneLast() {
        val tasks = listOf(
            task("a", due = "2027-03-20", assignee = me),
            task("b", due = "2027-03-05", support = listOf(me)),
            task("c", due = "2027-03-02", assignee = other),
            task("d", due = "2027-03-01", assignee = me, status = "erledigt"),
        )
        assertEquals(listOf("b", "a", "d"), TaskRules.mine(tasks, "p1", MineSort.DUE, today).map { it.id })
    }

    @Test
    fun ownOrderKeepsTheOrderOfTheLeadership() {
        val tasks = listOf(task("a", assignee = me, order = 2), task("b", assignee = me, order = 1))
        assertEquals(listOf("b", "a"), TaskRules.mine(tasks, "p1", MineSort.ORDER, today).map { it.id })
    }

    @Test
    fun peopleGroupsPutUnassignedLast() {
        val groups = TaskRules.byPerson(listOf(task("a"), task("b", assignee = other), task("c", assignee = me)))
        assertEquals(listOf("p1", "p2", null), groups.map { it.first?.id })
    }

    @Test
    fun kanbanHasFourColumnsAndShowsNextMeetingOnlyWhenUsed() {
        assertEquals(4, TaskRules.columns(listOf(task("a"))).size)
        val withMeeting = TaskRules.columns(listOf(task("a", status = "naechster_hoeck")))
        assertEquals(TaskStatus.NEXT_MEETING, withMeeting.last().first)
    }

    @Test
    fun movingNeedsNoDragAndStopsAtTheEnds() {
        val list = listOf(task("a"), task("b"), task("c"))
        assertEquals(listOf("b", "a", "c"), TaskRules.moved(list, "b", -1)!!.map { it.first })
        assertNull(TaskRules.moved(list, "a", -1))
        assertNull(TaskRules.moved(list, "c", 1))
    }

    @Test
    fun ressortTreeNestsAndSurvivesCycles() {
        fun r(id: String, parent: String?, order: Int) = Ressort(id, 1, id.uppercase(), id, parentId = parent, order = order)
        val tree = ressortTree(listOf(r("b", "a", 0), r("a", null, 0), r("c", "x", 1), r("x", "c", 0)))
        assertEquals(listOf("a" to 0, "b" to 1), tree.map { it.first.id to it.second }.take(2))
        // Ein Kreis (x unter c, c unter x) hat keinen Einstieg und fehlt, statt die App zu hängen.
        assertEquals(2, tree.size)
    }

    @Test
    fun countdownBeforeDuringAndAfterTheCamp() {
        fun label(toStart: Int, toEnd: Int) = countdownLabel(StartCountdown("2027-07-17", "2027-07-24", toStart, toEnd))
        assertEquals(CountdownLabel.DaysToGo(10), label(10, 17))
        assertEquals(CountdownLabel.StartsToday, label(0, 7))
        assertEquals(CountdownLabel.DayOf(3, 8), label(-2, 5))
        assertEquals(CountdownLabel.DayOf(8, 8), label(-7, 0))
        assertEquals(CountdownLabel.Over, label(-9, -2))
    }

    @Test
    fun percentIsRoundedDownAndSafeWithoutTasks() {
        assertEquals(0, percentDone(null))
        assertEquals(0, percentDone(TaskProgress(0, 0, 0)))
        assertEquals(33, percentDone(TaskProgress(3, 1, 0)))
    }

    @Test
    fun dayLabelsUseWords() {
        assertEquals(DayLabel.Today, dayLabel("2027-03-01", today))
        assertEquals(DayLabel.Tomorrow, dayLabel("2027-03-02", today))
        assertEquals(DayLabel.Name("Freitag"), dayLabel("2027-03-05", today))
        assertEquals(DayLabel.Name("15. März"), dayLabel("2027-03-15", today))
    }

    @Test
    fun weatherWarningLooksThreeDaysAhead() {
        val weather = Weather(days = listOf(WeatherDay("2027-03-04", warnings = listOf("hitze")), WeatherDay("2027-03-02", warnings = listOf("gewitter"))))
        assertEquals(LocalDate.of(2027, 3, 2) to listOf("gewitter"), nextWeatherWarning(weather, today))
        assertNull(nextWeatherWarning(Weather(days = listOf(WeatherDay("2027-03-04", warnings = listOf("hitze")))), today))
    }

    @Test
    fun nextMeetingIgnoresThePast() {
        val meetings = listOf(
            MeetingEntry("a", "2027-02-20", "19:00", "Alt"),
            MeetingEntry("b", "2027-03-08", "20:00", "Spät"),
            MeetingEntry("c", "2027-03-08", "19:00", "Früh"),
        )
        assertEquals("c", nextMeeting(meetings, today)?.id)
    }

    @Test
    fun rightsFollowTheRole() {
        val leadership = TaskRights.of(CampRole.LEADERSHIP, readOnly = false)
        val team = TaskRights.of(CampRole.TEAM, readOnly = false)
        val reading = TaskRights.of(CampRole.READ_ONLY, readOnly = false)
        assertTrue(leadership.edit && leadership.seeLeadershipStart && leadership.editRessorts)
        // Das Team setzt nur den Status; es sieht weder Ressorts noch Verlauf noch den Start der Leitung.
        assertTrue(team.setStatus)
        assertFalse(team.edit || team.readRessorts || team.readHistory || team.seeLeadershipStart)
        assertFalse(reading.edit || reading.setStatus)
        assertTrue(reading.readAll)
        // Ein abgeschlossenes Lager lässt nichts mehr ändern.
        assertFalse(TaskRights.of(CampRole.LEADERSHIP, readOnly = true).let { it.edit || it.setStatus || it.editRessorts })
    }

    // ---- wartende Änderungen über dem Stand des Servers ----

    private fun waiting(type: String, payload: kotlinx.serialization.json.JsonObject, state: ChangeState = ChangeState.WAITING, result: kotlinx.serialization.json.JsonObject? = null) =
        QueuedChange("op-$type", type, payload, "x", 0L, state = state, result = result)

    @Test
    fun offlineCreatedTaskShowsUpPending() {
        val create = waiting(TaskOps.CREATE, TaskPayloads.create("Zelte prüfen", ressortId = "r1", assigneeId = "p1", due = "2027-03-10"))
        val shown = applyQueued(emptyList(), listOf(create), mapOf("p1" to me), mapOf("r1" to TaskRessortRef("r1", "LO", "Logistik")))
        val created = shown.single()
        assertTrue(created.pending)
        assertEquals("op-${TaskOps.CREATE}", created.queuedId)
        assertEquals("Zelte prüfen", created.title)
        assertEquals("Spatz", created.assignee?.ceviName)
        assertEquals("LO", created.ressort?.abbreviation)
        assertEquals("2027-03-10", created.due)
    }

    @Test
    fun sentCreateIsHiddenOnceTheServerListHasIt() {
        val result = buildJsonObject { put("id", "t9"); put("version", 1) }
        val sent = waiting(TaskOps.CREATE, TaskPayloads.create("Zelte"), ChangeState.SENT, result)
        assertEquals(1, applyQueued(emptyList(), listOf(sent)).size)
        assertEquals(1, applyQueued(listOf(task("t9", "Zelte")), listOf(sent)).size)
    }

    @Test
    fun waitingStatusChangeAndDeleteAreVisibleAtOnce() {
        val base = listOf(task("a"), task("b"))
        val status = waiting(TaskOps.STATUS, TaskPayloads.status(base[0], TaskStatus.DONE))
        val delete = waiting(TaskOps.DELETE, TaskPayloads.delete(base[1]))
        val shown = applyQueued(base, listOf(status, delete))
        assertEquals(listOf("a"), shown.map { it.id })
        assertTrue(shown.single().done && shown.single().pending)
    }

    @Test
    fun sentChangeCarriesTheNewVersionAndRejectedOnesLayNothingOver() {
        val base = listOf(task("a"))
        val fields = mapOf("titel" to JsonPrimitive("Neu"), "frist" to JsonNull)
        val payload = TaskPayloads.change(base[0], fields)
        val sent = waiting(TaskOps.CHANGE, payload, ChangeState.SENT, buildJsonObject { put("id", "a"); put("version", 2) })
        val shown = applyQueued(base, listOf(sent)).single()
        assertEquals("Neu", shown.title)
        assertNull(shown.due)
        assertEquals(2L, shown.version)
        assertFalse(shown.pending)
        assertEquals(base, applyQueued(base, listOf(waiting(TaskOps.CHANGE, payload, ChangeState.REJECTED))))
        assertEquals(base, applyQueued(base, listOf(waiting(TaskOps.CHANGE, payload, ChangeState.CONFLICT))))
    }

    @Test
    fun offlineQuickCaptureMakesOneTitlePerLine() {
        val items = TasksViewModel.offlineItems("  Zelte prüfen \n\nKüche putzen\n")
        assertEquals(listOf("Zelte prüfen", "Küche putzen"), items.map { it.title })
        assertTrue(items.none { it.titleError })
        assertEquals(20, TasksViewModel.offlineItems((1..30).joinToString("\n") { "Aufgabe $it" }).size)
    }
}
