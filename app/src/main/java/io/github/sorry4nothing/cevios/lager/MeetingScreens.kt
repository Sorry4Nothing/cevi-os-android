package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * Die Höcks des Lagers (Entwurf L11): kommende und vergangene, je mit Datum, Ort und der Zahl der Traktanden. «Neuer Höck» legt einen
 * an, «Wie der letzte» übernimmt Titel, Ort und Zeit des letzten Höcks mit dem Datum eine Woche später. Das Team sieht nur Höcks
 * der Art `team` mit Datum, Zeit, Titel und Ort.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetingListScreen(
    vm: MeetingsViewModel,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.meetings.collectAsState()
    val today = remember { todayInCamp() }
    var editor by remember { mutableStateOf<MeetingDraft?>(null) }
    LaunchedEffect(camp?.id) { vm.loadMeetings() }

    CampPage(
        title = stringResource(R.string.meetings_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.meetings_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadMeetings() })
            is ApiResult.Ok -> {
                val upcoming = MeetingRules.upcoming(result.value, today)
                val past = MeetingRules.past(result.value, today)
                if (result.value.isEmpty()) {
                    StatePage(R.drawable.ic_calendar, stringResource(R.string.meetings_empty_title), stringResource(R.string.meetings_empty_text)) {
                        if (rights.editMeetings) PrimaryButton(stringResource(R.string.meetings_new), { editor = MeetingDraft.blank() }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.editMeetings) 160.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        if (upcoming.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.meetings_upcoming), Modifier.padding(top = Spacing.s2)) }
                            items(upcoming, key = { it.id }) { MeetingCard(it, rights, onOpen = { onOpen(it.id) }) }
                        }
                        if (past.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.meetings_past), Modifier.padding(top = Spacing.s4)) }
                            items(past, key = { it.id }) { MeetingCard(it, rights, onOpen = { onOpen(it.id) }) }
                        }
                    }
                }
                if (rights.editMeetings && result.value.isNotEmpty()) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        PrimaryButton(stringResource(R.string.meetings_new), { editor = MeetingDraft.blank() }, icon = R.drawable.ic_plus)
                        OutlinedPillButton(stringResource(R.string.meetings_like_last), { editor = MeetingDraft.like(MeetingRules.last(result.value)) })
                    }
                }
            }
        }
    }
    editor?.let { draft ->
        MeetingEditorSheet(
            draft, isNew = true,
            onSave = { saved ->
                editor = null
                vm.createMeeting(saved.title, saved.kind, saved.date!!, saved.time, saved.place, saved.guests) { onOpen(it.id) }
            },
            onDismiss = { editor = null },
        )
    }
}

