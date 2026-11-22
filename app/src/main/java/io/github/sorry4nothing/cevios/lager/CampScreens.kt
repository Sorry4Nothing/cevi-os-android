package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

@StringRes
private fun roleText(role: CampRole) = when (role) {
    CampRole.LEADERSHIP -> R.string.camp_role_leadership
    CampRole.LEADER -> R.string.camp_role_leader
    CampRole.TEAM -> R.string.camp_role_team
    CampRole.READ_ONLY -> R.string.camp_role_read_only
}

@StringRes
private fun statusText(camp: Camp) = when (camp.status) {
    "aktiv" -> R.string.camp_status_active
    "abgeschlossen" -> R.string.camp_status_closed
    "archiviert" -> R.string.camp_status_archived
    else -> R.string.camp_status_draft
}

private fun statusKind(camp: Camp) = when (camp.status) {
    "aktiv" -> ChipKind.YES
    "abgeschlossen", "archiviert" -> ChipKind.PENDING
    else -> ChipKind.AGE_GROUP
}

/** Lagerwahl (Entwurf L1): meine Lager als Karten, aktive zuerst, abgeschlossene unter ihrem eigenen Abschnitt. */
@Composable
fun CampListScreen(
    state: ApiResult<List<Camp>>?,
    pushMissing: Boolean,
    onDismissPushMissing: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(title = stringResource(R.string.camps_title), focusOnTitle = true)
        if (pushMissing) {
            Column(Modifier.padding(horizontal = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                NoticeBanner(NoticeKind.INFO, stringResource(R.string.camp_push_missing), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextActionButton(stringResource(R.string.close), onDismissPushMissing)
            }
        }
        when (state) {
            null -> CardSkeleton(stringResource(R.string.camps_loading))
            is ApiResult.Failure -> ErrorState(state.error, onRetry)
            is ApiResult.Ok ->
                if (state.value.isEmpty()) {
                    StatePage(R.drawable.ic_tent, stringResource(R.string.camps_empty_title), stringResource(R.string.camps_empty_text))
                } else {
                    val (closed, open) = state.value.partition { it.readOnly }
                    LazyColumn(
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        items(open, key = { it.id }) { CampCard(it, onOpen) }
                        if (closed.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.camps_section_closed),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = Spacing.s2, top = Spacing.s4).semantics { heading() },
                                )
                            }
                            items(closed, key = { it.id }) { CampCard(it, onOpen) }
                        }
                    }
                }
        }
    }
}

/** Eine Karte der Lagerwahl; TalkBack liest sie als ganzen Satz (Titel, Zustand, Zeitraum, Ort, Rolle). */
@Composable
private fun CampCard(camp: Camp, onOpen: (String) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .focusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = stringResource(R.string.camp_open)) { onOpen(camp.id) }
            .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
            Text(camp.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusChip(statusKind(camp), stringResource(statusText(camp)))
        }
        Text(campDates(camp.from, camp.until), style = MaterialTheme.typography.bodyMedium)
        camp.place?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
        Text(
            stringResource(R.string.camp_role, stringResource(roleText(camp.role))),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
    }
}

/**
 * Start eines Lagers. Der Entwurf zeigt hier Countdown, Aufgaben und Hoeck (Schnitt A1); dieses Gerüst zeigt, was das Lager selbst
 * hergibt: Titel, Zeitraum, Ort und die eigene Rolle, dazu den Hinweis auf ein abgeschlossenes Lager und die Warteschlange.
 */
