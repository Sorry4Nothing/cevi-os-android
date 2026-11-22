package io.github.sorry4nothing.cevios.lager

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class CampFormatTest {
    // Ein hängender Test soll mit Namen scheitern, nicht die CI stundenlang blockieren.
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
    @Test
    fun datesInWordsInSwissGerman() {
        assertEquals("17. bis 24. Juli 2027", campDates("2027-07-17", "2027-07-24"))
        assertEquals("28. Juli bis 3. August 2027", campDates("2027-07-28", "2027-08-03"))
        assertEquals("30. Dezember 2027 bis 3. Januar 2028", campDates("2027-12-30", "2028-01-03"))
        assertEquals("17. Juli 2027", campDates("2027-07-17", "2027-07-17"))
    }

    @Test
    fun anUnreadableDateStaysAsItIs() {
        assertEquals("gestern bis 2027-07-24", campDates("gestern", "2027-07-24"))
    }

    @Test
    fun permissionsFollowTheGrantedRightsAndWriteProtection() {
        val team = CampPermissions(CampRole.TEAM, readOnly = false, granted = setOf("aufgaben.eigene", "pinnwand.lesen"))
        assertEquals(true, team.mayWrite("aufgaben.eigene"))
        assertEquals(true, team.may("pinnwand.lesen"))
        assertEquals(false, team.mayWrite("pinnwand.bearbeiten"))
        assertEquals(false, CampPermissions(CampRole.LEADERSHIP, readOnly = true, granted = setOf("aufgaben.eigene")).mayWrite("aufgaben.eigene"))
        assertEquals(false, NO_PERMISSIONS.may("aufgaben.eigene"))
        assertEquals(true, CampPermissions(CampRole.TEAM, false).canRead("aufgabe"))
        assertEquals(false, CampPermissions(CampRole.TEAM, false, setOf("ressort")).canRead("aufgabe"))
        assertEquals(true, CampPermissions(CampRole.TEAM, false, setOf("aufgabe")).canRead("aufgabe"))
    }
}
