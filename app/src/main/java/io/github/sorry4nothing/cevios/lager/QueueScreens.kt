package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("d.M.")
private val SWISS_ZONE = ZoneId.of("Europe/Zurich")

@Composable
private fun timeText(millis: Long): String {
    val at = Instant.ofEpochMilli(millis).atZone(SWISS_ZONE)
    val time = TIME.format(at)
    return if (at.toLocalDate() == LocalDate.now(SWISS_ZONE)) stringResource(R.string.change_time_today, time) else "${DAY.format(at)} $time"
}

@StringRes
private fun reasonText(code: String?) = when (code) {
    "KEINE_BERECHTIGUNG" -> R.string.change_reason_forbidden
    "NICHT_GEFUNDEN" -> R.string.change_reason_not_found
    "LAGER_GESCHLOSSEN" -> R.string.change_reason_closed
    "UNGUELTIGE_EINGABE", "OPERATION_UNBEKANNT" -> R.string.change_reason_invalid
    else -> R.string.change_reason_unknown
}

@Composable
private fun StateChip(change: QueuedChange) = when (change.state) {
    ChangeState.WAITING -> StatusChip(ChipKind.WAITING, stringResource(R.string.change_waiting))
    ChangeState.SENT -> StatusChip(ChipKind.SENT, stringResource(R.string.change_sent))
    ChangeState.CONFLICT -> StatusChip(ChipKind.REVIEW, stringResource(R.string.change_conflict))
    ChangeState.REJECTED -> StatusChip(ChipKind.NO, stringResource(R.string.change_rejected))
}

/**
 * Warteschlange (Entwurf U6): Was ohne Netz entstanden ist, mit dem Stand je Änderung. Gesendete Änderungen bleiben sichtbar, bis
 * die Person den Bildschirm verlässt. Konflikte und Ablehnungen bleiben, bis sie entschieden sind.
 */
@Composable
fun QueueScreen(
    queue: QueueState,
    online: Boolean,
    onSendNow: () -> Unit,
    onDecide: (QueuedChange) -> Unit,
    onDiscard: (QueuedChange) -> Unit,
    onBack: () -> Unit,
) {
    val offline = !online || queue.offline
    Column(Modifier.fillMaxSize()) {
        Header(
            title = stringResource(R.string.queue_title),
            back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
            focusOnTitle = true,
        )
        if (queue.changes.isEmpty()) {
            StatePage(R.drawable.ic_check, stringResource(R.string.queue_empty_title), stringResource(R.string.queue_empty_text))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            if (offline && queue.waiting > 0) {
                NoticeBanner(
                    NoticeKind.WARNING,
                    stringResource(R.string.queue_offline_title) + ". " + pluralStringResource(R.plurals.queue_offline_text, queue.waiting, queue.waiting),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
            ) {
                queue.changes.forEachIndexed { index, change ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ChangeRow(change, onDecide, onDiscard)
                }
            }
            Text(
                stringResource(R.string.queue_memory_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.s2),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s5, vertical = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PrimaryButton(
                stringResource(R.string.queue_send_now),
                onSendNow,
                Modifier.fillMaxWidth(),
                enabled = queue.waiting > 0,
                loading = queue.sending,
                loadingText = stringResource(R.string.queue_sending),
            )
            if (offline && queue.waiting > 0) {
                Text(stringResource(R.string.queue_send_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChangeRow(change: QueuedChange, onDecide: (QueuedChange) -> Unit, onDiscard: (QueuedChange) -> Unit) {
    val decides = change.state == ChangeState.CONFLICT
    val reason = if (change.state == ChangeState.REJECTED) stringResource(reasonText(change.code)) else null
    val time = timeText(change.createdAt)
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (decides) Modifier.focusRing(shape).clickable(onClickLabel = stringResource(R.string.change_decide), role = Role.Button) { onDecide(change) } else Modifier)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(change.summary, style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(time, reason).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StateChip(change)
        if (change.state == ChangeState.REJECTED) TextActionButton(stringResource(R.string.change_discard), { onDiscard(change) })
    }
}

/**
 * Konflikt (Entwurf U7): «Deine Fassung» und «Aktuelle Fassung» nebeneinander (bei grosser Schrift untereinander), Unterschiede
 * markiert mit Farbe und Wort. Wer entscheidet, behält seine Fassung oder übernimmt die aktuelle; geschlossen wird erst dann.
 */
@Composable
fun ConflictScreen(change: QueuedChange, onKeepMine: () -> Unit, onTakeCurrent: () -> Unit, onBack: (() -> Unit)?) {
    val rows = conflictRows(change)
    val stacked = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.fillMaxSize()) {
        Header(
            title = stringResource(R.string.conflict_title),
            subtitle = change.summary,
            // Ohne Warteschlange (Konflikt bei einer direkten Änderung) gibt es keinen Weg zurück: Die Person entscheidet.
            back = onBack?.let { HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_queue), it) },
            titleStyle = MaterialTheme.typography.headlineMedium,
            focusOnTitle = true,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(R.string.conflict_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (stacked) {
                Version(R.string.conflict_mine, rows, mine = true)
                Version(R.string.conflict_current, rows, mine = false)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Version(R.string.conflict_mine, rows, mine = true, Modifier.weight(1f))
                    Version(R.string.conflict_current, rows, mine = false, Modifier.weight(1f))
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s5, vertical = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PrimaryButton(stringResource(R.string.conflict_keep_mine), onKeepMine, Modifier.fillMaxWidth())
            OutlinedPillButton(stringResource(R.string.conflict_take_current), onTakeCurrent, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Version(@StringRes title: Int, rows: List<ConflictRow>, mine: Boolean, modifier: Modifier = Modifier) {
    val empty = stringResource(R.string.conflict_empty_value)
    val differs = stringResource(R.string.conflict_differs)
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            val value = (if (mine) row.mine else row.current)?.let { conflictValue(row.key, it) } ?: empty
            // Der Unterschied steht als Farbe und als Wort: Der Screenreader hört «anders».
            val spoken = "${row.label}: $value" + if (row.differs) ", $differs" else ""
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
                Text(row.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = if (row.differs) {
                        Modifier.background(MaterialTheme.cevi.warningContainer, MaterialTheme.shapes.extraSmall).padding(horizontal = 4.dp)
                    } else {
                        Modifier
                    },
                    color = if (row.differs) MaterialTheme.cevi.onWarningContainer else MaterialTheme.colorScheme.onSurface,
                )
                if (row.differs) Text(differs, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Werte, die die API als Wort der Maschine schickt, in Worten der Person: Status, Ja und Nein, Daten. */
@Composable
private fun conflictValue(key: String, raw: String): String = when (key) {
    "status" -> stringResource(
        when (raw) {
            "besprochen" -> R.string.agenda_done
            "neu" -> R.string.inbox_status_new
            "in_arbeit" -> R.string.inbox_status_progress
            else -> statusText(TaskStatus.from(raw))
        },
    )
    "art" -> when (raw) {
        "lagerleitung" -> stringResource(R.string.meeting_kind_leadership)
        "team" -> stringResource(R.string.meeting_kind_team)
        else -> raw
    }
    "stern", "laufend", "wichtig", "angeheftet", "geschlossen" -> stringResource(if (raw == "true") R.string.button_yes else R.string.button_no)
    "frist", "spaeterAb", "datum" -> formatDayLong(raw)
    "sichtbarAb", "sichtbarBis" -> instantToDay(raw)?.let(::formatDayLong) ?: raw
    else -> raw
}
