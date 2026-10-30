package io.github.sorry4nothing.cevios.chat

import io.github.sorry4nothing.cevios.contacts.ContactList
import io.github.sorry4nothing.cevios.contacts.PhoneRequest
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult

class ChatApi(private val api: ApiClient) {
    suspend fun chats(): ApiResult<ChatList> = api.get("/api/v1/chats")

    /** Eine Seite zu 50 Nachrichten, neueste zuerst. `before` ist der Cursor `next` der vorherigen Seite. */
    suspend fun messages(chatId: String, before: String? = null): ApiResult<MessagePage> =
        api.get("/api/v1/chats/$chatId/nachrichten" + if (before == null) "" else "?vor=$before")

    suspend fun send(chatId: String, text: String): ApiResult<MessageIdResponse> =
        api.post("/api/v1/chats/$chatId/nachrichten", SendMessageRequest(text))

    /** Legt den Einzelchat an oder liefert den bestehenden (idempotent). Nur Stufenleitungen nennen `parentUserId`. */
    suspend fun directChat(ageGroupId: String, parentUserId: String? = null): ApiResult<ChatIdResponse> =
        api.post("/api/v1/chats/direkt", DirectChatRequest(ageGroupId, parentUserId))

    suspend fun hide(messageId: String): ApiResult<Unit> =
        api.postWithoutBody("/api/v1/nachrichten/$messageId/ausblenden")

    /** Setzt den Gelesen-Stand auf die Serverzeit; danach zählt `ungelesen` nur noch spätere Nachrichten. */
    suspend fun markRead(chatId: String): ApiResult<Unit> = api.putWithoutBody("/api/v1/chats/$chatId/gelesen")

    suspend fun help(): ApiResult<HelpResponse> = api.get("/api/v1/hilfe")

    /** Alle Stufenleitungen der Stufe (Kontaktpersonen im Gruppenchat). */
    suspend fun contacts(ageGroupId: String): ApiResult<ContactList> = api.get("/api/v1/stufen/$ageGroupId/kontakte")

    /** Setzt die eigene Telefonnummer (nur Stufenleitungen); ein leerer Text entfernt sie. */
    suspend fun setPhone(phone: String): ApiResult<Unit> = api.patch("/api/v1/ich", PhoneRequest(phone))

    suspend fun profile(): ApiResult<Profile> = api.get("/api/v1/ich")
}
