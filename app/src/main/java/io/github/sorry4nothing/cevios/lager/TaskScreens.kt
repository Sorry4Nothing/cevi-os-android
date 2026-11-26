package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import java.time.LocalDate

/** Ab dieser Breite stehen die Spalten des Kanban nebeneinander (Tablet im Hochformat, Entwurf T6); schmaler gibt es Reiter pro Status. */
private val KanbanColumnsBreakpoint = 720.dp

/**
 * Die Aufgabenliste (Entwurf L5, L9, T6). Ansichten: Liste mit Abschnitten, Meine, Personen, Ressort, Kanban. Alles, was die
 * Rolle nicht darf, fehlt: kein «Neue Aufgabe», kein Löschen, keine Auswahl für Lesende.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(
    vm: TasksViewModel,
    camp: Camp?,
    rights: TaskRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val state by vm.tasks.collectAsState()
    val view by vm.view.collectAsState()
    val mineSort by vm.mineSort.collectAsState()
    val onlyOverdue by vm.onlyOverdue.collectAsState()
    val selection by vm.selection.collectAsState()
    val undo by vm.undo.collectAsState()
    val error by vm.error.collectAsState()
    val ressorts by vm.ressorts.collectAsState()
    val me = vm.self
    val today = remember { todayInCamp() }

    var filterOpen by remember { mutableStateOf(false) }
    var snoozeFor by remember { mutableStateOf<CampTask?>(null) }
    var deleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var bulk by remember { mutableStateOf<Bulk?>(null) }
    var doneExpanded by remember { mutableStateOf(false) }
    var kanbanStatus by remember { mutableStateOf(TaskStatus.OPEN) }

    Column(Modifier.fillMaxSize()) {
        Header(
            title = stringResource(R.string.tasks_title),
            action = HeaderButton(R.drawable.ic_settings, stringResource(R.string.tasks_filter_label)) { filterOpen = true },
            focusOnTitle = true,
        )
        Column(Modifier.padding(horizontal = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            QueueBanner(queue, online, onOpenQueue)
            if (camp?.readOnly == true) {
                NoticeBanner(
                    NoticeKind.INFO,
                    stringResource(R.string.camp_closed_title) + ". " + stringResource(R.string.camp_closed_text),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            undo?.let { notice ->
                ActionBanner(NoticeKind.INFO, undoText(notice), stringResource(R.string.undo), notice.undo, vm::dismissUndo)
            }
            error?.let { failure ->
                ActionBanner(
                    NoticeKind.ERROR, stringResource(if (failure.code == TasksViewModel.CREATING_CODE) R.string.task_error_creating else R.string.task_error),
                    failure.retry?.let { stringResource(R.string.retry) }, failure.retry?.let { retry -> { vm.dismissError(); retry() } },
                    vm::dismissError,
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (val result = state) {
                null -> CardSkeleton(stringResource(R.string.tasks_loading))
                is ApiResult.Failure -> ErrorState(result.error, vm::retryTasks)
                is ApiResult.Ok -> {
                    val all = result.value
                    if (all.isEmpty()) {
                        StatePage(R.drawable.ic_check, stringResource(R.string.tasks_empty_title), stringResource(R.string.tasks_empty_text)) {
                            if (rights.edit) PrimaryButton(stringResource(R.string.tasks_new), vm::openQuick, icon = R.drawable.ic_plus)
                        }
                    } else {
                        val visible = if (onlyOverdue) all.filter { TaskRules.isOverdue(it, today) } else all
                        TasksBody(
                            vm, rights, me, visible, all, today, view, mineSort, selection, doneExpanded, { doneExpanded = !doneExpanded },
                            kanbanStatus, { kanbanStatus = it }, onOpenTask, { snoozeFor = it }, { deleteIds = it },
                            bottomPadding = if (selection != null || rights.edit) 96.dp else Spacing.s4,
                        )
                    }
                    val selected = selection
                    if (selected != null) {
                        SelectionBar(
                            count = selected.size,
                            rights = rights,
                            onEnd = vm::endSelecting,
                            onBulk = { kind ->
                                if (kind == Bulk.RESSORT) vm.loadRessortsIfNeeded()
                                bulk = kind
                            },
                            onDelete = { deleteIds = selected },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else if (rights.edit) {
                        PrimaryButton(
                            stringResource(R.string.tasks_new), vm::openQuick,
                            Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus,
                        )
                    }
                }
            }
        }
    }

    if (filterOpen) {
        FilterDialog(
            view = view, mineSort = mineSort, onlyOverdue = onlyOverdue,
            onSort = vm::setMineSort, onOnlyOverdue = vm::setOnlyOverdue, onDismiss = { filterOpen = false },
        )
    }
    snoozeFor?.let { task ->
        DateDialog(
            initial = task.snoozedUntil,
            onPick = { vm.snooze(task, it); snoozeFor = null },
            onDismiss = { snoozeFor = null },
            clearText = R.string.task_snooze_clear,
        )
    }
    deleteIds?.let { ids ->
        ConfirmDeleteDialog(
            title = pluralStringResource(R.plurals.task_delete_title, ids.size, ids.size),
            text = stringResource(R.string.task_delete_text),
            onConfirm = { vm.delete(ids); deleteIds = null },
            onDismiss = { deleteIds = null },
        )
    }
    val chosen = selection.orEmpty()
    val allTasks = (state as? ApiResult.Ok)?.value.orEmpty()
    when (bulk) {
        Bulk.STATUS -> ChoiceSheet(
            title = pluralStringResource(R.plurals.task_bulk_status_title, chosen.size, chosen.size),
            choices = TaskStatus.CHOOSABLE.map { Choice(it.wire, stringResource(statusText(it))) },
            initial = null,
            onApply = { wire -> bulk = null; wire?.let { vm.setStatusAll(chosen, TaskStatus.from(it)) } },
            onDismiss = { bulk = null },
        )
        Bulk.PERSON -> ChoiceSheet(
            title = pluralStringResource(R.plurals.task_bulk_person_title, chosen.size, chosen.size),
            choices = listOf(Choice(null, stringResource(R.string.task_nobody))) + TaskRules.pickable(allTasks, me, vm.members.collectAsState().value).map { Choice(it.id, it.display) },
            initial = null,
            onApply = { id -> bulk = null; vm.setAssigneeAll(chosen, id) },
            onDismiss = { bulk = null },
        )
        Bulk.RESSORT -> ChoiceSheet(
            title = pluralStringResource(R.plurals.task_bulk_ressort_title, chosen.size, chosen.size),
            choices = listOf(Choice(null, stringResource(R.string.task_no_ressort))) +
                (ressorts as? ApiResult.Ok)?.value.orEmpty().map { Choice(it.id, it.abbreviation + " " + it.name) },
            initial = null,
            onApply = { id -> bulk = null; vm.setRessortAll(chosen, id) },
            onDismiss = { bulk = null },
            emptyText = stringResource(R.string.task_ressorts_loading),
        )
        Bulk.DUE -> DateDialog(
            initial = null,
            onPick = { date -> bulk = null; vm.setDueAll(chosen, date) },
            onDismiss = { bulk = null },
        )
        null -> Unit
    }
}

private enum class Bulk { STATUS, PERSON, DUE, RESSORT }

@Composable
private fun undoText(notice: UndoNotice): String = when (notice.kind) {
    UndoKind.DONE -> stringResource(R.string.undo_done)
    UndoKind.STATUS -> pluralStringResource(R.plurals.undo_status, notice.count, notice.count, notice.status?.let { stringResource(statusText(it)) }.orEmpty())
    UndoKind.ASSIGNED -> pluralStringResource(R.plurals.undo_assigned, notice.count, notice.count)
    UndoKind.DUE -> pluralStringResource(R.plurals.undo_due, notice.count, notice.count)
    UndoKind.RESSORT -> pluralStringResource(R.plurals.undo_ressort, notice.count, notice.count)
    UndoKind.DELETED -> pluralStringResource(R.plurals.undo_deleted, notice.count, notice.count)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasksBody(
    vm: TasksViewModel,
    rights: TaskRights,
    me: TaskPerson,
    visible: List<CampTask>,
    all: List<CampTask>,
    today: LocalDate,
    view: TaskView,
    mineSort: MineSort,
    selection: Set<String>?,
    doneExpanded: Boolean,
    onToggleDone: () -> Unit,
    kanbanStatus: TaskStatus,
    onKanbanStatus: (TaskStatus) -> Unit,
    onOpenTask: (String) -> Unit,
    onSnooze: (CampTask) -> Unit,
    onDelete: (Set<String>) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val selecting = selection != null
    fun actionsFor(task: CampTask, order: List<CampTask>?): TaskActions {
        val movable = rights.edit && order != null && order.none { it.queuedId != null } && task.queuedId == null
        return TaskActions(
            onTake = if (rights.edit && !task.done && task.assignee?.id != me.id) ({ vm.take(task) }) else null,
            onStatus = if (rights.setStatus) ({ status -> vm.setStatus(task, status) }) else null,
            onSnooze = if (rights.edit && !task.done) ({ onSnooze(task) }) else null,
            onSelect = if (rights.setStatus) ({ vm.startSelecting(task.id) }) else null,
            onMoveUp = if (movable && order!!.firstOrNull()?.id != task.id) ({ vm.move(order, task.id, -1) }) else null,
            onMoveDown = if (movable && order!!.lastOrNull()?.id != task.id) ({ vm.move(order, task.id, 1) }) else null,
            onDelete = if (rights.edit) ({ onDelete(setOf(task.id)) }) else null,
        )
    }

    @Composable
    fun Card(task: CampTask, order: List<CampTask>? = null, showStatus: Boolean = true, moveLabel: Boolean = false) {
        TaskCard(
            task = task, today = today, actions = actionsFor(task, order),
            canTick = rights.setStatus && task.queuedId == null,
            selecting = selecting, selected = selection?.contains(task.id) == true,
            onOpen = { onOpenTask(task.id) }, onTick = { vm.toggleDone(task) }, onToggleSelected = { vm.toggleSelected(task.id) },
            showStatus = showStatus, moveLabel = moveLabel,
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (!selecting) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                TaskView.entries.forEach { entry ->
                    PickPill(stringResource(viewText(entry)), selected = view == entry, onClick = { vm.setView(entry) }, role = Role.Tab)
                }
            }
        }
        val contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, top = Spacing.s2, bottom = bottomPadding)
        val spacing = Arrangement.spacedBy(Spacing.s2)
        when (view) {
            TaskView.LIST -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                TaskRules.sections(visible, today).forEach { (section, tasks) ->
                    val collapsed = section == TaskSection.DONE && !doneExpanded
                    item(key = "h-$section") {
                        if (section == TaskSection.DONE) DoneHeader(tasks.size, !collapsed, onToggleDone) else SectionTitle(stringResource(sectionText(section)))
                    }
                    if (!collapsed) items(tasks, key = { it.id }) { Card(it) }
                }
                if (visible.isEmpty()) item(key = "none") { NoMatch() }
            }
            TaskView.MINE -> {
                val mine = TaskRules.mine(visible, me.id, mineSort, today)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = spacing) {
                    if (mine.isEmpty()) item(key = "none") { NoMatch(R.string.tasks_mine_empty) }
                    val order = if (mineSort == MineSort.ORDER) mine.filterNot { it.done } else null
                    items(mine, key = { it.id }) { Card(it, order) }
                }
            }
            TaskView.PEOPLE -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = spacing) {
                TaskRules.byPerson(visible).forEach { (person, tasks) ->
                    item(key = "p-${person?.id}") { GroupTitle(person?.display ?: stringResource(R.string.task_no_person), tasks.size) }
                    items(tasks, key = { it.id }) { Card(it) }
                }
            }
            TaskView.RESSORT -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = spacing) {
                TaskRules.byRessort(visible).forEach { (ressort, tasks) ->
                    item(key = "r-${ressort?.id}") { GroupTitle(ressort?.let { it.abbreviation + " " + it.name } ?: stringResource(R.string.task_no_ressort), tasks.size) }
                    items(tasks, key = { it.id }) { Card(it) }
                }
            }
            TaskView.KANBAN -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = TaskRules.columns(visible)
                if (maxWidth >= KanbanColumnsBreakpoint) {
                    Row(
                        Modifier.fillMaxSize().padding(start = Spacing.s3, end = Spacing.s3, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                    ) {
                        columns.forEach { (status, tasks) ->
                            KanbanColumn(status, tasks, Modifier.weight(1f)) { task -> Card(task, tasks, showStatus = false, moveLabel = true) }
                        }
                    }
                } else {
                    val selected = if (columns.any { it.first == kanbanStatus }) kanbanStatus else TaskStatus.OPEN
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.s3, vertical = Spacing.s1).selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                        ) {
                            columns.forEach { (status, tasks) ->
                                PickPill(stringResource(R.string.kanban_tab, stringResource(statusText(status)), tasks.size), status == selected, { onKanbanStatus(status) }, role = Role.Tab)
                            }
                        }
                        val tasks = columns.firstOrNull { it.first == selected }?.second.orEmpty()
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = spacing) {
                            if (tasks.isEmpty()) item(key = "none") { KanbanEmpty() }
                            items(tasks, key = { it.id }) { Card(it, tasks, showStatus = false, moveLabel = true) }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.StringRes
private fun viewText(view: TaskView) = when (view) {
    TaskView.LIST -> R.string.view_list
    TaskView.MINE -> R.string.view_mine
    TaskView.PEOPLE -> R.string.view_people
    TaskView.RESSORT -> R.string.view_ressort
    TaskView.KANBAN -> R.string.view_kanban
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.s1).semantics { heading() })
}

@Composable
private fun GroupTitle(text: String, count: Int) {
    Text(
        stringResource(R.string.task_group_title, text, count),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.s1, top = Spacing.s2).semantics { heading() },
    )
}

/** «Erledigt, 12 Aufgaben, eingeklappt»: Die erledigten Aufgaben klappen auf und zu. */
@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = Spacing.s1)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.task_section_done), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pluralStringResource(R.plurals.task_done_count, count, count, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right),
            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoMatch(text: Int = R.string.tasks_no_match) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.s4))
}

