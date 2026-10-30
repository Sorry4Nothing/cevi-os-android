package io.github.sorry4nothing.cevios.programme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ZeitTest {
    private fun termin(beginn: String?, ende: String?) = Programm(
        id = "x", titel = "T", status = "aktiv", ganztaegig = false, beginn = beginn, ende = ende,
    )

    @Test
    fun winterzeitIstEineStundeVorUtc() {
        val zeit = termin("2026-11-14T13:00:00.000Z", "2026-11-14T16:00:00.000Z").zeit() as Zeit.Termin
        assertEquals(LocalDateTime.of(2026, 11, 14, 14, 0), zeit.beginn.toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 11, 14, 17, 0), zeit.ende.toLocalDateTime())
    }

    @Test
    fun sommerzeitIstZweiStundenVorUtc() {
        val zeit = termin("2026-07-14T12:00:00.000Z", "2026-07-14T15:00:00.000Z").zeit() as Zeit.Termin
        assertEquals(LocalDateTime.of(2026, 7, 14, 14, 0), zeit.beginn.toLocalDateTime())
    }

    @Test
    fun spaetAbendsInUtcIstInDerSchweizSchonDerNaechsteTag() {
        val zeit = termin("2026-11-14T23:30:00Z", "2026-11-15T01:00:00Z").zeit() as Zeit.Termin
        assertEquals(LocalDate.of(2026, 11, 15), zeit.beginn.toLocalDate())
    }

    @Test
    fun ganztaegigNimmtDieKalendertageUnveraendert() {
        val p = Programm(id = "x", titel = "T", status = "aktiv", ganztaegig = true, tagVon = "2027-03-01", tagBis = "2027-03-03")
        assertEquals(Zeit.Ganztaegig(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 3)), p.zeit())
    }

    @Test
    fun fehlendeOderKaputteAngabenGebenKeineZeit() {
        assertNull(termin(null, null).zeit())
        assertNull(termin("morgen", "übermorgen").zeit())
        assertNull(Programm(id = "x", titel = "T", status = "aktiv", ganztaegig = true).zeit())
    }
}
