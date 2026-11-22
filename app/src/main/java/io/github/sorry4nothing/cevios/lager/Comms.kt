package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Pinnwand ----

/** Die fünf festen Reaktionen; der Server kennt nur diese Schlüssel, nie ein Emoji. */
enum class Reaction(val wire: String) {
    THUMBS_UP("daumen"),
    HEART("herz"),
    LAUGH("lachen"),
    SURPRISE("staunen"),
    THANKS("danke"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire }
    }
}

@Serializable
data class ReactionState(
    @SerialName("zaehler") val counts: Map<String, Int> = emptyMap(),
    @SerialName("eigene") val own: String? = null,
) {
    fun count(reaction: Reaction): Int = counts[reaction.wire] ?: 0

    val total: Int get() = counts.values.sum()
}

@Serializable
data class PollOption(
    val id: String,
    val text: String,
    @SerialName("stimmen") val votes: Int = 0,
    @SerialName("eigene") val own: Boolean = false,
    @SerialName("personen") val people: List<RessortPerson> = emptyList(),
)

@Serializable
data class Poll(
    val id: String,
    val version: Long,
    @SerialName("mehrfach") val multiple: Boolean = false,
    @SerialName("schliesstAm") val closesAt: String? = null,
    @SerialName("geschlossen") val closed: Boolean = false,
    @SerialName("namenFuerTeam") val namesForTeam: Boolean = false,
    @SerialName("namenSichtbar") val namesVisible: Boolean = false,
    @SerialName("teilnehmende") val participants: Int = 0,
    @SerialName("optionen") val options: List<PollOption> = emptyList(),
)

/** Empfänger eines Beitrags. Personen, Ressorts und Gruppen nennt der Server nur, wer die Pinnwand bearbeitet (sonst leer). */
@Serializable
data class PostRecipients(
    @SerialName("anAlle") val everyone: Boolean = true,
    @SerialName("rollen") val roles: List<String> = emptyList(),
    @SerialName("personen") val people: List<String> = emptyList(),
    @SerialName("ressortIds") val ressortIds: List<String> = emptyList(),
    @SerialName("gruppeIds") val groupIds: List<String> = emptyList(),
)

/** Wer den Beitrag geöffnet hat: die Zahl für alle, die Namen (`people`) nur mit dem Recht, die Pinnwand zu bearbeiten. */
@Serializable
data class PostSeen(
    @SerialName("anzahl") val count: Int = 0,
    @SerialName("eigene") val own: Boolean = false,
    @SerialName("personen") val people: List<RessortPerson>? = null,
)

@Serializable
data class Post(
    val id: String,
    val version: Long,
    @SerialName("titel") val title: String,
    @SerialName("text") val text: String = "",
    @SerialName("wichtig") val important: Boolean = false,
    @SerialName("angeheftet") val pinned: Boolean = false,
    @SerialName("sichtbarAb") val visibleFrom: String? = null,
    @SerialName("sichtbarBis") val visibleUntil: String? = null,
    @SerialName("vonPersonId") val authorId: String? = null,
    @SerialName("erstelltAm") val createdAt: String? = null,
    @SerialName("von") val author: RessortPerson? = null,
    @SerialName("empfaenger") val recipients: PostRecipients = PostRecipients(),
    @SerialName("dateien") val files: List<CampFile> = emptyList(),
    @SerialName("umfrage") val poll: Poll? = null,
    @SerialName("reaktionen") val reactions: ReactionState = ReactionState(),
    @SerialName("gesehen") val seen: PostSeen = PostSeen(),
)

@Serializable
data class PostPage(@SerialName("beitraege") val posts: List<Post>, @SerialName("naechster") val next: String? = null)

// ---- Eingang ----

enum class InboxKind(val wire: String) {
    IDEA("idee"),
    WISH("wunsch"),
    QUESTION("frage"),
    FEEDBACK("feedback"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: FEEDBACK
    }
}

enum class InboxStatus(val wire: String) {
    NEW("neu"),
    IN_PROGRESS("in_arbeit"),
    DONE("erledigt"),
    ;

    companion object {
        fun from(wire: String?) = entries.firstOrNull { it.wire == wire } ?: NEW
    }
}

