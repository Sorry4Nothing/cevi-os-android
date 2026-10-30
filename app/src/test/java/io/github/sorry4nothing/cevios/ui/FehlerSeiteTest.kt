package io.github.sorry4nothing.cevios.ui

import io.github.sorry4nothing.cevios.net.ApiFehler
import org.junit.Assert.assertEquals
import org.junit.Test

class FehlerSeiteTest {
    @Test
    fun jederFehlerBekommtEineSeite() {
        ApiFehler.entries.forEach { FehlerSeite.von(it) }
    }

    @Test
    fun zuordnungNachDemDesign() {
        assertEquals(FehlerSeite.OFFLINE, FehlerSeite.von(ApiFehler.NETZWERK))
        assertEquals(FehlerSeite.NICHT_GEFUNDEN, FehlerSeite.von(ApiFehler.NICHT_GEFUNDEN))
        assertEquals(FehlerSeite.KEIN_ZUGRIFF, FehlerSeite.von(ApiFehler.KEINE_BERECHTIGUNG))
        assertEquals(FehlerSeite.SITZUNG, FehlerSeite.von(ApiFehler.NICHT_ANGEMELDET))
        assertEquals(FehlerSeite.VERALTET, FehlerSeite.von(ApiFehler.APP_VERALTET))
        assertEquals(FehlerSeite.FEHLER, FehlerSeite.von(ApiFehler.UNBEKANNT))
        assertEquals(FehlerSeite.FEHLER, FehlerSeite.von(ApiFehler.ZU_VIELE_ANFRAGEN))
    }
}
