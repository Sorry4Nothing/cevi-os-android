package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.Avatar
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.DashedButton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.Instant
import java.time.ZoneId

private val Zurich = ZoneId.of("Europe/Zurich")

private enum class DetailEdit { TITLE, NOTE, DUE, PERSON, SNOOZE, CHECKPOINT, LINK, DELETE, ACTIONS }

/**
 * Eine Aufgabe (Entwurf L7): Status, Personen, Frist, Ressort, Notiz, Checkliste, Links und Verlauf. Wer nur den Status setzen darf
 * (Team), sieht keine Knöpfe zum Bearbeiten; wer nur liest, sieht den Status als Etikett. «Frühere Fassungen» gibt es nicht: Der
 * Server speichert keine Inhalte früherer Stände.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    vm: TasksViewModel,
    taskId: String,
    camp: Camp?,
    rights: TaskRights,
    queue: QueueState,
    online: Boolean,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenRessort: (String) -> Unit,
) {
    val state by vm.tasks.collectAsState()
    val detail by vm.detail.collectAsState()
    val error by vm.error.collectAsState()
    val undo by vm.undo.collectAsState()
    val members by vm.members.collectAsState()
    val people = remember(state, members) { TaskRules.pickable((state as? ApiResult.Ok)?.value.orEmpty(), vm.self, members) }
    val today = remember { todayInCamp() }
    var edit by remember { mutableStateOf<DetailEdit?>(null) }
    var deletingPoint by remember { mutableStateOf<Checkpoint?>(null) }
    var deletingLink by remember { mutableStateOf<TaskLink?>(null) }
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.task_back)) { onBack() }

    val task = (state as? ApiResult.Ok)?.value?.firstOrNull { it.id == taskId }
    if (task == null) {
        Column(Modifier.fillMaxSize()) {
            Header(title = stringResource(R.string.task_title_loading), back = back, focusOnTitle = true)
            when (val result = state) {
                null -> CardSkeleton(stringResource(R.string.task_loading), count = 2)
                is ApiResult.Failure -> ErrorState(result.error, vm::retryTasks)
                is ApiResult.Ok -> ErrorState(ApiError.NOT_FOUND, vm::retryTasks, onToPrograms = onBack, notFoundText = R.string.task_not_found_text, homeLabel = R.string.task_back)
            }
        }
        return
    }
    val details = (detail?.takeIf { it.taskId == taskId })?.details
    val history = (detail?.takeIf { it.taskId == taskId })?.history
    val canEdit = rights.edit && task.queuedId == null
    val canTick = rights.setStatus && task.queuedId == null
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        Header(
            title = task.title,
            subtitle = listOfNotNull(task.ressort?.name, task.due?.let { stringResource(R.string.task_due_short, formatDay(it)) }).joinToString(", ").ifBlank { null },
            back = back,
            extraAction = if (canEdit) {
                HeaderButton(
                    if (task.starred) R.drawable.ic_star_selected else R.drawable.ic_star,
                    stringResource(if (task.starred) R.string.task_unstar else R.string.task_star),
                ) { vm.setStar(task, !task.starred) }
            } else null,
            action = if (canEdit || canTick) HeaderButton(R.drawable.ic_more_horizontal, stringResource(R.string.task_more_actions)) { edit = DetailEdit.ACTIONS } else null,
            focusOnTitle = true,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s3).padding(bottom = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            QueueBanner(queue, online, onOpenQueue)
            if (camp?.readOnly == true) {
                NoticeBanner(
                    NoticeKind.INFO,
                    stringResource(R.string.camp_closed_title) + ". " + stringResource(R.string.camp_closed_text),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            undo?.let { notice -> ActionBanner(NoticeKind.INFO, stringResource(R.string.undo_done), stringResource(R.string.undo), notice.undo, vm::dismissUndo) }
            error?.let { failure ->
                ActionBanner(
                    NoticeKind.ERROR, stringResource(if (failure.code == TasksViewModel.CREATING_CODE) R.string.task_error_creating else R.string.task_error),
                    failure.retry?.let { stringResource(R.string.retry) }, failure.retry?.let { retry -> { vm.dismissError(); retry() } },
                    vm::dismissError,
                )
            }
            TaskChips(task, today, showStatus = false)

            // Status: wer ihn setzen darf, wählt; sonst steht er als Etikett da.
            if (canTick) {
                Row(
                    Modifier.fillMaxWidth().pickGroup(stringResource(R.string.group_task_status)),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        TaskStatus.CHOOSABLE.forEach { status ->
                            PickPill(stringResource(statusText(status)), task.status == status, { vm.setStatus(task, status) })
                        }
                    }
                }
                if (task.status == TaskStatus.NEXT_MEETING) Text(stringResource(R.string.task_status_next_meeting_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.task_status_is, stringResource(statusText(task.status))), style = MaterialTheme.typography.bodyLarge)
            }

            // Personen, Frist, Ressort, Wiederholung
            Section(R.string.task_people) {
                val lead = task.assignee
                if (lead == null) {
                    Text(stringResource(R.string.task_unassigned), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    PersonLine(lead.display, stringResource(if (lead.automatic) R.string.task_role_ressort_lead else R.string.task_role_lead), lead.imageUrl)
                }
                task.supporters.forEach { PersonLine(it.display, stringResource(R.string.task_role_support), it.imageUrl) }
                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        if (task.assignee?.id != vm.self.id) TextActionButton(stringResource(R.string.task_take), { vm.take(task) })
                        TextActionButton(stringResource(R.string.task_change_person), { edit = DetailEdit.PERSON })
                    }
                }
            }
            FactRow(R.string.task_due, task.due?.let { formatDayLong(it) } ?: stringResource(R.string.task_due_none), if (canEdit) ({ edit = DetailEdit.DUE }) else null)
            task.ressort?.let { ressort ->
                FactRow(
                    R.string.task_ressort, ressort.abbreviation + " " + ressort.name,
                    if (rights.readRessorts) ({ onOpenRessort(ressort.id) }) else null,
                    leading = { RessortCode(ressort.abbreviation) },
                )
            }
            task.repeat?.let { FactRow(R.string.task_repeat, stringResource(repeatText(it)), null) }
            task.snoozedUntil?.takeIf { TaskRules.isSnoozed(task, today) }?.let { FactRow(R.string.task_snooze_title, formatDayLong(it), if (canEdit) ({ edit = DetailEdit.SNOOZE }) else null) }

            Section(R.string.task_note) {
                val note = task.note
                if (note.isNullOrBlank()) {
                    if (canEdit) TextActionButton(stringResource(R.string.task_note_add), { edit = DetailEdit.NOTE })
                    else Text(stringResource(R.string.task_note_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(note, style = MaterialTheme.typography.bodyLarge)
                    if (canEdit) TextActionButton(stringResource(R.string.task_note_edit), { edit = DetailEdit.NOTE })
                }
            }

            // Checkliste (das Team darf sie nicht lesen)
            if (rights.readAll) when (details) {
                null -> CardSkeleton(stringResource(R.string.task_loading), count = 1)
                is ApiResult.Failure -> ErrorState(details.error, vm::retryDetail, Modifier.heightIn(max = 320.dp))
                is ApiResult.Ok -> {
                    val list = details.value.checklist.sortedBy { it.order }
                    Section(R.string.task_checklist, headingExtra = list.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.task_checklist_progress, it.count { c -> c.done }, it.size) }) {
                        list.forEach { point ->
                            CheckpointRow(
                                point, canTick, canEdit,
                                onToggle = { vm.toggleCheckpoint(task, point) },
                                onUp = if (canEdit && list.first().id != point.id) ({ vm.moveCheckpoint(task, list, point.id, -1) }) else null,
                                onDown = if (canEdit && list.last().id != point.id) ({ vm.moveCheckpoint(task, list, point.id, 1) }) else null,
                                onDelete = if (canEdit) ({ deletingPoint = point }) else null,
                            )
                        }
                        if (canEdit) DashedButton(stringResource(R.string.task_checkpoint_add), { edit = DetailEdit.CHECKPOINT }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                    }
                    if (details.value.links.isNotEmpty() || canEdit) {
                        Section(R.string.task_links) {
                            details.value.links.forEach { link ->
                                LinkRow(link, onOpen = { openLink(uriHandler, link.url) }, onDelete = if (canEdit) ({ deletingLink = link }) else null)
                            }
                            if (canEdit) DashedButton(stringResource(R.string.task_link_add), { edit = DetailEdit.LINK }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                        }
                    }
                }
            }

            // Verlauf
            (history as? ApiResult.Ok)?.value?.let { Section(R.string.task_history) { HistoryList(it) } }
        }
        // Die eine Hauptaktion: erledigen (oder wieder öffnen).
        if (canTick) {
            Column(Modifier.padding(horizontal = Spacing.s3).padding(bottom = Spacing.s3)) {
                if (task.done) OutlinedPillButton(stringResource(R.string.task_reopen), { vm.setStatus(task, TaskStatus.OPEN) }, Modifier.fillMaxWidth())
                else PrimaryButton(stringResource(R.string.task_mark_done), { vm.setStatus(task, TaskStatus.DONE) }, Modifier.fillMaxWidth())
            }
        }
    }

    deletingPoint?.let { point ->
        ConfirmDeleteDialog(
            stringResource(R.string.task_checkpoint_delete_title), stringResource(R.string.task_checkpoint_delete_text, point.text),
            onConfirm = { vm.deleteCheckpoint(task, point); deletingPoint = null }, onDismiss = { deletingPoint = null },
        )
    }
    deletingLink?.let { link ->
        ConfirmDeleteDialog(
            stringResource(R.string.task_link_delete_title), stringResource(R.string.task_link_delete_text, link.title),
            onConfirm = { vm.deleteLink(task, link); deletingLink = null }, onDismiss = { deletingLink = null },
        )
    }
    when (edit) {
        DetailEdit.TITLE -> TextDialog(stringResource(R.string.task_rename_title), stringResource(R.string.task_title_label), task.title, 200, stringResource(R.string.save), { vm.rename(task, it); edit = null }, { edit = null })
        DetailEdit.NOTE -> TextDialog(stringResource(R.string.task_note), stringResource(R.string.task_note), task.note.orEmpty(), 5000, stringResource(R.string.save), { vm.setNote(task, it); edit = null }, { edit = null }, multiline = true, allowEmpty = true)
        DetailEdit.DUE -> DateDialog(task.due, { vm.setDue(task, it); edit = null }, { edit = null })
        DetailEdit.SNOOZE -> DateDialog(task.snoozedUntil, { vm.snooze(task, it); edit = null }, { edit = null }, R.string.task_snooze_clear)
        DetailEdit.PERSON -> ChoiceSheet(
            title = stringResource(R.string.task_change_person),
            choices = listOf(Choice(null, stringResource(R.string.task_nobody))) + people.map { Choice(it.id, it.display) },
            initial = task.assigneeId,
            onApply = { vm.setAssignee(task, it); edit = null },
            onDismiss = { edit = null },
        )
        DetailEdit.CHECKPOINT -> TextDialog(stringResource(R.string.task_checkpoint_add), stringResource(R.string.task_checkpoint_label), "", 500, stringResource(R.string.task_add), { vm.addCheckpoint(task, it); edit = null }, { edit = null })
        DetailEdit.LINK -> LinkDialog({ title, url -> vm.addLink(task, title, url); edit = null }, { edit = null })
        DetailEdit.DELETE -> ConfirmDeleteDialog(
            pluralStringResource(R.plurals.task_delete_title, 1, 1), stringResource(R.string.task_delete_text),
            onConfirm = { vm.delete(setOf(task.id)); edit = null; onBack() }, onDismiss = { edit = null },
        )
        DetailEdit.ACTIONS -> ModalBottomSheet(onDismissRequest = { edit = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(stringResource(R.string.task_more_actions), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }.padding(bottom = Spacing.s2))
                if (canEdit) {
                    SheetAction(R.string.task_rename) { edit = DetailEdit.TITLE }
                    if (task.assignee?.id != vm.self.id && !task.done) SheetAction(R.string.task_take) { vm.take(task); edit = null }
                    if (!task.done) SheetAction(R.string.task_snooze) { edit = DetailEdit.SNOOZE }
                    SheetAction(R.string.task_delete) { edit = DetailEdit.DELETE }
                } else if (canTick) {
                    SheetAction(if (task.done) R.string.task_reopen else R.string.task_mark_done) { vm.toggleDone(task); edit = null }
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun SheetAction(text: Int, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).focusRing(shape).clip(shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) { Text(stringResource(text), style = MaterialTheme.typography.bodyLarge) }
}

@androidx.annotation.StringRes
private fun repeatText(wire: String) = when (wire) {
    "w1" -> R.string.task_repeat_week
    "w2" -> R.string.task_repeat_two_weeks
    else -> R.string.task_repeat_month
}

@Composable
private fun Section(title: Int, headingExtra: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        val text = stringResource(title) + (headingExtra?.let { ", $it" } ?: "")
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun PersonLine(name: String, role: String, imageUrl: String?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Avatar(name, size = 32.dp, imageUrl = imageUrl)
        Text(stringResource(R.string.task_person_role, name, role), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Eine Angabe mit Wort links oben und Wert; bedienbar, wenn `onClick` gesetzt ist (dann steht sie als Knopf da). */
