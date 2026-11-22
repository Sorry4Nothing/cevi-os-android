package io.github.sorry4nothing.cevios.lager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.push.SwitchRow
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.DialogText
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing

private enum class InboxTab { INBOX, MINE }

private fun kindText(kind: InboxKind): Int = when (kind) {
    InboxKind.IDEA -> R.string.inbox_kind_idea
    InboxKind.WISH -> R.string.inbox_kind_wish
    InboxKind.QUESTION -> R.string.inbox_kind_question
    InboxKind.FEEDBACK -> R.string.inbox_kind_feedback
}

private fun statusText(status: InboxStatus): Int = when (status) {
    InboxStatus.NEW -> R.string.inbox_status_new
    InboxStatus.IN_PROGRESS -> R.string.inbox_status_progress
    InboxStatus.DONE -> R.string.inbox_status_done
}

private fun statusChip(status: InboxStatus): ChipKind = when (status) {
    InboxStatus.NEW -> ChipKind.PENDING
    InboxStatus.IN_PROGRESS -> ChipKind.REVIEW
    InboxStatus.DONE -> ChipKind.YES
}

/** «9. November» aus einem Tag (`2027-11-09`); die API nennt bei Meldungen nur den Tag, nie die Uhrzeit. */
private fun dayText(iso: String): String = formatDay(iso)