@Composable
private fun MeetingCard(meeting: Meeting, rights: MeetingRights, onOpen: () -> Unit) {
    val count = meeting.agendaCount
    val whenText = formatMeetingWhen(meeting)
    val details = listOfNotNull(meeting.place?.takeIf { it.isNotBlank() }, count?.let { pluralStringResource(R.plurals.start_agenda, it, it) }).joinToString(", ")
    val hasMinutes = meeting.minutesId != null
    val description = listOfNotNull(meeting.title, whenText, details.ifBlank { null }, if (hasMinutes) stringResource(R.string.meetings_has_minutes) else null).joinToString(", ")
    InfoCard(description = description, onClick = onOpen, onClickLabel = stringResource(R.string.meetings_open)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(meeting.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (hasMinutes && rights.readAll) StatusChip(ChipKind.SENT, stringResource(R.string.meetings_minutes_chip))
        }
        Text(whenText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Was im Editor eines Höcks steht. */
data class MeetingDraft(
    val title: String = "",
    val kind: MeetingKind = MeetingKind.LEADERSHIP,
    val date: String? = null,
    val time: String = "",
    val place: String = "",
    val guests: String = "",
) {
    companion object {
        fun blank() = MeetingDraft()

        /** «Wie der letzte»: Titel, Art, Ort und Zeit des letzten Höcks, das Datum eine Woche später (oder nächste Woche). */
        fun like(last: Meeting?): MeetingDraft = if (last == null) blank() else MeetingDraft(
            title = last.title, kind = last.kind, date = (parseDay(last.date) ?: todayInCamp()).plusWeeks(1).toString(),
            time = last.time.orEmpty(), place = last.place.orEmpty(), guests = "",
        )

        fun of(meeting: Meeting) = MeetingDraft(meeting.title, meeting.kind, meeting.date, meeting.time.orEmpty(), meeting.place.orEmpty(), meeting.guests.orEmpty())
    }
}

/** Das Blatt, in dem ein Höck angelegt oder geändert wird. Fehlende Angaben zeigt es beim Speichern (der Knopf bleibt aktiv). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetingEditorSheet(initial: MeetingDraft, isNew: Boolean, onSave: (MeetingDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }
    var datePicking by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                stringResource(if (isNew) R.string.meeting_editor_new else R.string.meeting_editor_edit),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() },
            )
            LabeledTextField(
                stringResource(R.string.meeting_field_title), draft.title, { draft = draft.copy(title = it) }, 200, Modifier.fillMaxWidth(),
                hint = if (showErrors && draft.title.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            Text(stringResource(R.string.meeting_field_kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.pickGroup(stringResource(R.string.meeting_field_kind)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.meeting_kind_leadership), draft.kind == MeetingKind.LEADERSHIP, { draft = draft.copy(kind = MeetingKind.LEADERSHIP) })
                PickPill(stringResource(R.string.meeting_kind_team), draft.kind == MeetingKind.TEAM, { draft = draft.copy(kind = MeetingKind.TEAM) })
            }
            Text(
                stringResource(if (draft.kind == MeetingKind.TEAM) R.string.meeting_kind_team_hint else R.string.meeting_kind_leadership_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedPillButton(
                draft.date?.let { formatMeetingDay(it) } ?: stringResource(R.string.meeting_field_date_pick),
                { datePicking = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar,
            )
            if (showErrors && draft.date == null) Text(stringResource(R.string.error_field_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            LabeledTextField(
                stringResource(R.string.meeting_field_time), draft.time, { draft = draft.copy(time = it) }, 5, Modifier.fillMaxWidth(),
                hint = if (showErrors && !isValidTime(draft.time)) stringResource(R.string.error_time_format) else stringResource(R.string.meeting_field_time_hint),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            LabeledTextField(stringResource(R.string.meeting_field_place), draft.place, { draft = draft.copy(place = it) }, 200, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.meeting_field_guests), draft.guests, { draft = draft.copy(guests = it) }, 500, Modifier.fillMaxWidth())
            PrimaryButton(
                stringResource(R.string.save),
                {
                    if (draft.title.isBlank() || draft.date == null || !isValidTime(draft.time)) showErrors = true else onSave(draft)
                },
                Modifier.fillMaxWidth(),
            )
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
    if (datePicking) {
        DateDialog(initial = draft.date, onPick = { draft = draft.copy(date = it); datePicking = false }, onDismiss = { datePicking = false })
    }
}

/** Das Traktandum, das bearbeitet wird (oder ein neues). */
private data class ItemDraft(val title: String, val text: String, val decision: String, val responsible: Set<String>, val minutes: String = "")

/**
 * Die Traktanden eines Höcks (Entwurf L12): nummerierte Karten mit Titel und Verantwortlichen, «Auf nächsten Höck» mit Rückgängig,
 * «Nach oben» und «Nach unten» statt Ziehen, «Aus Aufgaben hinzufügen» und «Traktandum hinzufügen». Punkte, die auf einen Höck
 * warten, übernimmt «In diesen Höck übernehmen». Das Team sieht nur die Eckdaten eines Team-Höcks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendaScreen(
    vm: MeetingsViewModel,
    meetingId: String,
    camp: Camp?,
    rights: MeetingRights,
    queue: QueueState,
    online: Boolean,
    tasks: List<CampTask>,
    names: Map<String, String>,
    onOpenQueue: () -> Unit,
    onStartMode: () -> Unit,
    onOpenMinutes: () -> Unit,
    onShareCalendar: () -> Unit,
    onBack: () -> Unit,
    onDownloadCalendar: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val members by vm.members.collectAsState()
    val notice by vm.notice.collectAsState()
    val current = state?.takeIf { it.id == meetingId }
    var editing by remember { mutableStateOf<AgendaItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var meetingEdit by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    var pickTask by remember { mutableStateOf(false) }
    var deleteMeeting by remember { mutableStateOf(false) }
    var deleteItem by remember { mutableStateOf<AgendaItem?>(null) }
    LaunchedEffect(meetingId, camp?.id) {
        vm.openMeeting(meetingId)
        if (rights.editMeetings) vm.loadMembers(meetingId)
    }

    val meeting = (current?.meeting as? ApiResult.Ok)?.value
    CampPage(
        title = stringResource(R.string.agenda_title),
        subtitle = meeting?.let { it.title + ", " + formatMeetingDay(it.date) },
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_meetings), onBack),
        action = if (meeting != null) HeaderButton(R.drawable.ic_more_horizontal, stringResource(R.string.agenda_more)) { menu = true } else null,
        banners = {
            notice?.let { n ->
                val text = when (n.kind) {
                    MeetingNotice.Kind.MOVED_TO_NEXT -> stringResource(R.string.agenda_moved, n.title)
                    MeetingNotice.Kind.TAKEN_OVER -> pluralStringResource(R.plurals.agenda_taken_over, n.title.toIntOrNull() ?: 0, n.title.toIntOrNull() ?: 0)
                    MeetingNotice.Kind.TASK_CREATED -> stringResource(R.string.mention_task_created)
                }
                ActionBanner(NoticeKind.INFO, text, n.undo?.let { stringResource(R.string.undo) }, n.undo, vm::dismissNotice)
            }
        },
    ) {
        when (val result = current?.meeting) {
            null -> CardSkeleton(stringResource(R.string.agenda_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadMeeting(meetingId) })
            is ApiResult.Ok -> {
                val m = result.value
                val items = MeetingRules.sorted(m.agenda)
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    item { MeetingFacts(m) }
                    if (reorder) item { Text(stringResource(R.string.agenda_reorder_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (!rights.readAll) {
                        // Das Team sieht von einem Team-Höck nur Datum, Zeit, Titel und Ort.
                        item { OutlinedPillButton(stringResource(R.string.meeting_calendar), onShareCalendar, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar_plus) }
                        onDownloadCalendar?.let { download -> item { OutlinedPillButton(stringResource(R.string.dl_calendar), download, Modifier.fillMaxWidth(), icon = R.drawable.ic_download) } }
                        return@LazyColumn
                    }
                    if (items.isEmpty()) {
                        item {
                            Column(Modifier.padding(vertical = Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                Text(stringResource(R.string.agenda_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.agenda_empty_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(items, key = { it.id }) { item ->
                        val index = items.indexOf(item)
                        AgendaCard(
                            item = item, number = index + 1, names = names, rights = rights, reorder = reorder,
                            onEdit = { editing = item },
                            onUp = if (index > 0) ({ vm.moveItem(items, index, -1) }) else null,
                            onDown = if (index < items.lastIndex) ({ vm.moveItem(items, index, 1) }) else null,
                            onNext = { vm.moveToNext(item) },
                            onToggleDone = {
                                val status = if (item.status == AgendaStatus.DONE) AgendaStatus.OPEN else AgendaStatus.DONE
                                vm.changeItem(item, mapOf("status" to JsonPrimitive(status.wire)), item.title)
                            },
                            onDelete = { deleteItem = item },
                        )
                    }
                    val waiting = current.nextItems
                    if (rights.editMeetings && waiting.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                SectionTitle(stringResource(R.string.agenda_waiting))
                                waiting.forEach { Text("• " + it.title, style = MaterialTheme.typography.bodyLarge) }
                                OutlinedPillButton(stringResource(R.string.agenda_take_over), { vm.takeOver(m) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (rights.editMeetings) {
                        item {
                            Column(Modifier.padding(top = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                OutlinedPillButton(stringResource(R.string.agenda_from_tasks), { pickTask = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_list_check)
                                PrimaryButton(stringResource(R.string.agenda_add), { adding = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(top = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            OutlinedPillButton(stringResource(R.string.agenda_open_minutes), onOpenMinutes, Modifier.fillMaxWidth())
                            if (rights.readAll) OutlinedPillButton(stringResource(R.string.agenda_start_mode), onStartMode, Modifier.fillMaxWidth())
                        }
                    }
                }
                if (menu) {
                    ActionSheet(
                        stringResource(R.string.agenda_more),
                        buildList {
                            if (rights.editMeetings) {
                                add(stringResource(R.string.agenda_reorder) to { reorder = !reorder })
                                add(stringResource(R.string.meeting_edit) to { meetingEdit = true })
                            }
                            add(stringResource(R.string.meeting_calendar) to onShareCalendar)
                            onDownloadCalendar?.let { add(stringResource(R.string.dl_calendar) to it) }
                            if (rights.editMeetings) add(stringResource(R.string.meeting_delete) to { deleteMeeting = true })
                        },
                        onDismiss = { menu = false },
                    )
                }
                if (meetingEdit) {
                    MeetingEditorSheet(
                        MeetingDraft.of(m), isNew = false,
                        onSave = { saved ->
                            meetingEdit = false
                            vm.changeMeeting(m, meetingFields(m, saved), m.title)
                        },
                        onDismiss = { meetingEdit = false },
                    )
                }
                if (deleteMeeting) {
                    ConfirmDeleteDialog(
                        title = stringResource(R.string.meeting_delete_title, m.title),
                        text = stringResource(R.string.meeting_delete_text),
                        onConfirm = { deleteMeeting = false; vm.deleteMeeting(m, onBack) },
                        onDismiss = { deleteMeeting = false },
                    )
                }
                deleteItem?.let { item ->
                    ConfirmDeleteDialog(
                        title = stringResource(R.string.agenda_delete_title, item.title),
                        text = stringResource(R.string.agenda_delete_text),
                        onConfirm = { deleteItem = null; vm.deleteItem(item) },
                        onDismiss = { deleteItem = null },
                    )
                }
                if (pickTask) {
                    val open = tasks.filter { !it.done && it.queuedId == null && items.none { item -> item.taskId == it.id } }
                    ChoiceSheet(
                        title = stringResource(R.string.agenda_from_tasks),
                        choices = open.map { Choice(it.id, it.title) },
                        initial = null,
                        onApply = { id ->
                            pickTask = false
                            open.firstOrNull { it.id == id }?.let { task -> vm.createItem(m.id, task.title, null, emptyList(), task.id) }
                        },
                        onDismiss = { pickTask = false },
                        emptyText = stringResource(R.string.agenda_no_tasks),
                    )
                }
                if (adding || editing != null) {
                    val item = editing
                    AgendaEditorSheet(
                        initial = item?.let { ItemDraft(it.title, it.text.orEmpty(), it.decision.orEmpty(), it.responsible.map { p -> p.id }.toSet(), it.minutes?.toString().orEmpty()) } ?: ItemDraft("", "", "", emptySet()),
                        isNew = item == null, members = members,
                        onSave = { saved ->
                            adding = false; editing = null
                            if (item == null) {
                                vm.createItem(m.id, saved.title, saved.text, saved.responsible.toList(), minutes = MeetingRules.minutesOf(saved.minutes))
                            } else {
                                vm.changeItem(item, itemFields(item, saved), item.title)
                            }
                        },
                        onDismiss = { adding = false; editing = null },
                    )
                }
            }
        }
    }
}

private fun meetingFields(old: Meeting, draft: MeetingDraft): Map<String, JsonElement> = buildMap {
    if (draft.title.trim() != old.title) put("titel", JsonPrimitive(draft.title.trim()))
    if (draft.kind != old.kind) put("art", JsonPrimitive(draft.kind.wire))
    if (draft.date != null && draft.date != old.date) put("datum", JsonPrimitive(draft.date))
    if (draft.time.trim() != old.time.orEmpty()) put("zeit", nullable(draft.time.trim().ifBlank { null }))
    if (draft.place.trim() != old.place.orEmpty()) put("ort", nullable(draft.place.trim().ifBlank { null }))
    if (draft.guests.trim() != old.guests.orEmpty()) put("gaeste", nullable(draft.guests.trim().ifBlank { null }))
}

private fun itemFields(old: AgendaItem, draft: ItemDraft): Map<String, JsonElement> = buildMap {
    if (draft.title.trim() != old.title) put("titel", JsonPrimitive(draft.title.trim()))
    if (draft.text.trim() != old.text.orEmpty()) put("text", nullable(draft.text.trim().ifBlank { null }))
    if (draft.decision.trim() != old.decision.orEmpty()) put("entscheid", nullable(draft.decision.trim().ifBlank { null }))
    if (draft.responsible != old.responsible.map { it.id }.toSet()) put("verantwortlichePersonIds", JsonArray(draft.responsible.map(::JsonPrimitive)))
    if (MeetingRules.minutesOf(draft.minutes) != old.minutes) put("dauerMinuten", MeetingRules.minutesOf(draft.minutes)?.let { JsonPrimitive(it) } ?: JsonNull)
}

@Composable
private fun MeetingFacts(meeting: Meeting) {
    Column(Modifier.padding(horizontal = Spacing.s2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(formatMeetingWhen(meeting), style = MaterialTheme.typography.bodyLarge)
        meeting.place?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        meeting.guests?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.meeting_guests, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (meeting.kind == MeetingKind.TEAM) {
            Text(stringResource(R.string.meeting_kind_team_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Ein Traktandum als Karte: Nummer, Titel, Verantwortliche, Entscheid. Das Antippen öffnet den Editor. Im Umordnen-Modus stehen
 * «Nach oben» und «Nach unten» da, sonst im Menü der Karte; nie nur Ziehen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaCard(
    item: AgendaItem,
    number: Int,
    names: Map<String, String>,
    rights: MeetingRights,
    reorder: Boolean,
    onEdit: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onNext: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val duration = item.minutes?.let { pluralStringResource(R.plurals.agenda_minutes, it, it) }
    val who = (item.responsible.map { it.display } + listOfNotNull(duration)).joinToString(", ").ifBlank { null }
    val unknown = stringResource(R.string.mention_unknown)
    val decision = item.decision?.takeIf { it.isNotBlank() }?.let { renderMentions(it, names, unknown) }
    val done = item.status == AgendaStatus.DONE
    // Die Karte ist kein ganzer Knopf, weil das Menü darin ein eigener Knopf ist: Titel und Verantwortliche öffnen den Editor.
    val titleShape = MaterialTheme.shapes.small
    val spoken = buildList {
        add(stringResource(R.string.agenda_number, number, item.title))
        who?.let { add(it) }
    }.joinToString(", ")
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget)
                    .focusRing(titleShape)
                    .clip(titleShape)
                    .then(if (rights.editMeetings && !reorder) Modifier.clickable(onClickLabel = stringResource(R.string.agenda_edit), role = Role.Button, onClick = onEdit) else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
                    Text(number.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(renderMentions(item.title, names, unknown), style = MaterialTheme.typography.titleMedium)
                        who?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (reorder) {
                onUp?.let { RoundIcon(R.drawable.ic_chevron_up, stringResource(R.string.agenda_move_up, item.title), it) }
                onDown?.let { RoundIcon(R.drawable.ic_chevron_down, stringResource(R.string.agenda_move_down, item.title), it) }
            } else if (rights.editMeetings) {
                RoundIcon(R.drawable.ic_more_horizontal, stringResource(R.string.agenda_actions, item.title)) { menu = true }
            }
        }
        if (done) StatusChip(ChipKind.YES, stringResource(R.string.agenda_done))
        decision?.let { Text(stringResource(R.string.agenda_decision, it), style = MaterialTheme.typography.bodyMedium) }
    }
    if (menu) {
        ActionSheet(
            item.title,
            buildList {
                add(stringResource(R.string.agenda_edit) to onEdit)
                add(stringResource(if (done) R.string.agenda_mark_open else R.string.agenda_mark_done) to onToggleDone)
                add(stringResource(R.string.agenda_to_next) to onNext)
                onUp?.let { add(stringResource(R.string.agenda_move_up_short) to it) }
                onDown?.let { add(stringResource(R.string.agenda_move_down_short) to it) }
                add(stringResource(R.string.task_delete) to onDelete)
            },
            onDismiss = { menu = false },
        )
    }
}

/** Ein runder 48-dp-Knopf mit Symbol (Menü, Pfeile). */
@Composable
fun RoundIcon(icon: Int, description: String, onClick: () -> Unit) {
    io.github.sorry4nothing.cevios.ui.RoundButton(HeaderButton(icon, description, onClick))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AgendaEditorSheet(initial: ItemDraft, isNew: Boolean, members: List<MemberPresence>, onSave: (ItemDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                stringResource(if (isNew) R.string.agenda_editor_new else R.string.agenda_editor_edit),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() },
            )
            LabeledTextField(
                stringResource(R.string.agenda_field_title), draft.title, { draft = draft.copy(title = it) }, 200, Modifier.fillMaxWidth(),
                hint = if (showErrors && draft.title.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            LabeledTextField(stringResource(R.string.agenda_field_text), draft.text, { draft = draft.copy(text = it) }, 5000, Modifier.fillMaxWidth(), minLines = 3)
            LabeledTextField(
                stringResource(R.string.agenda_field_minutes), draft.minutes, { draft = draft.copy(minutes = it.filter(Char::isDigit)) }, 3, Modifier.fillMaxWidth(),
                hint = if (showErrors && !MeetingRules.isValidMinutes(draft.minutes)) stringResource(R.string.agenda_minutes_invalid) else stringResource(R.string.agenda_minutes_hint),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            if (!isNew) {
                LabeledTextField(stringResource(R.string.agenda_field_decision), draft.decision, { draft = draft.copy(decision = it) }, 1000, Modifier.fillMaxWidth(), minLines = 2)
            }
            if (members.isNotEmpty()) {
                Text(stringResource(R.string.agenda_field_responsible), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    members.forEach { member ->
                        PickPill(
                            member.display, member.id in draft.responsible,
                            { draft = draft.copy(responsible = if (member.id in draft.responsible) draft.responsible - member.id else draft.responsible + member.id) },
                            role = androidx.compose.ui.semantics.Role.Checkbox,
                        )
                    }
                }
                Text(stringResource(R.string.agenda_field_mention), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    members.forEach { member ->
                        PickPill("@" + member.display, false, { draft = draft.copy(text = (draft.text + " " + mentionMark(member.id)).trim() + " ") }, role = androidx.compose.ui.semantics.Role.Button)
                    }
                }
                Text(stringResource(R.string.agenda_mention_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(stringResource(R.string.save), { if (draft.title.isBlank() || !MeetingRules.isValidMinutes(draft.minutes)) showErrors = true else onSave(draft) }, Modifier.fillMaxWidth())
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
