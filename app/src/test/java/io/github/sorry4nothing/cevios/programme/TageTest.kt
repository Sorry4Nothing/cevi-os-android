package io.github.sorry4nothing.cevios.programme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TageTest {
    private fun termin(id: String, beginn: String, ende: String) =
        Programm(id = id, titel = id, status = "aktiv", ganztaegig = false, beginn = beginn, ende = ende)

    private fun ganztaegig(id: String, von: String, bis: String) =
        Programm(id = id, titel = id, status = "aktiv", ganztaegig = true, tagVon = von, tagBis = bis)

    @Test
    fun gleicherTagGibtEineGruppeMitMehrerenProgrammen() {
        val tage = gruppiereNachTag(
            listOf(
                termin("a", "2026-11-14T13:00:00.000Z", "2026-11-14T15:00:00.000Z"),
                termin("b", "2026-11-14T15:00:00.000Z", "2026-11-14T16:00:00.000Z"),
                termin("c", "2026-11-21T13:00:00.000Z", "2026-11-21T15:00:00.000Z"),
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 21)), tage.map { it.datum })
        assertEquals(listOf("a", "b"), tage[0].programme.map { it.id })
    }

    @Test
    fun spaetAbendsInUtcGehoertInDerSchweizZumNaechstenTag() {
        val tage = gruppiereNachTag(
            listOf(
                termin("a", "2026-11-14T10:00:00Z", "2026-11-14T11:00:00Z"),
                termin("b", "2026-11-14T23:30:00Z", "2026-11-15T00:30:00Z"),
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)), tage.map { it.datum })
    }

    @Test
    fun mehrtaegigesProgrammStehtAmErstenTag() {
        val tage = gruppiereNachTag(listOf(ganztaegig("lager", "2027-07-10", "2027-07-17")))
        assertEquals(LocalDate.of(2027, 7, 10), tage.single().datum)
    }

    @Test
    fun programmeOhneLesbareZeitStehenZusammenOhneDatum() {
        val ohne = Programm(id = "x", titel = "x", status = "aktiv", ganztaegig = false)
        val tage = gruppiereNachTag(listOf(ohne, ohne.copy(id = "y")))
        assertEquals(1, tage.size)
        assertEquals(null, tage.single().datum)
    }

    @Test
    fun leereListeGibtKeineTage() {
        assertEquals(emptyList<Tag>(), gruppiereNachTag(emptyList()))
    }

    @Test
    fun wochentagUndDatumFuerDieZeitleiste() {
        assertEquals("SA", wochentagKurz(LocalDate.of(2026, 11, 14)))
        assertEquals("MO", wochentagKurz(LocalDate.of(2026, 11, 16)))
        assertEquals("14. November", tagUndMonat(LocalDate.of(2026, 11, 14)))
    }
}
