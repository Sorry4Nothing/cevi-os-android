package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
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
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

private const val MODE_POLL_MS = 15_000L

/**
 * Das Protokoll eines Höcks (Entwurf L13): Anwesende, Traktanden mit Entscheiden und die Beschlüsse. Ein Protokoll ist eine
 * Verknüpfung auf eine Datei am Ablageort der Abteilung; Cevi OS speichert keinen Protokolltext, deshalb gibt es hier keine
 * Abschnitte zum Schreiben. Änderungen (Titel, Beschlüsse, Entscheide) haben einen Versionsvergleich: Hat sie jemand gleichzeitig
 * geändert, öffnet sich der Konfliktbildschirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinutesScreen(
    vm: MeetingsViewModel,
    meetingId: String,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    names: Map<String, String>,
    onOpenQueue: () -> Unit,
    onShareCalendar: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val minutesState by vm.minutes.collectAsState()
    val decisionsState by vm.decisions.collectAsState()
    val open = rememberLinkOpener()
    var linking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Minutes?>(null) }
    var decisionEdit by remember { mutableStateOf<Decision?>(null) }
    var decisionAdd by remember { mutableStateOf(false) }
    var decisionDelete by remember { mutableStateOf<Decision?>(null) }
    LaunchedEffect(meetingId, camp?.id) {
        vm.openMeeting(meetingId)
        vm.loadMinutes()
        vm.loadDecisions()
    }
    val current = state?.takeIf { it.id == meetingId }
    val meeting = (current?.meeting as? ApiResult.Ok)?.value
    val unknown = stringResource(R.string.mention_unknown)

    CampPage(
        title = stringResource(R.string.minutes_title),
        subtitle = meeting?.let { it.title + ", " + formatMeetingDay(it.date) },
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_meetings), onBack),
    ) {
        when (val result = current?.meeting) {
            null -> CardSkeleton(stringResource(R.string.minutes_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadMeeting(meetingId) })
            is ApiResult.Ok -> {
                val m = result.value
                val minutes = (minutesState as? ApiResult.Ok)?.value?.firstOrNull { it.meetingId == m.id && it.kind == MinutesKind.SESSION }
                val decisions = (decisionsState as? ApiResult.Ok)?.value.orEmpty().filter { it.meetingId == m.id }
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            SectionTitle(stringResource(R.string.minutes_file))
                            when {
                                minutesState == null -> Text(stringResource(R.string.minutes_loading), style = MaterialTheme.typography.bodyMedium)
                                minutesState is ApiResult.Failure -> ErrorState((minutesState as ApiResult.Failure).error, { vm.loadMinutes() })
                                minutes == null -> {
                                    Text(stringResource(R.string.minutes_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (rights.editMinutes) OutlinedPillButton(stringResource(R.string.minutes_link), { linking = true }, Modifier.fillMaxWidth())
                                }
                                else -> InfoCard {
                                    Text(minutes.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        minutes.file?.name ?: stringResource(R.string.minutes_file_gone),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                        minutes.file?.let { file -> OutlinedPillButton(stringResource(R.string.minutes_open), { open(file.link) }) }
                                        if (rights.editMinutes) TextActionButton(stringResource(R.string.minutes_rename), { renaming = minutes })
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            SectionTitle(stringResource(R.string.minutes_present))
                            if (m.present.isEmpty()) {
                                Text(stringResource(R.string.minutes_present_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(m.present.joinToString(", ") { it.display }, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item { SectionTitle(stringResource(R.string.minutes_agenda)) }
                    val items = MeetingRules.sorted(m.agenda)
                    if (items.isEmpty()) item { Text(stringResource(R.string.agenda_empty_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(items, key = { it.id }) { item ->
                        InfoCard {
                            Text(renderMentions(item.title, names, unknown), style = MaterialTheme.typography.titleMedium)
                            item.minutes?.let { Text(pluralStringResource(R.plurals.agenda_minutes, it, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item.text?.takeIf { it.isNotBlank() }?.let { Text(renderMentions(it, names, unknown), style = MaterialTheme.typography.bodyMedium) }
                            item.decision?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.agenda_decision, renderMentions(it, names, unknown)), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    item { SectionTitle(stringResource(R.string.minutes_decisions)) }
                    if (decisions.isEmpty()) item { Text(stringResource(R.string.minutes_decisions_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(decisions, key = { it.id }) { decision ->
                        InfoCard {
                            Text(renderMentions(decision.text, names, unknown), style = MaterialTheme.typography.bodyLarge)
                            if (rights.editMinutes) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                    TextActionButton(stringResource(R.string.decision_edit), { decisionEdit = decision })
                                    TextActionButton(stringResource(R.string.task_delete), { decisionDelete = decision })
                                }
                            }
                        }
                    }
                    if (rights.editMinutes) item { OutlinedPillButton(stringResource(R.string.decision_add), { decisionAdd = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus) }
                    item { OutlinedPillButton(stringResource(R.string.meeting_calendar), onShareCalendar, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar_plus) }
                }
                if (linking) {
                    MinutesLinkDialog(
                        defaultTitle = stringResource(R.string.minutes_default_title, m.title),
                        onConfirm = { title, name, link -> linking = false; vm.linkMinutes(m, title, name, link) {} },
                        onDismiss = { linking = false },
                    )
                }
                renaming?.let { minutes ->
                    TextDialog(
                        title = stringResource(R.string.minutes_rename), label = stringResource(R.string.minutes_field_title), initial = minutes.title, maxChars = 200,
                        confirmText = stringResource(R.string.save),
                        onConfirm = { text ->
                            renaming = null
                            if (text.trim() != minutes.title) vm.changeMinutes(minutes, mapOf("titel" to JsonPrimitive(text.trim())), minutes.title)
                        },
                        onDismiss = { renaming = null },
                    )
                }
                if (decisionAdd) {
                    TextDialog(
                        title = stringResource(R.string.decision_add), label = stringResource(R.string.decision_field_text), initial = "", maxChars = 1000,
                        confirmText = stringResource(R.string.save), multiline = true,
                        onConfirm = { text -> decisionAdd = false; vm.createDecision(text, minutes?.id, m.id) },
                        onDismiss = { decisionAdd = false },
                    )
                }
                decisionEdit?.let { decision ->
                    TextDialog(
                        title = stringResource(R.string.decision_edit), label = stringResource(R.string.decision_field_text), initial = decision.text, maxChars = 1000,
                        confirmText = stringResource(R.string.save), multiline = true,
                        onConfirm = { text ->
                            decisionEdit = null
                            if (text.trim() != decision.text) vm.changeDecision(decision, text, decision.text.lineSequence().first())
                        },
                        onDismiss = { decisionEdit = null },
                    )
                }
                decisionDelete?.let { decision ->
                    ConfirmDeleteDialog(
                        title = stringResource(R.string.decision_delete_title),
                        text = stringResource(R.string.decision_delete_text),
                        onConfirm = { decisionDelete = null; vm.deleteDecision(decision) },
                        onDismiss = { decisionDelete = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun MinutesLinkDialog(defaultTitle: String, onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(defaultTitle) }
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    CeviDialog(
        title = stringResource(R.string.minutes_link),
        onDismiss = onDismiss,
        confirmButton = {
            PrimaryButton(stringResource(R.string.save), {
                if (title.isBlank() || name.isBlank() || !isValidFileLink(link)) showErrors = true else onConfirm(title, name, link)
            })
        },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            DialogText(stringResource(R.string.minutes_link_text))
            LabeledTextField(stringResource(R.string.minutes_field_title), title, { title = it }, 200, Modifier.fillMaxWidth(), hint = if (showErrors && title.isBlank()) stringResource(R.string.error_field_required) else null)
            LabeledTextField(stringResource(R.string.file_field_name), name, { name = it }, 200, Modifier.fillMaxWidth(), hint = if (showErrors && name.isBlank()) stringResource(R.string.error_field_required) else null)
            LabeledTextField(
                stringResource(R.string.file_field_link), link, { link = it }, 2048, Modifier.fillMaxWidth(),
                hint = if (showErrors && !isValidFileLink(link)) stringResource(R.string.error_file_link) else stringResource(R.string.file_field_link_hint),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
        }
    }
}

/**
 * Der Höck-Modus (Entwurf L14): ein Traktandum je Bildschirm, Anwesende zum Abhaken und die Ressort-Runde. Der Stand wird alle
 * 15 Sekunden still neu gelesen, weil die Haken nicht im Änderungsfeed erscheinen. Das Team hat keinen Zugang.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingModeScreen(
    vm: MeetingsViewModel,
    meetingId: String,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    names: Map<String, String>,
    onOpenQueue: () -> Unit,
    onOpenRessort: (String) -> Unit,
    onEnd: () -> Unit,
) {
    val modeState by vm.mode.collectAsState()
    val open = rememberLinkOpener()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var decisionEdit by remember { mutableStateOf<AgendaItem?>(null) }
    LaunchedEffect(meetingId, camp?.id) {
        vm.openMode(meetingId)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(MODE_POLL_MS)
                vm.loadMode(meetingId, silent = true)
            }
        }
    }
    val current = modeState?.takeIf { it.id == meetingId }
    val mode = (current?.mode as? ApiResult.Ok)?.value
    val items = mode?.meeting?.agenda?.let(MeetingRules::sorted).orEmpty()
    val index = (current?.index ?: 0).coerceIn(0, maxOf(items.size - 1, 0))
    val (position, total) = MeetingRules.position(index, items.size)
    val unknown = stringResource(R.string.mention_unknown)

    CampPage(
        title = stringResource(R.string.mode_title),
        subtitle = mode?.let { m -> if (total > 0) stringResource(R.string.mode_subtitle, m.meeting.title, position, total) else m.meeting.title },
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_cross, stringResource(R.string.mode_end), onEnd),
    ) {
        when (val result = current?.mode) {
            null -> CardSkeleton(stringResource(R.string.mode_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadMode(meetingId) })
            is ApiResult.Ok -> LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s6),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                val item = items.getOrNull(index)
                if (item != null) {
                    item {
                        InfoCard {
                            Text(stringResource(R.string.mode_item_of, position, total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(renderMentions(item.title, names, unknown), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            val who = item.responsible.map { it.display } + listOfNotNull(item.minutes?.let { pluralStringResource(R.plurals.agenda_minutes, it, it) })
                            if (who.isNotEmpty()) Text(who.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            item.text?.takeIf { it.isNotBlank() }?.let { Text(renderMentions(it, names, unknown), style = MaterialTheme.typography.bodyLarge) }
                            item.links.forEach { link -> TextActionButton(link.title, { open(link.url) }) }
                            item.decision?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.agenda_decision, renderMentions(it, names, unknown)), style = MaterialTheme.typography.bodyMedium) }
                            if (item.status == AgendaStatus.DONE) StatusChip(ChipKind.YES, stringResource(R.string.agenda_done))
                            if (rights.editMeetings) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                    OutlinedPillButton(stringResource(R.string.mode_decision), { decisionEdit = item })
                                    OutlinedPillButton(
                                        stringResource(if (item.status == AgendaStatus.DONE) R.string.agenda_mark_open else R.string.agenda_mark_done),
                                        {
                                            val status = if (item.status == AgendaStatus.DONE) AgendaStatus.OPEN else AgendaStatus.DONE
                                            vm.changeItem(item, mapOf("status" to JsonPrimitive(status.wire)), item.title)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            if (index > 0) OutlinedPillButton(stringResource(R.string.mode_previous), { vm.goTo(index - 1) }, Modifier.weight(1f), icon = R.drawable.ic_back)
                            if (index < items.lastIndex) PrimaryButton(stringResource(R.string.mode_next), { vm.goTo(index + 1) }, Modifier.weight(1f))
                        }
                    }
                } else {
                    item { Text(stringResource(R.string.agenda_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
                }
                item {
                    val here = MeetingRules.presentCount(result.value.members)
                    SectionTitle(stringResource(R.string.mode_present, here, result.value.members.size))
                }
                items(result.value.members, key = { it.id }) { member ->
                    PresenceRow(member, enabled = rights.editMeetings) { vm.setPresent(meetingId, member.id, !member.present) }
                }
                result.value.ressortRound?.let { round ->
                    item { SectionTitle(stringResource(R.string.mode_ressort_round)) }
                    if (round.isEmpty()) item { Text(stringResource(R.string.mode_ressort_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(round, key = { it.id }) { ressort -> RessortRoundCard(ressort, onOpen = { onOpenRessort(ressort.id) }) }
                }
            }
        }
    }
    decisionEdit?.let { item ->
        TextDialog(
            title = stringResource(R.string.mode_decision), label = stringResource(R.string.agenda_field_decision), initial = item.decision.orEmpty(), maxChars = 1000,
            confirmText = stringResource(R.string.save), multiline = true, allowEmpty = true,
            onConfirm = { text ->
                decisionEdit = null
                if (text.trim() != item.decision.orEmpty()) vm.changeItem(item, mapOf("entscheid" to nullable(text.trim().ifBlank { null })), item.title)
            },
            onDismiss = { decisionEdit = null },
        )
    }
}

/** Eine Zeile mit Haken: Die ganze Zeile ist das Kästchen (mindestens 48 dp), der Zustand steht als Haken und für den Screenreader als Wort. */
@Composable
private fun PresenceRow(member: MemberPresence, enabled: Boolean, onToggle: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val stateText = stringResource(if (member.present) R.string.mode_is_present else R.string.mode_is_absent)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .toggleable(value = member.present, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = stateText; contentDescription = member.display }
            .padding(horizontal = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .androidBorder(member.present),
            contentAlignment = Alignment.Center,
        ) {
            if (member.present) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.cevi.onAccent, modifier = Modifier.size(18.dp))
        }
        Text(member.display, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Modifier.androidBorder(checked: Boolean): Modifier {
    val shape = MaterialTheme.shapes.extraSmall
    return if (checked) {
        this.then(Modifier.background(MaterialTheme.cevi.accent, shape))
    } else {
        this.then(Modifier.border(2.dp, MaterialTheme.colorScheme.outline, shape))
    }
}

@Composable
private fun RessortRoundCard(ressort: RessortRound, onOpen: () -> Unit) {
    val counts = ressort.tasks
    val line = counts?.let { stringResource(R.string.mode_ressort_counts, it.done, it.total) }
    val description = listOfNotNull(ressort.name, ressort.leader?.display?.ifBlank { null }, line, counts?.takeIf { it.overdue > 0 }?.let { pluralStringResource(R.plurals.task_overdue_count, it.overdue, it.overdue) }).joinToString(", ")
    InfoCard(description = description, onClick = onOpen, onClickLabel = stringResource(R.string.mode_ressort_open)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            RessortCode(ressort.abbreviation)
            Text(ressort.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        ressort.leader?.display?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (counts != null) {
            Text(line.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            if (counts.overdue > 0) StatusChip(ChipKind.NO, pluralStringResource(R.plurals.task_overdue_count, counts.overdue, counts.overdue))
        }
    }
}

/**
 * Die Erwähnungen (Entwurf L15): Stellen, an denen jemand dich erwähnt, mit «Als Aufgabe» und «Erledigt». Die Antwort des Servers
 * nennt den Titel des Inhalts, nie den übrigen Text; Marken `@{person:…}` zeigt die App als «@Name».
 */
@Composable
fun MentionsScreen(
    vm: MeetingsViewModel,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    names: Map<String, String>,
    onOpenQueue: () -> Unit,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.mentions.collectAsState()
    val notice by vm.notice.collectAsState()
    val unknown = stringResource(R.string.mention_unknown)
    LaunchedEffect(camp?.id) { vm.loadMentions() }
    CampPage(
        title = stringResource(R.string.mentions_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_start), onBack),
        banners = {
            notice?.takeIf { it.kind == MeetingNotice.Kind.TASK_CREATED }?.let { n ->
                ActionBanner(
                    NoticeKind.INFO, stringResource(R.string.mention_task_created),
                    n.openTaskId?.let { stringResource(R.string.task_open) }, n.openTaskId?.let { id -> { vm.dismissNotice(); onOpenTask(id) } },
                    vm::dismissNotice,
                )
            }
        },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.mentions_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadMentions() })
            is ApiResult.Ok -> if (result.value.isEmpty()) {
                StatePage(R.drawable.ic_info, stringResource(R.string.mentions_empty_title), stringResource(R.string.mentions_empty_text))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    items(result.value, key = { it.id }) { mention ->
                        val text = renderMentions(mention.title, names, unknown)
                        val source = stringResource(mentionSourceText(mention.sourceType), mentionDay(mention.createdAt))
                        InfoCard {
                            Text(source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                                if (mention.taskId != null) {
                                    OutlinedPillButton(stringResource(R.string.task_open), { onOpenTask(mention.taskId) })
                                } else if (rights.mentionToTask) {
                                    OutlinedPillButton(stringResource(R.string.mention_as_task), { vm.mentionToTask(mention) })
                                }
                                if (rights.markMention) {
                                    TextActionButton(stringResource(if (mention.done) R.string.mention_reopen else R.string.mention_mark_done), { vm.setMentionDone(mention, !mention.done) })
                                }
                                if (mention.done) StatusChip(ChipKind.YES, stringResource(R.string.mention_done))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mentionSourceText(type: String): Int = when (type) {
    "traktandum" -> R.string.mention_source_agenda
    "beschluss" -> R.string.mention_source_decision
    "pinnwand" -> R.string.mention_source_post
    else -> R.string.mention_source_task
}

/** «18. November» aus dem Zeitpunkt der Erwähnung (Zeit des Lagers). */
private fun mentionDay(iso: String): String = instantToDay(iso)?.let(::formatDay) ?: iso
