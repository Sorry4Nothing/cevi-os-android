package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SWAP_TIME = DateTimeFormatter.ofPattern("d.M. HH:mm").withZone(ZoneId.of("Europe/Zurich"))

/** «20.11. 20:40»; ein Zeitpunkt, den die App nicht lesen kann, bleibt leer. */
fun swapTime(iso: String?): String = iso?.let { runCatching { SWAP_TIME.format(Instant.parse(it)) }.getOrNull() }.orEmpty()

/**
 * Diensttausch (Entwurf L24). Die Lagerleitung und die Leitung sehen alle Anfragen und nehmen sie an oder lehnen sie ab; alle anderen
 * sehen die eigenen und ziehen offene zurück. Eine Anfrage entsteht im Schichtplan, bei der eigenen Schicht. Der Push an die
 * Lagerleitung (und an die Beteiligten beim Entscheid) nennt weder Person noch Schicht; dieser Bildschirm löst es auf.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwapsScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onOpenShifts: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.swaps.collectAsState()
    val planState by vm.plan.collectAsState()
    val people by vm.people.collectAsState()
    val busy by vm.busy.collectAsState()
    var choosing by remember { mutableStateOf<Swap?>(null) }
    LaunchedEffect(camp?.id) { vm.loadPlan(silent = true); vm.loadSwaps() }
    val plan = (planState as? ApiResult.Ok)?.value
    val names = remember(people) { people.associate { it.id to it.display } }

    CampPage(
        title = stringResource(R.string.swaps_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
        banners = { PlanNoticeBanner(vm) },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.swaps_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadSwaps() })
            is ApiResult.Ok -> {
                val swaps = PlanRules.sortedSwaps(result.value)
                if (swaps.isEmpty()) {
                    StatePage(R.drawable.ic_refresh, stringResource(R.string.swaps_empty_title), stringResource(if (rights.decideSwap) R.string.swaps_empty_text else R.string.swaps_empty_own)) {
                        if (rights.requestSwap) OutlinedPillButton(stringResource(R.string.swaps_to_plan), onOpenShifts)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        if (!rights.decideSwap && rights.requestSwap) item { TextActionButton(stringResource(R.string.swaps_to_plan), onOpenShifts) }
                        items(swaps, key = { it.id }) { swap ->
                            SwapCard(
                                swap, plan, names, rights, vm.selfId, busy,
                                onAccept = { if (swap.mitPersonId == null) choosing = swap else vm.decideSwap(swap, accept = true) },
                                onDecline = { vm.decideSwap(swap, accept = false) },
                                onWithdraw = { vm.withdrawSwap(swap) },
                            )
                        }
                    }
                }
            }
        }
    }

    choosing?.let { swap ->
        val choices = people.filter { it.id != swap.vonPersonId }.map { Choice(it.id, it.display) }
        ChoiceSheet(
            stringResource(R.string.swaps_who_takes), choices, initial = null,
            onApply = { key -> choosing = null; key?.let { vm.decideSwap(swap, accept = true, takeOverBy = it) } },
            onDismiss = { choosing = null },
            emptyText = stringResource(R.string.groups_nobody_known),
        )
    }
}

@Composable
private fun SwapCard(
    swap: Swap,
    plan: ShiftPlan?,
    names: Map<String, String>,
    rights: PlanRights,
    selfId: String,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val unknown = stringResource(R.string.swaps_someone)
    val shift = plan?.shifts?.firstOrNull { it.id == swap.schichtId }
    val duty = shift?.let { s -> plan.duties.firstOrNull { it.id == s.dienstId }?.name }
    val what = if (shift != null && duty != null) "$duty ${formatMeetingDay(shift.date)}" else stringResource(R.string.swaps_unknown_shift)
    val who = PlanRules.swapActor(swap, plan, names, unknown)
    val withShift = swap.mitSchichtId?.let { id -> plan?.shifts?.firstOrNull { it.id == id } }
    val partner = swap.mitName?.takeIf { it.isNotBlank() } ?: swap.mitPersonId?.let { names[it] } ?: withShift?.name ?: unknown
    val sentence = when {
        swap.mitSchichtId != null -> stringResource(R.string.swaps_sentence_swap, who, what, partner)
        swap.mitPersonId != null -> stringResource(R.string.swaps_sentence_person, who, what, partner)
        else -> stringResource(R.string.swaps_sentence_give, who, what)
    }
    val decider = swap.entschiedenVon?.let { names[it] }
    val chip = when (swap.status) {
        SwapStatus.OPEN -> ChipKind.PENDING to stringResource(R.string.swaps_status_open)
        SwapStatus.ACCEPTED -> ChipKind.YES to stringResource(R.string.swaps_status_accepted)
        SwapStatus.DECLINED -> ChipKind.NO to stringResource(R.string.swaps_status_declined)
        SwapStatus.WITHDRAWN -> ChipKind.NO to stringResource(R.string.swaps_status_withdrawn)
    }
    val whenText = if (swap.status == SwapStatus.OPEN) swapTime(swap.createdAt) else listOfNotNull(
        swapTime(swap.decidedAt ?: swap.createdAt).ifBlank { null },
        decider?.let { stringResource(R.string.swaps_by, it) }.takeIf { swap.status != SwapStatus.WITHDRAWN },
    ).joinToString(", ")
    val own = swap.vonPersonId == selfId
    InfoCard {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = joinDetail(sentence, chip.second, whenText, swap.note).orEmpty() }, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(sentence, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(chip.first, chip.second)
                if (whenText.isNotBlank()) Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            swap.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (swap.status == SwapStatus.OPEN) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                if (rights.decideSwap) {
                    val accept = stringResource(R.string.swaps_accept)
                    val decline = stringResource(R.string.swaps_decline)
                    PrimaryButton(accept, onAccept, Modifier.rowAction(accept, sentence), enabled = !busy)
                    OutlinedPillButton(decline, onDecline, Modifier.rowAction(decline, sentence), enabled = !busy)
                }
                if (own && rights.requestSwap) {
                    val withdraw = stringResource(R.string.swaps_withdraw)
                    TextActionButton(withdraw, onWithdraw, Modifier.rowAction(withdraw, sentence))
                }
            }
        }
    }
}
