package io.github.sorry4nothing.cevios.lager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class ArchiveLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun data(
        days: List<CampDay> = emptyList(),
        milestones: List<Milestone> = emptyList(),
        meetings: List<Meeting> = emptyList(),
        tasks: List<CampTask> = emptyList(),
    ) = TimelineData(Schedule(days, emptyList(), milestones), meetings, tasks)

    // ---- Rechte ----

    @Test
    fun addressBookIsForTheLeadershipOnly() {
        assertTrue(ArchiveRights.of(CampRole.LEADERSHIP, readOnly = false).readAddressBook)
        listOf(CampRole.LEADER, CampRole.TEAM, CampRole.READ_ONLY).forEach {
            val rights = ArchiveRights.of(it, readOnly = false)
            assertFalse(it.name, rights.readAddressBook || rights.editAddressBook || rights.editSchedule)
        }
    }

    @Test
    fun documentsAreEditedByLeadershipAndLeaderAndNothingChangesInAClosedCamp() {
        assertTrue(ArchiveRights.of(CampRole.LEADERSHIP, false).editDocuments)
        assertTrue(ArchiveRights.of(CampRole.LEADER, false).editDocuments)
        assertFalse(ArchiveRights.of(CampRole.TEAM, false).editDocuments)
        assertFalse(ArchiveRights.of(CampRole.READ_ONLY, false).editDocuments)
        val closed = ArchiveRights.of(CampRole.LEADERSHIP, readOnly = true)
        assertTrue(closed.readAddressBook)
        assertFalse(closed.editDocuments || closed.editAddressBook || closed.editSchedule)
    }

    // ---- Zeitplan ----

    @Test
    fun timelineSortsByDateAndKindAndNumbersTheDays() {
        val entries = TimelineRules.entries(
            data(
                days = listOf(CampDay("d2", "2027-07-18", null), CampDay("d1", "2027-07-17", "Anreise")),
                milestones = listOf(Milestone("m1", 1, "Lagerhaus fest", "2027-03-03", "Vertrag")),
                meetings = listOf(Meeting("h1", 1, date = "2027-03-10", time = "19:30", title = "Höck")),
                tasks = listOf(CampTask("t1", 1, "Offerte einholen", due = "2027-03-03")),
            ),
        )
        assertEquals(listOf("m1", "t1", "h1", "d1", "d2"), entries.map { it.id })
        assertEquals(listOf(TimelineKind.MILESTONE, TimelineKind.DUE), entries.take(2).map { it.kind })
        assertEquals(listOf(1, 2), entries.filter { it.kind == TimelineKind.DAY }.map { it.number })
        assertEquals("Anreise", entries.first { it.id == "d1" }.title)
        assertNull(entries.first { it.id == "d2" }.title)
        assertEquals("19:30", entries.first { it.id == "h1" }.detail)
    }

    @Test
    fun timelineSkipsDoneTasksAndTasksWithoutDueDate() {
        val entries = TimelineRules.entries(
            data(tasks = listOf(CampTask("a", 1, "Offen", due = "2027-03-05"), CampTask("b", 1, "Fertig", statusWire = "erledigt", due = "2027-03-05"), CampTask("c", 1, "Ohne Frist"))),
        )
        assertEquals(listOf("a"), entries.map { it.id })
    }

    @Test
    fun timelineGroupsByMonthInCalendarOrder() {
        val entries = TimelineRules.entries(data(milestones = listOf(Milestone("a", 1, "April", "2027-04-28"), Milestone("b", 1, "März", "2027-03-31"), Milestone("c", 1, "Januar 2028", "2028-01-02"))))
        val months = TimelineRules.byMonth(entries).map { it.first }
        assertEquals(listOf(YearMonth.of(2027, 3), YearMonth.of(2027, 4), YearMonth.of(2028, 1)), months)
    }

    @Test
    fun monthViewStartsInTheNextMonthWithEntries() {
        val entries = TimelineRules.entries(data(milestones = listOf(Milestone("a", 1, "x", "2027-03-03"), Milestone("b", 1, "y", "2027-05-10"))))
        assertEquals(YearMonth.of(2027, 3), TimelineRules.startMonth(entries, LocalDate.of(2027, 3, 20)))
        assertEquals(YearMonth.of(2027, 5), TimelineRules.startMonth(entries, LocalDate.of(2027, 4, 1)))
        assertEquals(YearMonth.of(2027, 5), TimelineRules.startMonth(entries, LocalDate.of(2028, 1, 1)))
        assertEquals(YearMonth.of(2027, 4), TimelineRules.startMonth(emptyList(), LocalDate.of(2027, 4, 9)))
    }

    @Test
    fun monthCellsStartOnMondayAndFillFullWeeks() {
        val march = TimelineRules.cells(YearMonth.of(2027, 3))
        assertEquals(35, march.size)
        assertEquals(LocalDate.of(2027, 3, 1), march.first())
        assertNull(march.last())
        val april = TimelineRules.cells(YearMonth.of(2027, 4))
        assertEquals(35, april.size)
        assertEquals(listOf(null, null, null), april.take(3))
        assertEquals(LocalDate.of(2027, 4, 1), april[3])
        assertEquals(LocalDate.of(2027, 4, 30), april.last { it != null })
    }

    @Test
    fun kindsOnADayListEachKindOnce() {
        val entries = TimelineRules.entries(data(milestones = listOf(Milestone("a", 1, "x", "2027-03-03")), tasks = listOf(CampTask("t", 1, "A", due = "2027-03-03"), CampTask("u", 1, "B", due = "2027-03-03"))))
        assertEquals(setOf(TimelineKind.MILESTONE, TimelineKind.DUE), TimelineRules.kindsOn(entries, LocalDate.of(2027, 3, 3)))
        assertTrue(TimelineRules.kindsOn(entries, LocalDate.of(2027, 3, 4)).isEmpty())
    }

    @Test
    fun anEmptyProposalOffersNothing() {
        assertTrue(ScheduleProposal().isEmpty)
        assertFalse(ScheduleProposal(days = listOf(ProposedDay("2027-07-17"))).isEmpty)
    }

    // ---- Suche ----

    @Test
    fun searchNeedsTwoCharacters() {
        assertFalse(SearchRules.valid(" a "))
        assertTrue(SearchRules.valid("Zelt"))
        assertTrue(SearchRules.valid(" ab "))
    }

    @Test
    fun searchGroupsInAFixedOrderAndKeepsTheServerOrderInsideAGroup() {
        val hits = listOf(
            SearchHit("dokument", "1", "Menüplan"), SearchHit("aufgabe", "2", "Zelte"), SearchHit("zukunft", "3", "Neu"),
            SearchHit("aufgabe", "4", "Zeltplatz"), SearchHit("hoeck", "5", "Höck"),
        )
        val groups = SearchRules.grouped(hits)
        assertEquals(listOf("aufgabe", "hoeck", "dokument", SearchRules.OTHER_TYPE), groups.map { it.first })
        assertEquals(listOf("2", "4"), groups.first().second.map { it.id })
        assertEquals(listOf("3"), groups.last().second.map { it.id })
    }

    @Test
    fun searchHitsOpenTheModuleThatOwnsThem() {
        fun go(type: String) = SearchRules.destination(SearchHit(type, "x", "t"))
        assertEquals(SearchDestination.Task("x"), go("aufgabe"))
        assertEquals(SearchDestination.Live(LiveArea.DOCUMENTS), go("dokument"))
        assertEquals(SearchDestination.Live(LiveArea.ADDRESSBOOK), go("adressbuch"))
        assertEquals(SearchDestination.Live(LiveArea.TIMELINE), go("meilenstein"))
        assertEquals(SearchDestination.Meetings, go("protokoll"))
        assertEquals(SearchDestination.Plan(PlanArea.ROOMS), go("zimmer"))
        assertEquals(SearchDestination.Pinboard, go("pinnwand"))
        assertNull(go("unbekannt"))
        // Jeder Typ der Suche, den die Reihenfolge kennt, führt irgendwohin.
        SearchRules.ORDER.forEach { assertTrue(it, go(it) != null) }
    }

    // ---- Dokumente und Adressbuch ----

    @Test
    fun onlyDocumentsOfTheCampCanBeDeleted() {
        assertTrue(CampDocument("dokument", "a", 3, "Plan").editable)
        assertFalse(CampDocument("pinnwand", "b", null, "Datei").editable)
        assertEquals(DocumentKind.LINK, DocumentKind.from(null))
        assertEquals(DocumentKind.IMAGE, DocumentKind.from("bild"))
    }

    @Test
    fun aContactNeverPrintsItsData() {
        val contact = Contact("c1", 1, "Beispiel Hauswart", "Hauswart", "Lagerhaus", "000 00 00", "kontakt@beispiel.ch", "Notiz")
        assertEquals("Contact(id=c1)", contact.toString())
        assertEquals("Hauswart, Lagerhaus", contact.subtitle)
        assertNull(Contact("c2", 1, "Nur Name").subtitle)
    }
}
