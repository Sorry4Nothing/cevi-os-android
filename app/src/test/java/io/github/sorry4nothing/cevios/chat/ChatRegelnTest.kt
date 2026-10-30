package io.github.sorry4nothing.cevios.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRegelnTest {
    private val stufe = ChatStufe("s1", "Pfadi", "PF")
    private val leitung = ChatPerson("l1", "Leitung")
    private val gruppe = Chat("c1", "stufe", stufe, null, listOf(leitung))
    private val einzel = Chat("c2", "direkt", stufe, ChatPerson("e1", "Eltern"), listOf(leitung))

    private val sl = Profil("l1", rollen = listOf(ProfilRolle("s1", "stufenleitung")))
    private val eltern = Profil(
        "e1",
        stufen = listOf(ProfilStufe("s1", "Pfadi"), ProfilStufe("s2", "Wölfe")),
        kinder = listOf(ProfilKind("k1", "s1")),
    )

    private fun nachricht(autorId: String = "e2", eigene: Boolean = false, ausgeblendet: Boolean = false) = ChatNachricht(
        id = "m1", text = if (ausgeblendet) null else "t", erstelltAm = "2027-01-01T10:00:00Z",
        autor = ChatPerson(autorId, "Name"), ausgeblendet = ausgeblendet, eigene = eigene,
    )

    @Test
    fun ausblendenNurStufenleitungImGruppenchatDerEigenenStufe() {
        assertTrue(ChatRegeln.darfAusblenden(gruppe, nachricht(), sl))
        assertFalse("nie im Einzelchat", ChatRegeln.darfAusblenden(einzel, nachricht(), sl))
        assertFalse("Eltern nicht", ChatRegeln.darfAusblenden(gruppe, nachricht(), eltern))
        assertFalse("schon ausgeblendet", ChatRegeln.darfAusblenden(gruppe, nachricht(ausgeblendet = true), sl))
        assertFalse("SL einer anderen Stufe", ChatRegeln.darfAusblenden(gruppe, nachricht(), Profil("l2", rollen = listOf(ProfilRolle("s9", "stufenleitung")))))
        assertFalse("ohne Profil", ChatRegeln.darfAusblenden(gruppe, nachricht(), null))
    }

    @Test
    fun meldenAuszerDieEigeneSichtbareNachricht() {
        assertTrue(ChatRegeln.darfMelden(nachricht()))
        assertFalse(ChatRegeln.darfMelden(nachricht(eigene = true)))
        assertTrue(ChatRegeln.darfMelden(nachricht(eigene = true, ausgeblendet = true)))
    }

    @Test
    fun stufenleitungOeffnetEinzelchatUeberDieNachrichtEinesElternteils() {
        assertTrue(ChatRegeln.darfEinzelchatOeffnen(gruppe, nachricht(), sl))
        assertFalse("eigene Nachricht", ChatRegeln.darfEinzelchatOeffnen(gruppe, nachricht(eigene = true), sl))
        assertFalse("Autor ist selbst Stufenleitung", ChatRegeln.darfEinzelchatOeffnen(gruppe, nachricht(autorId = "l1"), sl))
        assertFalse("nicht im Einzelchat", ChatRegeln.darfEinzelchatOeffnen(einzel, nachricht(), sl))
        assertFalse("Eltern nicht", ChatRegeln.darfEinzelchatOeffnen(gruppe, nachricht(), eltern))
    }

    @Test
    fun elternStartenEinzelchatNurInStufenMitEigenemKindUndOhneBestehendenChat() {
        assertEquals(listOf("s1"), ChatRegeln.stufenFuerEinzelchat(eltern, listOf(gruppe)).map { it.id })
        assertEquals("schon ein Einzelchat", emptyList<String>(), ChatRegeln.stufenFuerEinzelchat(eltern, listOf(gruppe, einzel)).map { it.id })
        assertTrue("ohne Profil", ChatRegeln.stufenFuerEinzelchat(null, listOf(gruppe)).isEmpty())
    }

    @Test
    fun werInDerStufeLeitungIstStartetKeinenEinzelchatAlsElternteil() {
        val beides = eltern.copy(rollen = listOf(ProfilRolle("s1", "stufenleitung")))
        assertTrue(ChatRegeln.stufenFuerEinzelchat(beides, listOf(gruppe)).isEmpty())
    }
}
