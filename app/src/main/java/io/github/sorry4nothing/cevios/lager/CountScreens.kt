package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/**
 * Die Abzählliste (Entwurf L25), nur für dich: Wer in der Liste steht, kommt vom Server (nur Lesen), die Haken liegen verschlüsselt auf diesem
 * Gerät und gehen an niemanden. Du wählst, wen du zählst: Teilnehmende, Leitende, eine Stufe oder eine Gruppe, mehrere zugleich.
 * «Zurücksetzen» löscht nur deine Haken und fragt vorher. Ohne Netz bleiben die Haken, die Liste lädt neu, sobald es wieder Netz gibt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.count.collectAsState()
    val selection by vm.selection.collectAsState()
    val groupsState by vm.groups.collectAsState()
    val participants by vm.participants.collectAsState()
    var resetting by remember { mutableStateOf(false) }
    LaunchedEffect(camp?.id) {
        vm.loadCount()
        vm.loadGroups(silent = true)
        vm.loadParticipants(silent = true)
    }
    val stages = PlanRules.stages((participants as? ApiResult.Ok)?.value.orEmpty())
    val groups = (groupsState as? ApiResult.Ok)?.value?.groups.orEmpty()
    // Im abgeschlossenen Lager bleibt nichts zum Ändern (und nichts auf dem Gerät: Es wird dort gelöscht).
    val writable = camp != null && !camp.readOnly

    CampPage(
        title = stringResource(R.string.count_title),
        subtitle = camp?.title,
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
        banners = { PlanNoticeBanner(vm) },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.count_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadCount() })
            is ApiResult.Ok -> {
                val page = result.value
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    item {
                        Text(
                            stringResource(R.string.count_summary, page.present, page.total),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    // Wer gezählt wird: mehrere zugleich, darum Kästchen statt Auswahl (Role.Checkbox); die Gruppe trägt den Namen für TalkBack.
                    item {
                        FlowRow(
                            Modifier.pickGroup(stringResource(R.string.count_who)).padding(vertical = Spacing.s2),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                        ) {
                            PickPill(stringResource(R.string.count_kind_children), selection.has(CountSource.Participants), { vm.toggleSource(CountSource.Participants) }, role = Role.Checkbox)
                            PickPill(stringResource(R.string.count_kind_team), selection.has(CountSource.Team), { vm.toggleSource(CountSource.Team) }, role = Role.Checkbox)
                        }
                    }
                    if (stages.isNotEmpty() || groups.isNotEmpty()) item {
                        FlowRow(
                            Modifier.pickGroup(stringResource(R.string.count_more_groups)).padding(bottom = Spacing.s2),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                        ) {
                            stages.forEach { s -> CountSource.Stage(s).let { PickPill(s, selection.has(it), { vm.toggleSource(it) }, role = Role.Checkbox) } }
                            groups.forEach { g -> CountSource.Group(g.id).let { PickPill(g.name, selection.has(it), { vm.toggleSource(it) }, role = Role.Checkbox) } }
                        }
                    }
                    if (page.entries.isEmpty()) {
                        item {
                            Text(stringResource(R.string.count_no_match), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(page.entries, key = { it.id }) { entry ->
                        CheckRow(entry.display, joinDetail(entry.stage), entry.checked, { vm.toggleCount(entry) }, enabled = writable)
                    }
                    if (writable && page.total > 0) item {
                        OutlinedPillButton(stringResource(R.string.count_reset), { resetting = true }, Modifier.fillMaxWidth().padding(top = Spacing.s3))
                    }
                }
            }
        }
    }

    if (resetting) {
        ConfirmDialog(
            stringResource(R.string.count_reset_title), stringResource(R.string.count_reset_text), stringResource(R.string.count_reset),
            onConfirm = { resetting = false; vm.resetCount() }, onDismiss = { resetting = false },
        )
    }
}
