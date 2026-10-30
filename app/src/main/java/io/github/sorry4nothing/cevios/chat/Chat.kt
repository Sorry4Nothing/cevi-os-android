package io.github.sorry4nothing.cevios.chat

import kotlinx.serialization.Serializable

@Serializable
data class ChatPerson(val id: String, val name: String)

@Serializable
data class ChatStufe(val id: String, val name: String, val kuerzel: String)

/** Ein Chat der Person: `typ` ist «stufe» (Gruppenchat) oder «direkt» (Einzelchat Eltern und Stufenleitung). */
@Serializable
data class Chat(
    val id: String,
    val typ: String,
    val stufe: ChatStufe,
    val eltern: ChatPerson? = null,
    val stufenleitung: List<ChatPerson> = emptyList(),
) {
    val istGruppe get() = typ == "stufe"
}

@Serializable
data class ChatListe(val chats: List<Chat>)

@Serializable
data class ProgrammBezug(val id: String, val titel: String)

/** `text` ist `null`, wenn die Nachricht ausgeblendet ist (nur die Autorin oder der Autor sieht sie dann noch). */
@Serializable
data class ChatNachricht(
    val id: String,
    val text: String? = null,
    val erstelltAm: String,
    val autor: ChatPerson,
    val programm: ProgrammBezug? = null,
    val ausgeblendet: Boolean = false,
    val eigene: Boolean = false,
)

@Serializable
data class NachrichtenSeite(val nachrichten: List<ChatNachricht>, val naechster: String? = null)

@Serializable
data class NachrichtAnfrage(val text: String)

@Serializable
data class NachrichtKennung(val id: String)

/**
 * Die geladenen Nachrichten eines Chats, neueste zuerst. Die IDs sind UUIDv7 in Kleinbuchstaben und
 * ordnen sich als Text nach der Zeit.
 */
data class Verlauf(val nachrichten: List<ChatNachricht> = emptyList(), val naechster: String? = null) {
    /**
     * Arbeitet die neueste Seite ein, ohne bereits geladene ältere Nachrichten zu verlieren.
     * Kamen seit dem letzten Laden mehr neue Nachrichten, als eine Seite fasst, bleibt eine Lücke: Dann gilt nur
     * die neue Seite, die älteren holt «Ältere laden» nach.
     */
    fun mitNeuesterSeite(seite: NachrichtenSeite): Verlauf {
        val aelteste = seite.nachrichten.lastOrNull()?.id ?: return Verlauf()
        val neuesteBekannte = nachrichten.firstOrNull()?.id
        if (neuesteBekannte == null || neuesteBekannte < aelteste) return Verlauf(seite.nachrichten, seite.naechster)
        val aeltere = nachrichten.filter { it.id < aelteste }
        return Verlauf(seite.nachrichten + aeltere, if (aeltere.isEmpty()) seite.naechster else naechster)
    }

    /** Hängt eine ältere Seite an. */
    fun mitAelterer(seite: NachrichtenSeite): Verlauf {
        val aelteste = nachrichten.lastOrNull()?.id
        val neu = if (aelteste == null) seite.nachrichten else seite.nachrichten.filter { it.id < aelteste }
        return Verlauf(nachrichten + neu, seite.naechster)
    }
}
