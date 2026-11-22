package io.github.sorry4nothing.cevios.lager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/** Die Knöpfe folgen der Liste `rechte` aus dem Lagerdetail, nicht der Rolle: Eine Einstellung des Lagers ändert sie sofort. */
class RightsFromListTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun only(vararg rights: String, readOnly: Boolean = false, role: CampRole = CampRole.TEAM) =
        CampPermissions(role, readOnly, granted = rights.toSet())

    @Test
    fun noRightsMeansNoButtonWhateverTheRole() {
        val p = CampPermissions(CampRole.LEADERSHIP, readOnly = false)
        assertEquals(TaskRights(false, false, false, false, false, false, true), TaskRights.of(p))
        assertEquals(PlanRights(false, false, true, false, false, false), PlanRights.of(p))
        assertFalse(LiveRights.of(p).let { it.ownAttendance || it.editShopping || it.tickPacking || it.editInfos || it.seesAttendance })
        assertFalse(MeetingRights.of(p).let { it.editMeetings || it.editPinboard || it.readInbox || it.sendInbox || it.interact })
        assertFalse(ArchiveRights.of(p).let { it.editDocuments || it.readAddressBook || it.editSchedule })
    }

    @Test
    fun aTeamWithAnEditRightGetsTheButton() {
        assertTrue(TaskRights.of(only("aufgaben.bearbeiten")).edit)
        assertTrue(PlanRights.of(only("einteilung.bearbeiten")).edit)
        assertTrue(MeetingRights.of(only("pinnwand.bearbeiten")).editPinboard)
        assertTrue(LiveRights.of(only("einkauf.bearbeiten")).editShopping)
        assertTrue(ArchiveRights.of(only("dokumente.bearbeiten")).editDocuments)
    }

    @Test
    fun aLeaderWithoutTheRightLosesTheButtonButKeepsTheStart() {
        val leader = defaultPermissions(CampRole.LEADER, without = setOf("aufgaben.bearbeiten", "pinnwand.bearbeiten"))
        val tasks = TaskRights.of(leader)
        assertFalse(tasks.edit)
        assertTrue("eigene Aufgaben bleiben", tasks.setStatus)
        assertTrue(tasks.seeLeadershipStart)
        assertFalse(MeetingRights.of(leader).editPinboard)
    }

    @Test
    fun eachTaskActionHasItsOwnRight() {
        assertTrue(TaskRights.of(only("aufgaben.eigene")).setStatus)
        assertFalse(TaskRights.of(only("aufgaben.eigene")).edit)
        assertTrue(TaskRights.of(only("aufgaben.eigene")).let { !it.readAll })
        assertTrue(TaskRights.of(only("aufgaben.lesen_alle")).readAll)
        assertTrue(TaskRights.of(only("ressorts.lesen")).readRessorts)
        assertTrue(TaskRights.of(only("ressorts.bearbeiten")).editRessorts)
        assertTrue(TaskRights.of(only("verlauf.lesen")).readHistory)
    }

    @Test
    fun eachPlanActionHasItsOwnRight() {
        assertTrue(PlanRights.of(only("teilnehmer.bearbeiten")).editParticipants)
        assertTrue(PlanRights.of(only("abzaehlen.schreiben")).count)
        assertTrue(PlanRights.of(only("diensttausch.anfragen")).requestSwap)
        assertTrue(PlanRights.of(only("diensttausch.entscheiden")).decideSwap)
        assertFalse(PlanRights.of(only("diensttausch.entscheiden")).requestSwap)
        assertFalse("das Team sieht nur das Eigene", PlanRights.of(only(role = CampRole.TEAM)).seeAll)
        assertTrue(PlanRights.of(only(role = CampRole.READ_ONLY)).seeAll)
    }

    @Test
    fun eachMeetingActionHasItsOwnRight() {
        assertTrue(MeetingRights.of(only("hoecks.lesen_alle")).readAll)
        assertTrue(MeetingRights.of(only("hoecks.bearbeiten")).editMeetings)
        assertTrue(MeetingRights.of(only("protokolle.bearbeiten")).editMinutes)
        assertTrue(MeetingRights.of(only("pinnwand.lesen")).readPinboard)
        assertTrue(MeetingRights.of(only("interaktion.schreiben")).interact)
        assertTrue(MeetingRights.of(only("eingang.lesen")).readInbox)
        assertTrue(MeetingRights.of(only("eingang.antworten")).answerInbox)
        assertTrue(MeetingRights.of(only("eingang.senden")).let { it.sendInbox && it.readOwnInbox })
        assertTrue(MeetingRights.of(only("aufgaben.eigene")).mentionToTask)
    }

    @Test
    fun eachLiveActionHasItsOwnRight() {
        assertTrue(LiveRights.of(only("anwesenheit.eigene")).let { it.ownAttendance && it.seesAttendance })
        assertTrue(LiveRights.of(only("anwesenheit.lesen_alle")).let { it.readAttendance && it.seesAttendance && !it.ownAttendance })
        assertTrue(LiveRights.of(only("anwesenheit.bearbeiten")).decideAttendance)
        assertTrue(LiveRights.of(only("tagesseiten.lesen")).readDayPages)
        assertTrue(LiveRights.of(only("tagesseiten.bearbeiten")).editDayPages)
        assertTrue(LiveRights.of(only("einkauf.lesen")).readShopping)
        assertTrue(LiveRights.of(only("packliste.abhaken")).tickPacking)
        assertTrue(LiveRights.of(only("vorlagen.anwenden")).applyTemplates)
        assertFalse(LiveRights.of(only("packliste.abhaken")).applyTemplates)
        assertTrue(LiveRights.of(only("infos.bearbeiten")).editInfos)
    }

    @Test
    fun eachArchiveActionHasItsOwnRight() {
        assertTrue(ArchiveRights.of(only("adressbuch.lesen")).readAddressBook)
        assertTrue(ArchiveRights.of(only("adressbuch.bearbeiten")).editAddressBook)
        assertTrue(ArchiveRights.of(only("zeitplan.bearbeiten")).editSchedule)
    }

    @Test
    fun aClosedCampKeepsTheReadRightsAndDropsEveryWrite() {
        val all = defaultPermissions(CampRole.LEADERSHIP, readOnly = true)
        assertTrue(TaskRights.of(all).readAll && !TaskRights.of(all).edit)
        assertTrue(LiveRights.of(all).seesAttendance && !LiveRights.of(all).ownAttendance && !LiveRights.of(all).todos)
        assertTrue(MeetingRights.of(all).readInbox && !MeetingRights.of(all).answerInbox && !MeetingRights.of(all).markMention)
    }
}
