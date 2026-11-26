package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.DialogText
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneOffset

/** Eine Wahl in einem Blatt: ein Schlüssel (`null` heisst «keine») und ein Wort. */
data class Choice(val key: String?, val label: String)

/**
 * Blatt mit einer Auswahl aus Pillen und den Knöpfen «Übernehmen» und «Abbrechen» (Entwurf L9: Status für mehrere Aufgaben).
 * `allowNone`: eine Pille «Keine» steht zuerst (für Person und Ressort).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChoiceSheet(
    title: String,
    choices: List<Choice>,
    initial: String?,
    onApply: (String?) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String? = null,
) {
    var picked by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (choices.isEmpty() && emptyText != null) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(
                Modifier.pickGroup(title),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                choices.forEach { choice -> PickPill(choice.label, selected = picked == choice.key, onClick = { picked = choice.key }) }
            }
            PrimaryButton(stringResource(R.string.task_apply), { onApply(picked) }, Modifier.fillMaxWidth(), enabled = choices.isNotEmpty())
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Datum wählen (Material-Kalender). `onPick(null)` entfernt das Datum; der Knopf dafür steht nur, wenn es schon eins gibt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(
    initial: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    @StringRes clearText: Int = R.string.task_due_clear,
) {
    val start = parseDay(initial)
    val state = rememberDatePickerState(initialSelectedDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextActionButton(stringResource(R.string.task_apply), {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } ?: onDismiss()
            })
        },
        dismissButton = {
            if (initial != null) TextActionButton(stringResource(clearText), { onPick(null) })
            TextActionButton(stringResource(R.string.cancel), onDismiss)
        },
    ) { DatePicker(state) }
}

/** Frage vor dem Löschen: Titel als Frage, die Knöpfe nennen die Folge. */
@Composable
fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CeviDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.task_delete), onConfirm) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) { DialogText(text) }
}

/** Ein Textfeld im Dialog (Titel, Notiz, Punkt, Link). */
@Composable
fun TextDialog(
    title: String,
    label: String,
    initial: String,
    maxChars: Int,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    multiline: Boolean = false,
    allowEmpty: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    CeviDialog(
        title = title,
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(confirmText, { onConfirm(text) }, enabled = allowEmpty || text.isNotBlank()) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        LabeledTextField(label, text, { text = it }, maxChars, Modifier.fillMaxWidth(), minLines = if (multiline) 4 else 1)
    }
}
