package io.github.sorry4nothing.cevios.termine

import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.chat.ProfileAgeGroup
import io.github.sorry4nothing.cevios.chat.ProfileRole
import io.github.sorry4nothing.cevios.chat.ProfileTag
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.calendarEntry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class TermineLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val today = LocalDate.of(2027, 5, 1)
    private val now = ZonedDateTime.of(2027, 5, 1, 10, 0, 0, 0, ZoneId.of("Europe/Zurich"))

    private fun draft(
        title: String = "Waldspiel",
        allDay: Boolean = false,
        day: LocalDate? = LocalDate.of(2027, 5, 8),
        from: String = "14:00",
        to: String = "17:00",
        tags: Set<String> = setOf("t1"),
    ) = ProposalDraft(title = title, allDay = allDay, day = day, from = from, to = to, tagIds = tags)

    @Test
    fun aCompleteProposalHasNoProblemAndKeepsTheSwissOffset() {
        assertTrue(proposalProblems(draft(), today, now).isEmpty())
        val request = proposalRequest(draft(), today, now)!!
        // Mai ist Sommerzeit (+02:00), im Winter +01:00: Der Offset kommt aus der Zone der Abteilung, nicht aus dem Gerät.
        assertEquals("2027-05-08T14:00:00+02:00", request.start)
        assertEquals("2027-05-08T17:00:00+02:00", request.end)
        assertEquals("2027-12-04T14:00:00+01:00", proposalRequest(draft(day = LocalDate.of(2027, 12, 4)), today, now)!!.start)
    }

    @Test
    fun anAllDayProposalSendsDaysAndNoTimes() {
        val request = proposalRequest(draft(allDay = true, from = "", to = ""), today, now)!!
        assertEquals("2027-05-08", request.dayFrom)
        assertEquals("2027-05-08", request.dayTo)
        assertNull(request.start)
    }

    @Test
    fun everyProblemIsNamed() {
        assertEquals(setOf(ProposalProblem.TITLE), proposalProblems(draft(title = "  "), today, now))
        assertEquals(setOf(ProposalProblem.TAGS), proposalProblems(draft(tags = emptySet()), today, now))
        assertEquals(setOf(ProposalProblem.DAY), proposalProblems(draft(day = null), today, now))
        assertEquals(setOf(ProposalProblem.FROM), proposalProblems(draft(from = "abc"), today, now))
        assertEquals(setOf(ProposalProblem.TO), proposalProblems(draft(to = "25:00"), today, now))
        assertEquals(setOf(ProposalProblem.ORDER), proposalProblems(draft(from = "17:00", to = "14:00"), today, now))
        assertEquals(setOf(ProposalProblem.PAST), proposalProblems(draft(day = LocalDate.of(2027, 4, 30)), today, now))
        assertEquals(setOf(ProposalProblem.PAST), proposalProblems(draft(day = today, from = "09:00", to = "09:30"), today, now))
    }

    @Test
    fun aProposalWithProblemsIsNotSent() {
        assertNull(proposalRequest(draft(title = ""), today, now))
    }

    @Test
    fun timesAreReadTolerantly() {
        assertEquals(java.time.LocalTime.of(14, 0), parseTime("14:00"))
        assertEquals(java.time.LocalTime.of(9, 30), parseTime(" 9.30 "))
        assertNull(parseTime("1400"))
        assertNull(parseTime("24:00"))
    }

    private fun field(key: String, type: String, required: Boolean) = EventField(key, key, type, if (type == "auswahl") listOf("a", "b") else emptyList(), required)

    @Test
    fun missingRequiredFieldsAreFoundPerType() {
        val fields = listOf(field("t", "text", true), field("j", "ja_nein", true), field("a", "auswahl", true), field("f", "text", false))
        assertEquals(setOf("t", "j", "a"), missingRequired(fields, emptyMap()))
        val answers: Map<String, JsonElement> = mapOf("t" to JsonPrimitive("  "), "j" to JsonPrimitive(false), "a" to JsonPrimitive("b"))
        // Leerer Text fehlt, ein «Nein» ist eine Antwort.
        assertEquals(setOf("t"), missingRequired(fields, answers))
    }

    @Test
    fun cleanAnswersDropEmptyAndUnknownFieldsAndTrimText() {
        val fields = listOf(field("t", "text", false), field("j", "ja_nein", false), field("f", "text", false))
        val clean = cleanAnswers(
            fields,
            mapOf("t" to JsonPrimitive("  Hallo "), "j" to JsonPrimitive(false), "f" to JsonPrimitive(""), "x" to JsonPrimitive("fremd")),
        )
        assertEquals(mapOf<String, JsonElement>("t" to JsonPrimitive("Hallo"), "j" to JsonPrimitive(false)), clean)
    }

    @Test
    fun deadlinesAndDaysAreShownInSwissTime() {
        assertEquals("1.6.2027, 22:00", eventDeadlineText("2027-06-01T20:00:00Z"))
        assertEquals("kaputt", eventDeadlineText("kaputt"))
        assertEquals("8.5.2027", dayText("2027-05-08"))
        assertEquals("", dayText(null))
    }

    private fun form(allDay: Boolean, from: String?, to: String?, start: String? = null, end: String? = null) =
        EventForm("e", "t", "Lager", allDay, start, end, from, to, true, null)

    @Test
    fun theDateLineNamesDaysAndTimes() {
        assertEquals("17. Juli bis 24. Juli 2027", eventDateLine(form(true, "2027-07-17", "2027-07-24")))
        assertEquals("17. Juli 2027", eventDateLine(form(true, "2027-07-17", "2027-07-17")))
        assertEquals("Samstag, 17. Juli, 14:00–17:00", eventDateLine(form(false, null, null, "2027-07-17T12:00:00Z", "2027-07-17T15:00:00Z")))
        assertEquals("", eventDateLine(form(false, null, null)))
    }

    @Test
    fun groupsShowTheirStagesAndTheFunction() {
        val profile = Profile(
            id = "u", roles = listOf(ProfileRole("s1", "stufenleitung")), ageGroups = listOf(ProfileAgeGroup("s1", "Wölfe")),
        )
        val labels = groupLabels(profile, "Abteilungsleitung", "Stufenleitung", "Leitung")
        assertEquals(listOf("Wölfe", "Stufenleitung"), labels.map { it.name })
        assertEquals("s1", labels.first().stageId)
        assertNull(labels.last().stageId)
        assertEquals(listOf("Abteilungsleitung"), groupLabels(Profile(id = "a", departmentLead = true), "Abteilungsleitung", "Stufenleitung", "Leitung").map { it.name })
        assertTrue("der Entwickler hat keine Funktion in der Abteilung", groupLabels(Profile(id = "e", developer = true), "A", "S", "L").isEmpty())
        assertTrue(groupLabels(Profile(id = "p"), "A", "S", "L").isEmpty())
    }

    @Test
    fun groupsAlsoShowTheOwnTeamsAndCampsInTheOrderOfTheServer() {
        val profile = Profile(
            id = "u", ageGroups = listOf(ProfileAgeGroup("s1", "Wölfe")),
            tags = listOf(ProfileTag("t1", "Hausteam", "team"), ProfileTag("t2", "Sommerlager", "lager"), ProfileTag("t3", "Neues", "unbekannt")),
        )
        val labels = groupLabels(profile, "Abteilungsleitung", "Stufenleitung", "Leitung")
        assertEquals(listOf("Wölfe", "Hausteam", "Sommerlager"), labels.map { it.name })
        assertEquals(listOf(R.string.group_type_stufe, R.string.group_type_team, R.string.group_type_lager), labels.map { it.type })
        assertNull("nur eine Stufe trägt ein Logo", labels[1].stageId)
        // Wer nur Tags hat, bekommt trotzdem Gruppen (kein «Du hast noch keine Gruppe»).
        assertEquals(listOf("Hausteam"), groupLabels(Profile(id = "p", tags = listOf(ProfileTag("t1", "Hausteam", "team"))), "A", "S", "L").map { it.name })
    }

    private fun program(allDay: Boolean, vararg fields: String) = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<Program>(
        """{"id":"p","titel":"Waldspiel","status":"aktiv","ganztaegig":$allDay${fields.joinToString("") { ",$it" }}}""",
    )

    @Test
    fun theDeviceCalendarGetsUtcMidnightForAllDayAndInstantsForTimedEvents() {
        val allDay = program(true, "\"tagVon\":\"2027-07-10\"", "\"tagBis\":\"2027-07-12\"", "\"ort\":\"Zeltplatz\"").calendarEntry()!!
        assertEquals(true, allDay.allDay)
        assertEquals(1_815_177_600_000L, allDay.beginMillis)
        assertEquals(1_815_350_400_000L, allDay.endMillis)
        assertEquals("Zeltplatz", allDay.place)
        val timed = program(false, "\"beginn\":\"2027-05-08T12:00:00Z\"", "\"ende\":\"2027-05-08T15:00:00Z\"").calendarEntry()!!
        assertEquals(false, timed.allDay)
        assertEquals(3L * 3_600_000L, timed.endMillis - timed.beginMillis)
        assertNull(program(false).calendarEntry())
    }
}
