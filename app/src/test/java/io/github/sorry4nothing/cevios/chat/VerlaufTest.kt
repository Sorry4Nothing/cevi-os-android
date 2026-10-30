package io.github.sorry4nothing.cevios.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VerlaufTest {
    private fun n(id: String) =
        ChatNachricht(id = id, text = "t", erstelltAm = "2027-01-01T10:00:00Z", autor = ChatPerson("a", "A"))

    private fun seite(vararg ids: String, naechster: String? = null) = NachrichtenSeite(ids.map(::n), naechster)

    private fun ids(v: Verlauf) = v.nachrichten.map { it.id }

    @Test
    fun ersteSeiteWirdUebernommen() {
        val v = Verlauf().mitNeuesterSeite(seite("0003", "0002", naechster = "0002"))
        assertEquals(listOf("0003", "0002"), ids(v))
        assertEquals("0002", v.naechster)
    }

    @Test
    fun neueNachrichtenKommenObenDazuUndAeltereBleiben() {
        val bisher = Verlauf(listOf("0005", "0004", "0003", "0002", "0001").map(::n), naechster = "0001")
        val v = bisher.mitNeuesterSeite(seite("0007", "0006", "0005", "0004", naechster = "0004"))
        assertEquals(listOf("0007", "0006", "0005", "0004", "0003", "0002", "0001"), ids(v))
        assertEquals("Cursor der älteren Seiten bleibt", "0001", v.naechster)
    }

    @Test
    fun beruehrungAmRandGiltAlsUeberlappung() {
        val bisher = Verlauf(listOf("0003", "0002").map(::n), naechster = null)
        val v = bisher.mitNeuesterSeite(seite("0004", "0003", naechster = "0003"))
        assertEquals(listOf("0004", "0003", "0002"), ids(v))
    }

    @Test
    fun zuVieleNeueNachrichtenLassenEineLueckeUndDieNeueSeiteGilt() {
        val bisher = Verlauf(listOf("0002", "0001").map(::n), naechster = null)
        val v = bisher.mitNeuesterSeite(seite("0099", "0098", "0097", naechster = "0097"))
        assertEquals(listOf("0099", "0098", "0097"), ids(v))
        assertEquals("0097", v.naechster)
    }

    @Test
    fun ausgeblendeteNachrichtVerschwindetAusDemNeuenBereich() {
        val bisher = Verlauf(listOf("0005", "0004", "0003").map(::n), naechster = null)
        val v = bisher.mitNeuesterSeite(seite("0005", "0003"))
        assertEquals(listOf("0005", "0003"), ids(v))
    }

    @Test
    fun leereSeiteGibtLeerenVerlauf() {
        val v = Verlauf(listOf("0001").map(::n)).mitNeuesterSeite(seite())
        assertEquals(Verlauf(), v)
    }

    @Test
    fun aeltereSeiteWirdAngehaengtOhneDoppelte() {
        val bisher = Verlauf(listOf("0005", "0004").map(::n), naechster = "0004")
        val v = bisher.mitAelterer(seite("0004", "0003", "0002", naechster = null))
        assertEquals(listOf("0005", "0004", "0003", "0002"), ids(v))
        assertEquals(null, v.naechster)
    }
}
