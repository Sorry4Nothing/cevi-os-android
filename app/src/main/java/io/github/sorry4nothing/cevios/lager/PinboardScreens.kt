package io.github.sorry4nothing.cevios.lager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.DashedButton
import io.github.sorry4nothing.cevios.ui.DialogText
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import io.github.sorry4nothing.cevios.push.SwitchRow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

@StringRes
private fun reactionText(reaction: Reaction): Int = when (reaction) {
    Reaction.THUMBS_UP -> R.string.reaction_thumbs_up
    Reaction.HEART -> R.string.reaction_heart
    Reaction.LAUGH -> R.string.reaction_laugh
    Reaction.SURPRISE -> R.string.reaction_surprise
    Reaction.THANKS -> R.string.reaction_thanks
}

/**
 * Die Pinnwand (Entwurf L16): Mitteilungen mit «Wichtig» und «Angeheftet», Zeitfenster, Dateien, Reaktionen und Umfragen. Wer die
 * Pinnwand bearbeitet, sieht auch Vergangenes und Künftiges und hat «Neue Mitteilung»; alle anderen sehen, was für sie gemeint ist
 * und gerade gilt. «Gesehen»-Zähler kennt die API nicht.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinboardScreen(
    vm: CommsViewModel,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onNewPost: () -> Unit,
    onEditPost: (Post) -> Unit,
) {
    val state by vm.posts.collectAsState()
    val notice by vm.notice.collectAsState()
    val now = remember(state) { Instant.now() }
    var deleting by remember { mutableStateOf<Post?>(null) }
    LaunchedEffect(camp?.id, rights) {
        vm.rights = rights
        vm.loadPosts()
    }
    CampPage(
        title = stringResource(R.string.pinboard_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        banners = { notice?.let { text -> io.github.sorry4nothing.cevios.ui.ActionBanner(NoticeKind.INFO, text, null, null, vm::dismissNotice) } },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.pinboard_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadPosts() })
            is ApiResult.Ok -> {
                if (result.value.isEmpty()) {
                    StatePage(R.drawable.ic_pin, stringResource(R.string.pinboard_empty_title), stringResource(R.string.pinboard_empty_text)) {
                        if (rights.editPinboard) PrimaryButton(stringResource(R.string.pinboard_new), onNewPost, icon = R.drawable.ic_plus)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.editPinboard) 96.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                    ) {
                        items(result.value, key = { it.id }) { post ->
                            PostCard(
                                post, rights, now, vm,
                                onEdit = { onEditPost(post) },
                                onDelete = { deleting = post },
                            )
                        }
                    }
                    if (rights.editPinboard) {
                        PrimaryButton(stringResource(R.string.pinboard_new), onNewPost, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }
    deleting?.let { post ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.post_delete_title, post.title),
            text = stringResource(R.string.post_delete_text),
            onConfirm = { deleting = null; vm.deletePost(post) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCard(post: Post, rights: MeetingRights, now: Instant, vm: CommsViewModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    val open = rememberLinkOpener()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val window = postState(post, now)
    val copiedText = stringResource(R.string.post_copied)
    val audience = audienceText(post)
    val until = instantToDay(post.visibleUntil)?.let { stringResource(R.string.post_visible_until, formatDay(it)) }
    val meta = listOfNotNull(audience, until).joinToString(", ")
    val author = post.author?.display?.takeIf { it.isNotBlank() }
    InfoCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            if (post.important) StatusChip(ChipKind.REVIEW, stringResource(R.string.post_important))
            if (post.pinned) StatusChip(ChipKind.AGE_GROUP, stringResource(R.string.post_pinned))
            if (window == PostWindow.UPCOMING) StatusChip(ChipKind.WAITING, stringResource(R.string.post_upcoming, instantToDay(post.visibleFrom)?.let(::formatDay).orEmpty()))
            if (window == PostWindow.EXPIRED) StatusChip(ChipKind.NO, stringResource(R.string.post_expired))
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(post.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (rights.editPinboard) io.github.sorry4nothing.cevios.ui.RoundButton(HeaderButton(R.drawable.ic_more_horizontal, stringResource(R.string.post_actions, post.title)) { menu = true })
        }
        if (post.text.isNotBlank()) Text(post.text, style = MaterialTheme.typography.bodyLarge)
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        author?.let { Text(stringResource(R.string.post_by, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        post.files.forEach { file -> FileRow(file) { open(file.link) } }
        ReactionRow(post, rights.interact, vm)
        TextActionButton(stringResource(R.string.post_copy), { copyToClipboard(context, copyText(post)); vm.showNotice(copiedText) })
        post.poll?.let { poll -> PollBlock(post, poll, rights, vm) }
    }
    if (menu) {
        ActionSheet(
            post.title,
            buildList {
                add(stringResource(R.string.post_edit) to onEdit)
                add(stringResource(if (post.pinned) R.string.post_unpin else R.string.post_pin) to { vm.setPinned(post, !post.pinned, post.title) })
                post.poll?.let { poll ->
                    add(stringResource(if (poll.closed) R.string.poll_reopen else R.string.poll_close) to { vm.closePoll(post, !poll.closed, post.title) })
                }
                add(stringResource(R.string.task_delete) to onDelete)
            },
            onDismiss = { menu = false },
        )
    }
}

@Composable
private fun audienceText(post: Post): String {
    val r = post.recipients
    return if (r.everyone || (r.roles.isEmpty() && r.people.isEmpty())) {
        stringResource(R.string.post_to_all)
    } else {
        val roles = r.roles.map { stringResource(roleText(it)) }
        stringResource(R.string.post_to, (roles + if (r.people.isNotEmpty()) listOf(pluralStringResource(R.plurals.post_persons, r.people.size, r.people.size)) else emptyList()).joinToString(", "))
    }
}

private fun roleText(role: String): Int = when (role) {
    "leitung" -> R.string.recipient_leadership
    "team" -> R.string.recipient_team
    else -> R.string.recipient_read
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("", text))
}

/** Eine Datei am Ablageort: Name, Typ und Grösse; ein Tipp öffnet den Link im Browser (nur https). */
@Composable
private fun FileRow(file: CampFile, onOpen: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val type = fileTypeText(file.type)
    val size = file.size?.let { formatSize(it) }
    val detail = listOfNotNull(type, size).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.file_open), role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = listOf(file.name, detail).filter { it.isNotBlank() }.joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.cevi.accent)
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.cevi.accent)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fileTypeText(type: String?): String? = when {
    type == null -> null
    type.endsWith("pdf") -> "PDF"
    type.startsWith("image/") -> "Bild"
    else -> null
}

