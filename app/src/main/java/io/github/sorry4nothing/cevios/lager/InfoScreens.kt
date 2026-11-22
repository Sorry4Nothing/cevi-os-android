package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

/**
 * Infos (Entwurf L32): die Abschnitte des Lagers mit Text, Links und Dateien (nur als Verknüpfung, https). Die Lagerleitung legt
 * Abschnitte an, ändert und löscht sie und ordnet sie mit «Nach oben» und «Nach unten» (kein Ziehen). Abschnitte «nur Lagerleitung»
 * liefert der Server nur dort, wo die Rolle sie lesen darf.
 */
@Composable
fun InfosScreen(frame: LiveFrame) {
    val vm = frame.vm
    val state by vm.infos.collectAsState()
    val busy by vm.busy.collectAsState()
    val edit = frame.rights.editInfos
    var editor by remember { mutableStateOf<InfoSection?>(null) }
    var creating by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<InfoSection?>(null) }
    var deleting by remember { mutableStateOf<InfoSection?>(null) }
    var reordering by remember { mutableStateOf(false) }
    LaunchedEffect(frame.camp?.id) { vm.loadInfos(silent = vm.infos.value is ApiResult.Ok) }

    LivePage(frame, stringResource(R.string.infos_title)) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.infos_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadInfos() })
            is ApiResult.Ok -> {
                val sections = result.value
                if (sections.isEmpty()) {
                    StatePage(R.drawable.ic_info, stringResource(R.string.infos_empty_title), stringResource(if (edit) R.string.infos_empty_text else R.string.infos_empty_text_read)) {
                        if (edit) PrimaryButton(stringResource(R.string.infos_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LiveColumn(bottom = if (edit) BOTTOM_SPACE else Spacing.s6) {
                        if (reordering) {
                            Text(stringResource(R.string.infos_reorder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            sections.forEachIndexed { index, section ->
                                Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                                    Text(section.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    if (index > 0) RoundButton(HeaderButton(R.drawable.ic_chevron_up, stringResource(R.string.infos_up_named, section.title)) { vm.moveInfo(sections, section.id, -1) })
                                    if (index < sections.lastIndex) RoundButton(HeaderButton(R.drawable.ic_chevron_down, stringResource(R.string.infos_down_named, section.title)) { vm.moveInfo(sections, section.id, 1) })
                                }
                            }
                            OutlinedPillButton(stringResource(R.string.infos_reorder_done), { reordering = false }, Modifier.fillMaxWidth())
                        } else {
                            sections.forEach { section -> InfoBlock(section, edit) { actions = section } }
                        }
                    }
                    if (edit && !reordering) {
                        PrimaryButton(stringResource(R.string.infos_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }
    actions?.let { section ->
        val sections = (state as? ApiResult.Ok)?.value.orEmpty()
        val entries = buildList {
            add(stringResource(R.string.infos_change) to { editor = section })
            if (sections.size > 1) add(stringResource(R.string.infos_reorder) to { reordering = true })
            add(stringResource(R.string.task_delete) to { deleting = section })
        }
        ActionSheet(section.title, entries) { actions = null }
    }
    if (creating || editor != null) {
        val section = editor
        InfoDialog(
            section, busy,
            onSave = { title, text ->
                if (section == null) vm.addInfo(title, text) { creating = false } else vm.changeInfo(section, title, text) { editor = null }
            },
            onDismiss = { creating = false; editor = null },
        )
    }
    deleting?.let { section ->
        ConfirmDialog(
            stringResource(R.string.infos_delete_title, section.title), stringResource(R.string.infos_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; vm.deleteInfo(section) }, onDismiss = { deleting = null },
        )
    }
}

private val BOTTOM_SPACE = Spacing.s8 + Spacing.s8 + Spacing.s8

/** Ein Abschnitt: Titel, Text, Links und Dateien; wer ändern darf, hat «Aktionen für …» daneben. */
@Composable
private fun InfoBlock(section: InfoSection, edit: Boolean, onActions: () -> Unit) {
    val open = rememberLinkOpener()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (section.leadershipOnly) StatusChip(ChipKind.AGE_GROUP, stringResource(R.string.pack_leadership_only))
            if (edit) RoundButton(HeaderButton(R.drawable.ic_more, stringResource(R.string.infos_actions_named, section.title), onActions))
        }
        section.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        section.links.forEach { link -> LinkRow(link.title, link.url) { open(link.url) } }
        section.files.forEach { file -> LinkRow(file.name, file.link) { open(file.link) } }
    }
}

@Composable
private fun LinkRow(title: String, url: String, onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.infos_open_link, title), role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.cevi.accent, modifier = Modifier.padding(vertical = Spacing.s2))
    }
}

/** Titel und Text eines Abschnitts. */
@Composable
private fun InfoDialog(section: InfoSection?, busy: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(section?.title.orEmpty()) }
    var text by remember { mutableStateOf(section?.text.orEmpty()) }
    CeviDialog(
        title = stringResource(if (section == null) R.string.infos_add else R.string.infos_change),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.save), { onSave(title, text) }, enabled = title.isNotBlank(), loading = busy) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            LabeledTextField(stringResource(R.string.infos_field_title), title, { title = it }, TITLE_MAX, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.infos_field_text), text, { text = it }, TEXT_MAX, Modifier.fillMaxWidth(), minLines = 4)
        }
    }
}

private const val TITLE_MAX = 120
private const val TEXT_MAX = 4000