@Composable
private fun FactRow(label: Int, value: String, onClick: (() -> Unit)?, leading: (@Composable () -> Unit)? = null) {
    val shape = MaterialTheme.shapes.small
    val labelText = stringResource(label)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "$labelText, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(labelText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (onClick != null) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckpointRow(
    point: Checkpoint,
    canTick: Boolean,
    canEdit: Boolean,
    onToggle: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val stateText = stringResource(if (point.done) R.string.checkpoint_done else R.string.checkpoint_open)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(if (point.done) R.string.task_reopen_label else R.string.task_tick_label, point.text)
        Box(
            Modifier
                .size(Sizes.touchTarget)
                .focusRing(Pill)
                .clip(Pill)
                .then(if (canTick) Modifier.toggleable(value = point.done, role = Role.Checkbox, onValueChange = { onToggle() }) else Modifier)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (point.done) {
                Box(Modifier.size(24.dp).background(MaterialTheme.cevi.successContainer, Pill), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check_selected), contentDescription = null, tint = MaterialTheme.cevi.onSuccessContainer, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.outline, Pill))
            }
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${point.text}, $stateText" }) {
            Text(point.text, style = MaterialTheme.typography.bodyLarge)
            point.doneBy?.display?.takeIf { point.done && it.isNotBlank() }?.let {
                Text(stringResource(R.string.checkpoint_done_by, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canEdit) {
            Box {
                val description = stringResource(R.string.checkpoint_actions, point.text)
                Box(
                    Modifier.size(Sizes.touchTarget).focusRing(Pill).clip(Pill).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_more_horizontal), contentDescription = null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onUp?.let { DropdownMenuItem({ Text(stringResource(R.string.task_move_up)) }, { menu = false; it() }, Modifier.heightIn(min = Sizes.touchTarget)) }
                    onDown?.let { DropdownMenuItem({ Text(stringResource(R.string.task_move_down)) }, { menu = false; it() }, Modifier.heightIn(min = Sizes.touchTarget)) }
                    onDelete?.let { DropdownMenuItem({ Text(stringResource(R.string.checkpoint_delete)) }, { menu = false; it() }, Modifier.heightIn(min = Sizes.touchTarget)) }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(link: TaskLink, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    val shape = MaterialTheme.shapes.small
    val host = remember(link.url) { link.url.removePrefix("https://").substringBefore('/') }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = Sizes.touchTarget)
                .focusRing(shape)
                .clip(shape)
                .clickable(onClickLabel = stringResource(R.string.task_link_open), role = Role.Button, onClick = onOpen)
                .padding(vertical = Spacing.s1),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(link.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.cevi.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onDelete?.let {
            val description = stringResource(R.string.task_link_delete, link.title)
            Box(
                Modifier.size(Sizes.touchTarget).focusRing(Pill).clip(Pill).clickable(role = Role.Button, onClick = it).semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_cross), contentDescription = null) }
        }
    }
}

@Composable
private fun LinkDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    val valid = title.isNotBlank() && url.trim().startsWith("https://") && url.trim().length > "https://".length
    io.github.sorry4nothing.cevios.ui.CeviDialog(
        title = stringResource(R.string.task_link_add),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.task_add), { onConfirm(title, url) }, enabled = valid) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            io.github.sorry4nothing.cevios.ui.LabeledTextField(stringResource(R.string.task_link_title_label), title, { title = it }, 200, Modifier.fillMaxWidth())
            io.github.sorry4nothing.cevios.ui.LabeledTextField(
                stringResource(R.string.task_link_url_label), url, { url = it }, 2000, Modifier.fillMaxWidth(),
                hint = stringResource(R.string.task_link_url_hint), keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
        }
    }
}

