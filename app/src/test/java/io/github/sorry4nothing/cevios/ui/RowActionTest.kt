package io.github.sorry4nothing.cevios.ui

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class RowActionTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    @Test
    fun namesTheRow() {
        assertEquals("Annehmen: Frühstück richten", rowActionLabel("Annehmen", "Frühstück richten"))
        assertEquals("Gelesen: Neue Aufgabe", rowActionLabel("Gelesen", "  Neue Aufgabe "))
    }

    @Test
    fun withoutRowNameItStaysTheAction() {
        assertEquals("Annehmen", rowActionLabel("Annehmen", null))
        assertEquals("Annehmen", rowActionLabel("Annehmen", "   "))
    }
}
