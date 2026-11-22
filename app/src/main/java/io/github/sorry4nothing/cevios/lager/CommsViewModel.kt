package io.github.sorry4nothing.cevios.lager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.Background
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Was die Person im Editor der Pinnwand eingegeben hat; die ViewModel-Funktion macht daraus die Anfragen. */
data class PostDraft(
    val title: String = "",
    val text: String = "",
    val important: Boolean = false,
    val pinned: Boolean = false,
    /** Empfänger: leer (auch ohne Ressorts, Gruppen und Personen) heisst «alle». Rollen sind `leitung`, `team`, `lesen`. */
    val roles: Set<String> = emptySet(),
    val ressortIds: Set<String> = emptySet(),
    val groupIds: Set<String> = emptySet(),
    /** Einzelne Personen als Empfänger setzt die App nicht; sie bleiben beim Ändern erhalten. */
    val peopleIds: Set<String> = emptySet(),
    val visibleFrom: String? = null,
    val visibleUntil: String? = null,
    val pollOptions: List<String> = emptyList(),
    val pollMultiple: Boolean = false,
    /** Dateien als Verknüpfung: Anzeigename und Link (https). */
    val files: List<Pair<String, String>> = emptyList(),
)

/** Wie die Empfänger eines Beitrags im Editor heissen: Ressorts und Programmgruppen des Lagers (die Oberfläche setzt sie). */
data class RecipientChoices(val ressorts: List<Pair<String, String>> = emptyList(), val groups: List<Pair<String, String>> = emptyList()) {
    fun ressortName(id: String): String? = ressorts.firstOrNull { it.first == id }?.second
    fun groupName(id: String): String? = groups.firstOrNull { it.first == id }?.second
}

/** Ob sich die Empfänger gegenüber dem gespeicherten Beitrag geändert haben. «Für alle» hat keine Rollen, Ressorts, Gruppen oder Personen. */
fun recipientsChanged(old: PostRecipients, draft: PostDraft): Boolean {
    if (old.everyone) return draft.roles.isNotEmpty() || draft.ressortIds.isNotEmpty() || draft.groupIds.isNotEmpty() || draft.peopleIds.isNotEmpty()
    return draft.roles != old.roles.toSet() || draft.ressortIds != old.ressortIds.toSet() || draft.groupIds != old.groupIds.toSet() || draft.peopleIds != old.people.toSet()
}

/** Der Empfänger-Teil der Anfrage; `null`, wenn der Beitrag für alle gilt. */
fun recipientsBody(draft: PostDraft): JsonObject? {
    if (draft.roles.isEmpty() && draft.ressortIds.isEmpty() && draft.groupIds.isEmpty() && draft.peopleIds.isEmpty()) return null
    return buildJsonObject {
        put("rollen", JsonArray(draft.roles.map(::JsonPrimitive)))
        put("personen", JsonArray(draft.peopleIds.map(::JsonPrimitive)))
        put("ressortIds", JsonArray(draft.ressortIds.map(::JsonPrimitive)))
        put("gruppeIds", JsonArray(draft.groupIds.map(::JsonPrimitive)))
    }
}

/** Hinweis nach dem Senden einer Meldung: bei einer anonymen steht der Rückfragecode hier, genau einmal. */
class SentInbox(val anonymous: Boolean, val code: String?)

/**
 * Kommunikation eines Lagers: Pinnwand mit Umfragen und Reaktionen, Eingang (senden, lesen, beantworten) und Benachrichtigungen mit
 * Einstellungen. Der Stand kommt vom Server (nur im Arbeitsspeicher); jede Änderung geht direkt hinaus.
 */
