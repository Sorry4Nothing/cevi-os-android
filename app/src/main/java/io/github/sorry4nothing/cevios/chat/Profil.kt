package io.github.sorry4nothing.cevios.chat

import kotlinx.serialization.Serializable

@Serializable
data class ProfilRolle(val stufeId: String, val typ: String)

@Serializable
data class ProfilStufe(val id: String, val name: String, val kuerzel: String = "")

@Serializable
data class ProfilKind(val id: String, val stufeId: String)

/** Der Teil des Profils (`GET /api/v1/ich`), den der Chat braucht. Kindernamen liest die App hier nicht. */
@Serializable
data class Profil(
    val id: String,
    val rollen: List<ProfilRolle> = emptyList(),
    val stufen: List<ProfilStufe> = emptyList(),
    val kinder: List<ProfilKind> = emptyList(),
) {
    fun istStufenleitung(stufeId: String) = rollen.any { it.typ == "stufenleitung" && it.stufeId == stufeId }
}

@Serializable
data class HilfeAntwort(val vertrauenspersonen: String? = null)

/** `elternUserId` nur für Stufenleitungen; ein Elternteil lässt es weg (und es bleibt dann im JSON weg). */
@Serializable
data class EinzelchatAnfrage(val stufeId: String, val elternUserId: String? = null)

@Serializable
data class ChatKennung(val id: String)

/**
 * Wann die App eine Aktion anbietet. Die Nachricht trägt dafür kein Flag, deshalb gilt das Profil. Die Entscheidung
 * trifft trotzdem der Server: Eine Aktion, die er ablehnt, zeigt die App als Fehler.
 */
object ChatRegeln {
    /** Stufenleitungen blenden im Gruppenchat der eigenen Stufe aus, nie im Einzelchat. */
    fun darfAusblenden(chat: Chat?, nachricht: ChatNachricht, profil: Profil?) =
        chat != null && profil != null && chat.istGruppe && !nachricht.ausgeblendet &&
            profil.istStufenleitung(chat.stufe.id)

    /** Gemeldet wird, was man in seinem Chat sieht; die eigene Nachricht nur, wenn sie ausgeblendet wurde. */
    fun darfMelden(nachricht: ChatNachricht) = !nachricht.eigene || nachricht.ausgeblendet

    /** Eine Stufenleitung öffnet den Einzelchat mit einem Elternteil über dessen Nachricht im Gruppenchat. */
    fun darfEinzelchatOeffnen(chat: Chat?, nachricht: ChatNachricht, profil: Profil?) =
        chat != null && profil != null && chat.istGruppe && !nachricht.eigene &&
            profil.istStufenleitung(chat.stufe.id) && chat.stufenleitung.none { it.id == nachricht.autor.id }

    /**
     * Stufen, in denen ein Elternteil einen Einzelchat starten kann: eigene Kinder in der Stufe, noch kein Einzelchat
     * dort, und in der eigenen Stufenleitung-Stufe nimmt man nur als Stufenleitung teil.
     */
    fun stufenFuerEinzelchat(profil: Profil?, chats: List<Chat>): List<ProfilStufe> {
        if (profil == null) return emptyList()
        return profil.stufen.filter { stufe ->
            profil.kinder.any { it.stufeId == stufe.id } &&
                !profil.istStufenleitung(stufe.id) &&
                chats.none { !it.istGruppe && it.stufe.id == stufe.id }
        }
    }
}