/** Verlauf: wer die Aufgabe angelegt und zuletzt geändert hat, dazu die letzten Einträge des Feeds (nur Art und Zeit, nie Inhalte). */
@Composable
private fun HistoryList(history: TaskHistory) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        HistoryLine(stringResource(R.string.history_created, history.createdBy?.display?.ifBlank { null } ?: stringResource(R.string.history_unknown)), history.createdAt)
        if (history.changedAt != history.createdAt) {
            HistoryLine(stringResource(R.string.history_changed, history.changedBy?.display?.ifBlank { null } ?: stringResource(R.string.history_unknown)), history.changedAt)
        }
        history.entries.take(HISTORY_ENTRIES).forEach { entry ->
            HistoryLine(stringResource(historyText(entry.type, entry.kind)), entry.time)
        }
    }
}

private const val HISTORY_ENTRIES = 6

@androidx.annotation.StringRes
private fun historyText(type: String, kind: String): Int = when (type) {
    "aufgabe_checkpunkt" -> when (kind) {
        "neu" -> R.string.history_checkpoint_new
        "geloescht" -> R.string.history_checkpoint_deleted
        else -> R.string.history_checkpoint_changed
    }
    "aufgabe_link" -> when (kind) {
        "neu" -> R.string.history_link_new
        "geloescht" -> R.string.history_link_deleted
        else -> R.string.history_link_changed
    }
    else -> when (kind) {
        "neu" -> R.string.history_task_new
        "geloescht" -> R.string.history_task_deleted
        else -> R.string.history_task_changed
    }
}

@Composable
private fun HistoryLine(text: String, time: String) {
    val whenText = historyTime(time)
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** «15. März, 14:30» in der Zeit des Lagers; ein unlesbarer Zeitpunkt steht unverändert da. */
private fun historyTime(iso: String): String {
    val moment = runCatching { Instant.parse(iso).atZone(Zurich) }.getOrNull() ?: return iso
    return formatDay(moment.toLocalDate().toString()) + ", " + "%02d:%02d".format(moment.hour, moment.minute)
}