/**
 * Der Eingang (Entwurf L18). Die Lagerleitung liest die Meldungen, beantwortet sie, ordnet sie einem Ressort zu und macht ein
 * Traktandum daraus. Wer senden darf, schreibt eine Idee, einen Wunsch, eine Frage oder Feedback, auf Wunsch anonym: Dann gibt es
 * keine Person bei der Meldung, und die Antwort liest man mit dem Rückfragecode. Eine anonyme Meldung zeigt nirgends einen Namen.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    vm: CommsViewModel,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    ressorts: List<Ressort>,
    upcoming: List<Meeting>,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val inbox by vm.inbox.collectAsState()
    val own by vm.own.collectAsState()
    val sent by vm.sent.collectAsState()
    val codeView by vm.codeView.collectAsState()
    val notice by vm.notice.collectAsState()
    val context = LocalContext.current
    var tab by remember(rights.readInbox) { mutableStateOf(if (rights.readInbox) InboxTab.INBOX else InboxTab.MINE) }
    var composing by remember { mutableStateOf(false) }
    var lookingUp by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<InboxEntry?>(null) }
    var assigning by remember { mutableStateOf<InboxEntry?>(null) }
    var toAgenda by remember { mutableStateOf<InboxEntry?>(null) }
    LaunchedEffect(camp?.id, rights.readInbox) {
        if (rights.readInbox) vm.loadInbox()
        if (rights.readOwnInbox) vm.loadOwn()
    }
    val doneText = stringResource(R.string.inbox_agenda_done)
    val sentText = stringResource(R.string.inbox_sent)
    val answerSummary = stringResource(R.string.inbox_summary_answer)
    val assignSummary = stringResource(R.string.inbox_summary_assign)

    CampPage(
        title = stringResource(R.string.inbox_title),
        subtitle = stringResource(R.string.inbox_subtitle),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_start), onBack),
        banners = { notice?.let { ActionBanner(NoticeKind.INFO, it, null, null, vm::dismissNotice) } },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (rights.readInbox && rights.readOwnInbox) {
                FlowRow(
                    Modifier.padding(horizontal = Spacing.s3).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    PickPill(stringResource(R.string.inbox_tab_inbox), tab == InboxTab.INBOX, { tab = InboxTab.INBOX }, role = Role.Tab)
                    PickPill(stringResource(R.string.inbox_tab_mine), tab == InboxTab.MINE, { tab = InboxTab.MINE }, role = Role.Tab)
                }
            }
            Column(Modifier.weight(1f)) {
                if (tab == InboxTab.INBOX && rights.readInbox) {
                    InboxList(inbox, rights, vm, onAnswer = { answering = it }, onAssign = { assigning = it }, onAgenda = { toAgenda = it })
                } else {
                    OwnList(own, rights, onCompose = { composing = true }, onLookUp = { lookingUp = true }, onRetry = { vm.loadOwn() })
                }
            }
        }
    }

    if (composing) {
        InboxComposeSheet(
            onSend = { kind, text, anonymous -> composing = false; vm.send(kind, text, anonymous) { if (!anonymous) vm.showNotice(sentText) } },
            onDismiss = { composing = false },
        )
    }
    sent?.takeIf { it.anonymous && it.code != null }?.let { result ->
        CeviDialog(
            title = stringResource(R.string.inbox_code_title),
            onDismiss = vm::codeSeen,
            confirmButton = { PrimaryButton(stringResource(R.string.inbox_code_noted), vm::codeSeen) },
            dismissButton = { TextActionButton(stringResource(R.string.inbox_code_copy), { copyCode(context, result.code!!) }) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(result.code!!, style = MaterialTheme.typography.headlineSmall)
                DialogText(stringResource(R.string.inbox_code_text))
            }
        }
    }
    if (lookingUp) CodeLookupDialog(vm, codeView) { lookingUp = false; vm.clearCodeView() }
    answering?.let { entry ->
        AnswerSheet(
            entry,
            onSave = { reply, status ->
                answering = null
                // Nur was sich ändert, geht hinaus; ohne Änderung bleibt es beim Schliessen.
                val newReply = reply.takeIf { it != entry.reply.orEmpty() }
                val newStatus = status.takeIf { it != entry.status }
                if (newReply != null || newStatus != null) vm.answer(entry, newReply, newStatus, answerSummary)
            },
            onDismiss = { answering = null },
        )
    }
    assigning?.let { entry ->
        ChoiceSheet(
            title = stringResource(R.string.inbox_assign),
            choices = listOf(Choice(null, stringResource(R.string.task_no_ressort))) + ressorts.map { Choice(it.id, it.abbreviation + " " + it.name) },
            initial = entry.ressortId,
            onApply = { id -> assigning = null; vm.assignRessort(entry, id, assignSummary) },
            onDismiss = { assigning = null },
        )
    }
    toAgenda?.let { entry ->
        AgendaFromInboxSheet(
            meetings = upcoming,
            onSave = { title, meetingId -> toAgenda = null; vm.toAgenda(entry, title, meetingId, doneText) },
            onDismiss = { toAgenda = null },
        )
    }
}

private fun copyCode(context: Context, code: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("", code))
}

@Composable
private fun InboxList(
    state: ApiResult<List<InboxEntry>>?,
    rights: MeetingRights,
    vm: CommsViewModel,
    onAnswer: (InboxEntry) -> Unit,
    onAssign: (InboxEntry) -> Unit,
    onAgenda: (InboxEntry) -> Unit,
) {
    when (state) {
        null -> CardSkeleton(stringResource(R.string.inbox_loading))
        is ApiResult.Failure -> ErrorState(state.error, { vm.loadInbox() })
        is ApiResult.Ok -> if (state.value.isEmpty()) {
            StatePage(R.drawable.ic_check, stringResource(R.string.inbox_empty_title), stringResource(R.string.inbox_empty_text))
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, top = Spacing.s2, bottom = Spacing.s6),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                items(state.value, key = { it.id }) { entry -> InboxCard(entry, rights, onAnswer, onAssign, onAgenda) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(entry: InboxEntry, rights: MeetingRights, onAnswer: (InboxEntry) -> Unit, onAssign: (InboxEntry) -> Unit, onAgenda: (InboxEntry) -> Unit) {
    // Bei einer anonymen Meldung steht nur «anonym» und der Tag, nie ein Name.
    val from = if (entry.anonymous) stringResource(R.string.inbox_anonymous) else entry.from?.display?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_unknown_sender)
    val meta = stringResource(R.string.inbox_meta, from, dayText(entry.createdOn))
    InfoCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            StatusChip(statusChip(entry.status), stringResource(statusText(entry.status)))
            StatusChip(ChipKind.AGE_GROUP, stringResource(kindText(entry.kind)))
            if (entry.agendaItemId != null) StatusChip(ChipKind.YES, stringResource(R.string.inbox_on_agenda))
        }
        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(entry.text, style = MaterialTheme.typography.bodyLarge)
        entry.reply?.let { Text(stringResource(R.string.inbox_reply_is, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (rights.answerInbox) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                OutlinedPillButton(stringResource(R.string.inbox_answer), { onAnswer(entry) })
                OutlinedPillButton(stringResource(R.string.inbox_assign), { onAssign(entry) })
                if (entry.agendaItemId == null && rights.editMeetings) OutlinedPillButton(stringResource(R.string.inbox_to_agenda), { onAgenda(entry) })
            }
        }
    }
}

@Composable
private fun OwnList(state: ApiResult<List<OwnInboxEntry>>?, rights: MeetingRights, onCompose: () -> Unit, onLookUp: () -> Unit, onRetry: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, top = Spacing.s2, bottom = Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        if (rights.sendInbox) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    PrimaryButton(stringResource(R.string.inbox_new), onCompose, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                    OutlinedPillButton(stringResource(R.string.inbox_lookup), onLookUp, Modifier.fillMaxWidth())
                }
            }
        }
        item { SectionTitle(stringResource(R.string.inbox_tab_mine), Modifier.padding(top = Spacing.s2)) }
        when (state) {
            null -> item { Text(stringResource(R.string.inbox_loading), style = MaterialTheme.typography.bodyMedium) }
            is ApiResult.Failure -> item { ErrorState(state.error, onRetry) }
            is ApiResult.Ok -> if (state.value.isEmpty()) {
                item { Text(stringResource(R.string.inbox_mine_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.value, key = { it.id }) { entry ->
                    val description = listOfNotNull(stringResource(kindText(entry.kind)), dayText(entry.createdOn), stringResource(statusText(entry.status)), entry.text, entry.reply?.let { stringResource(R.string.inbox_reply_is, it) }).joinToString(", ")
                    InfoCard(description = description) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(statusChip(entry.status), stringResource(statusText(entry.status)))
                            Text(stringResource(R.string.inbox_meta_own, stringResource(kindText(entry.kind)), dayText(entry.createdOn)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                        entry.reply?.let { Text(stringResource(R.string.inbox_reply_is, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InboxComposeSheet(onSend: (InboxKind, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(InboxKind.FEEDBACK) }
    var text by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(R.string.inbox_new), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                InboxKind.entries.forEach { PickPill(stringResource(kindText(it)), kind == it, { kind = it }) }
            }
            LabeledTextField(
                stringResource(R.string.inbox_field_text), text, { text = it }, 5000, Modifier.fillMaxWidth(), minLines = 4,
                hint = if (showErrors && text.isBlank()) stringResource(R.string.error_field_required) else stringResource(R.string.inbox_field_hint),
            )
            SwitchRow(R.drawable.ic_lock, stringResource(R.string.inbox_anonymous_switch), stringResource(if (anonymous) R.string.inbox_anonymous_on else R.string.inbox_anonymous_off), anonymous, true) { anonymous = it }
            PrimaryButton(stringResource(R.string.inbox_send), { if (text.isBlank()) showErrors = true else onSend(kind, text, anonymous) }, Modifier.fillMaxWidth())
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CodeLookupDialog(vm: CommsViewModel, view: ApiResult<CodeView>?, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    CeviDialog(
        title = stringResource(R.string.inbox_lookup),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.inbox_lookup_read), { vm.lookUpCode(code) }, enabled = normalizeCode(code).length >= 8) },
        dismissButton = { TextActionButton(stringResource(R.string.close), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            DialogText(stringResource(R.string.inbox_lookup_text))
            LabeledTextField(stringResource(R.string.inbox_code_field), code, { code = it }, 40, Modifier.fillMaxWidth())
            when (view) {
                null -> Unit
                is ApiResult.Failure -> NoticeText(stringResource(R.string.inbox_lookup_unknown))
                is ApiResult.Ok -> {
                    val v = view.value
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        StatusChip(statusChip(v.status), stringResource(statusText(v.status)))
                        StatusChip(ChipKind.AGE_GROUP, stringResource(kindText(v.kind)))
                    }
                    Text(
                        v.reply?.let { stringResource(R.string.inbox_reply_is, it) } ?: stringResource(R.string.inbox_no_reply_yet),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeText(text: String) {
    io.github.sorry4nothing.cevios.ui.NoticeBanner(NoticeKind.ERROR, text)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AnswerSheet(entry: InboxEntry, onSave: (String, InboxStatus) -> Unit, onDismiss: () -> Unit) {
    var reply by remember { mutableStateOf(entry.reply.orEmpty()) }
    var status by remember { mutableStateOf(if (entry.status == InboxStatus.NEW && entry.reply == null) InboxStatus.IN_PROGRESS else entry.status) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(R.string.inbox_answer), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledTextField(stringResource(R.string.inbox_field_reply), reply, { reply = it }, 5000, Modifier.fillMaxWidth(), minLines = 3, hint = stringResource(R.string.inbox_reply_hint))
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                InboxStatus.entries.forEach { PickPill(stringResource(statusText(it)), status == it, { status = it }) }
            }
            PrimaryButton(stringResource(R.string.save), { onSave(reply.trim(), status) }, Modifier.fillMaxWidth())
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Aus einer Meldung ein Traktandum machen: Der Titel ist neu (der Text der Meldung wird nie kopiert), der Höck die Wahl. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AgendaFromInboxSheet(meetings: List<Meeting>, onSave: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var meetingId by remember { mutableStateOf<String?>(null) }
    var showErrors by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(R.string.inbox_to_agenda), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.inbox_to_agenda_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledTextField(
                stringResource(R.string.agenda_field_title), title, { title = it }, 200, Modifier.fillMaxWidth(),
                hint = if (showErrors && title.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.inbox_next_meeting), meetingId == null, { meetingId = null })
                meetings.take(3).forEach { PickPill(it.title + " " + formatMeetingDay(it.date), meetingId == it.id, { meetingId = it.id }) }
            }
            PrimaryButton(stringResource(R.string.save), { if (title.isBlank()) showErrors = true else onSave(title, meetingId) }, Modifier.fillMaxWidth())
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

// ---- Benachrichtigungen ----

private enum class NotificationsTab { NEW, SETTINGS }

/** Wohin eine Benachrichtigung oder ein Push führt. Das Ziel gibt es nur, wenn die Person es sehen darf (der Server prüft). */
sealed interface CampTarget {
    data class Task(val id: String) : CampTarget
    data object Mentions : CampTarget
    data object Pinboard : CampTarget
    data object Inbox : CampTarget
    data object Notifications : CampTarget
    data object Swaps : CampTarget
    data object Start : CampTarget
}

