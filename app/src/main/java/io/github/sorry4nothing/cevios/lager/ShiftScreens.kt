package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Ab dieser Breite steht der Schichtplan als Raster (Tablet im Hochformat, Entwurf T23); schmaler gibt es eine Liste je Tag. */
private val GridBreakpoint = 720.dp
private val DayColumnWidth = 88.dp
private val MinCellWidth = 128.dp

/**
 * Der Schichtplan (Entwurf T23). Tablet: Raster mit den Diensten als Spalten und den Tagen als Zeilen. Handy: eine Liste je Tag mit
 * den Diensten. In beiden steht jede Zelle als ganzer Satz für den Screenreader da («Küche, Montag Vormittag, Jonas und Sina, noch
 * ein Platz frei»). Die Lagerleitung teilt per Tipp auf einen Dienst ein («Person wählen», kein Ziehen nötig) und lässt leere
 * Dienste als Vorschlag füllen; wer eine eigene Schicht hat, fragt einen Tausch an.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftPlanScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onOpenSwaps: () -> Unit,
    onBack: () -> Unit,
) {
    val planState by vm.plan.collectAsState()
    val scheduleState by vm.schedule.collectAsState()
    val suggestion by vm.suggestion.collectAsState()
    val busy by vm.busy.collectAsState()
    var staffing by remember { mutableStateOf<ShiftCell?>(null) }
    var swapping by remember { mutableStateOf<Shift?>(null) }
    var dutyEditor by remember { mutableStateOf<Duty?>(null) }
    var dutyCreating by remember { mutableStateOf(false) }
    var dutyPick by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Duty?>(null) }
    var withParticipants by remember { mutableStateOf(false) }
    var onlyPresent by remember { mutableStateOf(true) }
    LaunchedEffect(camp?.id) { vm.loadPlan() }

    CampPage(
        title = stringResource(R.string.shifts_title),
        subtitle = camp?.title,
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
        action = if (rights.edit) HeaderButton(R.drawable.ic_more_horizontal, stringResource(R.string.shifts_more)) { menu = true } else null,
        banners = { PlanNoticeBanner(vm) },
    ) {
        val plan = (planState as? ApiResult.Ok)?.value
        val schedule = (scheduleState as? ApiResult.Ok)?.value
        val failure = (planState as? ApiResult.Failure) ?: (scheduleState as? ApiResult.Failure)
        when {
            failure != null -> ErrorState(failure.error, { vm.loadPlan() })
            plan == null || schedule == null -> CardSkeleton(stringResource(R.string.shifts_loading))
            plan.duties.isEmpty() -> StatePage(R.drawable.ic_list_check, stringResource(R.string.shifts_empty_title), stringResource(if (rights.edit) R.string.shifts_empty_text else R.string.shifts_empty_team)) {
                if (rights.edit) PrimaryButton(stringResource(R.string.shifts_add_duty), { dutyCreating = true }, icon = R.drawable.ic_plus)
            }
            schedule.days.isEmpty() -> StatePage(R.drawable.ic_calendar, stringResource(R.string.shifts_no_days_title), stringResource(R.string.shifts_no_days_text))
            else -> {
                val rows = PlanRules.cells(plan, schedule)
                val canTap: (ShiftCell) -> Boolean = { cell -> rights.edit || (rights.requestSwap && cell.shifts.any { it.personId == vm.selfId }) }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= GridBreakpoint
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.edit) 96.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        if (wide) {
                            item {
                                ShiftGrid(rows, vm.selfId, maxWidth - Spacing.s3 * 2, canTap) { staffing = it }
                            }
                        } else {
                            rows.forEach { row ->
                                val day = row.first().day
                                item(key = "d-" + day.id) {
                                    val gaps = PlanRules.gaps(row)
                                    Column(Modifier.padding(top = Spacing.s2)) {
                                        SectionTitle(formatMeetingDay(day.date))
                                        if (gaps > 0) Text(pluralStringResource(R.plurals.shifts_gaps, gaps, gaps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                items(row, key = { it.day.id + it.duty.id }) { cell ->
                                    ShiftCellView(cell, vm.selfId, canTap(cell), Modifier.fillMaxWidth(), listLayout = true) { staffing = cell }
                                }
                            }
                        }
                        item {
                            Text(stringResource(R.string.shifts_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.s2))
                        }
                        if (plan.stats.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.shifts_distribution), Modifier.padding(top = Spacing.s2)) }
                            items(plan.stats.sortedByDescending { it.count }, key = { (it.personId ?: it.teilnehmerId).orEmpty() }) { stat ->
                                val text = pluralStringResource(R.plurals.shifts_stat, stat.count, stat.name, stat.count) +
                                    if (stat.nights > 0) ", " + pluralStringResource(R.plurals.shifts_stat_nights, stat.nights, stat.nights) else ""
                                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = Sizes.touchTarget).padding(top = Spacing.s2))
                            }
                        }
                        if (rights.edit) item {
                            InfoCard {
                                Text(stringResource(R.string.shifts_fill_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.shifts_fill_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ToggleRow(stringResource(R.string.shifts_fill_participants), withParticipants, { withParticipants = it })
                                ToggleRow(stringResource(R.string.shifts_fill_open), !onlyPresent, { onlyPresent = !it })
                                OutlinedPillButton(
                                    stringResource(R.string.plan_preview), { vm.previewSuggestion(withParticipants, onlyPresent) }, Modifier.fillMaxWidth(),
                                    loading = busy, loadingText = stringResource(R.string.plan_loading_preview),
                                )
                            }
                        }
                    }
                    if (rights.edit) {
                        PrimaryButton(stringResource(R.string.shifts_add_duty), { dutyCreating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }

    if (menu) {
        val plan = (planState as? ApiResult.Ok)?.value
        val entries = buildList {
            add(stringResource(R.string.shifts_add_duty) to { dutyCreating = true })
            if (plan?.duties?.isNotEmpty() == true) add(stringResource(R.string.shifts_edit_duty) to { dutyPick = true })
            add(stringResource(R.string.swaps_title) to onOpenSwaps)
        }
        ActionSheet(stringResource(R.string.shifts_title), entries) { menu = false }
    }
    if (dutyPick) {
        val plan = (planState as? ApiResult.Ok)?.value
        ChoiceSheet(
            stringResource(R.string.shifts_edit_duty), plan?.duties.orEmpty().map { Choice(it.id, it.name) }, initial = null,
            onApply = { key -> dutyPick = false; dutyEditor = plan?.duties?.firstOrNull { it.id == key } },
            onDismiss = { dutyPick = false },
        )
    }
    if (dutyCreating || dutyEditor != null) {
        val sections = (scheduleState as? ApiResult.Ok)?.value?.sections.orEmpty().sortedBy { it.order }
        DutyEditorSheet(
            dutyEditor, sections,
            onCreate = { name, sectionId, minimum, night -> dutyCreating = false; vm.createDuty(name, sectionId, minimum, night) },
            onChange = { duty, fields -> dutyEditor = null; vm.changeDuty(duty, fields, duty.name) },
            onDelete = dutyEditor?.let { duty -> { deleting = duty; dutyEditor = null } },
            onDismiss = { dutyCreating = false; dutyEditor = null },
        )
    }
    deleting?.let { duty ->
        ConfirmDialog(
            stringResource(R.string.shifts_delete_title, duty.name), stringResource(R.string.shifts_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; vm.deleteDuty(duty) }, onDismiss = { deleting = null },
        )
    }
    staffing?.let { cell ->
        StaffSheet(vm, cell, rights, onSwap = { shift -> staffing = null; swapping = shift }, onDismiss = { staffing = null })
    }
    swapping?.let { shift ->
        val plan = (planState as? ApiResult.Ok)?.value
        SwapRequestSheet(vm, shift, plan, onDismiss = { swapping = null })
    }
    suggestion?.let { result ->
        SuggestionSheet(
            result, busy,
            onApply = { vm.applySuggestion(result.seed, withParticipants, onlyPresent) },
            onDismiss = vm::clearSuggestion,
        )
    }
}

/** Der Satz zu einer Zelle für den Screenreader: Dienst, Tag mit Abschnitt, wer eingeteilt ist und wie viele Plätze frei sind. */
@Composable
private fun cellSpoken(cell: ShiftCell, selfId: String): String {
    val day = parseDay(cell.day.date)?.let { weekdayName(it) } ?: cell.day.date
    val dayText = listOfNotNull(day, cell.section?.name).joinToString(" ")
    val you = stringResource(R.string.shifts_you)
    val people = if (cell.shifts.isEmpty()) {
        stringResource(R.string.shifts_nobody)
    } else {
        cell.shifts.joinToString(", ") { if (it.personId == selfId) "${it.name} ($you)" else it.name }
    }
    val vacancy = if (cell.isFull) stringResource(R.string.shifts_full) else pluralStringResource(R.plurals.shifts_free, cell.missing, cell.missing)
    return stringResource(R.string.shifts_cell_spoken, cell.duty.name, dayText, people, vacancy)
}