@Composable
fun CampStartScreen(
    detail: ApiResult<Camp>?,
    queue: QueueState,
    online: Boolean,
    onRetry: () -> Unit,
    onToCamps: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        when (detail) {
            null -> {
                Header(title = stringResource(R.string.tab_start), focusOnTitle = true)
                CardSkeleton(stringResource(R.string.camp_loading), count = 2)
            }
            is ApiResult.Failure -> {
                // Das Lager gibt es für die Person nicht, auch wenn es existiert: Der Server verrät es nicht (404).
                val error = if (detail.error == ApiError.NOT_FOUND) ApiError.FORBIDDEN else detail.error
                Header(title = stringResource(R.string.tab_start), focusOnTitle = true)
                ErrorState(error, onRetry, onToPrograms = onToCamps, noAccessText = R.string.state_camp_access_text, homeLabel = R.string.to_camps)
            }
            is ApiResult.Ok -> {
                val camp = detail.value
                Header(title = stringResource(R.string.tab_start), subtitle = camp.title, focusOnTitle = true)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    QueueBanner(queue, online, onOpenQueue)
                    if (camp.readOnly) {
                        NoticeBanner(
                            NoticeKind.INFO,
                            stringResource(R.string.camp_closed_title) + ". " + stringResource(R.string.camp_closed_text),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .padding(Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        Text(campDates(camp.from, camp.until), style = MaterialTheme.typography.titleMedium)
                        camp.place?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            stringResource(R.string.camp_role, stringResource(roleText(camp.role))),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.camp_start_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Banner über dem Inhalt eines Lagers: «Ohne Netz · 3 Änderungen warten» (tippen öffnet die Warteschlange), Änderungen, die
 * Entscheidungen brauchen, und der Hinweis auf einen veralteten Stand. Eine Live-Region: Der Screenreader hört Änderungen.
 */
@Composable
fun QueueBanner(queue: QueueState, online: Boolean, onOpenQueue: () -> Unit, modifier: Modifier = Modifier) {
    val decisions = queue.needsDecision.size
    val waiting = queue.waiting
    val offline = !online || queue.offline
    val text = when {
        decisions > 0 -> pluralStringResource(R.plurals.queue_banner_decision, decisions, decisions)
        waiting > 0 && offline -> pluralStringResource(R.plurals.queue_banner_offline, waiting, waiting)
        waiting > 0 -> pluralStringResource(R.plurals.queue_banner_sending, waiting, waiting)
        offline -> stringResource(R.string.queue_offline_title) + " · " + stringResource(R.string.queue_offline_stale)
        else -> return
    }
    val kind = if (decisions > 0) NoticeKind.ERROR else NoticeKind.WARNING
    val opens = decisions > 0 || waiting > 0
    val shape = MaterialTheme.shapes.medium
    NoticeBanner(
        kind,
        text,
        modifier
            .clip(shape)
            .then(if (opens) Modifier.clickable(onClickLabel = stringResource(R.string.queue_open), role = Role.Button, onClick = onOpenQueue) else Modifier)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Das Menü «Bereich wechseln» (Entwurf B3): die Abteilung mit Programm, Chat und Profil, die aktiven Lager, «Alle Lager». */
@Composable
fun ModeMenu(
    inCamp: String?,
    camps: List<Camp>,
    unread: Map<String, Int>,
    onDepartment: () -> Unit,
    onCamp: (String) -> Unit,
    onAllCamps: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 56.dp, start = Spacing.s4, end = Spacing.s4),
            contentAlignment = Alignment.TopCenter,
        ) {
            val menuTitle = stringResource(R.string.mode_menu_title)
            Surface(
                // Antippen im Menü schliesst es nicht: nur das Wählen eines Eintrags.
                Modifier.fillMaxWidth().clickable(enabled = false) {},
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(8.dp).selectableGroup().semantics { paneTitle = menuTitle },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModeItem(R.drawable.ic_calendar, stringResource(R.string.department), stringResource(R.string.mode_department_sub), inCamp == null, null, onClick = onDepartment)
                    camps.filter { it.active }.forEach { camp ->
                        val news = unread[camp.id] ?: 0
                        ModeItem(
                            R.drawable.ic_tent, camp.title, stringResource(R.string.mode_camp_sub), inCamp == camp.id,
                            news.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.mode_camp_news, it, it) },
                        ) { onCamp(camp.id) }
                    }
                    ModeItem(R.drawable.ic_tent, stringResource(R.string.mode_all_camps), null, false, null, radio = false, onClick = onAllCamps)
                }
            }
        }
    }
}

@Composable
private fun ModeItem(icon: Int, title: String, sub: String?, selected: Boolean, news: String?, radio: Boolean = true, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRing(shape)
            .clip(shape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier)
            .then(
                if (radio) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(role = Role.Button, onClick = onClick),
            )
            .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.cevi.accent)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        news?.let { StatusChip(ChipKind.PENDING, it) }
        if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(20.dp))
    }
}

/** Ein Eintrag des Blatts «Mehr». */
class MoreEntry(@StringRes val text: Int, val icon: Int, val onClick: () -> Unit)

/**
 * Das Blatt «Mehr» (Entwurf L37, B7): Einträge in Abschnitten, zwei Spalten, bei grosser Schrift eine. Dieses Gerüst bringt den
 * Abschnitt «Du»; die Schnitte der Module hängen ihre Abschnitte (Planen, Im Lager, Ablage) an.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreSheet(sections: List<Pair<Int, List<MoreEntry>>>, onDismiss: () -> Unit) {
    val singleColumn = LocalDensity.current.fontScale >= 1.5f
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(stringResource(R.string.more_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            sections.forEach { (title, entries) ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxItemsInEachRow = if (singleColumn) 1 else 2,
                    ) {
                        entries.forEach { entry ->
                            val shape = MaterialTheme.shapes.small
                            Row(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = Sizes.touchTarget)
                                    .focusRing(shape)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable(role = Role.Button, onClick = entry.onClick)
                                    .padding(horizontal = Spacing.s3, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(entry.icon), contentDescription = null, tint = MaterialTheme.cevi.accent)
                                Text(stringResource(entry.text), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