/** «120 KB» oder «1.4 MB» aus Bytes. */
fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1_000_000.0)
    bytes >= 1_000 -> "${(bytes + 500) / 1_000} KB"
    else -> "$bytes B"
}

/** Die fünf Reaktionen: Wer reagieren darf, tippt; die eigene ist gewählt. Alle anderen sehen nur die Zahlen als Satz. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionRow(post: Post, canReact: Boolean, vm: CommsViewModel) {
    if (canReact) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Reaction.entries.forEach { reaction ->
                val count = post.reactions.count(reaction)
                val label = stringResource(reactionText(reaction))
                PickPill(if (count > 0) "$label $count" else label, post.reactions.own == reaction.wire, { vm.react(post, reaction) })
            }
        }
    } else if (post.reactions.total > 0) {
        val labels = Reaction.entries.associateWith { stringResource(reactionText(it)) }
        val line = Reaction.entries.filter { post.reactions.count(it) > 0 }.joinToString(", ") { "${post.reactions.count(it)} ${labels[it]}" }
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Die Umfrage zu einer Mitteilung: Antworten mit Balken und Zahl; wer stimmen darf, tippt eine Antwort an. Zu ist sie als Wort und Etikett. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PollBlock(post: Post, poll: Poll, rights: MeetingRights, vm: CommsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.padding(top = Spacing.s2)) {
        Text(stringResource(R.string.poll_title, post.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            if (poll.closed) StatusChip(ChipKind.NO, stringResource(R.string.poll_closed))
            if (poll.multiple && !poll.closed) StatusChip(ChipKind.PENDING, stringResource(R.string.poll_multiple))
            instantToDay(poll.closesAt)?.takeIf { !poll.closed }?.let { StatusChip(ChipKind.PENDING, stringResource(R.string.poll_until, formatDay(it))) }
        }
        val canVote = rights.interact && !poll.closed
        poll.options.forEach { option -> PollOptionRow(poll, option, canVote) { vm.vote(post, option) } }
        Text(pluralStringResource(R.plurals.poll_participants, poll.participants, poll.participants), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (canVote && poll.options.any { it.own }) TextActionButton(stringResource(R.string.poll_withdraw), { vm.withdrawVotes(post) })
    }
}

@Composable
private fun PollOptionRow(poll: Poll, option: PollOption, canVote: Boolean, onVote: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val percent = pollPercent(option, poll)
    val votes = pluralStringResource(R.plurals.poll_votes, option.votes, option.votes)
    val mine = stringResource(R.string.poll_mine)
    val spoken = listOfNotNull(option.text, votes, if (option.own) mine else null).joinToString(", ")
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .then(
                if (canVote) {
                    Modifier.selectable(selected = option.own, role = if (poll.multiple) Role.Checkbox else Role.RadioButton, onClick = onVote)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken; if (option.own) stateDescription = mine }
            .padding(vertical = Spacing.s1),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            if (option.own) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(18.dp))
            Text(option.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(votes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(Pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().clip(Pill).background(MaterialTheme.cevi.accent))
        }
        if (poll.namesVisible && option.people.isNotEmpty()) {
            Text(option.people.joinToString(", ") { it.display }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Eine Mitteilung schreiben oder ändern (Entwurf L17): Titel, Text, Empfänger, Zeitfenster, «Wichtig», «Anheften», Umfrage und
 * Dateien. Dateien sind Verknüpfungen auf den Ablageort (Name und https-Link); Cevi OS lädt nichts hoch. Beim Ändern gibt es nur
 * die Felder, die der Server ändern lässt (keine Umfrage, keine Dateien).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostEditorScreen(
    vm: CommsViewModel,
    existing: Post?,
    camp: Camp?,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember {
        mutableStateOf(
            existing?.let {
                PostDraft(
                    title = it.title, text = it.text, important = it.important, pinned = it.pinned,
                    roles = if (it.recipients.everyone) emptySet() else it.recipients.roles.toSet(),
                    visibleFrom = instantToDay(it.visibleFrom), visibleUntil = instantToDay(it.visibleUntil),
                )
            } ?: PostDraft(),
        )
    }
    var showErrors by remember { mutableStateOf(false) }
    var datePicking by remember { mutableStateOf<String?>(null) }
    var addingFile by remember { mutableStateOf(false) }
    var polling by remember { mutableStateOf(false) }
    val validOptions = draft.pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
    val pollInvalid = polling && validOptions.size < 2
    val windowInvalid = draft.visibleFrom != null && draft.visibleUntil != null && draft.visibleUntil!! < draft.visibleFrom!!

    CampPage(
        title = stringResource(if (existing == null) R.string.post_editor_new else R.string.post_editor_edit),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_pinboard), onBack),
    ) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s3).padding(bottom = Spacing.s6),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            if (showErrors && (draft.title.isBlank() || pollInvalid || windowInvalid)) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.error_check_fields))
            LabeledTextField(
                stringResource(R.string.post_field_title), draft.title, { draft = draft.copy(title = it) }, 200, Modifier.fillMaxWidth(),
                hint = if (showErrors && draft.title.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            LabeledTextField(stringResource(R.string.post_field_text), draft.text, { draft = draft.copy(text = it) }, 10000, Modifier.fillMaxWidth(), minLines = 4)
            Text(stringResource(R.string.post_field_recipients), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.recipient_all), draft.roles.isEmpty(), { draft = draft.copy(roles = emptySet()) }, role = Role.Checkbox)
                listOf("leitung", "team", "lesen").forEach { role ->
                    PickPill(
                        stringResource(roleText(role)), role in draft.roles,
                        { draft = draft.copy(roles = if (role in draft.roles) draft.roles - role else draft.roles + role) },
                        role = Role.Checkbox,
                    )
                }
            }
            OutlinedPillButton(
                stringResource(R.string.post_from, draft.visibleFrom?.let { formatMeetingDay(it) } ?: stringResource(R.string.post_window_now)),
                { datePicking = "from" }, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar,
            )
            OutlinedPillButton(
                stringResource(R.string.post_until, draft.visibleUntil?.let { formatMeetingDay(it) } ?: stringResource(R.string.post_window_open)),
                { datePicking = "until" }, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar,
            )
            if (windowInvalid) Text(stringResource(R.string.error_window_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            SwitchRow(R.drawable.ic_warning, stringResource(R.string.post_important), stringResource(R.string.post_important_hint), draft.important, true) { draft = draft.copy(important = it) }
            SwitchRow(R.drawable.ic_pin, stringResource(R.string.post_pin_switch), null, draft.pinned, true) { draft = draft.copy(pinned = it) }

            if (existing == null) {
                SectionTitle(stringResource(R.string.poll_editor_title))
                if (!polling) {
                    DashedButton(stringResource(R.string.poll_add), { polling = true; draft = draft.copy(pollOptions = listOf("", "")) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                } else {
                    draft.pollOptions.forEachIndexed { index, option ->
                        LabeledTextField(
                            stringResource(R.string.poll_option_n, index + 1), option,
                            { text -> draft = draft.copy(pollOptions = draft.pollOptions.mapIndexed { i, o -> if (i == index) text else o }) },
                            200, Modifier.fillMaxWidth(),
                            hint = if (showErrors && pollInvalid && option.isBlank()) stringResource(R.string.poll_error_min) else null,
                        )
                    }
                    if (draft.pollOptions.size < 10) {
                        DashedButton(stringResource(R.string.poll_option_add), { draft = draft.copy(pollOptions = draft.pollOptions + "") }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                    }
                    SwitchRow(R.drawable.ic_list_check, stringResource(R.string.poll_multiple_switch), null, draft.pollMultiple, true) { draft = draft.copy(pollMultiple = it) }
                    TextActionButton(stringResource(R.string.poll_remove), { polling = false; draft = draft.copy(pollOptions = emptyList(), pollMultiple = false) })
                }
                SectionTitle(stringResource(R.string.file_section))
                draft.files.forEachIndexed { index, (name, _) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextActionButton(stringResource(R.string.file_remove), { draft = draft.copy(files = draft.files.filterIndexed { i, _ -> i != index }) })
                    }
                }
                if (draft.files.size < 10) DashedButton(stringResource(R.string.file_add), { addingFile = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
            }
            PrimaryButton(
                stringResource(if (existing == null) R.string.post_send else R.string.save),
                {
                    if (draft.title.isBlank() || pollInvalid || windowInvalid) {
                        showErrors = true
                    } else if (existing == null) {
                        vm.createPost(draft, onBack)
                    } else {
                        vm.changePost(existing, postFields(existing, draft), existing.title, onBack)
                    }
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
    datePicking?.let { which ->
        DateDialog(
            initial = if (which == "from") draft.visibleFrom else draft.visibleUntil,
            onPick = { date -> draft = if (which == "from") draft.copy(visibleFrom = date) else draft.copy(visibleUntil = date); datePicking = null },
            onDismiss = { datePicking = null },
        )
    }
    if (addingFile) {
        FileLinkDialog(
            onConfirm = { name, link -> addingFile = false; draft = draft.copy(files = draft.files + (name to link)) },
            onDismiss = { addingFile = false },
        )
    }
}

/** Die Felder, die sich gegenüber dem gespeicherten Beitrag geändert haben (`null` hebt eine Grenze des Zeitfensters auf). */
private fun postFields(old: Post, draft: PostDraft): Map<String, JsonElement> = buildMap {
    if (draft.title.trim() != old.title) put("titel", JsonPrimitive(draft.title.trim()))
    if (draft.text.trim() != old.text) put("text", JsonPrimitive(draft.text.trim()))
    if (draft.important != old.important) put("wichtig", JsonPrimitive(draft.important))
    if (draft.pinned != old.pinned) put("angeheftet", JsonPrimitive(draft.pinned))
    if (draft.visibleFrom != instantToDay(old.visibleFrom)) put("sichtbarAb", draft.visibleFrom?.let { dayStartInstant(it) }?.let(::JsonPrimitive) ?: JsonNull)
    if (draft.visibleUntil != instantToDay(old.visibleUntil)) put("sichtbarBis", draft.visibleUntil?.let { dayEndInstant(it) }?.let(::JsonPrimitive) ?: JsonNull)
    val oldRoles = if (old.recipients.everyone) emptySet() else old.recipients.roles.toSet()
    if (draft.roles != oldRoles) {
        put("empfaenger", JsonObject(mapOf("rollen" to JsonArray(draft.roles.map(::JsonPrimitive)), "personen" to JsonArray(emptyList()))))
    }
}

@Composable
private fun FileLinkDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    CeviDialog(
        title = stringResource(R.string.file_add),
        onDismiss = onDismiss,
        confirmButton = {
            PrimaryButton(stringResource(R.string.task_apply), { if (name.isBlank() || !isValidFileLink(link)) showErrors = true else onConfirm(name.trim(), link.trim()) })
        },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            DialogText(stringResource(R.string.file_add_text))
            LabeledTextField(stringResource(R.string.file_field_name), name, { name = it }, 200, Modifier.fillMaxWidth(), hint = if (showErrors && name.isBlank()) stringResource(R.string.error_field_required) else null)
            LabeledTextField(
                stringResource(R.string.file_field_link), link, { link = it }, 2048, Modifier.fillMaxWidth(),
                hint = if (showErrors && !isValidFileLink(link)) stringResource(R.string.error_file_link) else stringResource(R.string.file_field_link_hint),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
        }
    }
}