/** Eine Zelle: Dienst (in der Liste), Namen, «1 von 2». Deine Dienste sind hervorgehoben und tragen das Wort «Du». */
@Composable
private fun ShiftCellView(cell: ShiftCell, selfId: String, tappable: Boolean, modifier: Modifier, listLayout: Boolean, onClick: () -> Unit) {
    val mine = cell.shifts.any { it.personId == selfId }
    val shape = MaterialTheme.shapes.medium
    val scheme = MaterialTheme.colorScheme
    val spoken = cellSpoken(cell, selfId)
    val you = stringResource(R.string.shifts_you)
    Column(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .background(if (mine) scheme.secondaryContainer else scheme.surfaceContainerLow, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .then(if (tappable) Modifier.clickable(onClickLabel = stringResource(R.string.shifts_open), role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        if (listLayout) {
            Text(cell.duty.name, style = MaterialTheme.typography.titleMedium)
            cell.section?.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        if (cell.shifts.isEmpty()) {
            Text(stringResource(R.string.shifts_empty_cell), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        cell.shifts.forEach { shift ->
            Text(if (shift.personId == selfId) "${shift.name} ($you)" else shift.name, style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.shifts_count, cell.shifts.size, cell.duty.minimum), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

/** Das Raster: eine Kopfzeile mit den Diensten und je Tag eine Zeile. Zu schmal für alle Spalten: waagrecht scrollbar. */
@Composable
private fun ShiftGrid(rows: List<List<ShiftCell>>, selfId: String, available: androidx.compose.ui.unit.Dp, canTap: (ShiftCell) -> Boolean, onCell: (ShiftCell) -> Unit) {
    val columns = rows.firstOrNull().orEmpty()
    val columnWidth = ((available - DayColumnWidth) / columns.size.coerceAtLeast(1)).coerceAtLeast(MinCellWidth)
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text("", Modifier.width(DayColumnWidth))
            columns.forEach { cell ->
                Column(Modifier.width(columnWidth)) {
                    Text(cell.duty.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    cell.section?.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(formatMeetingDay(row.first().day.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(DayColumnWidth).padding(top = Spacing.s3))
                row.forEach { cell -> ShiftCellView(cell, selfId, canTap(cell), Modifier.width(columnWidth), listLayout = false) { onCell(cell) } }
            }
        }
    }
}

/** Eintragen in einem Dienst an einem Tag: «Person wählen» mit Kontrollkästchen; für die eigene Schicht «Tausch anfragen». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffSheet(vm: PlanningViewModel, cell: ShiftCell, rights: PlanRights, onSwap: (Shift) -> Unit, onDismiss: () -> Unit) {
    val people by vm.people.collectAsState()
    val participantsState by vm.participants.collectAsState()
    val busy by vm.busy.collectAsState()
    var withParticipants by remember { mutableStateOf(cell.shifts.any { it.teilnehmerId != null }) }
    var picked by remember { mutableStateOf(cell.shifts.map { it.personId?.let { id -> "p:$id" } ?: "t:${it.teilnehmerId}" }.toSet()) }
    LaunchedEffect(withParticipants) { if (withParticipants) vm.loadParticipants(silent = true) }
    val mine = cell.shifts.firstOrNull { it.personId == vm.selfId }
    val day = formatMeetingDay(cell.day.date)
    val candidates = buildList {
        people.forEach { add(PickItem("p:${it.id}", it.display, null)) }
        cell.shifts.filter { it.teilnehmerId != null }.forEach { add(PickItem("t:${it.teilnehmerId}", it.name, stringResource(R.string.shifts_participant))) }
        if (withParticipants) {
            PlanRules.sortedParticipants((participantsState as? ApiResult.Ok)?.value.orEmpty().filter { !it.excluded }).forEach { p ->
                if (none { it.key == "t:${p.id}" }) add(PickItem("t:${p.id}", p.fullName, joinDetail(stringResource(R.string.shifts_participant), p.stage)))
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(stringResource(R.string.shifts_sheet_title, cell.duty.name, day), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.shifts_minimum, cell.duty.minimum), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mine != null && rights.requestSwap) {
                OutlinedPillButton(stringResource(R.string.shifts_request_swap), { onSwap(mine) }, Modifier.fillMaxWidth())
            }
            if (rights.edit) {
                Text(stringResource(R.string.shifts_pick_person), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                candidates.sortedBy { it.label.lowercase() }.forEach { item -> CheckRow(item.label, item.detail, item.key in picked, { picked = picked.toggled(item.key) }) }
                if (candidates.isEmpty()) Text(stringResource(R.string.groups_nobody_known), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow(stringResource(R.string.shifts_fill_participants), withParticipants, { withParticipants = it })
                PrimaryButton(
                    stringResource(R.string.save),
                    {
                        vm.staffDuty(cell.day.id, cell.duty.id, picked.filter { it.startsWith("p:") }.map { it.drop(2) }, picked.filter { it.startsWith("t:") }.map { it.drop(2) }, onDismiss)
                    },
                    Modifier.fillMaxWidth().padding(top = Spacing.s2), loading = busy, loadingText = stringResource(R.string.plan_saving),
                )
            }
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Einen Tausch anfragen (Entwurf L24): Die Person gibt die eigene Schicht ab, nennt eine Wunschperson oder tauscht gegen eine
 * Schicht einer anderen Person. Ob die Wunschperson zustimmt, klärt die Lagerleitung; Cevi OS fragt sie nicht.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SwapRequestSheet(vm: PlanningViewModel, shift: Shift, plan: ShiftPlan?, onDismiss: () -> Unit) {
    val people by vm.people.collectAsState()
    val busy by vm.busy.collectAsState()
    var form by remember { mutableStateOf(0) }
    var person by remember { mutableStateOf<String?>(null) }
    var other by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val others = people.filter { it.id != vm.selfId }
    val duties = plan?.duties.orEmpty().associateBy { it.id }
    val swappable = plan?.shifts.orEmpty().filter { it.personId != null && it.personId != vm.selfId && it.id != shift.id }
        .sortedWith(compareBy({ it.date }, { it.name.lowercase() }))
    val ready = when (form) {
        1 -> person != null
        2 -> other != null
        else -> true
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                stringResource(R.string.swaps_request_title, duties[shift.dienstId]?.name.orEmpty(), formatMeetingDay(shift.date)),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.swaps_request_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.pickGroup(stringResource(R.string.group_swap_form)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.swaps_form_give), form == 0, { form = 0 })
                PickPill(stringResource(R.string.swaps_form_person), form == 1, { form = 1 })
                PickPill(stringResource(R.string.swaps_form_swap), form == 2, { form = 2 })
            }
            if (form == 1) {
                FlowRow(Modifier.pickGroup(stringResource(R.string.group_swap_person)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    others.forEach { p -> PickPill(p.display, person == p.id, { person = p.id }) }
                }
                if (others.isEmpty()) Text(stringResource(R.string.groups_nobody_known), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (form == 2) {
                Column(Modifier.pickGroup(stringResource(R.string.group_swap_shift))) {
                    swappable.forEach { s ->
                        val label = stringResource(R.string.swaps_swap_option, s.name, duties[s.dienstId]?.name.orEmpty(), formatMeetingDay(s.date))
                        PickPill(label, other == s.id, { other = s.id }, Modifier.padding(bottom = Spacing.s2))
                    }
                }
                if (swappable.isEmpty()) Text(stringResource(R.string.swaps_no_swappable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LabeledTextField(stringResource(R.string.swaps_note), note, { note = it }, 500, Modifier.fillMaxWidth(), minLines = 2)
            PrimaryButton(
                stringResource(R.string.swaps_send),
                { vm.requestSwap(shift.id, if (form == 2) other else null, if (form == 1) person else null, note, onDismiss) },
                Modifier.fillMaxWidth(), enabled = ready, loading = busy, loadingText = stringResource(R.string.plan_saving),
            )
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionSheet(result: ShiftSuggestion, busy: Boolean, onApply: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(stringResource(R.string.shifts_suggestion_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(pluralStringResource(R.plurals.shifts_suggestion_new, result.shifts.size, result.shifts.size), style = MaterialTheme.typography.titleMedium)
            val missing = result.unfilled.sumOf { it.missing }
            if (missing > 0) NoticeBanner(NoticeKind.WARNING, pluralStringResource(R.plurals.shifts_suggestion_unfilled, missing, missing))
            if (result.truncated) NoticeBanner(NoticeKind.INFO, stringResource(R.string.shifts_suggestion_truncated))
            result.stats.sortedByDescending { it.total }.forEach { stat ->
                val text = stringResource(R.string.shifts_suggestion_stat, stat.name, stat.added, stat.total)
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = Sizes.touchTarget).padding(top = Spacing.s2))
            }
            PrimaryButton(stringResource(R.string.task_apply), onApply, Modifier.fillMaxWidth().padding(top = Spacing.s2), enabled = result.shifts.isNotEmpty(), loading = busy, loadingText = stringResource(R.string.plan_saving))
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DutyEditorSheet(
    initial: Duty?,
    sections: List<DaySection>,
    onCreate: (String, String, Int, Boolean) -> Unit,
    onChange: (Duty, Map<String, JsonElement>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var sectionId by remember { mutableStateOf(initial?.tagesabschnittId ?: sections.firstOrNull()?.id) }
    var minimum by remember { mutableStateOf((initial?.minimum ?: 1).toString()) }
    var night by remember { mutableStateOf(initial?.night ?: false) }
    var showErrors by remember { mutableStateOf(false) }
    val min = minimum.toIntOrNull()
    val minOk = min != null && min in 1..20
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(if (initial == null) R.string.shifts_add_duty else R.string.shifts_edit_duty), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            LabeledTextField(
                stringResource(R.string.shifts_field_name), name, { name = it }, 100, Modifier.fillMaxWidth(),
                hint = if (showErrors && name.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            if (initial == null) {
                Text(stringResource(R.string.shifts_field_section), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.pickGroup(stringResource(R.string.shifts_field_section)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    sections.forEach { s -> PickPill(s.name, sectionId == s.id, { sectionId = s.id }) }
                }
                if (sections.isEmpty()) Text(stringResource(R.string.shifts_no_sections), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            LabeledTextField(
                stringResource(R.string.shifts_field_minimum), minimum, { minimum = it.filter(Char::isDigit) }, 2, Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
                hint = if (showErrors && !minOk) stringResource(R.string.error_minimum) else stringResource(R.string.shifts_field_minimum_hint),
            )
            ToggleRow(stringResource(R.string.shifts_field_night), night, { night = it })
            PrimaryButton(
                stringResource(R.string.save),
                {
                    if (name.isBlank() || !minOk || (initial == null && sectionId == null)) {
                        showErrors = true
                    } else if (initial == null) {
                        onCreate(name, sectionId!!, min!!, night)
                    } else {
                        val fields = buildMap<String, JsonElement> {
                            if (name.trim() != initial.name) put("name", JsonPrimitive(name.trim()))
                            if (min != initial.minimum) put("mindestbesetzung", JsonPrimitive(min))
                            if (night != initial.night) put("nacht", JsonPrimitive(night))
                        }
                        if (fields.isEmpty()) onDismiss() else onChange(initial, fields)
                    }
                },
                Modifier.fillMaxWidth(),
            )
            onDelete?.let { TextActionButton(stringResource(R.string.shifts_delete_duty), it, Modifier.fillMaxWidth()) }
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