/** Eine Meldung im Eingang der Lagerleitung. Bei einer anonymen Meldung ist `from` `null`: Es gibt nirgends einen Namen. */
@Serializable
data class InboxEntry(
    val id: String,
    val version: Long,
    @SerialName("art") val kindWire: String,
    val text: String,
    @SerialName("anonym") val anonymous: Boolean = false,
    @SerialName("status") val statusWire: String = "neu",
    @SerialName("antwort") val reply: String? = null,
    @SerialName("beantwortetTag") val repliedOn: String? = null,
    @SerialName("erstelltTag") val createdOn: String,
    @SerialName("ressortId") val ressortId: String? = null,
    @SerialName("traktandumId") val agendaItemId: String? = null,
    @SerialName("von") val from: RessortPerson? = null,
) {
    val kind: InboxKind get() = InboxKind.from(kindWire)
    val status: InboxStatus get() = InboxStatus.from(statusWire)
}

@Serializable
data class InboxPage(@SerialName("eintraege") val entries: List<InboxEntry>, @SerialName("naechster") val next: String? = null)

/** Eine eigene, nicht anonyme Meldung mit Status und Antwort. */
@Serializable
data class OwnInboxEntry(
    val id: String,
    @SerialName("art") val kindWire: String,
    val text: String,
    @SerialName("status") val statusWire: String = "neu",
    @SerialName("antwort") val reply: String? = null,
    @SerialName("beantwortetTag") val repliedOn: String? = null,
    @SerialName("erstelltTag") val createdOn: String,
) {
    val kind: InboxKind get() = InboxKind.from(kindWire)
    val status: InboxStatus get() = InboxStatus.from(statusWire)
}

@Serializable
data class OwnInboxPage(@SerialName("eintraege") val entries: List<OwnInboxEntry>, @SerialName("naechster") val next: String? = null)

@Serializable
data class InboxSendBody(@SerialName("art") val kind: String, val text: String, @SerialName("anonym") val anonymous: Boolean)

/** Antwort auf das Senden. Bei einer anonymen Meldung steht der Rückfragecode genau einmal da und nirgends sonst. */
@Serializable
data class InboxSent(
    @SerialName("anonym") val anonymous: Boolean,
    val id: String? = null,
    @SerialName("rueckfragecode") val code: String? = null,
    @SerialName("art") val kindWire: String,
    @SerialName("status") val statusWire: String = "neu",
    @SerialName("antwort") val reply: String? = null,
)

@Serializable
data class CodeBody(val code: String)

/** Status und Antwort einer anonymen Meldung, gelesen mit dem Rückfragecode. */
@Serializable
data class CodeView(
    @SerialName("art") val kindWire: String,
    @SerialName("status") val statusWire: String = "neu",
    @SerialName("antwort") val reply: String? = null,
    @SerialName("beantwortetTag") val repliedOn: String? = null,
    @SerialName("erstelltTag") val createdOn: String,
) {
    val kind: InboxKind get() = InboxKind.from(kindWire)
    val status: InboxStatus get() = InboxStatus.from(statusWire)
}

// ---- Benachrichtigungen ----

/** Die Arten der Benachrichtigungen in der Reihenfolge der Einstellungen. */
val NOTIFICATION_KINDS = listOf(
    "aufgabe_zugeteilt", "aufgabe_geaendert", "erwaehnung", "frist", "pinnwand_wichtig", "eingang_antwort",
    "diensttausch_anfrage", "diensttausch_entscheid", "anwesenheit_anfrage", "adressbuch_aufraeumen",
)

@Serializable
data class NotificationFull(
    val id: String,
    @SerialName("art") val kind: String,
    @SerialName("objektTyp") val objectType: String,
    @SerialName("objektId") val objectId: String,
    @SerialName("gelesen") val read: Boolean = false,
    @SerialName("erstelltAm") val createdAt: String,
)

@Serializable
data class NotificationsPage(@SerialName("eintraege") val entries: List<NotificationFull>, @SerialName("naechster") val next: String? = null)

@Serializable
data class MarkReadBody(@SerialName("ids") val ids: List<String>? = null)

@Serializable
data class NotificationSetting(@SerialName("art") val kind: String, @SerialName("aktiv") val active: Boolean)

@Serializable
data class NotificationSettings(@SerialName("arten") val kinds: List<NotificationSetting>)
