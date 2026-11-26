package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.Avatar
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
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
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Eine Person in einem Zimmer, die die Person gerade verwaltet (Aktionen oder Verschieben). */
private data class SeatedMember(val room: Room, val member: RoomMember)

/**
 * Zimmer und Zelte (Entwurf L20): Karten mit Belegung und den Namen der Leute. Wer einteilen darf, ordnet Personen zu, nimmt sie
 * heraus oder verschiebt sie («Verschieben nach …» statt Ziehen). Das Team sieht nur das eigene Zimmer mit den Mitbewohnern.
 * Überbelegung ist eine Warnung, keine Sperre.
 */
@Composable
fun RoomsScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.rooms.collectAsState()
    val busy by vm.busy.collectAsState()
    var editor by remember { mutableStateOf<Room?>(null) }
    var creating by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<String?>(null) }
    var assignOpen by remember { mutableStateOf(false) }
    var memberActions by remember { mutableStateOf<SeatedMember?>(null) }
    var moving by remember { mutableStateOf<SeatedMember?>(null) }
    var deleting by remember { mutableStateOf<Room?>(null) }
    LaunchedEffect(camp?.id) { vm.loadRooms() }

    CampPage(
        title = stringResource(R.string.rooms_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.rooms_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadRooms() })
            is ApiResult.Ok -> {
                val rooms = result.value.rooms.sortedWith(compareBy({ it.order }, { it.name.lowercase() }))
                val open = result.value.open?.let { it.participants + it.team } ?: 0
                if (rooms.isEmpty()) {
                    StatePage(R.drawable.ic_tent, stringResource(R.string.rooms_empty_title), stringResource(if (rights.edit) R.string.rooms_empty_text else R.string.rooms_empty_team)) {
                        if (rights.edit) PrimaryButton(stringResource(R.string.rooms_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.edit) 96.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        if (open > 0 && rights.edit) item {
                            Column {
                                NoticeBanner(NoticeKind.WARNING, pluralStringResource(R.plurals.rooms_open, open, open))
                                TextActionButton(stringResource(R.string.rooms_assign), { assigning = null; assignOpen = true })
                            }
                        }
                        items(rooms, key = { it.id }) { room ->
                            RoomCard(
                                room, rights,
                                onAssign = { assigning = room.id; assignOpen = true },
                                onEdit = { editor = room },
                                onMember = { memberActions = SeatedMember(room, it) },
                            )
                        }
                    }
                    if (rights.edit) {
                        PrimaryButton(stringResource(R.string.rooms_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
                if (assignOpen) {
                    AssignToRoomSheet(vm, rooms, assigning, busy, onDismiss = { assignOpen = false; vm.clearOpenPeople() })
                }
            }
        }
    }

    memberActions?.let { seated ->
        val otherRooms = (state as? ApiResult.Ok)?.value?.rooms.orEmpty().filter { it.id != seated.room.id }
        val entries = buildList {
            add(stringResource(R.string.rooms_take_out) to { vm.removeFromRoom(seated.room.id, seated.member) })
            if (otherRooms.isNotEmpty()) add(stringResource(R.string.rooms_move) to { moving = seated })
        }
        ActionSheet(seated.member.name, entries) { memberActions = null }
    }
    moving?.let { seated ->
        val choices = (state as? ApiResult.Ok)?.value?.rooms.orEmpty().filter { it.id != seated.room.id }.map { Choice(it.id, it.name) }
        ChoiceSheet(
            stringResource(R.string.rooms_move_title, seated.member.name), choices, initial = null,
            onApply = { key -> moving = null; key?.let { vm.moveToRoom(seated.room.id, it, seated.member) } },
            onDismiss = { moving = null },
        )
    }
    if (creating || editor != null) {
        RoomEditorSheet(
            editor,
            onSave = { name, kind, seats, note ->
                val existing = editor
                if (existing == null) {
                    vm.createRoom(name, kind, seats, note)
                } else {
                    val fields = buildMap<String, JsonElement> {
                        if (name.trim() != existing.name) put("name", JsonPrimitive(name.trim()))
                        if (kind != existing.kind) put("art", JsonPrimitive(kind.wire))
                        if (seats != existing.seats) put("plaetze", JsonPrimitive(seats))
                        if (note.trim() != existing.note.orEmpty()) put("notiz", textField(note))
                    }
                    if (fields.isNotEmpty()) vm.changeRoom(existing, fields, existing.name)
                }
                creating = false; editor = null
            },
            onDelete = editor?.let { room -> { deleting = room; editor = null } },
            onDismiss = { creating = false; editor = null },
        )
    }
    deleting?.let { room ->
        ConfirmDialog(
            stringResource(R.string.rooms_delete_title, room.name), stringResource(R.string.rooms_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; vm.deleteRoom(room) }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RoomCard(room: Room, rights: PlanRights, onAssign: () -> Unit, onEdit: () -> Unit, onMember: (RoomMember) -> Unit) {
    val kindText = stringResource(if (room.kind == RoomKind.TENT) R.string.rooms_kind_tent else R.string.rooms_kind_room)
    val fill = room.seats?.let { stringResource(R.string.rooms_fill, room.occupied, it) }
    val over = stringResource(R.string.rooms_overbooked)
    val spoken = joinDetail(room.name, kindText, fill, if (room.overbooked) over else null)
    InfoCard {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = spoken.orEmpty() },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                fill?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StatusChip(ChipKind.AGE_GROUP, kindText)
            if (room.overbooked) StatusChip(ChipKind.REVIEW, over)
        }
        room.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (room.members.isEmpty()) {
            Text(stringResource(R.string.rooms_nobody), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        room.members.forEach { member ->
            val shape = MaterialTheme.shapes.small
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .focusRing(shape)
                    .then(if (rights.edit) Modifier.clickable(onClickLabel = stringResource(R.string.rooms_member_actions), role = Role.Button) { onMember(member) } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = member.name },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(member.name, imageUrl = member.imageUrl)
                Text(member.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (rights.edit) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                val assign = stringResource(R.string.rooms_assign)
                val edit = stringResource(R.string.rooms_edit)
                OutlinedPillButton(assign, onAssign, Modifier.rowAction(assign, room.name), icon = R.drawable.ic_plus)
                TextActionButton(edit, onEdit, Modifier.rowAction(edit, room.name))
            }
        }
    }
}

/** Offene Personen wählen und einem Zimmer zuteilen (Teilnehmende oder Team); ohne vorgegebenes Zimmer wählt die Person es zuerst. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssignToRoomSheet(vm: PlanningViewModel, rooms: List<Room>, preselected: String?, busy: Boolean, onDismiss: () -> Unit) {
    var team by remember { mutableStateOf(false) }
    var roomId by remember { mutableStateOf(preselected) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val open by vm.openPeople.collectAsState()
    LaunchedEffect(team) { picked = emptySet(); vm.loadOpenPeople(team) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(stringResource(R.string.rooms_assign), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.rooms_tab_participants), !team, { team = false })
                PickPill(stringResource(R.string.rooms_tab_team), team, { team = true })
            }
            Text(stringResource(R.string.rooms_pick_room), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                rooms.forEach { room -> PickPill(room.name, roomId == room.id, { roomId = room.id }) }
            }
            when (val result = open) {
                null -> Text(stringResource(R.string.plan_loading), style = MaterialTheme.typography.bodyMedium)
                is ApiResult.Failure -> Text(stringResource(R.string.task_error), color = MaterialTheme.colorScheme.error)
                is ApiResult.Ok -> {
                    if (result.value.isEmpty()) Text(stringResource(R.string.rooms_nobody_open), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    result.value.forEach { person ->
                        CheckRow(person.display, joinDetail(person.stage), person.id in picked, { picked = picked.toggled(person.id) })
                    }
                }
            }
            PrimaryButton(
                stringResource(R.string.rooms_assign_confirm),
                {
                    val id = roomId ?: return@PrimaryButton
                    vm.assignToRoom(id, if (team) emptyList() else picked.toList(), if (team) picked.toList() else emptyList(), onDismiss)
                },
                Modifier.fillMaxWidth().padding(top = Spacing.s2), enabled = picked.isNotEmpty() && roomId != null, loading = busy, loadingText = stringResource(R.string.plan_saving),
            )
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoomEditorSheet(initial: Room?, onSave: (String, RoomKind, Int, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: RoomKind.ROOM) }
    var seats by remember { mutableStateOf(initial?.seats?.toString().orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    val seatCount = seats.trim().toIntOrNull()
    val seatsValid = seatCount != null && seatCount in 1..1000
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(if (initial == null) R.string.rooms_add else R.string.rooms_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            LabeledTextField(
                stringResource(R.string.rooms_field_name), name, { name = it }, 100, Modifier.fillMaxWidth(),
                hint = if (showErrors && name.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.rooms_kind_room), kind == RoomKind.ROOM, { kind = RoomKind.ROOM })
                PickPill(stringResource(R.string.rooms_kind_tent), kind == RoomKind.TENT, { kind = RoomKind.TENT })
            }
            LabeledTextField(
                stringResource(R.string.rooms_field_seats), seats, { seats = it.filter(Char::isDigit) }, 4, Modifier.fillMaxWidth(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                hint = if (showErrors && !seatsValid) stringResource(R.string.error_seats) else null,
            )
            LabeledTextField(stringResource(R.string.rooms_field_note), note, { note = it }, 500, Modifier.fillMaxWidth(), minLines = 2)
            PrimaryButton(stringResource(R.string.save), { if (name.isBlank() || !seatsValid) showErrors = true else onSave(name, kind, seatCount!!, note) }, Modifier.fillMaxWidth())
            onDelete?.let { TextActionButton(stringResource(R.string.rooms_delete), it, Modifier.fillMaxWidth()) }
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
