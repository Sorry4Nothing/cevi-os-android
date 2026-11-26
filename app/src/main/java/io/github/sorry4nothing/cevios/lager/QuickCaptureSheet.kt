package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing

private sealed interface Chooser {
    data class ForPerson(val index: Int) : Chooser
    data class ForRessort(val index: Int) : Chooser
    data class ForDue(val index: Int) : Chooser
}

/**
 * Das Blatt «Schnell erfassen» (Entwurf L8, L10). Jede Zeile wird eine Aufgabe; `@Person`, ein Datum in Worten und `#Ressort`
 * erkennt der Server (die Regeln sind für alle Apps gleich), die App zeigt, was er erkannt hat, und lässt ändern. Ähnliche
 * Aufgaben meldet das Blatt vor dem Anlegen. Ohne Netz bleibt jede Zeile ein Titel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickCaptureSheet(vm: TasksViewModel) {
    val state by vm.quick.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val ressorts by vm.ressorts.collectAsState()
    var chooser by remember { mutableStateOf<Chooser?>(null) }
    LaunchedEffect(Unit) { vm.loadRessortsIfNeeded() }
    val members by vm.members.collectAsState()
    val people = TaskRules.pickable((tasks as? ApiResult.Ok)?.value.orEmpty(), vm.self, members)

    ModalBottomSheet(onDismissRequest = vm::closeQuick, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(stringResource(R.string.quick_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (state.showErrors && state.unresolved) {
                NoticeBanner(NoticeKind.ERROR, stringResource(R.string.quick_check_fields), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }
            state.error?.let {
                NoticeBanner(NoticeKind.ERROR, stringResource(R.string.task_error), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            }
            if (state.offline) NoticeBanner(NoticeKind.WARNING, stringResource(R.string.quick_offline), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            LabeledTextField(
                label = stringResource(R.string.quick_label),
                value = state.text,
                onValueChange = vm::editQuick,
                maxChars = 500,
                modifier = Modifier.fillMaxWidth(),
                hint = stringResource(R.string.quick_hint),
                minLines = 3,
                enabled = !state.saving,
            )
            val duplicate = state.duplicate
            if (duplicate != null) {
                DuplicateQuestion(
                    existingTitle = duplicate.existing.title,
                    saving = state.saving,
                    onMerge = vm::mergeDuplicate,
                    onKeepBoth = vm::keepBoth,
                )
            } else {
                if (state.items.isNotEmpty()) {
                    Text(
                        stringResource(if (state.offline) R.string.quick_offline_items else R.string.quick_recognized),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() },
                    )
                    state.items.forEachIndexed { index, item ->
                        QuickItemCard(
                            item, index,
                            showErrors = state.showErrors,
                            onPerson = { chooser = Chooser.ForPerson(index) },
                            onRessort = { chooser = Chooser.ForRessort(index) },
                            onDue = { chooser = Chooser.ForDue(index) },
                        )
                    }
                    Text(
                        pluralStringResource(R.plurals.quick_count, state.items.size, state.items.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                PrimaryButton(
                    text = if (state.items.size > 1) pluralStringResource(R.plurals.quick_submit_many, state.items.size, state.items.size) else stringResource(R.string.quick_submit),
                    onClick = vm::submitQuick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSubmit,
                    loading = state.saving,
                    loadingText = stringResource(R.string.saving),
                )
                OutlinedPillButton(stringResource(R.string.cancel), vm::closeQuick, Modifier.fillMaxWidth())
            }
        }
    }

    when (val choosing = chooser) {
        is Chooser.ForPerson -> state.items.getOrNull(choosing.index)?.let { item ->
            val candidates = ((item.person as? QuickChoice.Unresolved)?.candidates.orEmpty().map { Choice(it.id, it.display) } +
                people.map { Choice(it.id, it.display) }).distinctBy { it.key }
            ChoiceDialog(
                title = stringResource(R.string.quick_choose_person),
                choices = listOf(Choice(null, stringResource(R.string.task_nobody))) + candidates,
                selected = (item.person as? QuickChoice.Chosen)?.value?.id,
                onPick = { id ->
                    chooser = null
                    val known = (item.person as? QuickChoice.Unresolved)?.candidates.orEmpty().firstOrNull { it.id == id }
                        ?: people.firstOrNull { it.id == id }?.let { PersonCandidate(it.id, it.name ?: it.display, it.ceviName) }
                    vm.chooseQuickPerson(choosing.index, if (id == null) null else known)
                },
                onDismiss = { chooser = null },
            )
        }
        is Chooser.ForRessort -> state.items.getOrNull(choosing.index)?.let { item ->
            val all = (ressorts as? ApiResult.Ok)?.value.orEmpty().map { RessortCandidate(it.id, it.abbreviation, it.name) }
            val candidates = ((item.ressort as? QuickChoice.Unresolved)?.candidates.orEmpty() + all).distinctBy { it.id }
            ChoiceDialog(
                title = stringResource(R.string.quick_choose_ressort),
                choices = listOf(Choice(null, stringResource(R.string.task_no_ressort))) + candidates.map { Choice(it.id, it.abbreviation + " " + it.name) },
                selected = (item.ressort as? QuickChoice.Chosen)?.value?.id,
                onPick = { id -> chooser = null; vm.chooseQuickRessort(choosing.index, candidates.firstOrNull { it.id == id }) },
                onDismiss = { chooser = null },
            )
        }
        is Chooser.ForDue -> state.items.getOrNull(choosing.index)?.let { item ->
            DateDialog(item.due, { vm.chooseQuickDue(choosing.index, it); chooser = null }, { chooser = null })
        }
        null -> Unit
    }
}

@Composable
private fun DuplicateQuestion(existingTitle: String, saving: Boolean, onMerge: () -> Unit, onKeepBoth: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(R.string.duplicate_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.duplicate_text, existingTitle), style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(stringResource(R.string.duplicate_merge), onMerge, Modifier.fillMaxWidth(), loading = saving, loadingText = stringResource(R.string.saving))
        OutlinedPillButton(stringResource(R.string.duplicate_keep_both), onKeepBoth, Modifier.fillMaxWidth(), enabled = !saving)
    }
}

/** Eine erkannte Zeile: Titel und darunter, was der Server erkannt hat. Antippen ändert die Angabe (mindestens 48 dp hoch). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickItemCard(item: QuickItem, index: Int, showErrors: Boolean, onPerson: () -> Unit, onRessort: () -> Unit, onDue: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(item.title.ifBlank { item.line }, style = MaterialTheme.typography.titleMedium)
        if (item.titleError) Text(stringResource(R.string.quick_title_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            when (val person = item.person) {
                is QuickChoice.Chosen -> Tappable(stringResource(R.string.quick_change_person), onPerson) { StatusChip(ChipKind.AGE_GROUP, person.value.display) }
                is QuickChoice.Unresolved -> Tappable(stringResource(R.string.quick_check_person, person.text), onPerson) {
                    StatusChip(ChipKind.REVIEW, stringResource(R.string.quick_person_unresolved, person.text))
                }
                QuickChoice.None -> Unit
            }
            item.due?.let { date -> Tappable(stringResource(R.string.quick_change_due), onDue) { StatusChip(ChipKind.AGE_GROUP, formatDay(date)) } }
            when (val ressort = item.ressort) {
                is QuickChoice.Chosen -> Tappable(stringResource(R.string.quick_change_ressort), onRessort) { StatusChip(ChipKind.AGE_GROUP, ressort.value.name) }
                is QuickChoice.Unresolved -> Tappable(stringResource(R.string.quick_check_ressort, ressort.text), onRessort) {
                    StatusChip(ChipKind.REVIEW, stringResource(R.string.quick_ressort_unresolved, ressort.text))
                }
                QuickChoice.None -> Unit
            }
        }
        // Eine offene Angabe sagt der Fehlertext beim Feld: «Ich habe @Jona nicht gefunden. Meintest du @Jonas?»
        (item.person as? QuickChoice.Unresolved)?.takeIf { showErrors }?.let { person ->
            val hint = person.candidates.firstOrNull()?.let { stringResource(R.string.quick_person_hint, person.text, it.name.substringBefore(' ')) }
                ?: stringResource(R.string.quick_person_unknown, person.text)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        (item.ressort as? QuickChoice.Unresolved)?.takeIf { showErrors }?.let { ressort ->
            Text(stringResource(R.string.quick_ressort_unknown, ressort.text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Macht ein Etikett bedienbar: 48 dp hoch, vom Screenreader als Knopf mit seinem Zweck angesagt. */
@Composable
private fun Tappable(onClickLabel: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.heightIn(min = Sizes.touchTarget).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) { content() }
}

/** Auswahl aus einer Gruppe von Pillen in einem Dialog (zum Ändern einer erkannten Angabe). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceDialog(title: String, choices: List<Choice>, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    CeviDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            choices.forEach { PickPill(it.label, selected == it.key, { onPick(it.key) }) }
        }
    }
}
