package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.DialogText
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/** Ein Eintrag einer Auswahl in einem Blatt: Schlüssel, Name und eine Angabe darunter (zum Beispiel die Stufe). */
data class PickItem(val key: String, val label: String, val detail: String? = null)

/** Eine Zeile mit Kontrollkästchen (mindestens 48 dp); der Screenreader hört Name, Angabe und Zustand als einen Satz. */
@Composable
fun CheckRow(label: String, detail: String?, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(vertical = Spacing.s2)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Ein Schalter mit Text (Rolle «Schalter»); der Zustand steht im Schalter selbst, nicht nur in der Farbe. */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Ein Blatt mit Mehrfachauswahl: Titel, optionaler Kopf (zum Beispiel Reiter), die Zeilen mit Kontrollkästchen, «Übernehmen» und
 * «Abbrechen». `initial` sind die schon gewählten Schlüssel. Ohne Zeilen steht `emptyText`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    title: String,
    items: List<PickItem>,
    confirmText: String,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String,
    initial: Set<String> = emptySet(),
    loading: Boolean = false,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    var picked by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            header()
            if (items.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEach { item -> CheckRow(item.label, item.detail, item.key in picked, { picked = picked.toggled(item.key) }) }
            PrimaryButton(confirmText, { onConfirm(picked) }, Modifier.fillMaxWidth().padding(top = Spacing.s2), enabled = picked.isNotEmpty() || initial.isNotEmpty(), loading = loading)
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Frage vor einer folgenreichen Aktion: Der Titel ist die Frage, die Knöpfe nennen die Folge. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CeviDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(confirmLabel, onConfirm) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) { DialogText(text) }
}

/** Der Hinweis nach einer Aktion; er bleibt, bis die Person ihn schliesst, und wird angesagt. */
@Composable
fun PlanNoticeBanner(vm: PlanningViewModel) {
    val notice by vm.notice.collectAsState()
    val text = when (notice) {
        null -> return
        PlanNotice.SWAP_ACCEPTED -> R.string.plan_notice_swap_accepted
        PlanNotice.SWAP_DECLINED -> R.string.plan_notice_swap_declined
        PlanNotice.SWAP_REQUESTED -> R.string.plan_notice_swap_requested
        PlanNotice.SWAP_WITHDRAWN -> R.string.plan_notice_swap_withdrawn
        PlanNotice.COUNT_RESET -> R.string.plan_notice_count_reset
        PlanNotice.MIX_APPLIED -> R.string.plan_notice_mix_applied
        PlanNotice.SUGGESTION_APPLIED -> R.string.plan_notice_suggestion_applied
        PlanNotice.IMPORTED -> R.string.plan_notice_imported
    }
    ActionBanner(NoticeKind.INFO, stringResource(text), null, null, vm::dismissNotice)
}

/** Ein Wort für Stufe oder Art mit der Zahl dahinter: «Wölfe, 5». */
fun joinDetail(vararg parts: String?): String? = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }

/** Die Bereiche der Einteilung; je einer ist ein Bildschirm, den das Blatt «Mehr» öffnet. */
enum class PlanArea { PARTICIPANTS, ROOMS, TRIPS, GROUPS, SHIFTS, SWAPS, COUNT }

/** Zeigt den Bildschirm zu einem Bereich der Einteilung. Schichtplan und Diensttausch verweisen aufeinander. */
@Composable
fun PlanScreen(
    area: PlanArea,
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onOpenArea: (PlanArea) -> Unit,
    onBack: () -> Unit,
) {
    when (area) {
        PlanArea.PARTICIPANTS -> ParticipantsScreen(vm, camp, rights, queue, online, onOpenQueue, onBack)
        PlanArea.ROOMS -> RoomsScreen(vm, camp, rights, queue, online, onOpenQueue, onBack)
        PlanArea.TRIPS -> TripsScreen(vm, camp, rights, queue, online, onOpenQueue, onBack)
        PlanArea.GROUPS -> GroupsScreen(vm, camp, rights, queue, online, onOpenQueue, onBack)
        PlanArea.SHIFTS -> ShiftPlanScreen(vm, camp, rights, queue, online, onOpenQueue, onOpenSwaps = { onOpenArea(PlanArea.SWAPS) }, onBack = onBack)
        PlanArea.SWAPS -> SwapsScreen(vm, camp, rights, queue, online, onOpenQueue, onOpenShifts = { onOpenArea(PlanArea.SHIFTS) }, onBack = onBack)
        PlanArea.COUNT -> CountScreen(vm, camp, queue, online, onOpenQueue, onBack)
    }
}
