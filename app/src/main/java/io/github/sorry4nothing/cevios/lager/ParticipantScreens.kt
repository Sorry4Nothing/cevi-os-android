package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Die Teilnehmenden des Lagers: Name, Stufe und «Nicht dabei». Wer `teilnehmer.bearbeiten` hat (Standard: Lagerleitung), sieht den
 * Cevi-Namen und kann erfassen, ändern, löschen und aus eingefügtem Text importieren. Nur Vorname, Nachname und Stufe: Der Server
 * kennt weder Gesundheitsangaben noch Telefon oder Adresse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantsScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.participants.collectAsState()
    var stage by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<Participant?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<Participant?>(null) }
    var deleting by remember { mutableStateOf<Participant?>(null) }
    LaunchedEffect(camp?.id) { vm.loadParticipants() }

    CampPage(
        title = stringResource(R.string.participants_title),
        subtitle = (state as? ApiResult.Ok)?.value?.let { pluralStringResource(R.plurals.plan_people_count, it.size, it.size) },
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
        action = if (rights.editParticipants) HeaderButton(R.drawable.ic_list_check, stringResource(R.string.participants_import)) { vm.clearImport(); importing = true } else null,
        banners = { PlanNoticeBanner(vm) },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.participants_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadParticipants() })
            is ApiResult.Ok -> {
                val all = PlanRules.sortedParticipants(result.value)
                val stages = PlanRules.stages(all)
                val shown = all.filter { stage == null || it.stage == stage }
                if (all.isEmpty()) {
                    StatePage(R.drawable.ic_group, stringResource(R.string.participants_empty_title), stringResource(R.string.participants_empty_text)) {
                        if (rights.editParticipants) PrimaryButton(stringResource(R.string.participants_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.editParticipants) 96.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        if (stages.isNotEmpty()) item {
                            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                PickPill(stringResource(R.string.filter_all), stage == null, { stage = null })
                                stages.forEach { s -> PickPill(s, stage == s, { stage = s }) }
                            }
                        }
                        items(shown, key = { it.id }) { person -> ParticipantRow(person, rights) { actions = person } }
                    }
                    if (rights.editParticipants) {
                        PrimaryButton(stringResource(R.string.participants_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }

    actions?.let { person ->
        val entries = buildList {
            if (rights.editParticipants) add(stringResource(R.string.participants_edit) to { editor = person })
            if (rights.edit) {
                val label = if (person.excluded) R.string.participants_include else R.string.participants_exclude
                add(stringResource(label) to { vm.setExcluded(listOf(person.id), !person.excluded) })
            }
            if (rights.editParticipants) add(stringResource(R.string.participants_delete) to { deleting = person })
        }
        ActionSheet(person.fullName, entries) { actions = null }
    }
    if (creating || editor != null) {
        ParticipantEditorSheet(
            editor,
            onSave = { first, last, stageText, cevi ->
                val existing = editor
                if (existing == null) {
                    vm.createParticipant(first, last, stageText, cevi)
                } else {
                    val fields = buildMap<String, JsonElement> {
                        if (first.trim() != existing.firstName) put("vorname", JsonPrimitive(first.trim()))
                        if (last.trim() != existing.lastName) put("nachname", JsonPrimitive(last.trim()))
                        if (stageText.trim() != existing.stage.orEmpty()) put("stufe", textField(stageText))
                        if (cevi.trim() != existing.ceviName.orEmpty()) put("ceviName", textField(cevi))
                    }
                    if (fields.isNotEmpty()) vm.changeParticipant(existing, fields, existing.fullName)
                }
                creating = false; editor = null
            },
            onDismiss = { creating = false; editor = null },
        )
    }
    deleting?.let { person ->
        ConfirmDialog(
            stringResource(R.string.participants_delete_title, person.fullName), stringResource(R.string.participants_delete_text), stringResource(R.string.participants_delete),
            onConfirm = { deleting = null; vm.deleteParticipant(person) }, onDismiss = { deleting = null },
        )
    }
    if (importing) ImportSheet(vm, onDismiss = { importing = false; vm.clearImport() })
}

@Composable
private fun ParticipantRow(person: Participant, rights: PlanRights, onOpen: () -> Unit) {
    val excluded = stringResource(R.string.participants_excluded)
    val description = joinDetail(person.fullName, person.ceviName?.takeIf { rights.editParticipants }, person.stage, if (person.excluded) excluded else null).orEmpty()
    val manage = rights.edit || rights.editParticipants
    InfoCard(
        description = description,
        onClick = if (manage) onOpen else null,
        onClickLabel = stringResource(R.string.participants_actions),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(person.fullName, style = MaterialTheme.typography.titleMedium)
                person.ceviName?.takeIf { rights.editParticipants && it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            person.stage?.takeIf { it.isNotBlank() }?.let { StatusChip(ChipKind.AGE_GROUP, it) }
            if (person.excluded) StatusChip(ChipKind.NO, excluded)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantEditorSheet(initial: Participant?, onSave: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf(initial?.firstName.orEmpty()) }
    var last by remember { mutableStateOf(initial?.lastName.orEmpty()) }
    var stage by remember { mutableStateOf(initial?.stage.orEmpty()) }
    var cevi by remember { mutableStateOf(initial?.ceviName.orEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                stringResource(if (initial == null) R.string.participants_add else R.string.participants_edit),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() },
            )
            LabeledTextField(
                stringResource(R.string.participants_first_name), first, { first = it }, 100, Modifier.fillMaxWidth(),
                hint = if (showErrors && first.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            LabeledTextField(
                stringResource(R.string.participants_last_name), last, { last = it }, 100, Modifier.fillMaxWidth(),
                hint = if (showErrors && last.isBlank()) stringResource(R.string.error_field_required) else null,
            )
            LabeledTextField(stringResource(R.string.participants_stage), stage, { stage = it }, 50, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.participants_cevi_name), cevi, { cevi = it }, 50, Modifier.fillMaxWidth(), hint = stringResource(R.string.participants_cevi_name_hint))
            PrimaryButton(stringResource(R.string.save), { if (first.isBlank() || last.isBlank()) showErrors = true else onSave(first, last, stage, cevi) }, Modifier.fillMaxWidth())
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Teilnehmende aus eingefügtem Text: zuerst die Vorschau mit Fehlern und Doppelten, dann «Übernehmen». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(vm: PlanningViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val preview by vm.importPreview.collectAsState()
    val busy by vm.busy.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(R.string.participants_import), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.participants_import_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledTextField(
                stringResource(R.string.participants_import_field), text, { text = it; vm.clearImport() }, 40000, Modifier.fillMaxWidth(), minLines = 6,
            )
            preview?.let { result ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Text(pluralStringResource(R.plurals.participants_import_new, result.rows.size, result.rows.size), style = MaterialTheme.typography.titleMedium)
                    result.failures.forEach { Text(stringResource(R.string.participants_import_failure, it.line, stringResource(importFailureText(it.code))), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    result.duplicates.forEach {
                        Text(stringResource(R.string.participants_import_duplicate, it.line, stringResource(if (it.reason == "vorhanden") R.string.participants_import_known else R.string.participants_import_twice)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val ready = preview?.rows?.isNotEmpty() == true
            if (ready) {
                PrimaryButton(stringResource(R.string.task_apply), { vm.applyImport(text, onDismiss) }, Modifier.fillMaxWidth(), loading = busy, loadingText = stringResource(R.string.plan_saving))
            } else {
                PrimaryButton(stringResource(R.string.plan_preview), { vm.previewImport(text) }, Modifier.fillMaxWidth(), enabled = text.isNotBlank(), loading = busy, loadingText = stringResource(R.string.plan_loading_preview))
            }
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@StringRes
private fun importFailureText(code: String): Int = when (code) {
    "ZU_WENIG_SPALTEN" -> R.string.import_error_few_columns
    "ZU_VIELE_SPALTEN" -> R.string.import_error_many_columns
    "VORNAME_FEHLT" -> R.string.import_error_first_name
    "NACHNAME_FEHLT" -> R.string.import_error_last_name
    "ZU_LANG" -> R.string.import_error_too_long
    else -> R.string.import_error_characters
}
