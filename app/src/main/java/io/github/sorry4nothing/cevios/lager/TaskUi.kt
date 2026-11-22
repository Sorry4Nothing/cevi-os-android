package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.LocalDate

@StringRes
fun statusText(status: TaskStatus): Int = when (status) {
    TaskStatus.OPEN -> R.string.task_status_open
    TaskStatus.RUNNING -> R.string.task_status_running
    TaskStatus.WAITING -> R.string.task_status_waiting
    TaskStatus.DONE -> R.string.task_status_done
    TaskStatus.NEXT_MEETING -> R.string.task_status_next_meeting
}

@StringRes
fun sectionText(section: TaskSection): Int = when (section) {
    TaskSection.OVERDUE -> R.string.task_section_overdue
    TaskSection.SOON -> R.string.task_section_soon
    TaskSection.LATER -> R.string.task_section_later
    TaskSection.NO_DUE -> R.string.task_section_no_due
    TaskSection.DONE -> R.string.task_section_done
}

/** «Name, Frist 15. März»: wer die Aufgabe trägt und bis wann. */
@Composable
fun taskPersonLine(task: CampTask): String {
    val who = task.assignee?.display?.takeIf { it.isNotBlank() } ?: stringResource(R.string.task_unassigned)
    return if (task.due != null) stringResource(R.string.task_person_due, who, formatDay(task.due)) else who
}

/** Die Etiketten einer Aufgabe: überfällig, bald, Status, wartet auf Senden. Zustand steht immer als Wort mit Icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskChips(task: CampTask, today: LocalDate, showStatus: Boolean = true) {
    val overdue = TaskRules.overdueDays(task, today)
    val snoozed = TaskRules.isSnoozed(task, today)
    val soon = TaskRules.isSoon(task, today) && overdue == null
    val showsStatus = showStatus && task.status != TaskStatus.OPEN && task.status != TaskStatus.DONE
    if (overdue == null && !soon && !showsStatus && !task.pending && !snoozed) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        if (overdue != null) StatusChip(ChipKind.NO, pluralStringResource(R.plurals.task_overdue_since, overdue, overdue))
        if (soon) StatusChip(ChipKind.PENDING, stringResource(R.string.task_soon))
        if (snoozed) StatusChip(ChipKind.PENDING, stringResource(R.string.task_snoozed_until, formatDay(task.snoozedUntil)))
        if (showsStatus) StatusChip(ChipKind.AGE_GROUP, stringResource(statusText(task.status)))
        if (task.pending) StatusChip(ChipKind.WAITING, stringResource(R.string.change_waiting))
    }
}

/** Kürzel und Name des Ressorts: Das Kürzel steht in einer kleinen Fläche, der Name daneben. */
@Composable
fun RessortBadge(ressort: TaskRessortRef, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RessortCode(ressort.abbreviation)
        Text(ressort.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RessortCode(code: String) {
    Box(
        Modifier.widthIn(min = 28.dp).heightIn(min = 22.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCorner6).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val RoundedCorner6 = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)

/** Was die Aktionen einer Karte tun. Eine Aktion, die es für die Rolle nicht gibt, ist `null` und fehlt im Menü. */
class TaskActions(
    val onTake: (() -> Unit)? = null,
    val onStatus: ((TaskStatus) -> Unit)? = null,
    val onSnooze: (() -> Unit)? = null,
    val onSelect: (() -> Unit)? = null,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
) {
    val any: Boolean get() = onTake != null || onStatus != null || onSnooze != null || onSelect != null || onMoveUp != null || onMoveDown != null || onDelete != null
}

/**
 * Eine Aufgabe als Karte (Entwurf `cv-task`): Haken, Titel, Ressort, Person und Frist, Etiketten, Menü. Das Antippen öffnet die
 * Aufgabe, der Haken setzt «Erledigt», das Menü zeigt die Aktionen der Rolle (langes Drücken gibt es nicht, alles steht im Menü).
 * In der Auswahl (`selecting`) ersetzt ein Kästchen den Haken. TalkBack liest die Karte als ganzen Satz.
 */
@Composable
fun TaskCard(
    task: CampTask,
    today: LocalDate,
    actions: TaskActions,
    canTick: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onTick: () -> Unit,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
    moveLabel: Boolean = false,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    val personLine = taskPersonLine(task)
    val overdue = TaskRules.overdueDays(task, today)
    val sentence = buildString {
        append(task.title)
        task.ressort?.let { append(", ").append(it.name) }
        append(", ").append(personLine)
        overdue?.let { append(", ").append(pluralStringResource(R.plurals.task_overdue_since, it, it).lowercase()) }
    }
    val statusWord = stringResource(statusText(task.status))
    val waitingWord = stringResource(R.string.change_waiting)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, shape)
            .border(1.dp, if (selected) MaterialTheme.cevi.accent else colors.outlineVariant, shape)
            .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        verticalAlignment = Alignment.Top,
    ) {
        when {
            selecting -> SelectBox(task.title, selected, onToggleSelected)
            canTick -> TickCircle(task.title, task.done, onTick)
            else -> Box(Modifier.size(Spacing.s2))
        }
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = Sizes.touchTarget)
                .focusRing(shape)
                .clip(shape)
                .clickable(onClickLabel = stringResource(R.string.task_open), role = Role.Button) { if (selecting) onToggleSelected() else onOpen() }
                .semantics(mergeDescendants = true) {
                    contentDescription = sentence
                    stateDescription = buildString {
                        append(statusWord)
                        if (task.pending) append(", ").append(waitingWord)
                    }
                }
                .padding(horizontal = Spacing.s1, vertical = Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            task.ressort?.let { RessortBadge(it) }
            Text(personLine, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            TaskChips(task, today, showStatus)
        }
        if (!selecting && actions.any) TaskMenuButton(task, actions, moveLabel)
    }
}

/** Der runde Haken vor der Aufgabe (48 dp Fläche, 24 dp Kreis): offen leer, erledigt grün mit Häkchen. */
@Composable
private fun TickCircle(title: String, done: Boolean, onTick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (done) R.string.task_reopen_label else R.string.task_tick_label, title)
    Box(
        Modifier
            .size(Sizes.touchTarget)
            .focusRing(Pill)
            .clip(Pill)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onTick() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(Modifier.size(24.dp).background(MaterialTheme.cevi.successContainer, Pill), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check_selected), contentDescription = null, tint = MaterialTheme.cevi.onSuccessContainer, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(Modifier.size(24.dp).border(2.dp, colors.outline, Pill))
        }
    }
}

