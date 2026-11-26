package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("de-CH"))

private fun monthTitle(month: YearMonth): String = MONTH_FORMAT.format(month)

private const val WEEK_DAYS = 7
private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/**
 * Zeitplan (Entwurf T31): Lagertage, Meilensteine, Höcks und Fristen der Aufgaben als Liste nach Datum und als Monatsansicht.
 * Am Handy ist es eine Spalte, auf dem Tablet dieselbe in begrenzter Breite. Die Lagerleitung übernimmt den Vorschlag des Servers
 * für fehlende Lagertage und Abschnitte. Meilensteine ändern und verschieben (mit Vorschau) hat der Entwurf nicht.
 */
@Composable
fun TimelineScreen(frame: LiveFrame, archive: ArchiveViewModel, onOpenTask: (String) -> Unit, quietRessorts: List<String> = emptyList(), onOpenRessorts: (() -> Unit)? = null) {
    val state by archive.timeline.collectAsState()
    val proposal by archive.proposal.collectAsState()
    val busy by archive.busy.collectAsState()
    var calendar by rememberSaveable { mutableStateOf(false) }
    var proposalHidden by remember { mutableStateOf(false) }
    var quietHidden by remember { mutableStateOf(false) }
    LaunchedEffect(frame.camp?.id) { archive.loadTimeline(silent = archive.timeline.value is ApiResult.Ok) }

    ArchivePage(frame, archive, stringResource(R.string.timeline_title)) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.timeline_loading))
            is ApiResult.Failure -> ErrorState(result.error, { archive.loadTimeline() })
            is ApiResult.Ok -> {
                val entries = remember(result.value) { TimelineRules.entries(result.value) }
                val offer = proposal?.takeIf { !proposalHidden }
                if (entries.isEmpty()) {
                    StatePage(R.drawable.ic_calendar, stringResource(R.string.timeline_empty_title), stringResource(R.string.timeline_empty_text)) {
                        if (offer != null) PrimaryButton(stringResource(R.string.timeline_proposal_action), { archive.adoptProposal() }, loading = busy)
                    }
                } else {
                    LiveColumn {
                        offer?.let {
                            ActionBanner(
                                NoticeKind.INFO,
                                stringResource(R.string.timeline_proposal_title) + ". " + stringResource(R.string.timeline_proposal_text),
                                stringResource(R.string.timeline_proposal_action), { archive.adoptProposal() }, { proposalHidden = true },
                            )
                        }
                        if (quietRessorts.isNotEmpty() && !quietHidden) {
                            ActionBanner(
                                NoticeKind.INFO,
                                stringResource(R.string.timeline_quiet_title) + ". " + stringResource(R.string.timeline_quiet_text, quietRessorts.joinToString(", ")),
                                onOpenRessorts?.let { stringResource(R.string.timeline_quiet_action) }, onOpenRessorts, { quietHidden = true },
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            PickPill(stringResource(R.string.timeline_tab_list), !calendar, { calendar = false }, role = Role.Tab)
                            PickPill(stringResource(R.string.timeline_tab_calendar), calendar, { calendar = true }, role = Role.Tab)
                        }
                        if (calendar) MonthView(entries, onOpenTask) else TimelineList(entries, onOpenTask)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineList(entries: List<TimelineEntry>, onOpenTask: (String) -> Unit) {
    TimelineRules.byMonth(entries).forEach { (month, rows) ->
        LiveSection(monthTitle(month)) { rows.forEach { TimelineRow(it, onOpenTask) } }
    }
}

/** Die Texte einer Zeile: Titel, zweite Zeile und Art. Aus ihnen entsteht auch der ganze Satz für den Screenreader. */
private class RowTexts(val title: String, val detail: String, val kind: String)

@Composable
private fun rowTexts(entry: TimelineEntry): RowTexts = when (entry.kind) {
    TimelineKind.DAY -> RowTexts(
        entry.title ?: stringResource(R.string.timeline_day_number, entry.number ?: 0),
        dayTitle(entry.date.toString()),
        stringResource(R.string.timeline_kind_day),
    )
    TimelineKind.MILESTONE -> RowTexts(
        entry.title.orEmpty(),
        listOfNotNull(formatDay(entry.date.toString()), entry.detail).joinToString(", "),
        stringResource(R.string.timeline_kind_milestone),
    )
    TimelineKind.MEETING -> RowTexts(
        entry.title.orEmpty(),
        listOfNotNull(formatMeetingDay(entry.date.toString()), entry.detail).joinToString(", "),
        stringResource(R.string.timeline_kind_meeting),
    )
    TimelineKind.DUE -> RowTexts(
        entry.title.orEmpty(),
        if (entry.detail != null) stringResource(R.string.timeline_due_line_ressort, formatDay(entry.date.toString()), entry.detail) else stringResource(R.string.timeline_due_line, formatDay(entry.date.toString())),
        stringResource(R.string.timeline_kind_due),
    )
}

/** Eine Zeile des Zeitplans; eine Frist öffnet ihre Aufgabe. */
@Composable
private fun TimelineRow(entry: TimelineEntry, onOpenTask: (String) -> Unit) {
    val texts = rowTexts(entry)
    val spoken = stringResource(R.string.timeline_row_spoken, texts.kind, texts.title, texts.detail)
    val shape = MaterialTheme.shapes.small
    val open = entry.id?.takeIf { entry.kind == TimelineKind.DUE }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .then(if (open != null) Modifier.clickable(onClickLabel = stringResource(R.string.search_open_named, texts.title), role = Role.Button) { onOpenTask(open) } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = Spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(texts.title, style = MaterialTheme.typography.bodyLarge)
            Text(texts.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip(ChipKind.AGE_GROUP, texts.kind)
    }
}

// ---- Monatsansicht ----

@Composable
private fun MonthView(entries: List<TimelineEntry>, onOpenTask: (String) -> Unit) {
    val today = remember { todayInCamp() }
    var month by rememberSaveable { mutableStateOf(TimelineRules.startMonth(entries, today).toString()) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = YearMonth.parse(month)
    Column(Modifier.fillMaxWidth().widthIn(max = Sizes.contentMaxWidth), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(HeaderButton(R.drawable.ic_back, stringResource(R.string.timeline_previous_month)) { month = shown.minusMonths(1).toString(); picked = null })
            Text(
                monthTitle(shown), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            RoundButton(HeaderButton(R.drawable.ic_chevron_right, stringResource(R.string.timeline_next_month)) { month = shown.plusMonths(1).toString(); picked = null })
        }
        MonthGridFrame {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                    WEEKDAYS.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                }
                TimelineRules.cells(shown).chunked(WEEK_DAYS).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                Box(Modifier.weight(1f))
                            } else {
                                DayCell(date, today, TimelineRules.kindsOn(entries, date), date.toString() == picked, { picked = date.toString() }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        Legend()
        val day = parseDay(picked)
        if (day != null) {
            val rows = entries.filter { it.date == day }
            LiveSection(dayTitle(day.toString())) {
                if (rows.isEmpty()) {
                    Text(stringResource(R.string.timeline_day_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    rows.forEach { TimelineRow(it, onOpenTask) }
                }
            }
        }
    }
}

/**
 * Jede Tageszelle ist mindestens 48 dp breit: Reicht der Platz knapp nicht, ragt das Raster symmetrisch in den Seitenrand
 * (bis `GRID_BLEED`); ist es noch schmaler, scrollt es seitwärts, statt Zellen zu kürzen.
 */
@Composable
private fun MonthGridFrame(content: @Composable () -> Unit) {
    val minWidth = Sizes.touchTarget * WEEK_DAYS
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, minWidth)
        if (width - maxWidth <= GRID_BLEED * 2) {
            Box(Modifier.requiredWidth(width)) { content() }
        } else {
            Box(Modifier.horizontalScroll(rememberScrollState())) { Box(Modifier.width(width)) { content() } }
        }
    }
}

private val GRID_BLEED = Spacing.s3

@Composable
private fun kindLabel(kind: TimelineKind): String = stringResource(
    when (kind) {
        TimelineKind.DAY -> R.string.timeline_kind_day
        TimelineKind.MILESTONE -> R.string.timeline_kind_milestone
        TimelineKind.MEETING -> R.string.timeline_kind_meeting
        TimelineKind.DUE -> R.string.timeline_legend_due
    },
)

/** Ein Tag: die Zahl und je Art ein Zeichen; der Screenreader hört Tag und Arten als ganzen Satz. */
@Composable
private fun DayCell(date: LocalDate, today: LocalDate, kinds: Set<TimelineKind>, selected: Boolean, onPick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val dayText = dayTitle(date.toString())
    val spoken = if (kinds.isEmpty()) dayText else stringResource(R.string.timeline_cell_spoken, dayText, kinds.map { kindLabel(it) }.joinToString(", "))
    Column(
        modifier
            .heightIn(min = Sizes.touchTarget + 8.dp)
            .focusRing(shape)
            .clip(shape)
            .then(if (selected) Modifier.background(MaterialTheme.cevi.accent) else if (date == today) Modifier.border(2.dp, MaterialTheme.cevi.accent, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onPick)
            .semantics(mergeDescendants = true) { contentDescription = spoken; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (kinds.isEmpty()) FontWeight.Normal else FontWeight.Bold,
            color = if (selected) MaterialTheme.cevi.onAccent else scheme.onSurface,
        )
        Row(Modifier.padding(top = Spacing.s1), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TimelineKind.entries.filter { it in kinds }.forEach { Mark(it, selected) }
        }
    }
}

/** Das Zeichen einer Art: Form und Farbe unterscheiden sich, die Legende nennt sie mit Wort. */
@Composable
private fun Mark(kind: TimelineKind, onAccent: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val color: Color = if (onAccent) MaterialTheme.cevi.onAccent else when (kind) {
        TimelineKind.MILESTONE -> scheme.primary
        TimelineKind.MEETING -> MaterialTheme.cevi.accent
        TimelineKind.DAY -> scheme.outline
        TimelineKind.DUE -> scheme.onSurface
    }
    val shape: Shape = when (kind) {
        TimelineKind.MILESTONE -> CircleShape
        TimelineKind.MEETING -> RoundedCornerShape(2.dp)
        TimelineKind.DAY -> CircleShape
        TimelineKind.DUE -> RoundedCornerShape(1.dp)
    }
    val size = if (kind == TimelineKind.DUE) Modifier.size(width = 10.dp, height = 3.dp) else Modifier.size(7.dp)
    val base = Modifier.then(size)
    Box(if (kind == TimelineKind.DAY) base.border(1.5.dp, color, shape) else base.background(color, shape))
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, horizontalArrangement = Arrangement.spacedBy(Spacing.s4), verticalAlignment = Alignment.CenterVertically) {
        TimelineKind.entries.sortedBy { LEGEND_ORDER.indexOf(it) }.forEach { kind ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
                Mark(kind, onAccent = false)
                Text(kindLabel(kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val LEGEND_ORDER = listOf(TimelineKind.MILESTONE, TimelineKind.MEETING, TimelineKind.DAY, TimelineKind.DUE)
