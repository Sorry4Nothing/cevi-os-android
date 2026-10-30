package io.github.sorry4nothing.cevios.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiFehlerTest {
    @Test
    fun jederFehlerHatEinenEigenenText() {
        assertEquals(ApiFehler.entries.size, ApiFehler.entries.map { it.text }.toSet().size)
    }

    @Test
    fun unbekannterStatusIstUnbekannterFehler() {
        assertEquals(ApiFehler.UNBEKANNT, ApiFehler.vonStatus(418))
    }
}