@Composable
private fun KanbanEmpty() {
    Column(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.kanban_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.kanban_empty_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Eine Spalte des Kanban auf dem Tablet. TalkBack liest sie als «Offen, 2 Aufgaben» und danach die Karten wie eine Liste. */
@Composable
private fun KanbanColumn(status: TaskStatus, tasks: List<CampTask>, modifier: Modifier, card: @Composable (CampTask) -> Unit) {
    val name = stringResource(statusText(status))
    val label = pluralStringResource(R.plurals.kanban_column_label, tasks.size, name, tasks.size)
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(Spacing.s2)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(tasks.size.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            if (tasks.isEmpty()) item(key = "none") { KanbanEmpty() }
            items(tasks, key = { it.id }) { card(it) }
        }
    }
}

/** Die Leiste der Mehrfachauswahl (Entwurf L9). Jede Aktion gibt es nur, wenn die Rolle sie hat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(
    count: Int,
    rights: TaskRights,
    onEnd: () -> Unit,
    onBulk: (Bulk) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countText = pluralStringResource(R.plurals.task_selected, count, count)
    Surface(
        modifier.fillMaxWidth().padding(horizontal = Spacing.s3).padding(bottom = Spacing.s3),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Column(Modifier.padding(horizontal = Spacing.s3, vertical = Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                val endLabel = stringResource(R.string.task_selection_end)
                Box(
                    Modifier.size(Sizes.touchTarget).focusRing(Pill).clip(Pill).clickable(role = Role.Button, onClick = onEnd).semantics { contentDescription = endLabel },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_cross), contentDescription = null) }
                Text(countText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                if (rights.setStatus) BarButton(R.string.task_bulk_status) { onBulk(Bulk.STATUS) }
                if (rights.edit) {
                    BarButton(R.string.task_bulk_person) { onBulk(Bulk.PERSON) }
                    BarButton(R.string.task_bulk_due) { onBulk(Bulk.DUE) }
                    BarButton(R.string.task_bulk_ressort) { onBulk(Bulk.RESSORT) }
                    BarButton(R.string.task_delete, onDelete)
                }
            }
        }
    }
}

@Composable
private fun BarButton(text: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Sizes.touchTarget)
            .focusRing(Pill)
            .clip(Pill)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface), Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(text), style = MaterialTheme.typography.labelLarge) }
}

/** «Filter und Ansicht»: Sortierung von «Meine» und «Nur überfällige». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(
    view: TaskView,
    mineSort: MineSort,
    onlyOverdue: Boolean,
    onSort: (MineSort) -> Unit,
    onOnlyOverdue: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    CeviDialog(
        title = stringResource(R.string.tasks_filter_title),
        onDismiss = onDismiss,
        confirmButton = { TextActionButton(stringResource(R.string.done), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            if (view == TaskView.MINE) {
                Text(stringResource(R.string.tasks_sort_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                FlowRow(Modifier.pickGroup(stringResource(R.string.tasks_sort_title)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    PickPill(stringResource(R.string.sort_due), mineSort == MineSort.DUE, { onSort(MineSort.DUE) })
                    PickPill(stringResource(R.string.sort_order), mineSort == MineSort.ORDER, { onSort(MineSort.ORDER) })
                    PickPill(stringResource(R.string.sort_ressort), mineSort == MineSort.RESSORT, { onSort(MineSort.RESSORT) })
                }
            }
            Text(stringResource(R.string.tasks_filter_show), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            PickPill(stringResource(R.string.filter_only_overdue), onlyOverdue, { onOnlyOverdue(!onlyOverdue) })
        }
    }
}
