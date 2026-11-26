package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Die eine Hauptaktion des Starts: «Schnell erfassen». Es gibt sie nur, wo die Rolle Aufgaben anlegen darf. */
@Composable
fun StartFab(vm: TasksViewModel, rights: TaskRights) {
    if (rights.edit) PrimaryButton(stringResource(R.string.start_quick), vm::openQuick, icon = R.drawable.ic_plus)
}

/**
 * Start der Lagerleitung und der Leitung (Entwurf L2): Countdown oder «Tag X von Y», Fortschritt, überfällige und eigene Aufgaben,
 * Wetterwarnung, nächster Höck, «Heute im Lager» und «Neu für dich». Was die Rolle nicht lesen darf, fehlt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.LeadershipStart(
    vm: TasksViewModel,
    camp: Camp,
    onOpenTask: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTarget: (CampTarget) -> Unit = {},
    onOpenMeeting: (String) -> Unit = {},
    onOpenToday: () -> Unit = {},
) {
    val data by vm.start.collectAsState()
    val reloads by vm.reloads.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val today = remember { todayInCamp() }
    LaunchedEffect(camp.id, reloads) { vm.loadStart(silent = reloads > 0) }

    when (val start = data.start) {
        null -> CardSkeleton(stringResource(R.string.camp_start_loading), count = 2)
        is ApiResult.Failure -> ErrorState(start.error, { vm.loadStart(silent = false) }, Modifier.heightIn(min = 420.dp))
        is ApiResult.Ok -> {
            val value = start.value
            ProgressCard(value, camp)
            nextWeatherWarning(data.weather, today)?.let { (day, warnings) ->
                val names = warnings.map { stringResource(weatherText(it)) }
                NoticeBanner(
                    NoticeKind.WARNING,
                    stringResource(R.string.start_weather, weatherDayName(day, today), names.joinToString(", ")),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            value.own?.let { own ->
                StartSection(R.string.start_for_you) {
                    if (own.isEmpty()) {
                        Text(stringResource(R.string.start_nothing_open), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    own.sortedWith(compareBy({ TaskRules.dueDate(it) ?: LocalDate.MAX }, { it.order })).take(START_TASKS).forEach { task ->
                        StartTaskRow(task, today, onOpen = { onOpenTask(task.id) })
                    }
                    if (own.size > START_TASKS) TextActionButton(stringResource(R.string.start_all_mine, own.size), onOpenTasks)
                }
            }
            data.meeting?.let { meeting ->
                StartSection(R.string.start_next_meeting) {
                    val shape = MaterialTheme.shapes.small
                    val whenText = when (val label = dayLabel(meeting.date, today)) {
                        DayLabel.Today -> stringResource(R.string.day_today)
                        DayLabel.Tomorrow -> stringResource(R.string.day_tomorrow)
                        is DayLabel.Name -> label.text
                    }
                    val line = listOfNotNull(whenText, meeting.time?.takeIf { it.isNotBlank() }).joinToString(", ") +
                        (meeting.agendaCount?.let { ", " + pluralStringResource(R.plurals.start_agenda, it, it) } ?: "")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .focusRing(shape)
                            .clip(shape)
                            .clickable(onClickLabel = stringResource(R.string.meetings_open), role = Role.Button) { onOpenMeeting(meeting.id) }
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                        meeting.place?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            data.today?.let { TodaySection(it, camp, onOpenToday) }
            NewsSection(data.news, (tasks as? ApiResult.Ok)?.value.orEmpty(), onOpenTask, onOpenTarget)
        }
    }
}

private const val START_TASKS = 5

/** Start des Teams (Entwurf L3): meine Aufgaben und was es Neues gibt. Dienste, Zimmer und Fahrten folgen mit den Schnitten A3 und A4. */
@Composable
fun ColumnScope.TeamStart(
    vm: TasksViewModel,
    camp: Camp,
    onOpenTask: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTarget: (CampTarget) -> Unit = {},
    live: LiveViewModel? = null,
    onOpenToday: () -> Unit = {},
    onOpenShifts: () -> Unit = {},
    onOpenPacking: () -> Unit = {},
) {
    val data by vm.start.collectAsState()
    val reloads by vm.reloads.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val today = remember { todayInCamp() }
    LaunchedEffect(camp.id, reloads) { vm.loadStart(silent = reloads > 0) }

    when (val state = tasks) {
        null -> CardSkeleton(stringResource(R.string.camp_start_loading), count = 2)
        is ApiResult.Failure -> ErrorState(state.error, vm::retryTasks, Modifier.heightIn(min = 420.dp))
        is ApiResult.Ok -> {
            val own = TaskRules.mine(state.value, vm.self.id, MineSort.DUE, today).filterNot { it.done }
            if (live != null) TeamArea(live, camp, onOpenToday, onOpenShifts, onOpenPacking)
            if (own.isEmpty() && data.news.isNullOrEmpty() && live == null) {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s6), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(stringResource(R.string.team_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.team_empty_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (own.isNotEmpty()) {
                StartSection(R.string.team_my_tasks) {
                    own.take(START_TASKS).forEach { task -> StartTaskRow(task, today, onOpen = { onOpenTask(task.id) }) }
                    if (own.size > START_TASKS) TextActionButton(stringResource(R.string.start_all_mine, own.size), onOpenTasks)
                }
            }
            NewsSection(data.news, state.value, onOpenTask, onOpenTarget)
        }
    }
}

@Composable
private fun StartSection(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Countdown, Fortschrittsbalken und die Zähler (Entwurf L2). Der Balken sagt dem Screenreader den Anteil in Prozent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressCard(start: CampStart, camp: Camp) {
    val shape = MaterialTheme.shapes.medium
    val label = when (val label = countdownLabel(start.countdown)) {
        is CountdownLabel.DaysToGo -> pluralStringResource(R.plurals.start_days_to_go, label.days, label.days)
        CountdownLabel.StartsToday -> stringResource(R.string.start_starts_today)
        is CountdownLabel.DayOf -> stringResource(R.string.start_day_of, label.day, label.total)
        CountdownLabel.Over -> stringResource(R.string.start_over)
    }
    val tasks = start.progress.tasks
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(campDates(camp.from, camp.until), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (tasks != null && tasks.total > 0) {
            val percent = percentDone(tasks)
            val name = stringResource(R.string.start_progress_label)
            val text = stringResource(R.string.start_percent_done, percent)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(Pill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .semantics(mergeDescendants = true) {
                        contentDescription = name
                        stateDescription = text
                        progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
                    },
            ) {
                Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().clip(Pill).background(MaterialTheme.cevi.accent))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                if (tasks.overdue > 0) StatusChip(ChipKind.NO, stringResource(R.string.start_overdue_count, tasks.overdue))
                start.own?.size?.takeIf { it > 0 }?.let { StatusChip(ChipKind.PENDING, stringResource(R.string.start_own_count, it)) }
            }
        }
        start.progress.milestones?.next?.let { next ->
            Text(
                stringResource(R.string.start_next_milestone, next.title, formatDay(next.date)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Eine Aufgabe in einer Liste des Starts: Titel, Person und Frist, Zustand als Etikett. */
@Composable
internal fun StartTaskRow(task: CampTask, today: LocalDate, onOpen: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val overdue = TaskRules.overdueDays(task, today)
    val line = if (overdue != null) {
        stringResource(R.string.start_task_overdue, task.assignee?.display ?: stringResource(R.string.task_unassigned), pluralStringResource(R.plurals.task_overdue_since, overdue, overdue).lowercase())
    } else {
        taskPersonLine(task)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = stringResource(R.string.task_open), role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = task.title + ", " + line }
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            overdue != null -> StatusChip(ChipKind.NO, stringResource(R.string.task_overdue))
            TaskRules.isSoon(task, today) -> StatusChip(ChipKind.PENDING, stringResource(R.string.task_soon))
        }
    }
}

/** «Heute im Lager»: Tageschef und Zahl der Dienste; vor dem ersten Lagertag steht, ab wann es das gibt. */
@Composable
private fun TodaySection(today: CampToday, camp: Camp, onOpenToday: () -> Unit) {
    StartSection(R.string.start_today) {
        if (today.lagertag == null) {
            Text(stringResource(R.string.start_today_empty, formatDay(camp.from)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val lead = today.dayPage?.page?.dayLead
            val leadName = listOfNotNull(lead?.name, lead?.ceviName?.let { "($it)" }).joinToString(" ")
            Text(
                if (leadName.isNotBlank()) stringResource(R.string.start_day_lead, leadName) else stringResource(R.string.start_day_lead_none),
                style = MaterialTheme.typography.bodyLarge,
            )
            today.shifts?.let { shifts ->
                val count = shifts.duties.size
                if (count > 0) Text(pluralStringResource(R.plurals.start_duties, count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextActionButton(stringResource(R.string.start_open_today), onOpenToday)
        }
    }
}

/** «Neu für dich»: die letzten Benachrichtigungen als Sätze. Sie tragen keinen Text; die Sätze entstehen aus Art und Ziel. */
@Composable
private fun NewsSection(news: List<Notification>?, tasks: List<CampTask>, onOpenTask: (String) -> Unit, onOpenTarget: (CampTarget) -> Unit) {
    val entries = news.orEmpty().filterNot { it.read }.take(NEWS_ENTRIES)
    if (entries.isEmpty()) return
    StartSection(R.string.start_news) {
        entries.forEach { entry ->
            val task = if (entry.objectType.startsWith("aufgabe")) tasks.firstOrNull { it.id == entry.objectId } else null
            val text = newsText(entry.kind, task?.title)
            val shape = MaterialTheme.shapes.medium
            val target = campTargetFor(entry.kind, entry.objectType, entry.objectId)
            val opens = target != CampTarget.Start && (target !is CampTarget.Task || task != null)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .focusRing(shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .then(
                        if (opens) {
                            Modifier.clickable(onClickLabel = stringResource(if (task != null) R.string.task_open else R.string.notif_open), role = Role.Button) {
                                if (target is CampTarget.Task) onOpenTask(target.id) else onOpenTarget(target)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text, style = MaterialTheme.typography.titleMedium)
                Text(newsTime(entry.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextActionButton(stringResource(R.string.notif_all), { onOpenTarget(CampTarget.Notifications) })
    }
}

private const val NEWS_ENTRIES = 3

@Composable
internal fun newsText(kind: String, title: String?): String = when (kind) {
    "aufgabe_zugeteilt" -> if (title != null) stringResource(R.string.news_assigned, title) else stringResource(R.string.news_assigned_generic)
    "aufgabe_geaendert" -> if (title != null) stringResource(R.string.news_changed, title) else stringResource(R.string.news_changed_generic)
    "frist" -> if (title != null) stringResource(R.string.news_due, title) else stringResource(R.string.news_due_generic)
    "erwaehnung" -> stringResource(R.string.news_mention)
    "pinnwand_wichtig" -> stringResource(R.string.news_pinboard)
    "eingang_antwort" -> stringResource(R.string.news_reply)
    "diensttausch_anfrage" -> stringResource(R.string.news_swap_request)
    "diensttausch_entscheid" -> stringResource(R.string.news_swap_decision)
    "anwesenheit_anfrage" -> stringResource(R.string.news_attendance)
    else -> stringResource(R.string.news_other)
}

/** «Heute, 20:14», «Gestern, 20:14» oder «15. März, 20:14» in der Zeit des Lagers. */
@Composable
internal fun newsTime(iso: String): String {
    val zone = ZoneId.of("Europe/Zurich")
    val moment = runCatching { Instant.parse(iso).atZone(zone) }.getOrNull() ?: return iso
    val time = "%02d:%02d".format(moment.hour, moment.minute)
    val today = LocalDate.now(zone)
    return when (moment.toLocalDate()) {
        today -> stringResource(R.string.time_today, time)
        today.minusDays(1) -> stringResource(R.string.time_yesterday, time)
        else -> formatDay(moment.toLocalDate().toString()) + ", " + time
    }
}

@Composable
internal fun weatherDayName(day: LocalDate, today: LocalDate): String = when (java.time.temporal.ChronoUnit.DAYS.between(today, day)) {
    0L -> stringResource(R.string.day_today)
    1L -> stringResource(R.string.day_tomorrow)
    else -> weekdayName(day)
}

@androidx.annotation.StringRes
internal fun weatherText(code: String): Int = when (code) {
    "gewitter" -> R.string.weather_thunder
    "boeen" -> R.string.weather_gusts
    "hitze" -> R.string.weather_heat
    "regen" -> R.string.weather_rain
    else -> R.string.weather_other
}