/**
 * Baut das Ziel aus Art oder Push-`typ` und dem Objekt. Aufgaben öffnen sich selbst; Erwähnungen führen zur Liste der Erwähnungen
 * (der Inhalt ist ein Traktandum, ein Beschluss oder ein Beitrag, den die Liste mit Titel nennt); wichtige Mitteilungen zur
 * Pinnwand, Antworten auf Feedback in den Eingang; Diensttausch (Anfrage und Entscheid) zur Liste der Anfragen; alles andere zum Start.
 */
fun campTargetFor(kind: String?, objectType: String, objectId: String): CampTarget = when {
    kind == "erwaehnung" || kind == "lager_erwaehnung" -> CampTarget.Mentions
    kind == "pinnwand_wichtig" || kind == "lager_pinnwand" -> if (objectType == "adressbuch") CampTarget.Start else CampTarget.Pinboard
    kind == "eingang_antwort" || kind == "lager_eingang" -> CampTarget.Inbox
    kind == "diensttausch_anfrage" || kind == "diensttausch_entscheid" || kind == "lager_einteilung" || objectType == "diensttausch" -> CampTarget.Swaps
    objectType.startsWith("aufgabe") -> CampTarget.Task(objectId)
    else -> CampTarget.Start
}

private fun notificationKindText(kind: String): Int = when (kind) {
    "aufgabe_zugeteilt" -> R.string.notif_kind_task_assigned
    "aufgabe_geaendert" -> R.string.notif_kind_task_changed
    "erwaehnung" -> R.string.notif_kind_mention
    "frist" -> R.string.notif_kind_due
    "pinnwand_wichtig" -> R.string.notif_kind_pinboard
    "eingang_antwort" -> R.string.notif_kind_inbox_reply
    "diensttausch_anfrage" -> R.string.notif_kind_swap_request
    "diensttausch_entscheid" -> R.string.notif_kind_swap_decision
    "anwesenheit_anfrage" -> R.string.notif_kind_attendance
    else -> R.string.notif_kind_address_book
}

