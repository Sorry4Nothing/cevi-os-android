package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class LiveLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val day = "d1"
    private val morning = "s1"
    private val afternoon = "s2"
    private fun entry(section: String, value: Attendance, day: String = this.day) = AttendanceEntry("e-$section-$day", 1, day, section, value.wire)
    private fun own(vararg entries: AttendanceEntry, requests: List<AttendanceRequest> = emptyList(), deadline: String? = null) = AttendanceOwn(entries.toList(), requests, deadline)

    // ---- Rechte ----

    @Test
    fun rightsFollowTheStandardRoles() {
        val leadership = LiveRights.of(CampRole.LEADERSHIP, readOnly = false)
        assertTrue(leadership.decideAttendance && leadership.editInfos && leadership.editShopping && leadership.editDayPages)
        val leader = LiveRights.of(CampRole.LEADER, readOnly = false)
        assertTrue(leader.ownAttendance && leader.readAttendance && leader.editShopping && leader.editDayPages)
        assertFalse(leader.decideAttendance || leader.editInfos)
        val team = LiveRights.of(CampRole.TEAM, readOnly = false)
        assertTrue(team.ownAttendance && team.tickPacking && team.todos)
        assertFalse(team.readShopping || team.readDayPages || team.readAttendance || team.editShopping)
        val reader = LiveRights.of(CampRole.READ_ONLY, readOnly = false)
        assertTrue(reader.readShopping && reader.readDayPages && reader.readAttendance && reader.todos)
        assertFalse(reader.ownAttendance || reader.tickPacking || reader.editShopping)
    }

    @Test
    fun aClosedCampLeavesNothingToChange() {
        val closed = LiveRights.of(CampRole.LEADERSHIP, readOnly = true)
        assertFalse(closed.ownAttendance || closed.decideAttendance || closed.editInfos || closed.editShopping || closed.tickPacking || closed.todos || closed.editDayPages)
        assertTrue(closed.readAttendance && closed.readShopping && closed.readDayPages)
    }

    // ---- Anwesenheit ----

    @Test
    fun tappingCyclesThroughPresentAbsentOpen() {
        assertEquals(Attendance.PRESENT, Attendance.OPEN.next())
        assertEquals(Attendance.ABSENT, Attendance.PRESENT.next())
        assertEquals(Attendance.OPEN, Attendance.ABSENT.next())
        assertEquals(Attendance.OPEN, Attendance.from("x"))
    }

    @Test
    fun wholeDayWithOneTap() {
        assertEquals(Attendance.PRESENT, LiveRules.wholeDay(listOf(Attendance.OPEN, Attendance.OPEN, Attendance.OPEN)))
        assertEquals(Attendance.ABSENT, LiveRules.wholeDay(listOf(Attendance.PRESENT, Attendance.PRESENT)))
        assertEquals(Attendance.OPEN, LiveRules.wholeDay(listOf(Attendance.ABSENT, Attendance.ABSENT)))
        // gemischt: erst alle auf «Da»
        assertEquals(Attendance.PRESENT, LiveRules.wholeDay(listOf(Attendance.ABSENT, Attendance.PRESENT)))
    }

    @Test
    fun openFieldsGoDirectlyAndConfirmedOnesNeedARequest() {
        val state = own(entry(morning, Attendance.PRESENT))
        val draft = mapOf(
            AttendanceKey(day, morning) to Attendance.ABSENT,
            AttendanceKey(day, afternoon) to Attendance.PRESENT,
        )
        val plan = LiveRules.plan(state, draft, passed = false)
        assertEquals(listOf(AttendanceField(day, afternoon, Attendance.PRESENT)), plan.direct)
        assertEquals(listOf(AttendanceField(day, morning, Attendance.ABSENT)), plan.requests)
    }

    @Test
    fun afterTheDeadlineEveryChangeNeedsARequest() {
        val plan = LiveRules.plan(own(), mapOf(AttendanceKey(day, morning) to Attendance.PRESENT), passed = true)
        assertTrue(plan.direct.isEmpty())
        assertEquals(1, plan.requests.size)
    }

    @Test
    fun unchangedValuesAndFieldsWithAWaitingRequestDropOut() {
        val waiting = AttendanceRequest("r1", 1, "p", day, afternoon, "F", statusWire = "offen")
        val decided = AttendanceRequest("r2", 1, "p", day, morning, "F", statusWire = "angenommen")
        val state = own(entry(morning, Attendance.PRESENT), requests = listOf(waiting, decided))
        val draft = mapOf(
            AttendanceKey(day, morning) to Attendance.PRESENT, // gleich
            AttendanceKey(day, afternoon) to Attendance.PRESENT, // Anfrage wartet
        )
        val plan = LiveRules.plan(state, draft, passed = false)
        assertTrue(plan.direct.isEmpty() && plan.requests.isEmpty())
        assertEquals("r1", LiveRules.waitingFor(state, AttendanceKey(day, afternoon))!!.id)
        assertNull(LiveRules.waitingFor(state, AttendanceKey(day, morning)))
    }

    @Test
    fun aConfirmedFieldCanBeReopenedByRequest() {
        val plan = LiveRules.plan(own(entry(morning, Attendance.ABSENT)), mapOf(AttendanceKey(day, morning) to Attendance.OPEN), passed = false)
        assertEquals(AttendanceField(day, morning, Attendance.OPEN), plan.requests.single())
    }

    @Test
    fun deadlineIsPassedOnlyWhenThereIsOneBeforeNow() {
        val now = Instant.parse("2027-07-18T10:00:00Z")
        assertFalse(LiveRules.deadlinePassed(null, now))
        assertFalse(LiveRules.deadlinePassed("2027-07-18T10:00:01Z", now))
        assertTrue(LiveRules.deadlinePassed("2027-07-17T21:59:00.000Z", now))
        assertFalse(LiveRules.deadlinePassed("kein Datum", now))
    }

    @Test
    fun deadlineIsTheEndOfTheChosenDayInZurich() {
        assertEquals("2027-07-17T21:59:00Z", LiveRules.deadlineAt(LocalDate.of(2027, 7, 17)))
        assertEquals(LocalDate.of(2027, 7, 17), LiveRules.deadlineDate("2027-07-17T21:59:00.000Z"))
        assertNull(LiveRules.deadlineDate(null))
    }

    @Test
    fun summaryCountsPerDayAndSection() {
        val schedule = Schedule(
            listOf(CampDay("d2", "2027-07-18"), CampDay("d1", "2027-07-17")),
            listOf(DaySection("s2", "Nachmittag", null, 1), DaySection("s1", "Vormittag", null, 0)),
        )
        val summary = AttendanceSummary(
            listOf(
                AttendanceCount("d1", "s1", "A", 2, 10),
                AttendanceCount("d1", "s1", "F", 1, 0),
                AttendanceCount("d1", "s1", "O", 0, 3),
                AttendanceCount("d2", "s2", "A", 1, 4),
            ),
        )
        val days = LiveRules.summary(summary, schedule)
        assertEquals(listOf("d1", "d2"), days.map { it.day.id })
        assertEquals(listOf("Vormittag", "Nachmittag"), days[0].sections.map { it.section.name })
        assertEquals(Triple(12, 1, 3), days[0].sections[0].let { Triple(it.present, it.absent, it.open) })
        assertEquals(Triple(0, 0, 0), days[0].sections[1].let { Triple(it.present, it.absent, it.open) })
        assertEquals(5, days[1].sections[1].present)
    }

    // ---- Tagesseiten und Heute ----

    @Test
    fun dayOfCampCountsFromTheFirstDay() {
        assertEquals(3 to 8, LiveRules.dayOfCamp("2027-07-17", "2027-07-24", LocalDate.of(2027, 7, 19)))
        assertEquals(1 to 8, LiveRules.dayOfCamp("2027-07-17", "2027-07-24", LocalDate.of(2027, 7, 17)))
        assertEquals(8 to 8, LiveRules.dayOfCamp("2027-07-17", "2027-07-24", LocalDate.of(2027, 7, 24)))
        assertNull(LiveRules.dayOfCamp("2027-07-17", "2027-07-24", LocalDate.of(2027, 7, 16)))
        assertNull(LiveRules.dayOfCamp("2027-07-17", "2027-07-24", LocalDate.of(2027, 7, 25)))
        assertNull(LiveRules.dayOfCamp("kein Datum", "2027-07-24", LocalDate.of(2027, 7, 19)))
    }

    @Test
    fun myShiftsAreOnlyTheOwnOnesInTheOrderOfTheDay() {
        val duties = listOf(
            Duty("k", 1, "Küche", tagesabschnittId = "s2"),
            Duty("w", 1, "Wache", tagesabschnittId = "s1"),
        )
        val shifts = listOf(
            Shift("a", 1, "k", "d1", "2027-07-19", "s2", personId = "me", name = "Ich"),
            Shift("b", 1, "w", "d1", "2027-07-19", "s1", personId = "me", name = "Ich"),
            Shift("c", 1, "k", "d1", "2027-07-19", "s2", personId = "other", name = "Anna"),
        )
        val sections = listOf(DaySection("s1", "Vormittag", null, 0), DaySection("s2", "Nachmittag", null, 1))
        val mine = LiveRules.myShiftsToday(TodayShiftPlan(duties, shifts), "me", sections)
        assertEquals(listOf("Wache", "Küche"), mine.map { it.first?.name })
        assertTrue(LiveRules.myShiftsToday(null, "me", sections).isEmpty())
    }

    @Test
    fun dueTasksAreMineOpenAndDueTodayOrEarlier() {
        val today = LocalDate.of(2027, 7, 19)
        val mine = CampTask("t1", 1, "Fotos", assigneeId = "me", due = "2027-07-19")
        val late = CampTask("t2", 1, "Brot", assigneeId = "me", due = "2027-07-17")
        val later = CampTask("t3", 1, "Später", assigneeId = "me", due = "2027-07-20")
        val done = CampTask("t4", 1, "Fertig", assigneeId = "me", due = "2027-07-19", statusWire = "erledigt")
        val other = CampTask("t5", 1, "Fremd", assigneeId = "x", due = "2027-07-19")
        val none = CampTask("t6", 1, "Ohne Frist", assigneeId = "me")
        assertEquals(listOf("t2", "t1"), LiveRules.dueForMe(listOf(mine, late, later, done, other, none), "me", today).map { it.id })
    }

    @Test
    fun nextDayForPushingATask() {
        assertEquals("2027-07-20", LiveRules.nextDay("2027-07-19"))
        assertEquals("2027-08-01", LiveRules.nextDay("2027-07-31"))
        assertNull(LiveRules.nextDay(null))
    }

    @Test
    fun blocksComeInTheOrderOfTheServerPerKind() {
        val blocks = listOf(
            DayBlock("a", 1, "wichtig", "B", order = 2),
            DayBlock("b", 1, "idee", "I", order = 0),
            DayBlock("c", 1, "wichtig", "A", order = 1),
        )
        assertEquals(listOf("A", "B"), LiveRules.blocks(blocks, BlockKind.IMPORTANT).map { it.text })
        assertEquals(listOf("I"), LiveRules.blocks(blocks, BlockKind.IDEA).map { it.text })
        assertTrue(LiveRules.blocks(blocks, BlockKind.PICTURE).isEmpty())
    }

    // ---- Einkauf ----

    @Test
    fun amountsAreWrittenWithoutNeedlessZeros() {
        assertEquals("6 kg", LiveRules.formatAmount(6.0, "kg"))
        assertEquals("1.5 l", LiveRules.formatAmount(1.5, "l"))
        assertEquals("0.25", LiveRules.formatAmount(0.25, null))
        assertEquals("3", LiveRules.formatAmount(3.0, " "))
        assertNull(LiveRules.formatAmount(null, "kg"))
    }

    @Test
    fun amountInputAcceptsCommaAndPoint() {
        assertEquals(1.5, LiveRules.parseAmount("1,5")!!, 0.0)
        assertEquals(12.0, LiveRules.parseAmount(" 12 ")!!, 0.0)
        assertNull(LiveRules.parseAmount(""))
        assertNull(LiveRules.parseAmount("viel"))
        assertNull(LiveRules.parseAmount("-2"))
        assertNull(LiveRules.parseAmount("0"))
        assertNull(LiveRules.parseAmount("1e9"))
    }

    private fun total(name: String, group: String, done: Boolean = false, amount: Double? = null, unit: String? = null, count: Int = 1, ids: List<String> = emptyList()) =
        ShopTotal(name, amount, unit, group, done = done, count = count, entries = ids.map { ShopEntry(it, 1) })

    @Test
    fun shopSectionsFollowTheWalkThroughTheStore() {
        val items = listOf(
            total("Reis", "vorrat", amount = 12.0, unit = "kg", count = 3, ids = listOf("1", "2", "3")),
            total("Äpfel", "obst_gemuese", amount = 6.0, unit = "kg", ids = listOf("4")),
            total("Birnen", "obst_gemuese", done = true, ids = listOf("5")),
            total("Zucker", "unbekannt", ids = listOf("6")),
        )
        val sections = LiveRules.shopSections(items)
        assertEquals(listOf(ShopGroup.PRODUCE, ShopGroup.PANTRY, ShopGroup.OTHER), sections.map { it.group })
        // Offenes vor Erledigtem
        assertEquals(listOf("Äpfel", "Birnen"), sections[0].lines.map { it.name })
        assertEquals("12 kg", sections[1].lines.single().amount)
        assertEquals(3, sections[1].lines.single().entries.size)
    }

    @Test
    fun shopTextListsOnlyOpenArticlesWithoutNames() {
        val sections = LiveRules.shopSections(
            listOf(
                total("Äpfel", "obst_gemuese", amount = 6.0, unit = "kg", ids = listOf("4")),
                total("Birnen", "obst_gemuese", done = true, ids = listOf("5")),
                total("Reis", "vorrat", amount = 12.0, unit = "kg", ids = listOf("1")),
            ),
        )
        val text = LiveRules.shopText(sections) { if (it == ShopGroup.PRODUCE) "Früchte und Gemüse" else "Trockenes" }
        assertEquals("Früchte und Gemüse\n- Äpfel 6 kg\n\nTrockenes\n- Reis 12 kg", text)
    }

    @Test
    fun suggestionsDropTheTypedNameItself() {
        val list = listOf(ShopSuggestion("Käse", 4), ShopSuggestion("Käsekuchen", 1))
        assertEquals(listOf("Käsekuchen"), LiveRules.matching(list, " käse ").map { it.name })
    }

    // ---- Packliste ----

    private fun packItem(id: String, section: String, text: String, state: String?, order: Int = 0, leadership: Boolean = false) =
        PackItem(id, 1, section, text, leadership, order, state)

    @Test
    fun packSectionsHideRemovedPointsAndAddOwnOnes() {
        val list = PackList(
            items = listOf(
                packItem("a", "Kleidung", "Regenjacke", "abgehakt", 0),
                packItem("b", "Kleidung", "Pulli", "offen", 1),
                packItem("c", "Sonstiges", "Kasse", "offen", 2, leadership = true),
                packItem("d", "Sonstiges", "Radio", "entfernt", 3),
            ),
            own = listOf(OwnPackItem("e", "Kleidung", "Mütze", true), OwnPackItem("f", "Eigene", "Buch", false)),
        )
        val sections = LiveRules.packSections(list)
        assertEquals(listOf("Kleidung", "Sonstiges", "Eigene"), sections.map { it.title })
        assertEquals(listOf("Regenjacke", "Pulli", "Mütze"), sections[0].rows.map { it.text })
        assertTrue(sections[0].rows[2].own && sections[0].rows[2].checked)
        assertTrue(sections[1].rows.single().leadershipOnly)
        assertEquals(2 to 5, LiveRules.packCount(list))
    }

    @Test
    fun packCountSkipsRemovedPoints() {
        val list = PackList(
            items = listOf(packItem("a", "K", "x", "abgehakt"), packItem("b", "K", "y", "offen"), packItem("c", "K", "z", "entfernt")),
            own = listOf(OwnPackItem("e", "K", "q", true)),
        )
        assertEquals(2 to 3, LiveRules.packCount(list))
    }

    // ---- Notfall ----

    @Test
    fun routePrefersTheSavedHttpsLinkThenTheAddress() {
        assertEquals("https://maps.example.org/x", LiveRules.routeUri(EmergencyAddress("Beispielberg", "Beispielweg 1", "https://maps.example.org/x")))
        // ein Link ohne https gilt nicht
        assertEquals("geo:0,0?q=Beispielweg%201%2C%20Beispielberg", LiveRules.routeUri(EmergencyAddress("Beispielberg", "Beispielweg 1", "http://maps.example.org/x")))
        assertEquals("geo:0,0?q=Beispielberg", LiveRules.routeUri(EmergencyAddress("Beispielberg", null, null)))
        assertNull(LiveRules.routeUri(EmergencyAddress()))
        assertNull(LiveRules.routeUri(EmergencyAddress(" ", "", null)))
    }

    @Test
    fun onlyContactsWithANumberAreCallable() {
        val contacts = listOf(
            EmergencyContact("eintrag", "a", 1, "Erste Hilfe", null, "Sina", "Eule", "+41 00 000 00 00", 0),
            EmergencyContact("ressort", null, null, "Sicherheit", "p", "Jonas", null, null, null),
            EmergencyContact("eintrag", "c", 1, "Leitung", null, "Mara", null, " ", 1),
        )
        assertEquals(listOf("Sina"), LiveRules.callable(contacts).map { it.name })
        assertEquals("Sina (Eule)", contacts[0].display)
        assertEquals("Jonas", contacts[1].display)
    }

    private val emergency = Emergency(contacts = listOf(EmergencyContact("eintrag", "a", 1, "Erste Hilfe", null, "Sina", null, "+41 00 000 00 00", 0)))
    private val t1 = Instant.parse("2027-07-19T16:02:00Z")
    private val t2 = Instant.parse("2027-07-19T16:30:00Z")

    @Test
    fun theEmergencyWindowKeepsTheLastStateWhenTheNetworkFails() {
        val first = ApiResult.Ok(EmergencyState(emergency, t1))
        val (shown, stale) = LiveRules.emergencyAfter(first, ApiResult.Failure(ApiError.NETWORK), t2)
        assertSame(first, shown)
        assertTrue(stale)
    }

    @Test
    fun aNewStateReplacesTheOldOneAndIsNotStale() {
        val first = ApiResult.Ok(EmergencyState(emergency, t1))
        val (shown, stale) = LiveRules.emergencyAfter(first, ApiResult.Ok(emergency.copy(contacts = emptyList())), t2)
        assertEquals(t2, (shown as ApiResult.Ok).value.loadedAt)
        assertTrue(shown.value.emergency.contacts.isEmpty())
        assertFalse(stale)
    }

    @Test
    fun withoutAnOldStateTheErrorShows() {
        val (shown, stale) = LiveRules.emergencyAfter(null, ApiResult.Failure(ApiError.NETWORK), t2)
        assertTrue(shown is ApiResult.Failure)
        assertFalse(stale)
        val (afterError, _) = LiveRules.emergencyAfter(ApiResult.Failure(ApiError.NETWORK), ApiResult.Failure(ApiError.NETWORK), t2)
        assertTrue(afterError is ApiResult.Failure)
    }

    // ---- Infos ----

    @Test
    fun movingASectionStaysInsideTheList() {
        val list = listOf(InfoSection("a", 1, "A"), InfoSection("b", 1, "B"), InfoSection("c", 1, "C"))
        assertEquals(listOf("b", "a", "c"), LiveRules.moved(list, "a", 1).map { it.id })
        assertEquals(listOf("a", "c", "b"), LiveRules.moved(list, "c", -1).map { it.id })
        assertSame(list, LiveRules.moved(list, "a", -1))
        assertSame(list, LiveRules.moved(list, "c", 1))
        assertSame(list, LiveRules.moved(list, "x", 1))
    }

    // ---- Ziele ----

    @Test
    fun attendancePushesAndNotificationsOpenTheAttendance() {
        assertEquals(CampTarget.Attendance, campTargetFor("anwesenheit_anfrage", "anwesenheit_anfrage", "x"))
        assertEquals(CampTarget.Attendance, campTargetFor("anwesenheit_entscheid", "anwesenheit", "x"))
        assertEquals(CampTarget.Attendance, campTargetFor("lager_anwesenheit", "anwesenheit", "x"))
    }
}