class CommsViewModel(
    private val api: CommsApi,
    private val camp: CampViewModel,
    private val texts: CommsTexts,
) : ViewModel() {
    private val openCamp = MutableStateFlow<String?>(null)
    private val campId: String? get() = openCamp.value

    val writer = DirectWriter(viewModelScope) { texts.fieldLabels() }

    /** Die Rechte der Rolle im offenen Lager; die Oberfläche setzt sie, sobald das Lager geladen ist. */
    var rights: MeetingRights = MeetingRights.of(NO_PERMISSIONS)

    private val _posts = MutableStateFlow<ApiResult<List<Post>>?>(null)
    val posts: StateFlow<ApiResult<List<Post>>?> = _posts.asStateFlow()

    private val _choices = MutableStateFlow(RecipientChoices())

    /** Ressorts und Gruppen für die Empfängerwahl und für die Namen auf den Karten. */
    val choices: StateFlow<RecipientChoices> = _choices.asStateFlow()

    fun setChoices(choices: RecipientChoices) {
        _choices.value = choices
    }

    /** Beiträge, die ich schon gemeldet habe oder bei denen die Meldung nicht geht (nicht lesbar): Sie werden nicht nochmals gemeldet. */
    private val reportedSeen = mutableSetOf<String>()

    private val _inbox = MutableStateFlow<ApiResult<List<InboxEntry>>?>(null)
    val inbox: StateFlow<ApiResult<List<InboxEntry>>?> = _inbox.asStateFlow()

    private val _own = MutableStateFlow<ApiResult<List<OwnInboxEntry>>?>(null)
    val own: StateFlow<ApiResult<List<OwnInboxEntry>>?> = _own.asStateFlow()

    private val _sent = MutableStateFlow<SentInbox?>(null)
    val sent: StateFlow<SentInbox?> = _sent.asStateFlow()

    private val _codeView = MutableStateFlow<ApiResult<CodeView>?>(null)
    val codeView: StateFlow<ApiResult<CodeView>?> = _codeView.asStateFlow()

    private val _notifications = MutableStateFlow<ApiResult<List<NotificationFull>>?>(null)
    val notifications: StateFlow<ApiResult<List<NotificationFull>>?> = _notifications.asStateFlow()

    private val _settings = MutableStateFlow<ApiResult<Map<String, Boolean>>?>(null)
    val settings: StateFlow<ApiResult<Map<String, Boolean>>?> = _settings.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** Eine Bestätigung, zum Beispiel «Als Traktandum hinzugefügt»; bleibt, bis die Person sie schliesst. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch { camp.changes.collect(::onChange) }
    }

    fun open(id: String) {
        if (openCamp.value == id) return
        reset()
        openCamp.value = id
    }

    fun close() {
        reset()
        openCamp.value = null
    }

    private fun reset() {
        _posts.value = null
        _choices.value = RecipientChoices()
        reportedSeen.clear()
        _inbox.value = null
        _own.value = null
        _sent.value = null
        _codeView.value = null
        codeLookups++
        _notifications.value = null
        _notice.value = null
        writer.clear()
    }

    private fun onChange(change: CampChange) {
        if (campId == null) return
        if (change.reload || CampChange.QUEUE_SENT in change.types || change.types.any { it.startsWith("pinnwand") || it.startsWith("umfrage") }) {
            if (_posts.value != null) loadPosts(silent = true)
        }
    }

    fun showNotice(text: String) {
        _notice.value = text
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ---- Pinnwand ----

    fun loadPosts(silent: Boolean = false) {
        val id = campId ?: return
        val all = rights.editPinboard
        viewModelScope.launch(Background()) {
            if (!silent) _posts.value = null
            val result = api.posts(id, all)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _posts.value == null) _posts.value = result
        }
    }

    /**
     * Meldet die sichtbaren Beiträge, die ich noch nicht gesehen habe, einmal je Beitrag (gebündelt, höchstens 50). Ein Beitrag
     * ausserhalb des Zeitfensters oder nicht für mich bestimmt (die Bearbeitenden sehen auch diese) lässt die Sammelmeldung mit 404
     * scheitern: Dann geht jeder einzeln, und was nicht geht, bleibt still. Ein Netzfehler versucht es beim nächsten Laden wieder.
     */
    fun reportSeen(posts: List<Post>, now: java.time.Instant = java.time.Instant.now()) {
        val id = campId ?: return
        if (!rights.readPinboard) return
        val fresh = posts.filter { !it.seen.own && postState(it, now) == PostWindow.VISIBLE && it.id !in reportedSeen }.map { it.id }.take(SEEN_BATCH)
        if (fresh.isEmpty()) return
        reportedSeen += fresh
        viewModelScope.launch(Background()) {
            when (val result = api.markSeen(id, fresh)) {
                is ApiResult.Ok -> markSeenLocally(fresh)
                is ApiResult.Failure -> when {
                    result.error == ApiError.NETWORK -> reportedSeen -= fresh.toSet()
                    fresh.size > 1 && result.error == ApiError.NOT_FOUND -> fresh.forEach { post ->
                        if (api.markOneSeen(id, post) is ApiResult.Ok) markSeenLocally(listOf(post))
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun markSeenLocally(ids: List<String>) {
        _posts.update { current ->
            val ok = current as? ApiResult.Ok ?: return@update current
            ApiResult.Ok(ok.value.map { if (it.id in ids && !it.seen.own) it.copy(seen = it.seen.copy(count = it.seen.count + 1, own = true)) else it })
        }
    }

    private fun replacePost(post: Post) {
        _posts.update { current ->
            val ok = current as? ApiResult.Ok ?: return@update current
            ApiResult.Ok(ok.value.map { if (it.id == post.id) post else it })
        }
    }

    private var creatingPost = false

    /** Legt eine Mitteilung an: erst die Dateien als Verknüpfung, dann den Beitrag, dann die Umfrage. */
    fun createPost(draft: PostDraft, onDone: () -> Unit) {
        val id = campId ?: return
        // Ein zweiter Tipp während der Anfrage legte die Mitteilung doppelt an (die Route kennt keine Wiederholung).
        if (creatingPost) return
        creatingPost = true
        writer.dismissError()
        viewModelScope.launch {
            try {
                val fileIds = mutableListOf<String>()
                for ((name, link) in draft.files) {
                    when (val added = api.addFile(id, name.trim(), link.trim())) {
                        is ApiResult.Ok -> fileIds += added.value.id
                        is ApiResult.Failure -> return@launch writer.fail(added) { createPost(draft, onDone) }
                    }
                }
                val created = when (val result = api.createPost(id, postBody(draft, fileIds))) {
                    is ApiResult.Ok -> result.value
                    is ApiResult.Failure -> return@launch writer.fail(result) { createPost(draft, onDone) }
                }
                val options = draft.pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
                if (options.size >= 2) {
                    when (val poll = api.createPoll(id, created.id, options, draft.pollMultiple)) {
                        is ApiResult.Ok -> Unit
                        // Die Mitteilung steht schon: Ein neuer Versuch legt nur die Umfrage an.
                        is ApiResult.Failure -> {
                            loadPosts(silent = true)
                            return@launch writer.fail(poll) { createPoll(created, options, draft.pollMultiple, onDone) }
                        }
                    }
                }
                loadPosts(silent = true)
                onDone()
            } finally {
                creatingPost = false
            }
        }
    }

    private fun createPoll(post: Post, options: List<String>, multiple: Boolean, onDone: () -> Unit) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val poll = api.createPoll(id, post.id, options, multiple)) {
                is ApiResult.Ok -> {
                    loadPosts(silent = true)
                    onDone()
                }
                is ApiResult.Failure -> writer.fail(poll) { createPoll(post, options, multiple, onDone) }
            }
        }
    }

    private fun postBody(draft: PostDraft, fileIds: List<String>): JsonObject = buildJsonObject {
        put("titel", draft.title.trim())
        if (draft.text.isNotBlank()) put("text", draft.text.trim())
        put("wichtig", draft.important)
        put("angeheftet", draft.pinned)
        draft.visibleFrom?.let { from -> dayStartInstant(from)?.let { put("sichtbarAb", it) } }
        draft.visibleUntil?.let { until -> dayEndInstant(until)?.let { put("sichtbarBis", it) } }
        recipientsBody(draft)?.let { put("empfaenger", it) }
        if (fileIds.isNotEmpty()) put("dateiIds", JsonArray(fileIds.map(::JsonPrimitive)))
    }

    fun changePost(post: Post, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}) {
        val id = campId ?: return
        writer.submit(
            summary, fields, post.version,
            call = { v -> api.changePost(id, post.id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) },
            onCurrent = { loadPosts(silent = false) },
        ) {
            replacePost(it)
            onDone()
        }
    }

    fun setPinned(post: Post, pinned: Boolean, summary: String) = changePost(post, mapOf("angeheftet" to JsonPrimitive(pinned)), summary) { loadPosts(silent = true) }

    fun deletePost(post: Post) {
        val id = campId ?: return
        writer.submit("", emptyMap(), post.version, { v -> api.deletePost(id, post.copy(version = v)) }, { loadPosts() }) { loadPosts(silent = true) }
    }

    /** Eine Stimme: Der Server setzt die eigenen Stimmen auf genau diese Antworten und liefert die Umfrage mit den neuen Zahlen. */
    fun vote(post: Post, option: PollOption) {
        val id = campId ?: return
        val poll = post.poll ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.vote(id, post.id, votesAfterTap(poll, option))) {
                is ApiResult.Ok -> replacePost(post.copy(poll = result.value))
                is ApiResult.Failure -> {
                    // Eine Umfrage, die inzwischen zu ist, zeigt der neue Stand; sonst steht der Fehler da.
                    if (result.code == "UMFRAGE_GESCHLOSSEN") loadPosts(silent = true)
                    writer.fail(result) { vote(post, option) }
                }
            }
        }
    }

    fun withdrawVotes(post: Post) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.withdrawVotes(id, post.id)) {
                is ApiResult.Ok -> loadPosts(silent = true)
                is ApiResult.Failure -> writer.fail(result) { withdrawVotes(post) }
            }
        }
    }

    fun closePoll(post: Post, closed: Boolean, summary: String) {
        val id = campId ?: return
        val poll = post.poll ?: return
        val fields = mapOf<String, JsonElement>("geschlossen" to JsonPrimitive(closed))
        writer.submit(
            summary, fields, poll.version,
            call = { v -> api.changePoll(id, post.id, JsonObject(mapOf("version" to JsonPrimitive(v)) + fields)) },
            onCurrent = { loadPosts(silent = false) },
        ) { replacePost(post.copy(poll = it)) }
    }

    /** Reaktion setzen; ein Tipp auf die eigene nimmt sie zurück. */
    fun react(post: Post, reaction: Reaction) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            if (post.reactions.own == reaction.wire) {
                when (val result = api.unreact(id, post.id)) {
                    is ApiResult.Ok -> replacePost(post.copy(reactions = post.reactions.without(reaction)))
                    is ApiResult.Failure -> writer.fail(result) { react(post, reaction) }
                }
            } else {
                when (val result = api.react(id, post.id, reaction)) {
                    is ApiResult.Ok -> replacePost(post.copy(reactions = result.value))
                    is ApiResult.Failure -> writer.fail(result) { react(post, reaction) }
                }
            }
        }
    }

    // ---- Eingang ----

    fun loadInbox(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _inbox.value = null
            val result = api.inbox(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _inbox.value == null) _inbox.value = result
        }
    }

    fun loadOwn(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _own.value = null
            val result = api.ownInbox(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _own.value == null) _own.value = result
        }
    }

    fun send(kind: InboxKind, text: String, anonymous: Boolean, onDone: () -> Unit) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.sendInbox(id, kind, text.trim(), anonymous)) {
                is ApiResult.Ok -> {
                    _sent.value = SentInbox(result.value.anonymous, result.value.code)
                    if (!anonymous) loadOwn(silent = true)
                    onDone()
                }
                is ApiResult.Failure -> writer.fail(result) { send(kind, text, anonymous, onDone) }
            }
        }
    }

    /** Die Person hat den Rückfragecode gesehen: Er steht nirgends sonst und wird hier vergessen. */
    fun codeSeen() {
        _sent.value = null
    }

    private var codeLookups = 0

    fun lookUpCode(code: String) {
        val id = campId ?: return
        writer.dismissError()
        val lookup = ++codeLookups
        viewModelScope.launch {
            _codeView.value = null
            val result = api.codeStatus(id, normalizeCode(code))
            // Nur die letzte Abfrage im gleichen Lager zählt; eine späte Antwort zeigt sonst den Stand eines anderen Codes.
            if (campId == id && lookup == codeLookups) _codeView.value = result
        }
    }

    fun clearCodeView() {
        codeLookups++
        _codeView.value = null
    }

    /** Antwort und Status zusammen; `reply` leer nimmt die Antwort zurück. Ein Konflikt erscheint im Konfliktbildschirm. */
    fun answer(entry: InboxEntry, reply: String?, status: InboxStatus?, summary: String, onDone: () -> Unit = {}) {
        val fields = buildMap<String, JsonElement> {
            if (reply != null) put("antwort", if (reply.isBlank()) JsonNull else JsonPrimitive(reply.trim()))
            status?.let { put("status", JsonPrimitive(it.wire)) }
        }
        changeInbox(entry, fields, summary, onDone)
    }

    fun assignRessort(entry: InboxEntry, ressortId: String?, summary: String) =
        changeInbox(entry, mapOf("ressortId" to (ressortId?.let(::JsonPrimitive) ?: JsonNull)), summary)

    private fun changeInbox(entry: InboxEntry, fields: Map<String, JsonElement>, summary: String, onDone: () -> Unit = {}) {
        val id = campId ?: return
        writer.submit(
            summary, fields, entry.version,
            call = { v -> api.answerInbox(id, entry.copy(version = v), JsonObject(fields)) },
            onCurrent = { loadInbox(silent = false) },
        ) { changed ->
            _inbox.update { current ->
                val ok = current as? ApiResult.Ok ?: return@update current
                ApiResult.Ok(ok.value.map { if (it.id == changed.id) changed else it })
            }
            onDone()
        }
    }

    /** «Als Traktandum»: Der Server legt es mit dem angegebenen Titel an (nie mit dem Text der Meldung) und verknüpft es. */
    fun toAgenda(entry: InboxEntry, title: String, meetingId: String?, doneText: String) {
        val id = campId ?: return
        writer.dismissError()
        viewModelScope.launch {
            when (val result = api.inboxToAgenda(id, entry, title.trim(), meetingId)) {
                is ApiResult.Ok -> {
                    _inbox.update { current ->
                        val ok = current as? ApiResult.Ok ?: return@update current
                        ApiResult.Ok(ok.value.map { if (it.id == result.value.id) result.value else it })
                    }
                    _notice.value = doneText
                }
                is ApiResult.Failure -> {
                    if (result.conflict != null) loadInbox(silent = true)
                    writer.fail(result) { toAgenda(entry, title, meetingId, doneText) }
                }
            }
        }
    }

    // ---- Benachrichtigungen ----

    fun loadNotifications(silent: Boolean = false) {
        val id = campId ?: return
        viewModelScope.launch(Background()) {
            if (!silent) _notifications.value = null
            val result = api.notifications(id)
            if (campId != id) return@launch
            if (result is ApiResult.Ok || !silent || _notifications.value == null) _notifications.value = result
        }
    }

    /** Markiert eine Benachrichtigung (`ids` mit einer ID) oder alle als gelesen. Die Liste zeigt es sofort. */
    fun markRead(ids: List<String>?) {
        val id = campId ?: return
        _notifications.update { current ->
            val ok = current as? ApiResult.Ok ?: return@update current
            ApiResult.Ok(ok.value.map { if (ids == null || it.id in ids) it.copy(read = true) else it })
        }
        viewModelScope.launch(Background()) {
            api.markRead(id, ids)
            camp.refreshUnread(id)
        }
    }

    fun loadSettings() {
        viewModelScope.launch(Background()) {
            _settings.value = when (val result = api.settings()) {
                is ApiResult.Ok -> ApiResult.Ok(result.value.kinds.associate { it.kind to it.active })
                is ApiResult.Failure -> result
            }
        }
    }

    fun setSetting(kind: String, active: Boolean) {
        writer.dismissError()
        // Der Schalter springt sofort; scheitert das Speichern, springt er zurück und der Fehler steht da.
        _settings.update { current ->
            val ok = current as? ApiResult.Ok ?: return@update current
            ApiResult.Ok(ok.value + (kind to active))
        }
        viewModelScope.launch {
            when (val result = api.setSetting(kind, active)) {
                is ApiResult.Ok -> Unit
                is ApiResult.Failure -> {
                    _settings.update { current ->
                        val ok = current as? ApiResult.Ok ?: return@update current
                        ApiResult.Ok(ok.value + (kind to !active))
                    }
                    writer.fail(result) { setSetting(kind, active) }
                }
            }
        }
    }
}

private fun ReactionState.without(reaction: Reaction): ReactionState =
    copy(counts = counts + (reaction.wire to ((counts[reaction.wire] ?: 1) - 1).coerceAtLeast(0)), own = null)

private const val SEEN_BATCH = 50
