package io.github.sorry4nothing.cevios.chat

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult

class ChatApi(private val api: ApiClient) {
    suspend fun chats(): ApiResult<ChatListe> = api.get("/api/v1/chats")

    /** Eine Seite zu 50 Nachrichten, neueste zuerst. `vor` ist der Cursor `naechster` der vorherigen Seite. */
    suspend fun nachrichten(chatId: String, vor: String? = null): ApiResult<NachrichtenSeite> =
        api.get("/api/v1/chats/$chatId/nachrichten" + if (vor == null) "" else "?vor=$vor")

    suspend fun senden(chatId: String, text: String): ApiResult<NachrichtKennung> =
        api.post("/api/v1/chats/$chatId/nachrichten", NachrichtAnfrage(text))

    /** Legt den Einzelchat an oder liefert den bestehenden (idempotent). Nur Stufenleitungen nennen `elternUserId`. */
    suspend fun einzelchat(stufeId: String, elternUserId: String? = null): ApiResult<ChatKennung> =
        api.post("/api/v1/chats/direkt", EinzelchatAnfrage(stufeId, elternUserId))

    suspend fun ausblenden(nachrichtId: String): ApiResult<Unit> =
        api.postOhneBody("/api/v1/nachrichten/$nachrichtId/ausblenden")

    suspend fun melden(nachrichtId: String): ApiResult<Unit> =
        api.postOhneBody("/api/v1/nachrichten/$nachrichtId/melden")

    suspend fun hilfe(): ApiResult<HilfeAntwort> = api.get("/api/v1/hilfe")

    suspend fun profil(): ApiResult<Profil> = api.get("/api/v1/ich")
}
