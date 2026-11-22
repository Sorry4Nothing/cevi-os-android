package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/** Das Wetter eines Tages in einem Satz: «Klar, 24° / 12°, Regen 3 mm»; ohne Angaben leer. */
@Composable
fun weatherLine(day: WeatherDay): String {
    val parts = buildList {
        day.condition?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (day.high != null && day.low != null) add(stringResource(R.string.weather_temps, Math.round(day.high), Math.round(day.low)))
        day.rain?.takeIf { it >= 1.0 }?.let { add(stringResource(R.string.weather_rain_amount, Math.round(it))) }
    }
    return parts.joinToString(", ")
}

/** Die Warnungen eines Tages als Wörter: «Gewitter, Böen». */
@Composable
fun weatherWarnings(day: WeatherDay): String {
    val names = day.warnings.map { stringResource(weatherText(it)) }
    return names.joinToString(", ")
}

/** «Mittwoch 19. Juli». */
fun dayTitle(date: String): String = parseDay(date)?.let { weekdayName(it) + " " + formatDay(date) } ?: date

/**
 * Heute (Entwurf L4): dein Dienst, Wichtiges des Tages, eigene To-dos, Fälliges und der Weg zum Notfall. Was die Rolle nicht lesen
 * darf (Tagesseite, Anwesenheit, Schichten), liefert der Server nicht; es fehlt dann einfach. Der Stand bleibt im Arbeitsspeicher:
 * Ohne Netz steht der zuletzt geladene.
 */
