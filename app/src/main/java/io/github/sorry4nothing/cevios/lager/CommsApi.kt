package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Der Typ des Ziels einer Reaktion; die API kennt heute nur Beiträge der Pinnwand. */
private const val REACTION_TARGET = "pinnwand_beitrag"

/** Die Routen der Kommunikation: Pinnwand, Umfragen, Reaktionen, Eingang und Benachrichtigungen. Alle brauchen Netz. */
class CommsApi(private val api: ApiClient) {
    // ---- Pinnwand ----

    /** Die Beiträge, die die Person sehen darf; mit `all` auch Vergangenes und Künftiges (nur wer die Pinnwand bearbeitet). */
    suspend fun posts(campId: String, all: Boolean): ApiResult<List<Post>> {
        val result = mutableListOf<Post>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/pinnwand?limit=$PAGE_SIZE" + (if (all) "&status=alle" else "") + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<PostPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    result += page.value.posts
                    after = page.value.next ?: return ApiResult.Ok(result)
                }
            }
        }
        return ApiResult.Ok(result)
    }

    suspend fun createPost(campId: String, body: JsonObject): ApiResult<Post> = api.post("/api/v1/lager/$campId/pinnwand", body)

    suspend fun changePost(campId: String, postId: String, body: JsonObject): ApiResult<Post> =
        api.patchJson("/api/v1/lager/$campId/pinnwand/$postId", body)

    suspend fun deletePost(campId: String, post: Post): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/pinnwand/${post.id}?version=${post.version}")

    suspend fun addFile(campId: String, name: String, link: String): ApiResult<CampFile> =
        api.post("/api/v1/lager/$campId/dateien", buildJsonObject { put("anzeigename", name); put("link", link) })

    suspend fun createPoll(campId: String, postId: String, options: List<String>, multiple: Boolean): ApiResult<Poll> =
        api.post(
            "/api/v1/lager/$campId/pinnwand/$postId/umfrage",
            buildJsonObject {
                put("optionen", JsonArray(options.map(::JsonPrimitive)))
                put("mehrfach", multiple)
            },
        )

    suspend fun changePoll(campId: String, postId: String, body: JsonObject): ApiResult<Poll> =
        api.patchJson("/api/v1/lager/$campId/pinnwand/$postId/umfrage", body)

    /** Setzt die eigenen Stimmen auf genau diese Antworten; der Server gibt die Umfrage mit den neuen Zahlen zurück. */
    suspend fun vote(campId: String, postId: String, optionIds: List<String>): ApiResult<Poll> =
        api.putJson("/api/v1/lager/$campId/pinnwand/$postId/umfrage/stimme", buildJsonObject { put("optionIds", JsonArray(optionIds.map(::JsonPrimitive))) })

    suspend fun withdrawVotes(campId: String, postId: String): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/pinnwand/$postId/umfrage/stimme")

    suspend fun react(campId: String, postId: String, reaction: Reaction): ApiResult<ReactionState> =
        api.putJson("/api/v1/lager/$campId/reaktionen/$REACTION_TARGET/$postId", buildJsonObject { put("emoji", reaction.wire) })

    suspend fun unreact(campId: String, postId: String): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/reaktionen/$REACTION_TARGET/$postId")

    // ---- Eingang ----

    /** Der Eingang der Lagerleitung (nur wer ihn lesen darf). */
    suspend fun inbox(campId: String): ApiResult<List<InboxEntry>> {
        val all = mutableListOf<InboxEntry>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/eingang?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<InboxPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.entries
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun ownInbox(campId: String): ApiResult<List<OwnInboxEntry>> {
        val all = mutableListOf<OwnInboxEntry>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/eingang/eigene?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<OwnInboxPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.entries
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    suspend fun sendInbox(campId: String, kind: InboxKind, text: String, anonymous: Boolean): ApiResult<InboxSent> =
        api.post("/api/v1/lager/$campId/eingang", InboxSendBody(kind.wire, text, anonymous))

    suspend fun codeStatus(campId: String, code: String): ApiResult<CodeView> = api.post("/api/v1/lager/$campId/eingang/status", CodeBody(code))

    suspend fun answerInbox(campId: String, entry: InboxEntry, body: JsonObject): ApiResult<InboxEntry> =
        api.patchJson("/api/v1/lager/$campId/eingang/${entry.id}", JsonObject(mapOf("version" to JsonPrimitive(entry.version)) + body))

    suspend fun inboxToAgenda(campId: String, entry: InboxEntry, title: String, meetingId: String?): ApiResult<InboxEntry> =
        api.post(
            "/api/v1/lager/$campId/eingang/${entry.id}/traktandum",
            buildJsonObject {
                put("version", entry.version)
                put("titel", title)
                meetingId?.let { put("hoeckId", it) }
            },
        )

    // ---- Benachrichtigungen ----

    suspend fun notifications(campId: String): ApiResult<List<NotificationFull>> {
        val all = mutableListOf<NotificationFull>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val path = "/api/v1/lager/$campId/benachrichtigungen?limit=$PAGE_SIZE" + (after?.let { "&nach=$it" } ?: "")
            when (val page = api.get<NotificationsPage>(path)) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.entries
                    after = page.value.next ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    /** Markiert die genannten Benachrichtigungen (höchstens 100) als gelesen; ohne `ids` alle. */
    suspend fun markRead(campId: String, ids: List<String>?): ApiResult<Unit> =
        api.postAction("/api/v1/lager/$campId/benachrichtigungen/gelesen", MarkReadBody(ids))

    suspend fun settings(): ApiResult<NotificationSettings> = api.get("/api/v1/ich/lager-benachrichtigungen")

    suspend fun setSetting(kind: String, active: Boolean): ApiResult<Unit> =
        api.put("/api/v1/ich/lager-benachrichtigungen", NotificationSettings(listOf(NotificationSetting(kind, active))))

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}
