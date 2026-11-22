package io.github.sorry4nothing.cevios.lager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Rechte, Raster des Schichtplans, bekannte Personen und Sortierungen der Einteilung. */
class PlanningLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val me = "me"
    private val day1 = CampDay("d1", "2027-07-17")
    private val day2 = CampDay("d2", "2027-07-18")
    private val morning = DaySection("s1", "Vormittag", order = 1)
    private val evening = DaySection("s2", "Abend", order = 2)
    private val kitchen = Duty("k", 1, "Küche", minimum = 2, tagesabschnittId = "s1")
    private val guard = Duty("n", 1, "Nachtwache", minimum = 1, night = true, tagesabschnittId = "s2")
    private fun shift(id: String, day: CampDay, duty: Duty, personId: String?, name: String, participantId: String? = null) =
        Shift(id, 1, duty.id, day.id, day.date, duty.tagesabschnittId, personId, participantId, name)

    private val schedule = Schedule(listOf(day2, day1), listOf(evening, morning))

    @Test
    fun rightsFollowTheDefaultRoles() {
        val leadership = PlanRights.of(CampRole.LEADERSHIP, readOnly = false)
        assertTrue(leadership.editParticipants && leadership.edit && leadership.decideSwap && leadership.count && leadership.seeAll)
        val leader = PlanRights.of(CampRole.LEADER, readOnly = false)
        assertFalse(leader.editParticipants)
        assertTrue(leader.edit && leader.decideSwap && leader.requestSwap)
        val team = PlanRights.of(CampRole.TEAM, readOnly = false)
        assertFalse(team.edit || team.decideSwap || team.seeAll)
        assertTrue(team.count && team.requestSwap)
        val reading = PlanRights.of(CampRole.READ_ONLY, readOnly = false)
        assertFalse(reading.edit || reading.count || reading.requestSwap)
        assertTrue(reading.seeAll)
    }

    @Test
    fun aClosedCampOffersNothingToChange() {
        val closed = PlanRights.of(CampRole.LEADERSHIP, readOnly = true)
        assertFalse(closed.editParticipants || closed.edit || closed.count || closed.requestSwap || closed.decideSwap)
    }

    @Test
    fun cellsHaveOneRowPerDayByDateAndOneColumnPerDutyBySection() {
        val plan = ShiftPlan(listOf(guard, kitchen), emptyList(), emptyList())
        val rows = PlanRules.cells(plan, schedule)
        assertEquals(listOf("2027-07-17", "2027-07-18"), rows.map { it.first().day.date })
        assertEquals(listOf("Küche", "Nachtwache"), rows.first().map { it.duty.name })
        assertEquals("Vormittag", rows.first().first().section?.name)
    }

    @Test
    fun aCellKnowsHowManySeatsAreFree() {
        val plan = ShiftPlan(
            listOf(kitchen, guard),
            listOf(shift("1", day1, kitchen, me, "Jonas"), shift("2", day1, guard, "x", "Tim"), shift("3", day2, kitchen, "y", "Sina"), shift("4", day2, kitchen, "z", "Lea")),
            emptyList(),
        )
        val rows = PlanRules.cells(plan, schedule)
        val first = rows[0]
        assertEquals(1, first[0].missing)
        assertFalse(first[0].isFull)
        assertTrue(first[1].isFull)
        assertEquals(1, PlanRules.gaps(first))
        assertEquals(0, PlanRules.gaps(rows[1].filter { it.duty.id == "k" }))
        assertEquals(1, PlanRules.gaps(rows[1]))
    }

    @Test
    fun anOverfilledCellNeverCountsNegativeSeats() {
        val plan = ShiftPlan(listOf(guard), listOf(shift("1", day1, guard, "a", "A"), shift("2", day1, guard, "b", "B")), emptyList())
        assertEquals(0, PlanRules.cells(plan, schedule).first().single().missing)
    }

    @Test
    fun shiftsOfFindsOnlyTheOwnShiftsAndNeverParticipants() {
        val plan = ShiftPlan(listOf(kitchen), listOf(shift("1", day1, kitchen, me, "Ich"), shift("2", day1, kitchen, null, "Kind", participantId = "c")), emptyList())
        assertEquals(listOf("1"), PlanRules.shiftsOf(plan, me).map { it.id })
    }

    @Test
    fun knownPeopleMergeEverySourceOnceAndLeaveOutParticipants() {
        val plan = ShiftPlan(listOf(kitchen), listOf(shift("1", day1, kitchen, "a", "Anna"), shift("2", day1, kitchen, null, "Kind", participantId = "c")), emptyList())
        val groups = GroupList(listOf(Group("g", 1, "Falken", people = listOf(GroupPerson("b", "Ben", "Biber", "leitung")))), 0, 0)
        val rooms = RoomList(listOf(Room("r", 1, "Eiger", "zimmer", members = listOf(RoomMember("person", "c1", "Cora"), RoomMember("teilnehmer", "t1", "Kind")))), null)
        val trips = listOf(Trip("t", 1, "hin", "auto", driver = TripDriver("d", "Dan"), passengers = listOf(TripPerson("m", 1, personId = "a", name = "Anna"))))
        val people = PlanRules.knownPeople(plan, groups, rooms, trips, listOf(RessortPerson("e", "Eva")))
        assertEquals(listOf("Anna", "Ben (Biber)", "Cora", "Dan", "Eva"), people.map { it.display })
    }

    @Test
    fun tripsAreSortedByDateAndTimeAndKeepTheDirection() {
        val trips = listOf(
            Trip("1", 1, "hin", "auto", date = "2027-07-17", time = "09:00"),
            Trip("2", 1, "rueck", "auto", date = "2027-07-24"),
            Trip("3", 1, "hin", "oev", date = "2027-07-17", time = "08:12"),
            Trip("4", 1, "hin", "auto"),
        )
        assertEquals(listOf("3", "1", "4"), PlanRules.tripsOf(trips, TripDirection.THERE).map { it.id })
        assertEquals(listOf("2"), PlanRules.tripsOf(trips, TripDirection.BACK).map { it.id })
    }

    @Test
    fun seatedKeysCoverDriversAndPassengersOfOneDirection() {
        val trips = listOf(
            Trip("1", 1, "hin", "auto", fahrerPersonId = "d", passengers = listOf(TripPerson("m", 1, teilnehmerId = "t", name = "Kind"), TripPerson("n", 1, personId = "p", name = "Pia"))),
            Trip("2", 1, "rueck", "auto", fahrerPersonId = "other"),
        )
        assertEquals(setOf("p:d", "t:t", "p:p"), PlanRules.seatedKeys(trips, TripDirection.THERE))
    }

    @Test
    fun swapsShowOpenRequestsFirstOldestFirstThenDecidedNewestFirst() {
        fun swap(id: String, status: String, at: String) = Swap(id, 1, "p", "s", null, null, null, status, null, null, at)
        val sorted = PlanRules.sortedSwaps(
            listOf(swap("a", "angenommen", "2027-07-14T10:00:00Z"), swap("b", "offen", "2027-07-16T10:00:00Z"), swap("c", "offen", "2027-07-15T10:00:00Z"), swap("d", "abgelehnt", "2027-07-15T10:00:00Z")),
        )
        assertEquals(listOf("c", "b", "d", "a"), sorted.map { it.id })
    }

    @Test
    fun swapActorFallsBackWhenThePersonIsUnknown() {
        val swap = Swap("s", 1, "p", "x", null, null, null, "offen", null, null, "t")
        assertEquals("Pia", PlanRules.swapActor(swap, null, mapOf("p" to "Pia"), "jemand"))
        assertEquals("jemand", PlanRules.swapActor(swap, null, emptyMap(), "jemand"))
    }

    @Test
    fun swapTimeIsReadableInSwissTimeAndEmptyWhenUnreadable() {
        assertEquals("16.7. 21:40", swapTime("2027-07-16T19:40:00.000Z"))
        assertEquals("", swapTime("kaputt"))
        assertEquals("", swapTime(null))
    }

    @Test
    fun stagesAreUniqueAndSorted() {
        val list = listOf(
            Participant("1", 1, "A", "A", "Wölfe"), Participant("2", 1, "B", "B", "Adler"), Participant("3", 1, "C", "C", "Wölfe"), Participant("4", 1, "D", "D", null),
        )
        assertEquals(listOf("Adler", "Wölfe"), PlanRules.stages(list))
        assertEquals(listOf("A", "B", "C", "D"), PlanRules.sortedParticipants(list).map { it.firstName })
    }

    @Test
    fun toggledAddsAndRemoves() {
        assertEquals(setOf("a", "b"), setOf("a").toggled("b"))
        assertEquals(emptySet<String>(), setOf("a").toggled("a"))
    }

    @Test
    fun campTargetOpensTheSwapsForTheSwapPushesAndKinds() {
        assertEquals(CampTarget.Swaps, campTargetFor("lager_einteilung", "diensttausch", "x"))
        assertEquals(CampTarget.Swaps, campTargetFor("diensttausch_anfrage", "diensttausch", "x"))
        assertEquals(CampTarget.Swaps, campTargetFor("diensttausch_entscheid", "irgendwas", "x"))
        // Die Anwesenheit hat seit A4 einen eigenen Bildschirm.
        assertEquals(CampTarget.Attendance, campTargetFor("lager_anwesenheit", "anwesenheit", "x"))
    }

    @Test
    fun spokenDayNamesTheWeekday() {
        assertEquals("Samstag, 17. Juli", spokenDay("2027-07-17"))
        assertEquals("kaputt", spokenDay("kaputt"))
    }

    @Test
    fun percentHandlesAnEmptyList() {
        assertEquals(0, PlanRules.percent(0, 0))
        assertEquals(81, PlanRules.percent(18, 22))
    }
}