/** Das Kästchen der Auswahl (Entwurf `cv-selcheck`). */
@Composable
fun SelectBox(title: String, selected: Boolean, onToggle: () -> Unit) {
    val label = stringResource(R.string.task_select_label, title)
    Box(
        Modifier
            .size(Sizes.touchTarget)
            .focusRing(Pill)
            .clip(Pill)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        if (selected) {
            Box(Modifier.size(24.dp).background(MaterialTheme.cevi.accent, shape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check_selected), contentDescription = null, tint = MaterialTheme.cevi.onAccent, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.outline, shape))
        }
    }
}

/** Das Menü mit den Aktionen einer Aufgabe. */
@Composable
private fun TaskMenuButton(task: CampTask, actions: TaskActions, moveLabel: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        val description = stringResource(if (moveLabel) R.string.task_move_label else R.string.task_actions_label, task.title)
        Box(
            Modifier
                .size(Sizes.touchTarget)
                .focusRing(Pill)
                .clip(Pill)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_more_horizontal), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable fun item(@StringRes text: Int, action: () -> Unit) = DropdownMenuItem(
                text = { Text(stringResource(text)) },
                onClick = { open = false; action() },
                modifier = Modifier.heightIn(min = Sizes.touchTarget),
            )
            actions.onTake?.let { item(R.string.task_take, it) }
            actions.onStatus?.let { setStatus ->
                TaskStatus.CHOOSABLE.filter { it != task.status }.forEach { status ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_move_to, stringResource(statusText(status)))) },
                        onClick = { open = false; setStatus(status) },
                        modifier = Modifier.heightIn(min = Sizes.touchTarget),
                    )
                }
            }
            actions.onSnooze?.let { item(R.string.task_snooze, it) }
            actions.onMoveUp?.let { item(R.string.task_move_up, it) }
            actions.onMoveDown?.let { item(R.string.task_move_down, it) }
            actions.onSelect?.let { item(R.string.task_select, it) }
            actions.onDelete?.let { item(R.string.task_delete, it) }
        }
    }
}
