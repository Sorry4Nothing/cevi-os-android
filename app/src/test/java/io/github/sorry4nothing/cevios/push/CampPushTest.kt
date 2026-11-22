package io.github.sorry4nothing.cevios.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lager-Pushes: Ziel aus `typ` und `id`, Kanäle, Unterdrücken beim offenen Objekt. */
class CampPushTest {
    private val id = "0190a1b2-3333-7e5f-8a9b-0c1d2e3f4a5b"

    @Test
    fun everyCampTypeBecomesATarget() {
        listOf("lager_aufgabe", "lager_erwaehnung", "lager_pinnwand", "lager_eingang", "lager_einteilung", "lager_anwesenheit").forEach {
            assertEquals(PushTarget.Camp(it, id), PushTarget.from(it, id))
        }
    }

    @Test
    fun chatAndProgramStayAsTheyWere() {
        assertEquals(PushTarget.Chat(id), PushTarget.from("chat", id))
        assertEquals(PushTarget.Program(id), PushTarget.from("programm", id))
    }

    @Test
    fun unknownTypesAndBrokenIdsLeadNowhere() {
        assertNull(PushTarget.from("lager_unbekannt", id))
        assertNull(PushTarget.from("lager_aufgabe", "../../admin"))
        assertNull(PushTarget.from("lager_aufgabe", null))
    }

    @Test
    fun campChannelsComeFromTheServer() {
        assertEquals(Notifications.CAMP_TASKS_CHANNEL, Notifications.channelOf("lager_aufgaben"))
        assertEquals(Notifications.CAMP_MESSAGES_CHANNEL, Notifications.channelOf("lager_mitteilungen"))
        assertEquals(Notifications.CAMP_PLANNING_CHANNEL, Notifications.channelOf("lager_einteilung"))
    }

    @Test
    fun aPushToTheOpenObjectShowsNothing() {
        assertTrue(OpenCampObject.hides("lager_aufgabe", id, open = id))
        assertFalse(OpenCampObject.hides("lager_aufgabe", id, open = "andere"))
        assertFalse(OpenCampObject.hides("lager_aufgabe", id, open = null))
        assertFalse(OpenCampObject.hides("chat", id, open = id))
        assertFalse(OpenCampObject.hides("lager_aufgabe", null, open = id))
    }
}