/**
 * Die Benachrichtigungen eines Lagers (Entwurf L19): Neues mit «Gelesen», die Einstellungen pro Art. Eine Zeile trägt Art und Ziel,
 * keinen Text; die Sätze entstehen aus der Art. Eine abgeschaltete Art gibt weder Eintrag noch Push.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LagerNotificationsScreen(
    vm: CommsViewModel,
    camp: Camp?,
    queue: QueueState,
    online: Boolean,
    tasks: List<CampTask>,
    onOpenQueue: () -> Unit,
    onOpenTarget: (CampTarget) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.notifications.collectAsState()
    val settings by vm.settings.collectAsState()
    var tab by remember { mutableStateOf(NotificationsTab.NEW) }
    LaunchedEffect(camp?.id) {
        vm.loadNotifications()
        vm.loadSettings()
    }
    CampPage(
        title = stringResource(R.string.notif_title),
        subtitle = camp?.title,
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_start), onBack),
    ) {
        Column(Modifier.fillMaxWidth()) {
            FlowRow(
                Modifier.padding(horizontal = Spacing.s3).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PickPill(stringResource(R.string.notif_tab_new), tab == NotificationsTab.NEW, { tab = NotificationsTab.NEW }, role = Role.Tab)
                PickPill(stringResource(R.string.notif_tab_settings), tab == NotificationsTab.SETTINGS, { tab = NotificationsTab.SETTINGS }, role = Role.Tab)
            }
            Column(Modifier.weight(1f)) {
                if (tab == NotificationsTab.NEW) {
                    when (val result = state) {
                        null -> CardSkeleton(stringResource(R.string.notif_loading))
                        is ApiResult.Failure -> ErrorState(result.error, { vm.loadNotifications() })
                        is ApiResult.Ok -> if (result.value.isEmpty()) {
                            StatePage(R.drawable.ic_bell, stringResource(R.string.notif_empty_title), stringResource(R.string.notif_empty_text))
                        } else {
                            LazyColumn(
                                Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, top = Spacing.s2, bottom = Spacing.s6),
                                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                            ) {
                                if (result.value.any { !it.read }) {
                                    item { TextActionButton(stringResource(R.string.notif_mark_all), { vm.markRead(null) }) }
                                }
                                items(result.value, key = { it.id }) { entry ->
                                    val task = if (entry.objectType.startsWith("aufgabe")) tasks.firstOrNull { it.id == entry.objectId } else null
                                    val text = newsText(entry.kind, task?.title)
                                    val time = newsTime(entry.createdAt)
                                    val target = campTargetFor(entry.kind, entry.objectType, entry.objectId)
                                    val opener = { if (!entry.read) vm.markRead(listOf(entry.id)); onOpenTarget(target) }
                                    val spokenLine = text + ", " + time + if (entry.read) "" else ", " + stringResource(R.string.notif_unread)
                                    val rowShape = MaterialTheme.shapes.small
                                    InfoCard {
                                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .heightIn(min = Sizes.touchTarget)
                                                    .focusRing(rowShape)
                                                    .clip(rowShape)
                                                    .clickable(onClickLabel = stringResource(R.string.notif_open), role = Role.Button, onClick = opener)
                                                    .semantics(mergeDescendants = true) { contentDescription = spokenLine },
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Text(text, style = MaterialTheme.typography.titleMedium)
                                                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (!entry.read) {
                                                StatusChip(ChipKind.PENDING, stringResource(R.string.notif_unread))
                                            }
                                        }
                                        if (!entry.read) TextActionButton(stringResource(R.string.notif_mark_read), { vm.markRead(listOf(entry.id)) })
                                    }
                                }
                            }
                        }
                    }
                } else {
                    when (val result = settings) {
                        null -> CardSkeleton(stringResource(R.string.notif_loading))
                        is ApiResult.Failure -> ErrorState(result.error, { vm.loadSettings() })
                        is ApiResult.Ok -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s3, vertical = Spacing.s2)) {
                            Text(stringResource(R.string.notif_settings_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.s2))
                            NOTIFICATION_KINDS.forEach { kind ->
                                SwitchRow(R.drawable.ic_bell, stringResource(notificationKindText(kind)), null, result.value[kind] ?: true, true) { vm.setSetting(kind, it) }
                            }
                        }
                    }
                }
            }
        }
    }
}
