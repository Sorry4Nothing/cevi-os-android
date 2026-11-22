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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/**
 * Packliste (Entwurf L29): die Punkte der Lagerleitung nach Abschnitten, dazu eigene Punkte, die nur die Person sieht. Abhaken und
 * Entfernen gelten nur für die eigene Liste; der Haken springt zurück, wenn der Server ihn nicht annimmt. «nur Lagerleitung» steht als
 * Wort an Punkten, die sonst niemand sieht.
 */
@Composable
fun PackingScreen(frame: LiveFrame) {
    val vm = frame.vm
    val state by vm.pack.collectAsState()
    val busy by vm.busy.collectAsState()
    val tick = frame.rights.tickPacking
    var adding by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<PackRow?>(null) }
    var showRemoved by remember { mutableStateOf(false) }
    LaunchedEffect(frame.camp?.id) { vm.loadPack(silent = vm.pack.value is ApiResult.Ok) }

    LivePage(frame, stringResource(R.string.pack_title)) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.pack_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadPack() })
            is ApiResult.Ok -> {
                val list = result.value
                val sections = LiveRules.packSections(list)
                val (done, total) = LiveRules.packCount(list)
                val removed = list.items.filter { it.state == PackState.REMOVED }
                if (sections.isEmpty() && removed.isEmpty()) {
                    StatePage(R.drawable.ic_bring_along, stringResource(R.string.pack_empty_title), stringResource(R.string.pack_empty_text)) {
                        if (tick) PrimaryButton(stringResource(R.string.pack_add_own), { adding = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LiveColumn {
                        val count = stringResource(R.string.pack_count, done, total)
                        Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        sections.forEach { section ->
                            LiveSection(section.title) {
                                section.rows.forEach { row ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            CheckRow(row.text, null, row.checked, { vm.togglePack(row) }, enabled = tick)
                                            if (row.leadershipOnly) StatusChip(ChipKind.AGE_GROUP, stringResource(R.string.pack_leadership_only), Modifier.padding(start = Spacing.s8 + Spacing.s8, bottom = Spacing.s1))
                                        }
                                        if (tick) RoundButton(HeaderButton(R.drawable.ic_more, stringResource(R.string.pack_actions_named, row.text)) { actions = row })
                                    }
                                }
                            }
                        }
                        if (tick) TextActionButton(stringResource(R.string.pack_add_own), { adding = true })
                        if (removed.isNotEmpty()) {
                            TextActionButton(stringResource(if (showRemoved) R.string.pack_hide_removed else R.string.pack_show_removed, removed.size), { showRemoved = !showRemoved })
                            if (showRemoved) {
                                LiveSection(stringResource(R.string.pack_removed_title)) {
                                    removed.forEach { item ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                            if (tick) TextActionButton(stringResource(R.string.pack_restore), { vm.restorePackItem(item.id) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    actions?.let { row ->
        val entry = if (row.own) stringResource(R.string.task_delete) to { vm.deleteOwnPackItem(row) } else stringResource(R.string.pack_remove) to { vm.removePackItem(row) }
        ActionSheet(row.text, listOf(entry)) { actions = null }
    }
    if (adding) {
        val sections = (state as? ApiResult.Ok)?.value?.let { LiveRules.packSections(it).map { s -> s.title } }.orEmpty()
        OwnPackDialog(
            suggestion = sections.firstOrNull() ?: stringResource(R.string.pack_own_section), busy = busy,
            onSave = { section, text -> vm.addOwnPackItem(section, text) { adding = false } },
            onDismiss = { adding = false },
        )
    }
}

/** Ein eigener Punkt: Abschnitt und Text. Der Abschnitt ist vorgeschlagen und frei änderbar. */
@Composable
private fun OwnPackDialog(suggestion: String, busy: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var section by remember { mutableStateOf(suggestion) }
    var text by remember { mutableStateOf("") }
    CeviDialog(
        title = stringResource(R.string.pack_add_own),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.pack_add_save), { onSave(section, text) }, enabled = section.isNotBlank() && text.isNotBlank(), loading = busy) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            LabeledTextField(stringResource(R.string.pack_own_text), text, { text = it }, TEXT_MAX, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.pack_own_section_label), section, { section = it }, SECTION_MAX, Modifier.fillMaxWidth())
        }
    }
}

private const val TEXT_MAX = 200
private const val SECTION_MAX = 80
