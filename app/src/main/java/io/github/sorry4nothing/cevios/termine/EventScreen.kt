package io.github.sorry4nothing.cevios.termine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.LoadingSkeleton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.SlowHint
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import kotlinx.serialization.json.JsonElement

private const val TEXT_LIMIT = 500

/**
 * Anmeldung zu einem Anlass (Entwurf K4). Je Teilnehmende (ein Kind oder die Person selbst) ein Abschnitt: «Aus deinem Konto» nur
 * zum Lesen, darunter die Zusatzfelder der Leitung. Ist die Anmeldung zu, fehlen die Knöpfe. Gesundheitsdaten gibt es nie.
 */
@Composable
fun EventScreen(
    form: ApiResult<EventForm>?,
    states: Map<String, EventViewModel.ParticipantState>,
    onAnswer: (childId: String?, key: String, value: JsonElement) -> Unit,
    onText: (childId: String?, key: String, text: String) -> Unit,
    onSubmit: (childId: String?) -> Unit,
    onUnregister: (childId: String?) -> Unit,
    onDismiss: (childId: String?) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_event), onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (form) {
            null -> {
                Header(title = null, back = back)
                LoadingSkeleton(stringResource(R.string.anlass_loading))
            }
            is ApiResult.Failure -> {
                Header(title = null, back = back)
                ErrorState(form.error, onRetry, onToPrograms = onBack, homeLabel = R.string.back_to_event)
            }
            is ApiResult.Ok -> EventContent(form.value, states, onAnswer, onText, onSubmit, onUnregister, onDismiss, back)
        }
    }
}

@Composable
private fun EventContent(
    form: EventForm,
    states: Map<String, EventViewModel.ParticipantState>,
    onAnswer: (String?, String, JsonElement) -> Unit,
    onText: (String?, String, String) -> Unit,
    onSubmit: (String?) -> Unit,
    onUnregister: (String?) -> Unit,
    onDismiss: (String?) -> Unit,
    back: HeaderButton,
) {
    val anyRegistered = form.participants.any { it.registration?.registered == true }
    Header(
        title = stringResource(R.string.anlass_title, form.title),
        subtitle = eventDateLine(form).ifEmpty { null },
        back = back,
        titleStyle = MaterialTheme.typography.headlineMedium,
        focusOnTitle = true,
    )
    when {
        // Zu und niemand angemeldet: nur der Satz, kein Knopf.
        !form.open && !anyRegistered -> StatePage(R.drawable.ic_lock, stringResource(R.string.anlass_closed_title), stringResource(R.string.anlass_closed_text))
        form.participants.isEmpty() -> StatePage(R.drawable.ic_group, stringResource(R.string.anlass_no_participants_title), stringResource(R.string.anlass_no_participants_text))
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            if (!form.open) NoticeBanner(NoticeKind.INFO, stringResource(R.string.anlass_closed_text))
            form.deadline?.takeIf { form.open }?.let {
                Text(stringResource(R.string.anlass_deadline, eventDeadlineText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            form.participants.forEach { person ->
                ParticipantSection(form, person, states[EventViewModel.keyOf(person.childId)] ?: EventViewModel.ParticipantState(), onAnswer, onText, onSubmit, onUnregister, onDismiss)
            }
            Spacer(Modifier.height(Spacing.s5))
        }
    }
}

@Composable
private fun ParticipantSection(
    form: EventForm,
    person: EventParticipant,
    state: EventViewModel.ParticipantState,
    onAnswer: (String?, String, JsonElement) -> Unit,
    onText: (String?, String, String) -> Unit,
    onSubmit: (String?) -> Unit,
    onUnregister: (String?) -> Unit,
    onDismiss: (String?) -> Unit,
) {
    val id = person.childId
    val registered = person.registration?.registered == true
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Text(person.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (registered) StatusChip(ChipKind.YES, stringResource(R.string.anlass_registered))
        ResultBanner(person, state, form.registerFor, { onSubmit(id) }, { onUnregister(id) }, { onDismiss(id) })
        if (state.problems?.isNotEmpty() == true) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.anlass_check))
        if (person.account.isNotEmpty()) AccountCard(person.account)
        if (form.open) {
            if (form.fields.isNotEmpty()) {
                Text(stringResource(R.string.anlass_extra), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                form.fields.forEach { field ->
                    FieldInput(field, state.answers[field.key], field.key in state.problems.orEmpty(), state.running, { onAnswer(id, field.key, it) }, { onText(id, field.key, it) })
                }
                NoticeBanner(NoticeKind.INFO, stringResource(R.string.anlass_no_health))
            }
            PrimaryButton(
                stringResource(if (registered) R.string.anlass_submit_change else R.string.anlass_submit),
                { onSubmit(id) },
                Modifier.fillMaxWidth(),
                loading = state.running,
                loadingText = stringResource(R.string.anlass_saving),
            )
            SlowHint(state.running)
            if (registered) TextActionButton(stringResource(R.string.anlass_unregister), { onUnregister(id) })
        }
    }
}

/** Erfolg mit «Rückgängig» (nach dem ersten Anmelden), Fehler mit «Nochmals versuchen»; beides bleibt, bis die Person es schliesst. */
@Composable
private fun ResultBanner(
    person: EventParticipant,
    state: EventViewModel.ParticipantState,
    registerFor: String,
    onRetry: () -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    val error = state.error
    when {
        error != null -> ActionBanner(
            NoticeKind.ERROR,
            when {
                error == ApiError.REGISTRATION_CLOSED -> stringResource(R.string.anlass_closed_text)
                state.rejected -> stringResource(R.string.anlass_rejected)
                else -> stringResource(R.string.anlass_failed)
            },
            if (error == ApiError.REGISTRATION_CLOSED) null else stringResource(R.string.retry),
            if (error == ApiError.REGISTRATION_CLOSED) null else onRetry,
            onClose,
        )
        state.done == EventViewModel.Done.REGISTERED -> ActionBanner(
            NoticeKind.SUCCESS, stringResource(R.string.anlass_done_registered, person.name),
            stringResource(R.string.proposal_undo), onUndo, onClose,
        )
        state.done == EventViewModel.Done.CHANGED -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.anlass_done_changed), null, null, onClose)
        state.done == EventViewModel.Done.UNREGISTERED -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.anlass_done_unregistered, person.name), null, null, onClose)
    }
}

