package io.github.sorry4nothing.cevios.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRole(@SerialName("stufeId") val ageGroupId: String, @SerialName("typ") val type: String)

@Serializable
data class ProfileAgeGroup(val id: String, val name: String, @SerialName("kuerzel") val abbreviation: String = "")

@Serializable
data class ProfileChild(val id: String, @SerialName("stufeId") val ageGroupId: String)

/** Der Teil des Profils (`GET /api/v1/ich`), den der Chat braucht. Kindernamen liest die App hier nicht. */
@Serializable
data class Profile(
    val id: String,
    val admin: Boolean = false,
    @SerialName("telefon") val phone: String? = null,
    @SerialName("rollen") val roles: List<ProfileRole> = emptyList(),
    @SerialName("stufen") val ageGroups: List<ProfileAgeGroup> = emptyList(),
    @SerialName("kinder") val children: List<ProfileChild> = emptyList(),
) {
    fun isGroupLeader(ageGroupId: String) = roles.any { it.type == "stufenleitung" && it.ageGroupId == ageGroupId }
}

@Serializable
data class HelpResponse(@SerialName("vertrauenspersonen") val trustedPersons: String? = null)

/** `parentUserId` nur für Stufenleitungen; ein Elternteil lässt es weg (und es bleibt dann im JSON weg). */
@Serializable
data class DirectChatRequest(@SerialName("stufeId") val ageGroupId: String, @SerialName("elternUserId") val parentUserId: String? = null)

@Serializable
data class ChatIdResponse(val id: String)

/** Was man mit einer Nachricht tun kann (langes Drücken und Screenreader-Aktionen). */
enum class MessageAction { OPEN_DIRECT_CHAT, HIDE, REPORT }

/**
 * Wann die App eine Aktion anbietet. Die Nachricht trägt dafür kein Flag, deshalb gilt das Profil. Die Entscheidung
 * trifft trotzdem der Server: Eine Aktion, die er ablehnt, zeigt die App als Fehler.
 */
object ChatRules {
    /** Stufenleitungen blenden im Gruppenchat der eigenen Stufe aus, nie im Einzelchat. */
    fun mayHide(chat: Chat?, message: ChatMessage, profile: Profile?) =
        chat != null && profile != null && chat.isGroup && !message.hidden &&
            profile.isGroupLeader(chat.ageGroup.id)

    /** Gemeldet wird, was man in seinem Chat sieht; die eigene Nachricht nur, wenn sie ausgeblendet wurde. */
    fun mayReport(message: ChatMessage) = !message.own || message.hidden

    /** Eine Stufenleitung öffnet den Einzelchat mit einem Elternteil über dessen Nachricht im Gruppenchat. */
    fun mayOpenDirectChat(chat: Chat?, message: ChatMessage, profile: Profile?) =
        chat != null && profile != null && chat.isGroup && !message.own &&
            profile.isGroupLeader(chat.ageGroup.id) && chat.groupLeaders.none { it.id == message.author.id }

    /** Alle Aktionen, die zu einer Nachricht erlaubt sind, in der Reihenfolge des Dialogs. Leer heisst: keine Aktion. */
    fun actions(chat: Chat?, message: ChatMessage, profile: Profile?): List<MessageAction> = buildList {
        if (mayOpenDirectChat(chat, message, profile)) add(MessageAction.OPEN_DIRECT_CHAT)
        if (mayHide(chat, message, profile)) add(MessageAction.HIDE)
        if (mayReport(message)) add(MessageAction.REPORT)
    }

    /**
     * Stufen, in denen ein Elternteil einen Einzelchat starten kann: eigene Kinder in der Stufe, noch kein Einzelchat
     * dort, und in der eigenen Stufenleitung-Stufe nimmt man nur als Stufenleitung teil.
     */
    fun ageGroupsForDirectChat(profile: Profile?, chats: List<Chat>): List<ProfileAgeGroup> {
        if (profile == null) return emptyList()
        return profile.ageGroups.filter { ageGroup ->
            profile.children.any { it.ageGroupId == ageGroup.id } &&
                !profile.isGroupLeader(ageGroup.id) &&
                chats.none { !it.isGroup && it.ageGroup.id == ageGroup.id }
        }
    }
}
