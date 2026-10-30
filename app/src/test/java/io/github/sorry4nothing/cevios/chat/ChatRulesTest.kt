package io.github.sorry4nothing.cevios.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRulesTest {
    private val ageGroup = ChatAgeGroup("s1", "Pfadi", "PF")
    private val leader = ChatPerson("l1", "Leitung")
    private val group = Chat("c1", "stufe", ageGroup, null, listOf(leader))
    private val direct = Chat("c2", "direkt", ageGroup, ChatPerson("e1", "Eltern"), listOf(leader))

    private val groupLeader = Profile("l1", roles = listOf(ProfileRole("s1", "stufenleitung")))
    private val parent = Profile(
        "e1",
        ageGroups = listOf(ProfileAgeGroup("s1", "Pfadi"), ProfileAgeGroup("s2", "Wölfe")),
        children = listOf(ProfileChild("k1", "s1")),
    )

    private fun message(authorId: String = "e2", own: Boolean = false, hidden: Boolean = false) = ChatMessage(
        id = "m1", text = if (hidden) null else "t", createdAt = "2027-01-01T10:00:00Z",
        author = ChatPerson(authorId, "Name"), hidden = hidden, own = own,
    )

    @Test
    fun hideOnlyGroupLeaderInGroupChatOfOwnAgeGroup() {
        assertTrue(ChatRules.mayHide(group, message(), groupLeader))
        assertFalse("nie im Einzelchat", ChatRules.mayHide(direct, message(), groupLeader))
        assertFalse("Eltern nicht", ChatRules.mayHide(group, message(), parent))
        assertFalse("schon ausgeblendet", ChatRules.mayHide(group, message(hidden = true), groupLeader))
        assertFalse("SL einer anderen Stufe", ChatRules.mayHide(group, message(), Profile("l2", roles = listOf(ProfileRole("s9", "stufenleitung")))))
        assertFalse("ohne Profil", ChatRules.mayHide(group, message(), null))
    }

    @Test
    fun reportExceptOwnVisibleMessage() {
        assertTrue(ChatRules.mayReport(message()))
        assertFalse(ChatRules.mayReport(message(own = true)))
        assertTrue(ChatRules.mayReport(message(own = true, hidden = true)))
    }

    @Test
    fun groupLeaderOpensDirectChatViaParentMessage() {
        assertTrue(ChatRules.mayOpenDirectChat(group, message(), groupLeader))
        assertFalse("eigene Nachricht", ChatRules.mayOpenDirectChat(group, message(own = true), groupLeader))
        assertFalse("Autor ist selbst Stufenleitung", ChatRules.mayOpenDirectChat(group, message(authorId = "l1"), groupLeader))
        assertFalse("nicht im Einzelchat", ChatRules.mayOpenDirectChat(direct, message(), groupLeader))
        assertFalse("Eltern nicht", ChatRules.mayOpenDirectChat(group, message(), parent))
    }

    @Test
    fun actionsListOnlyWhatIsAllowed() {
        assertEquals(
            listOf(MessageAction.OPEN_DIRECT_CHAT, MessageAction.HIDE, MessageAction.REPORT),
            ChatRules.actions(group, message(), groupLeader),
        )
        assertEquals(listOf(MessageAction.REPORT), ChatRules.actions(group, message(), parent))
        assertEquals("eigene sichtbare Nachricht: keine Aktion", emptyList<MessageAction>(), ChatRules.actions(group, message(own = true), parent))
        assertEquals(emptyList<MessageAction>(), ChatRules.actions(null, message(own = true), null))
    }

    @Test
    fun parentStartsDirectChatOnlyInAgeGroupsWithOwnChildAndNoExistingChat() {
        assertEquals(listOf("s1"), ChatRules.ageGroupsForDirectChat(parent, listOf(group)).map { it.id })
        assertEquals("schon ein Einzelchat", emptyList<String>(), ChatRules.ageGroupsForDirectChat(parent, listOf(group, direct)).map { it.id })
        assertTrue("ohne Profil", ChatRules.ageGroupsForDirectChat(null, listOf(group)).isEmpty())
    }

    @Test
    fun whoLeadsAgeGroupStartsNoDirectChatAsParent() {
        val both = parent.copy(roles = listOf(ProfileRole("s1", "stufenleitung")))
        assertTrue(ChatRules.ageGroupsForDirectChat(both, listOf(group)).isEmpty())
    }
}
