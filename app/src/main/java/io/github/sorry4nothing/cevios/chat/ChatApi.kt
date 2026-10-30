package io.github.sorry4nothing.cevios.chat

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

    suspend fun report(messageId: String): ApiResult<Unit> =
        api.postWithoutBody("/api/v1/nachrichten/$messageId/melden")

    suspend fun help(): ApiResult<HelpResponse> = api.get("/api/v1/hilfe")

    suspend fun profile(): ApiResult<Profile> = api.get("/api/v1/ich")
}
