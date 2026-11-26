package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.Instant

/** Das Wort zu einem Wert der Anwesenheit: Da, Frei, Offen. */
@Composable
private fun Attendance.word(): String = stringResource(
    when (this) {
        Attendance.PRESENT -> R.string.att_present
        Attendance.ABSENT -> R.string.att_absent
        Attendance.OPEN -> R.string.att_open
    },
)

/**
 * Anwesenheit (Entwurf L26): die eigene Zeile als Raster aus Tagen und Abschnitten. Ein Tipp auf ein Feld wechselt zwischen Da, Frei
 * und Offen, «Ganzer Tag» setzt alle Abschnitte mit einem Tipp. «Speichern» trägt offene Felder bis zur Frist direkt ein; ein
 * schon bestätigtes Feld (oder jedes nach der Frist) braucht eine Anfrage an die Lagerleitung. Die Lagerleitung entscheidet die
 * Anfragen und setzt die Frist; wer alle lesen darf, sieht die Zusammenfassung. Der Server kennt dafür keine Warteschlange: Ohne
 * Netz scheitert das Speichern, der Entwurf bleibt.
 */
@Composable
fun AttendanceScreen(frame: LiveFrame) {
    val vm = frame.vm
    val rights = frame.rights
    val ownState by vm.own.collectAsState()
    val draft by vm.draft.collectAsState()
    val scheduleState by vm.schedule.collectAsState()
    val summaryState by vm.summary.collectAsState()
    val requestsState by vm.requests.collectAsState()
    val busy by vm.busy.collectAsState()
    var deadlineDialog by remember { mutableStateOf(false) }
    var requestNote by remember { mutableStateOf(false) }
    // Wer das Recht `anwesenheit.eigene` nicht hat (Rolle «Lesen»), hat keine eigene Zeile; der Server antwortet dort 403.
    val loadsOwn = frame.camp?.permissions()?.may("anwesenheit.eigene") == true
    LaunchedEffect(frame.camp?.id) {
        vm.loadSchedule(silent = vm.schedule.value is ApiResult.Ok)
        if (loadsOwn) vm.loadOwn(silent = vm.own.value is ApiResult.Ok)
        if (rights.readAttendance) vm.loadSummary(silent = vm.summary.value is ApiResult.Ok)
        if (rights.decideAttendance) vm.loadRequests(silent = vm.requests.value is ApiResult.Ok)
    }

    LivePage(frame, stringResource(R.string.att_title), stringResource(R.string.att_subtitle)) {
        val schedule = (scheduleState as? ApiResult.Ok)?.value
        when {
            scheduleState == null || (ownState == null && loadsOwn) -> CardSkeleton(stringResource(R.string.att_loading))
            scheduleState is ApiResult.Failure -> ErrorState((scheduleState as ApiResult.Failure).error, { vm.loadSchedule() })
            schedule == null || schedule.days.isEmpty() || schedule.sections.isEmpty() ->
                StatePage(R.drawable.ic_calendar, stringResource(R.string.att_no_days_title), stringResource(R.string.att_no_days_text))
            else -> {
                val own = (ownState as? ApiResult.Ok)?.value
                val ownError = ownState as? ApiResult.Failure
                LiveColumn {
                    if (ownError != null && own == null) {
                        ErrorState(ownError.error, { vm.loadOwn() }, Modifier.heightIn(min = ERROR_MIN))
                    }
                    if (own != null) {
                        OwnAttendance(frame, schedule, own, draft, busy, onRequest = { requestNote = true }, onDeadline = { deadlineDialog = true })
                    }
                    if (rights.decideAttendance) {
                        when (val result = requestsState) {
                            is ApiResult.Ok -> RequestsSection(frame, result.value, busy)
                            is ApiResult.Failure -> ErrorState(result.error, { vm.loadRequests() }, Modifier.heightIn(min = ERROR_MIN))
                            null -> Unit
                        }
                        if (own == null) DeadlineRow(frame, null, { deadlineDialog = true })
                    }
                    if (rights.readAttendance) {
                        when (val result = summaryState) {
                            is ApiResult.Ok -> SummarySection(LiveRules.summary(result.value, schedule))
                            is ApiResult.Failure -> ErrorState(result.error, { vm.loadSummary() }, Modifier.heightIn(min = ERROR_MIN))
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
    if (deadlineDialog) {
        val current = LiveRules.deadlineDate((ownState as? ApiResult.Ok)?.value?.deadline)
        DateDialog(
            initial = current?.toString(),
            onPick = { date ->
                deadlineDialog = false
                vm.setDeadline(date?.let(::parseDay))
            },
            onDismiss = { deadlineDialog = false },
            clearText = R.string.att_deadline_clear,
        )
    }
    if (requestNote) {
        TextDialog(
            stringResource(R.string.att_request_title), stringResource(R.string.att_request_note), "", maxChars = NOTE_CHARS,
            confirmText = stringResource(R.string.att_request_send), allowEmpty = true, multiline = true,
            onConfirm = { text -> requestNote = false; vm.requestAttendance(text) }, onDismiss = { requestNote = false },
        )
    }
}

private val ERROR_MIN = 320.dp
private const val NOTE_CHARS = 500

/** Die eigene Zeile: Name, Raster, Hinweis zur Frist, «Speichern» und die Anfragen. */
@Composable
private fun OwnAttendance(frame: LiveFrame, schedule: Schedule, own: AttendanceOwn, draft: Map<AttendanceKey, Attendance>, busy: Boolean, onRequest: () -> Unit, onDeadline: () -> Unit) {
    val vm = frame.vm
    val now = remember { Instant.now() }
    val passed = LiveRules.deadlinePassed(own.deadline, now)
    val plan = LiveRules.plan(own, draft, passed)
    val editable = frame.rights.ownAttendance
    val sections = schedule.sections.sortedBy { it.order }
    val vertical = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

    Text(frame.self.display, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    own.deadline?.let { deadline ->
        val date = LiveRules.deadlineDate(deadline)
        Text(
            stringResource(if (passed) R.string.att_deadline_passed else R.string.att_deadline_until, date?.let { formatDay(it.toString()) } ?: deadline),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (frame.rights.decideAttendance) DeadlineRow(frame, own.deadline, onDeadline)

    if (!vertical) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            sections.forEach { section ->
                Text(section.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
    }
    schedule.days.sortedBy { it.date }.forEach { day ->
        val title = dayTitle(day.date)
        val values = sections.map { s ->
            val key = AttendanceKey(day.id, s.id)
            draft[key] ?: LiveRules.valueOf(own, day.id, s.id)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (editable) {
                    val wholeDay = stringResource(R.string.att_whole_day)
                    TextActionButton(wholeDay, { vm.tapDay(day.id, sections, values) }, Modifier.rowAction(wholeDay, title))
                }
            }
            if (vertical) {
                sections.forEachIndexed { i, section ->
                    val key = AttendanceKey(day.id, section.id)
                    AttendanceCell(title, section.name, values[i], changed = key in draft, waiting = LiveRules.waitingFor(own, key) != null, editable = editable, showName = true, modifier = Modifier.fillMaxWidth()) {
                        vm.tapCell(key, values[i])
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    sections.forEachIndexed { i, section ->
                        val key = AttendanceKey(day.id, section.id)
                        AttendanceCell(title, section.name, values[i], changed = key in draft, waiting = LiveRules.waitingFor(own, key) != null, editable = editable, showName = false, modifier = Modifier.weight(1f)) {
                            vm.tapCell(key, values[i])
                        }
                    }
                }
            }
        }
    }
    if (editable) {
        Text(stringResource(R.string.att_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (plan.requests.isNotEmpty()) {
            NoticeBanner(NoticeKind.WARNING, stringResource(R.string.att_request_needed_title) + ". " + stringResource(R.string.att_request_needed_text))
            OutlinedPillButton(stringResource(R.string.att_request_send), onRequest, Modifier.fillMaxWidth(), loading = busy)
        }
        PrimaryButton(stringResource(R.string.save), vm::saveAttendance, Modifier.fillMaxWidth(), enabled = plan.direct.isNotEmpty(), loading = busy)
        if (draft.isNotEmpty()) TextActionButton(stringResource(R.string.att_discard), vm::discardDraft)
    }
    OwnRequests(own.requests)
}

private const val LARGE_FONT_SCALE = 1.3f

/**
 * Ein Feld des Rasters (mindestens 48 dp): Symbol und Wort, nie nur Farbe. «Geändert» (noch nicht gespeichert) und «Anfrage offen»
 * stehen als Wort im Satz für den Screenreader.
 */
@Composable
private fun AttendanceCell(
    day: String,
    section: String,
    value: Attendance,
    changed: Boolean,
    waiting: Boolean,
    editable: Boolean,
    showName: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = MaterialTheme.cevi
    val shape = MaterialTheme.shapes.small
    val (container, content, icon) = when (value) {
        Attendance.PRESENT -> Triple(colors.successContainer, colors.onSuccessContainer, R.drawable.ic_check)
        Attendance.ABSENT -> Triple(scheme.surfaceContainerHigh, scheme.onSurface, R.drawable.ic_cross)
        Attendance.OPEN -> Triple(scheme.surfaceContainerLow, scheme.onSurfaceVariant, R.drawable.ic_question)
    }
    val word = value.word()
    val state = listOfNotNull(
        word,
        if (changed) stringResource(R.string.att_changed) else null,
        if (waiting) stringResource(R.string.att_request_waiting) else null,
    ).joinToString(", ")
    val description = "$day, $section"
    Box(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .background(container)
            .border(if (changed) 2.dp else 1.dp, if (changed) colors.accent else scheme.outlineVariant, shape)
            .then(if (editable) Modifier.clickable(onClickLabel = stringResource(R.string.att_cell_change), role = Role.Button, onClick = onTap) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = state
            }
            .padding(horizontal = Spacing.s2, vertical = Spacing.s2),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.padding(end = 2.dp))
            Text(if (showName) "$section: $word" else word, style = MaterialTheme.typography.labelLarge, color = content)
            if (waiting) Text("…", style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** Die Frist des Teams (nur die Lagerleitung ändert sie). */
@Composable
private fun DeadlineRow(frame: LiveFrame, deadline: String?, onChange: () -> Unit) {
    val date = LiveRules.deadlineDate(deadline)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (date != null) stringResource(R.string.att_deadline_value, formatDay(date.toString())) else stringResource(R.string.att_deadline_none),
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
        )
        if (frame.rights.decideAttendance) TextActionButton(stringResource(R.string.att_deadline_change), onChange)
    }
}

/** Die eigenen Anfragen mit ihrem Stand als Wort. */
@Composable
private fun OwnRequests(requests: List<AttendanceRequest>) {
    if (requests.isEmpty()) return
    LiveSection(stringResource(R.string.att_my_requests)) {
        requests.take(REQUEST_LIST_MAX).forEach { request ->
            val kind = when (request.status) {
                RequestStatus.OPEN -> ChipKind.WAITING
                RequestStatus.ACCEPTED -> ChipKind.YES
                RequestStatus.DECLINED -> ChipKind.NO
            }
            val status = stringResource(
                when (request.status) {
                    RequestStatus.OPEN -> R.string.att_status_open
                    RequestStatus.ACCEPTED -> R.string.att_status_accepted
                    RequestStatus.DECLINED -> R.string.att_status_declined
                },
            )
            val line = stringResource(R.string.att_request_line, request.value.word())
            InfoCard(description = "$line, $status") {
                Text(line, style = MaterialTheme.typography.bodyLarge)
                StatusChip(kind, status)
            }
        }
    }
}

private const val REQUEST_LIST_MAX = 10

/** Die offenen Anfragen des Teams (nur die Lagerleitung): annehmen oder ablehnen, einzeln oder alle. */
@Composable
private fun RequestsSection(frame: LiveFrame, requests: List<AttendanceRequest>, busy: Boolean) {
    val vm = frame.vm
    val names = remember(frame.people) { frame.people.associate { it.id to it.display } }
    LiveSection(stringResource(R.string.att_requests_title)) {
        if (requests.isEmpty()) {
            Text(stringResource(R.string.att_requests_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@LiveSection
        }
        requests.forEach { request ->
            val who = names[request.personId] ?: stringResource(R.string.att_person_unknown)
            val line = stringResource(R.string.att_requests_line, who, request.value.word())
            InfoCard(description = line) {
                Text(line, style = MaterialTheme.typography.bodyLarge)
                request.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    val accept = stringResource(R.string.att_accept)
                    val decline = stringResource(R.string.att_decline)
                    TextActionButton(accept, { vm.decide(true, listOf(request)) }, Modifier.rowAction(accept, line))
                    TextActionButton(decline, { vm.decide(false, listOf(request)) }, Modifier.rowAction(decline, line))
                }
            }
        }
        if (requests.size > 1) {
            val batch = requests.take(DECIDE_MAX)
            PrimaryButton(stringResource(R.string.att_accept_all, batch.size), { vm.decide(true, batch) }, Modifier.fillMaxWidth(), loading = busy)
            OutlinedPillButton(stringResource(R.string.att_decline_all, batch.size), { vm.decide(false, batch) }, Modifier.fillMaxWidth(), loading = busy)
        }
    }
}

private const val DECIDE_MAX = 50

/** Die Zusammenfassung: je Tag und Abschnitt, wie viele da, frei und offen sind. Es stehen nur Zahlen da, keine Namen. */
@Composable
private fun SummarySection(days: List<AttendanceSummaryDay>) {
    LiveSection(stringResource(R.string.att_summary_title)) {
        days.forEach { day ->
            val title = dayTitle(day.day.date)
            InfoCard(description = title) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                day.sections.forEach { row ->
                    val line = stringResource(R.string.att_summary_line, row.section.name, row.present, row.absent, row.open)
                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { contentDescription = line })
                }
            }
        }
    }
}