@Composable
fun TodayScreen(frame: LiveFrame, nav: LiveNav, taskList: List<CampTask>, tasks: TasksViewModel) {
    val vm = frame.vm
    val state by vm.today.collectAsState()
    val todosState by vm.todos.collectAsState()
    val scheduleState by vm.schedule.collectAsState()
    val today = remember { todayInCamp() }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(frame.camp?.id) {
        vm.loadToday(silent = vm.today.value is ApiResult.Ok)
        vm.loadTodos(silent = vm.todos.value is ApiResult.Ok)
        vm.loadSchedule(silent = true)
    }
    val position = frame.camp?.let { LiveRules.dayOfCamp(it.from, it.until, today) }
    val weekday = weekdayName(today) + " " + formatDay(today.toString())
    val subtitle = if (position != null) stringResource(R.string.today_subtitle, position.first, position.second, weekday) else weekday

    LivePage(
        frame, stringResource(R.string.today_title), subtitle,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_start), frame.onBack),
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.today_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadToday() })
            is ApiResult.Ok -> {
                val view = result.value
                val sections = (scheduleState as? ApiResult.Ok)?.value?.sections.orEmpty()
                val duties = LiveRules.myShiftsToday(view.shifts, frame.self.id, sections)
                val important = LiveRules.blocks(view.day?.blocks.orEmpty(), BlockKind.IMPORTANT)
                val due = LiveRules.dueForMe(taskList, frame.self.id, today)
                val todos = (todosState as? ApiResult.Ok)?.value?.todos.orEmpty()
                val weather = view.weather?.day
                val nothing = view.lagertag != null && duties.isEmpty() && important.isEmpty() && due.isEmpty() && todos.isEmpty() && weather == null
                if (view.lagertag == null) {
                    StatePage(R.drawable.ic_calendar, stringResource(R.string.today_before_title), stringResource(R.string.today_before_text, formatDay(frame.camp?.from))) {
                        OutlinedPillButton(stringResource(R.string.emerg_title), { nav.onOpenArea(LiveArea.EMERGENCY) }, icon = R.drawable.ic_phone)
                    }
                } else if (nothing && !adding) {
                    StatePage(R.drawable.ic_list_check, stringResource(R.string.today_empty_title), stringResource(R.string.today_empty_text)) {
                        PrimaryButton(stringResource(R.string.today_open_shifts), nav.onOpenShifts, icon = R.drawable.ic_list_check)
                        if (frame.rights.todos) TextActionButton(stringResource(R.string.todo_add), { adding = true })
                        OutlinedPillButton(stringResource(R.string.emerg_title), { nav.onOpenArea(LiveArea.EMERGENCY) }, icon = R.drawable.ic_phone)
                    }
                } else {
                    LiveColumn {
                        if (weather != null && weather.warnings.isNotEmpty()) {
                            NoticeBanner(NoticeKind.WARNING, stringResource(R.string.today_weather_warning, weatherWarnings(weather)))
                        }
                        weather?.let { weatherLine(it).takeIf { line -> line.isNotBlank() } }?.let { line ->
                            Text(line, style = MaterialTheme.typography.bodyLarge)
                        }
                        view.day?.page?.tageschef?.let { lead ->
                            Text(stringResource(R.string.day_lead_line, lead.display), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (duties.isNotEmpty()) {
                            LiveSection(stringResource(R.string.today_duty)) {
                                duties.forEach { (duty, section) ->
                                    val time = listOfNotNull(section?.name, section?.time?.takeIf { it.isNotBlank() }).joinToString(", ")
                                    InfoCard(description = listOfNotNull(duty?.name, time.ifBlank { null }).joinToString(", ")) {
                                        Text(duty?.name ?: stringResource(R.string.today_duty_unknown), style = MaterialTheme.typography.titleLarge)
                                        if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (important.isNotEmpty()) {
                            LiveSection(stringResource(R.string.today_important)) {
                                important.forEach { block ->
                                    InfoCard(description = block.text) { Text(block.text, style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        }
                        if (frame.rights.todos || todos.isNotEmpty()) {
                            LiveSection(stringResource(R.string.todo_title)) {
                                todos.forEach { todo ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        CheckRow(todo.text, null, todo.done, { vm.toggleTodo(todo) }, Modifier.weight(1f), enabled = frame.rights.todos)
                                        if (frame.rights.todos) {
                                            RoundButton(HeaderButton(R.drawable.ic_cross, stringResource(R.string.todo_delete_named, todo.text)) { vm.deleteTodo(todo) })
                                        }
                                    }
                                }
                                if (todos.isEmpty()) Text(stringResource(R.string.todo_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (frame.rights.todos) TextActionButton(stringResource(R.string.todo_add), { adding = true })
                            }
                        }
                        if (due.isNotEmpty()) {
                            LiveSection(stringResource(R.string.today_due)) {
                                due.forEach { task -> StartTaskRow(task, today, onOpen = { nav.onOpenTask(task.id) }) }
                            }
                        }
                        view.attendance?.let { summary -> TodayAttendance(summary, view.lagertag, sections) }
                        if (frame.rights.readDayPages && view.lagertag != null) {
                            OutlinedPillButton(stringResource(R.string.today_open_day), { nav.onOpenDay(view.lagertag.id) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar)
                        }
                        OutlinedPillButton(stringResource(R.string.emerg_title), { nav.onOpenArea(LiveArea.EMERGENCY) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_phone)
                    }
                }
            }
        }
    }
    if (adding) {
        TextDialog(
            stringResource(R.string.todo_add), stringResource(R.string.todo_label), "", maxChars = TODO_MAX,
            confirmText = stringResource(R.string.todo_add), onConfirm = { text -> adding = false; vm.addTodo(text) }, onDismiss = { adding = false },
        )
    }
}

private const val TODO_MAX = 200

/** Wer heute da ist: die Zahlen je Abschnitt aus der Zusammenfassung, ohne Namen. */
@Composable
private fun TodayAttendance(summary: AttendanceSummary, lagertag: TodayLagertag, sections: List<DaySection>) {
    val rows = LiveRules.summary(summary, Schedule(listOf(CampDay(lagertag.id, lagertag.date, lagertag.label)), sections)).firstOrNull()?.sections.orEmpty()
    if (rows.isEmpty()) return
    LiveSection(stringResource(R.string.today_attendance)) {
        rows.forEach { row ->
            val line = stringResource(R.string.att_summary_line, row.section.name, row.present, row.absent, row.open)
            Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { contentDescription = line })
        }
    }
}

/**
 * Die Seite eines Lagertags (Entwurf L27): Tageschef, Wetter, Wichtiges, Ideen, Aufgaben mit Frist an diesem Tag («In den nächsten
 * Tag schieben») und Programmbilder (nur als Verknüpfung auf die Datei, die App lädt nichts hoch). Wer `tagesseiten.bearbeiten` hat,
 * ändert Tageschef, Notiz und Blöcke.
 */
@Composable
fun DayScreen(frame: LiveFrame, nav: LiveNav, dayId: String, tasks: TasksViewModel) {
    val vm = frame.vm
    val state by vm.day.collectAsState()
    val daysState by vm.days.collectAsState()
    val scheduleState by vm.schedule.collectAsState()
    val taskState by tasks.tasks.collectAsState()
    val open = rememberLinkOpener()
    val today = remember { todayInCamp() }
    var menu by remember { mutableStateOf(false) }
    var pickLead by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }
    var newBlock by remember { mutableStateOf<BlockKind?>(null) }
    var blockActions by remember { mutableStateOf<DayBlock?>(null) }
    var editBlock by remember { mutableStateOf<DayBlock?>(null) }
    var deleteBlock by remember { mutableStateOf<DayBlock?>(null) }
    LaunchedEffect(dayId) {
        vm.loadDay(dayId, silent = (vm.day.value as? ApiResult.Ok)?.value?.lagertag?.id == dayId)
        vm.loadSchedule(silent = true)
        vm.loadDays(silent = true)
    }
    val page = (state as? ApiResult.Ok)?.value
    val schedule = (scheduleState as? ApiResult.Ok)?.value
    val index = schedule?.days?.sortedBy { it.date }?.indexOfFirst { it.id == dayId } ?: -1
    val title = if (index >= 0) stringResource(R.string.day_title, index + 1) else stringResource(R.string.day_title_plain)
    val subtitle = page?.lagertag?.date?.let(::dayTitle)

    LivePage(
        frame, title, subtitle,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_today), frame.onBack),
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.day_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadDay(dayId) })
            is ApiResult.Ok -> {
                val value = result.value
                // Aufgaben mit den wartenden Änderungen darüber: Ein «Verschieben» zeigt die Seite sofort.
                val known = (taskState as? ApiResult.Ok)?.value.orEmpty().associateBy { it.id }
                val dayTasks = value.tasks.map { known[it.id] ?: it }.filter { TaskRules.dueDate(it)?.toString() == value.lagertag.date }
                val important = LiveRules.blocks(value.blocks, BlockKind.IMPORTANT)
                val ideas = LiveRules.blocks(value.blocks, BlockKind.IDEA)
                val pictures = LiveRules.blocks(value.blocks, BlockKind.PICTURE)
                val lead = value.page?.tageschef
                val empty = lead == null && value.page?.note.isNullOrBlank() && value.blocks.isEmpty() && dayTasks.isEmpty() && value.weather == null
                if (empty) {
                    StatePage(
                        R.drawable.ic_calendar, stringResource(R.string.day_empty_title),
                        stringResource(if (frame.rights.editDayPages) R.string.day_empty_text else R.string.day_empty_text_read),
                    ) {
                        if (frame.rights.editDayPages) PrimaryButton(stringResource(R.string.day_edit), { menu = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LiveColumn(bottom = if (frame.rights.editDayPages) EDIT_BUTTON_SPACE else Spacing.s6) {
                        lead?.let {
                            LiveSection(stringResource(R.string.day_lead)) { Text(it.display, style = MaterialTheme.typography.titleLarge) }
                        }
                        value.weather?.let { weather ->
                            val line = weatherLine(weather)
                            if (weather.warnings.isNotEmpty()) NoticeBanner(NoticeKind.WARNING, stringResource(R.string.today_weather_warning, weatherWarnings(weather)))
                            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyLarge)
                        }
                        value.page?.note?.takeIf { it.isNotBlank() }?.let { note ->
                            LiveSection(stringResource(R.string.day_note)) { Text(note, style = MaterialTheme.typography.bodyLarge) }
                        }
                        BlockSection(R.string.day_important, important, frame.rights.editDayPages) { blockActions = it }
                        BlockSection(R.string.day_ideas, ideas, frame.rights.editDayPages) { blockActions = it }
                        if (dayTasks.isNotEmpty()) {
                            LiveSection(stringResource(R.string.todo_title)) {
                                dayTasks.forEach { task ->
                                    StartTaskRow(task, today, onOpen = { nav.onOpenTask(task.id) })
                                    if (tasks.rights.edit && !task.done) {
                                        TextActionButton(stringResource(R.string.day_push_task), { LiveRules.nextDay(task.due)?.let { tasks.setDue(task, it) } })
                                    }
                                }
                            }
                        }
                        if (pictures.isNotEmpty()) {
                            LiveSection(stringResource(R.string.day_pictures)) {
                                pictures.forEach { block ->
                                    val link = block.datei?.link
                                    InfoCard(
                                        description = block.text,
                                        onClick = if (link != null) ({ open(link) }) else null,
                                        onClickLabel = stringResource(R.string.day_open_picture),
                                    ) {
                                        Text(block.text, style = MaterialTheme.typography.bodyLarge)
                                        block.datei?.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                        DayNavigation(dayId, (daysState as? ApiResult.Ok)?.value?.days.orEmpty(), nav.onOpenDay)
                    }
                }
                if (frame.rights.editDayPages && !empty) {
                    PrimaryButton(stringResource(R.string.day_edit), { menu = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                }
                if (menu) {
                    ActionSheet(
                        stringResource(R.string.day_edit),
                        listOf(
                            stringResource(R.string.day_lead_change) to { pickLead = true },
                            stringResource(R.string.day_note_change) to { editNote = true },
                            stringResource(R.string.day_add_important) to { newBlock = BlockKind.IMPORTANT },
                            stringResource(R.string.day_add_idea) to { newBlock = BlockKind.IDEA },
                        ),
                    ) { menu = false }
                }
                if (pickLead) {
                    val choices = listOf(Choice(null, stringResource(R.string.day_lead_nobody))) + frame.people.map { Choice(it.id, it.display) }
                    val leadLabel = stringResource(R.string.day_lead)
                    ChoiceSheet(
                        stringResource(R.string.day_lead_change), choices, initial = value.page?.tageschefPersonId,
                        onApply = { key ->
                            pickLead = false
                            vm.changeDayPage(value, mapOf("tageschefPersonId" to (key?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)), leadLabel)
                        },
                        onDismiss = { pickLead = false },
                    )
                }
                if (editNote) {
                    val noteLabel = stringResource(R.string.day_note)
                    TextDialog(
                        stringResource(R.string.day_note_change), stringResource(R.string.day_note), value.page?.note.orEmpty(), maxChars = NOTE_MAX,
                        confirmText = stringResource(R.string.save), multiline = true, allowEmpty = true,
                        onConfirm = { text -> editNote = false; vm.changeDayPage(value, mapOf("notiz" to textField(text)), noteLabel) },
                        onDismiss = { editNote = false },
                    )
                }
            }
        }
    }
    newBlock?.let { kind ->
        TextDialog(
            stringResource(if (kind == BlockKind.IMPORTANT) R.string.day_add_important else R.string.day_add_idea), stringResource(R.string.field_text), "", maxChars = NOTE_MAX,
            confirmText = stringResource(R.string.save), multiline = true,
            onConfirm = { text -> newBlock = null; vm.addBlock(dayId, kind, text) }, onDismiss = { newBlock = null },
        )
    }
    blockActions?.let { block ->
        ActionSheet(
            block.text.clip(ACTION_TITLE_MAX),
            listOf(stringResource(R.string.day_block_change) to { editBlock = block }, stringResource(R.string.task_delete) to { deleteBlock = block }),
        ) { blockActions = null }
    }
    editBlock?.let { block ->
        TextDialog(
            stringResource(R.string.day_block_change), stringResource(R.string.field_text), block.text, maxChars = NOTE_MAX,
            confirmText = stringResource(R.string.save), multiline = true,
            onConfirm = { text -> editBlock = null; vm.changeBlock(dayId, block, text) }, onDismiss = { editBlock = null },
        )
    }
    deleteBlock?.let { block ->
        ConfirmDialog(
            stringResource(R.string.day_block_delete_title), block.text.clip(ACTION_TITLE_MAX), stringResource(R.string.task_delete),
            onConfirm = { deleteBlock = null; vm.deleteBlock(dayId, block) }, onDismiss = { deleteBlock = null },
        )
    }
}

private const val NOTE_MAX = 2000
private val EDIT_BUTTON_SPACE = 96.dp
private const val ACTION_TITLE_MAX = 60

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BlockSection(title: Int, blocks: List<DayBlock>, editable: Boolean, onActions: (DayBlock) -> Unit) {
    if (blocks.isEmpty()) return
    LiveSection(stringResource(title)) {
        blocks.forEach { block ->
            InfoCard(
                description = block.text,
                onClick = if (editable) ({ onActions(block) }) else null,
                onClickLabel = stringResource(R.string.day_block_actions),
            ) { Text(block.text, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** «Voriger Tag» und «Nächster Tag»; nur mit der Liste der Tage (sie braucht `tagesseiten.lesen`). */
@Composable
private fun DayNavigation(dayId: String, days: List<DayListEntry>, onOpenDay: (String) -> Unit) {
    val sorted = days.sortedBy { it.date }
    val index = sorted.indexOfFirst { it.lagertagId == dayId }
    if (index < 0) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (index > 0) TextActionButton(stringResource(R.string.day_previous), { onOpenDay(sorted[index - 1].lagertagId) }) else Spacer(Modifier)
        if (index < sorted.lastIndex) TextActionButton(stringResource(R.string.day_next), { onOpenDay(sorted[index + 1].lagertagId) })
    }
}
