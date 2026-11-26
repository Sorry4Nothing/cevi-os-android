package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/**
 * Die Abzählliste (Entwurf L25): eine gemeinsame Liste für alle, die abzählen. Ein Haken gilt sofort für alle («18 von 22 abgezählt,
 * für alle sichtbar»); der Filter zeigt Teilnehmende oder Team, eine Stufe oder eine Gruppe. «Zurücksetzen» nimmt alle Haken zurück
 * und fragt vorher. Der Server kennt dafür keine Warteschlange: Ohne Netz springt ein Haken zurück und der Fehler erscheint.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.count.collectAsState()
    val filter by vm.filter.collectAsState()
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
                val filtered = filter.stage != null || filter.groupId != null
                if (page.entries.isEmpty() && !filtered) {
                    StatePage(R.drawable.ic_group, stringResource(R.string.count_empty_title), stringResource(R.string.count_empty_text))
                } else {
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
                        item {
                            FlowRow(Modifier.pickGroup(stringResource(R.string.group_people_or_team)).padding(vertical = Spacing.s2), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                PickPill(stringResource(R.string.count_kind_children), !filter.team, { vm.setFilter(CountFilter()) })
                                PickPill(stringResource(R.string.count_kind_team), filter.team, { vm.setFilter(CountFilter(team = true)) })
                            }
                        }
                        if (!filter.team && (stages.isNotEmpty() || groups.isNotEmpty())) item {
                            FlowRow(Modifier.pickGroup(stringResource(R.string.group_count_filter)).padding(bottom = Spacing.s2), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                PickPill(stringResource(R.string.filter_all), !filtered, { vm.setFilter(CountFilter()) })
                                stages.forEach { s -> PickPill(s, filter.stage == s, { vm.setFilter(CountFilter(stage = s)) }) }
                                groups.forEach { g -> PickPill(g.name, filter.groupId == g.id, { vm.setFilter(CountFilter(groupId = g.id)) }) }
                            }
                        }
                        if (page.entries.isEmpty()) item {
                            Text(stringResource(R.string.count_no_match), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedPillButton(stringResource(R.string.count_filter_reset), { vm.setFilter(CountFilter(team = filter.team)) })
                        }
                        items(page.entries, key = { it.id }) { entry ->
                            CheckRow(
                                entry.display,
                                joinDetail(entry.stage),
                                entry.checked,
                                { vm.toggleCount(entry) },
                                enabled = rights.count,
                            )
                        }
                        if (rights.count && page.total > 0) item {
                            OutlinedPillButton(stringResource(R.string.count_reset), { resetting = true }, Modifier.fillMaxWidth().padding(top = Spacing.s3))
                        }
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
