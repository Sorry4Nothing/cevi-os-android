package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/**
 * Lager-Check «Sind wir bereit?» (kein Entwurf, aus vorhandenen Bausteinen gebaut): oben der Gesamtstand, darunter je Prüfer sein
 * Stand mit den Hinweisen, unten die eigenen Punkte der Lagerleitung. Der Server rechnet; die App zeigt, was er liefert. Ein Prüfer,
 * dessen Quelle die Rolle nicht lesen darf, fehlt in der Antwort und damit hier. Zustand steht immer als Symbol und Wort.
 */
@Composable
fun CheckScreen(frame: LiveFrame, check: CheckViewModel, rights: CheckRights) {
    val state by check.data.collectAsState()
    val busy by check.busy.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<CheckItem?>(null) }
    var renaming by remember { mutableStateOf<CheckItem?>(null) }
    var deleting by remember { mutableStateOf<CheckItem?>(null) }
    LaunchedEffect(frame.camp?.id) { check.load(silent = check.data.value is ApiResult.Ok) }

    CampPage(
        title = stringResource(R.string.check_title),
        subtitle = frame.camp?.title,
        camp = frame.camp, queue = frame.queue, online = frame.online, onOpenQueue = frame.onOpenQueue, writer = check.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), frame.onBack),
        banners = { CheckNoticeBanner(check) },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.check_loading))
            is ApiResult.Failure -> ErrorState(result.error, { check.load() })
            is ApiResult.Ok -> {
                val data = result.value
                val points = data.points
                LiveColumn {
                    data.check?.let { SummaryCard(it) }
                    data.check?.let { current ->
                        // Sind die eigenen Punkte unten zu sehen, steht ihr Prüfer nicht noch einmal als Karte da.
                        CheckRules.sortedCheckers(current).filterNot { it.id == OWN_CHECKER && points != null }.forEach { CheckerCard(it) }
                    }
                    if (points != null) {
                        PointsSection(
                            points = points, rights = rights, busy = busy,
                            onToggle = { point -> check.setDone(point, !point.done) },
                            onActions = { actions = it },
                            onAdd = { adding = true },
                        )
                    }
                }
            }
        }
    }
    if (adding) {
        TextDialog(
            title = stringResource(R.string.check_add_title), label = stringResource(R.string.check_point_label), initial = "",
            maxChars = CheckRules.TITLE_MAX, confirmText = stringResource(R.string.check_add_save),
            onConfirm = { text -> check.addPoint(text) { adding = false } }, onDismiss = { adding = false },
        )
    }
    actions?.let { point ->
        ActionSheet(
            point.title,
            listOf(
                stringResource(R.string.check_rename) to { renaming = point },
                stringResource(R.string.task_delete) to { deleting = point },
            ),
        ) { actions = null }
    }
    renaming?.let { point ->
        TextDialog(
            title = stringResource(R.string.check_rename), label = stringResource(R.string.check_point_label), initial = point.title,
            maxChars = CheckRules.TITLE_MAX, confirmText = stringResource(R.string.check_rename_save),
            onConfirm = { text -> check.rename(point, text) { renaming = null } }, onDismiss = { renaming = null },
        )
    }
    deleting?.let { point ->
        ConfirmDeleteDialog(
            stringResource(R.string.check_delete_title), stringResource(R.string.check_delete_text, point.title),
            onConfirm = { deleting = null; check.deletePoint(point) }, onDismiss = { deleting = null },
        )
    }
}

private const val OWN_CHECKER = "eigene"

@Composable
private fun CheckNoticeBanner(check: CheckViewModel) {
    val notice by check.notice.collectAsState()
    val text = when (notice) {
        null -> return
        CheckNotice.POINT_ADDED -> R.string.check_notice_added
        CheckNotice.POINT_DELETED -> R.string.check_notice_deleted
    }
    ActionBanner(NoticeKind.INFO, stringResource(text), null, null, check::dismissNotice)
}

private fun CheckLevel.chip(): ChipKind = when (this) {
    CheckLevel.READY -> ChipKind.YES
    CheckLevel.NOTES -> ChipKind.REVIEW
    CheckLevel.OPEN -> ChipKind.NO
}

@Composable
private fun CheckLevel.label(): String = stringResource(
    when (this) {
        CheckLevel.READY -> R.string.check_level_ready
        CheckLevel.NOTES -> R.string.check_level_notes
        CheckLevel.OPEN -> R.string.check_level_open
    },
)

/** Der Gesamtstand in einem Satz, dazu der Tag der Rechnung; wird angesagt, wenn er sich beim Neuladen ändert. */
@Composable
private fun SummaryCard(check: CampCheck) {
    val blocking = CheckRules.blockingCount(check)
    InfoCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        StatusChip(check.level.chip(), check.level.label())
        Text(
            when (check.level) {
                CheckLevel.READY -> stringResource(R.string.check_summary_ready)
                CheckLevel.NOTES -> stringResource(R.string.check_summary_notes)
                CheckLevel.OPEN -> pluralStringResource(R.plurals.check_summary_open, blocking.coerceAtLeast(1), blocking.coerceAtLeast(1))
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.check_as_of, formatDayLong(check.date)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckerCard(checker: CheckChecker) {
    InfoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
            Text(checker.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            StatusChip(checker.level.chip(), checker.level.label())
        }
        if (checker.notes.isEmpty()) {
            Text(stringResource(R.string.check_nothing_to_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        checker.notes.forEach { NoteBlock(it) }
    }
}

@Composable
private fun NoteBlock(note: CheckNote) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        StatusChip(if (note.blocking) ChipKind.NO else ChipKind.REVIEW, stringResource(if (note.blocking) R.string.check_note_open else R.string.check_note_hint))
        Text(note.text, style = MaterialTheme.typography.bodyLarge)
        note.examples.forEach { example ->
            val line = example.date?.let { stringResource(R.string.check_example_dated, example.title, formatDay(it)) } ?: example.title
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.s3))
        }
        // Der Server nennt höchstens fünf Beispiele; die Gesamtzahl sagt, wie viele es sonst noch gibt.
        val more = (note.count ?: 0) - note.examples.size
        if (note.examples.isNotEmpty() && more > 0) {
            Text(stringResource(R.string.check_more, more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.s3))
        }
    }
}

@Composable
private fun PointsSection(
    points: List<CheckItem>,
    rights: CheckRights,
    busy: Boolean,
    onToggle: (CheckItem) -> Unit,
    onActions: (CheckItem) -> Unit,
    onAdd: () -> Unit,
) {
    LiveSection(stringResource(R.string.check_own_title)) {
        if (points.isEmpty()) {
            Text(
                stringResource(if (rights.edit) R.string.check_own_empty_edit else R.string.check_own_empty),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CheckRules.sortedPoints(points).forEach { point ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { CheckRow(point.title, null, point.done, { onToggle(point) }, enabled = rights.edit && !busy) }
                if (rights.edit) RoundButton(HeaderButton(R.drawable.ic_more, stringResource(R.string.check_point_actions, point.title)) { onActions(point) })
            }
        }
        if (rights.edit) {
            if (CheckRules.canAdd(points)) OutlinedPillButton(stringResource(R.string.check_add), onAdd, icon = R.drawable.ic_plus)
            else Text(stringResource(R.string.check_limit, CheckRules.MAX_POINTS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