/** «Aus deinem Konto»: nur lesbar. Was nicht stimmt, ändert die Person im Profil. */
@Composable
private fun AccountCard(values: List<AccountValue>) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.anlass_account), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
        values.forEach { value ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(value.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value.value, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(stringResource(R.string.anlass_account_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldInput(
    field: EventField,
    answer: JsonElement?,
    problem: Boolean,
    busy: Boolean,
    onAnswer: (JsonElement) -> Unit,
    onText: (String) -> Unit,
) {
    val label = if (field.required) field.label else stringResource(R.string.anlass_optional, field.label)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        when (field.type) {
            "text" -> LabeledTextField(label, answer?.asAnswerText().orEmpty(), onText, TEXT_LIMIT, minLines = 2, enabled = !busy)
            "ja_nein" -> {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.selectableGroup().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    val chosen = answer?.asAnswerBoolean()
                    PickPill(stringResource(R.string.anlass_yes), chosen == true, { onAnswer(kotlinx.serialization.json.JsonPrimitive(true)) }, enabled = !busy)
                    PickPill(stringResource(R.string.anlass_no), chosen == false, { onAnswer(kotlinx.serialization.json.JsonPrimitive(false)) }, enabled = !busy)
                }
            }
            else -> {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.selectableGroup().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    field.options.forEach { option ->
                        PickPill(option, answer?.asAnswerText() == option, { onAnswer(kotlinx.serialization.json.JsonPrimitive(option)) }, enabled = !busy)
                    }
                }
            }
        }
        if (problem) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(if (field.type == "text") R.string.anlass_error_text else R.string.anlass_error_choice),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
