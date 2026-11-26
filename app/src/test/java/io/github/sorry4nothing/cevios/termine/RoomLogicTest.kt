package io.github.sorry4nothing.cevios.termine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class RoomLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val now = ZonedDateTime.of(2027, 5, 1, 10, 0, 0, 0, ZoneId.of("Europe/Zurich"))

    private fun draft(
        room: String? = "r1",
        day: LocalDate? = LocalDate.of(2027, 5, 8),
        from: String = "14:00",
        to: String = "17:00",
        purpose: String = "Waldspiel",
    ) = RoomDraft(room, day, from, to, purpose)

    @Test
    fun aCompleteRequestHasNoProblemAndKeepsTheSwissOffset() {
        assertTrue(roomProblems(draft(), now).isEmpty())
        val body = roomRequestBody(draft(), now)!!
        // Mai ist Sommerzeit (+02:00), im Winter +01:00: Der Offset kommt aus der Zone der Abteilung, nicht aus dem Gerät.
        assertEquals("2027-05-08T14:00:00+02:00", body.start)
        assertEquals("2027-05-08T17:00:00+02:00", body.end)
        assertEquals("2027-12-04T14:00:00+01:00", roomRequestBody(draft(day = LocalDate.of(2027, 12, 4)), now)!!.start)
        assertEquals("Waldspiel", body.purpose)
    }

    @Test
    fun everyProblemIsNamed() {
        assertEquals(setOf(RoomProblem.ROOM), roomProblems(draft(room = null), now))
        assertEquals(setOf(RoomProblem.DAY), roomProblems(draft(day = null), now))
        assertEquals(setOf(RoomProblem.FROM), roomProblems(draft(from = "abc"), now))
        assertEquals(setOf(RoomProblem.TO), roomProblems(draft(to = "25:00"), now))
        assertEquals(setOf(RoomProblem.ORDER), roomProblems(draft(from = "17:00", to = "14:00"), now))
        assertEquals(setOf(RoomProblem.ORDER), roomProblems(draft(from = "14:00", to = "14:00"), now))
        assertEquals(setOf(RoomProblem.PURPOSE), roomProblems(draft(purpose = "   "), now))
        assertNull(roomRequestBody(draft(room = null), now))
    }

    @Test
    fun theStartMustLieInTheFuture() {
        assertEquals(setOf(RoomProblem.PAST), roomProblems(draft(day = LocalDate.of(2027, 4, 30)), now))
        // Heute, aber vor jetzt: vorbei. Genau jetzt: nicht in der Zukunft.
        assertEquals(setOf(RoomProblem.PAST), roomProblems(draft(day = LocalDate.of(2027, 5, 1), from = "09:00", to = "11:00"), now))
        assertEquals(setOf(RoomProblem.PAST), roomProblems(draft(day = LocalDate.of(2027, 5, 1), from = "10:00", to = "11:00"), now))
        assertTrue(roomProblems(draft(day = LocalDate.of(2027, 5, 1), from = "10:01", to = "11:00"), now).isEmpty())
    }

    @Test
    fun thePurposeIsOneLineOfAtMostTwoHundredCharacters() {
        assertTrue(roomProblems(draft(purpose = "a".repeat(200)), now).isEmpty())
        assertEquals(setOf(RoomProblem.PURPOSE), roomProblems(draft(purpose = "a".repeat(201)), now))
        assertEquals(setOf(RoomProblem.PURPOSE), roomProblems(draft(purpose = "Spiel\nmit Gästen"), now))
        assertEquals("Spiel mit Gästen", oneLine("Spiel\nmit Gästen"))
        assertEquals("Spiel mit Gästen", oneLine("Spiel \r\n mit Gästen"))
        assertEquals("Waldspiel", roomRequestBody(draft(purpose = "  Waldspiel  "), now)!!.purpose)
    }

    @Test
    fun theStatesOfARequest() {
        assertEquals(RoomStatus.OPEN, roomStatus("angefragt"))
        assertEquals(RoomStatus.CONFIRMED, roomStatus("bestaetigt"))
        assertEquals(RoomStatus.REJECTED, roomStatus("abgelehnt"))
        assertEquals(RoomStatus.CANCELLED, roomStatus("storniert"))
        assertEquals(RoomStatus.UNKNOWN, roomStatus("neu"))
    }

    private fun request(status: String, end: String) =
        RoomRequest("a1", "r1", "Pfadiheim Beispielwald", "2027-05-08T12:00:00Z", end, "Waldspiel", status)

    @Test
    fun onlyOpenAndConfirmedRequestsOfTheFutureCanBeWithdrawn() {
        val at = Instant.parse("2027-05-08T15:00:00Z")
        assertTrue(canCancelRequest(request("angefragt", "2027-05-08T16:00:00Z"), at))
        assertTrue(canCancelRequest(request("bestaetigt", "2027-05-08T16:00:00Z"), at))
        assertFalse(canCancelRequest(request("bestaetigt", "2027-05-08T14:00:00Z"), at))
        assertFalse(canCancelRequest(request("abgelehnt", "2027-05-08T16:00:00Z"), at))
        assertFalse(canCancelRequest(request("storniert", "2027-05-08T16:00:00Z"), at))
        assertFalse(canCancelRequest(request("angefragt", "kein Datum"), at))
    }

    @Test
    fun theTimeReadsInSwissTimeOnOneLine() {
        // Winterzeit: 13:00 UTC ist 14:00 in Zürich; der 11.12.2027 ist ein Samstag.
        assertEquals("Sa 11.12., 14:00–18:00", roomWhen("2027-12-11T13:00:00Z", "2027-12-11T17:00:00Z"))
        assertEquals("Fr 4.6., 19:30–21:30", roomWhen("2027-06-04T17:30:00Z", "2027-06-04T19:30:00Z"))
        assertEquals("Fr 4.6., 22:00 – Sa 5.6., 02:00", roomWhen("2027-06-04T20:00:00Z", "2027-06-05T00:00:00Z"))
        assertEquals("", roomWhen("morgen", "später"))
    }

    @Test
    fun theSpokenTimeNamesTheWeekdayInFull() {
        assertEquals("Samstag, 11. Dezember, 14:00 bis 18:00", roomWhenSpoken("2027-12-11T13:00:00Z", "2027-12-11T17:00:00Z"))
        assertEquals("Mittwoch, 2. Juni, 22:00 bis Donnerstag, 3. Juni, 02:00", roomWhenSpoken("2027-06-02T20:00:00Z", "2027-06-03T00:00:00Z"))
        assertEquals("", roomWhenSpoken("morgen", "später"))
    }

    @Test
    fun theDayIsNamedInFull() {
        assertEquals("Samstag, 12.12.", roomDayLong(LocalDate.of(2026, 12, 12)))
        assertEquals("So", dayNameShort(LocalDate.of(2026, 12, 13)))
    }
}
