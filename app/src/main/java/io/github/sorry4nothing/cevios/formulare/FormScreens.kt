package io.github.sorry4nothing.cevios.formulare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.lager.DateDialog
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.termine.DateField
import io.github.sorry4nothing.cevios.termine.dayText
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
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
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.LocalDate

/** Die offenen Formulare der Website (kein Entwurf: aus Kopf, Karten und Chips gebaut). */
@Composable
fun FormListScreen(
    list: ApiResult<FormList>?,
    onOpen: (FormSummary) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.forms_back_profile), onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.forms_title), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        when (list) {
            null -> CardSkeleton(stringResource(R.string.forms_loading))
            is ApiResult.Failure -> ErrorState(list.error, onRetry, onToPrograms = onBack, homeLabel = R.string.forms_back_profile)
            is ApiResult.Ok ->
                if (list.value.forms.isEmpty()) {
                    StatePage(R.drawable.ic_list_check, stringResource(R.string.forms_empty_title), stringResource(R.string.forms_empty_text))
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                    ) {
                        list.value.forms.forEach { FormCard(it) { onOpen(it) } }
                        Spacer(Modifier.height(Spacing.s2))
                    }
                }
        }
    }
}

@Composable
private fun FormCard(form: FormSummary, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val open = stringResource(R.string.forms_open_label)
    val title = form.title?.takeIf { it.isNotBlank() } ?: form.pageTitle
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = open, role = Role.Button, onClick = onClick)
            // Die Karte ist für den Screenreader ein Satz: Titel, Seite, Stand, Frist.
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (form.answered) StatusChip(ChipKind.YES, stringResource(R.string.forms_answered)) else StatusChip(ChipKind.PENDING, stringResource(R.string.forms_not_answered))
        }
        if (!form.title.isNullOrBlank() && form.title != form.pageTitle) {
            Text(stringResource(R.string.forms_card_page, form.pageTitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        form.closesOn?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.forms_closes, dayText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ein Formular ausfüllen. Die Eingaben leben nur im Arbeitsspeicher; «Senden» ist gesperrt, solange eine Antwort aussteht. */
@Composable
fun FormScreen(
    definition: ApiResult<FormDefinition>?,
    values: Map<String, FormValue>,
    send: FormsViewModel.SendState,
    onText: (String, String) -> Unit,
    onFlag: (String, Boolean) -> Unit,
    onToggle: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.forms_back), onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (definition) {
            null -> {
                Header(title = null, back = back)
                LoadingSkeleton(stringResource(R.string.forms_loading_one))
            }
            is ApiResult.Failure -> {
                Header(title = null, back = back)
                ErrorState(definition.error, onRetry, onToPrograms = onBack, homeLabel = R.string.forms_back)
            }
            is ApiResult.Ok -> FormContent(definition.value, values, send, onText, onFlag, onToggle, onSubmit, onDismiss, onBack, back)
        }
    }
}

@Composable
private fun FormContent(
    form: FormDefinition,
    values: Map<String, FormValue>,
    send: FormsViewModel.SendState,
    onText: (String, String) -> Unit,
    onFlag: (String, Boolean) -> Unit,
    onToggle: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    back: HeaderButton,
) {
    Header(
        title = form.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.forms_title_fallback),
        subtitle = form.closesOn?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.forms_closes, dayText(it)) },
        back = back,
        titleStyle = MaterialTheme.typography.headlineMedium,
        focusOnTitle = true,
    )
    val toBack = stringResource(R.string.forms_back)
    when {
        send.done -> StatePage(R.drawable.ic_check, stringResource(R.string.forms_done_title), form.thanks.ifBlank { stringResource(R.string.forms_thanks_default) }) {
            PrimaryButton(toBack, onBack)
        }
        form.closed -> StatePage(R.drawable.ic_lock, stringResource(R.string.forms_closed_title), stringResource(R.string.forms_closed_text)) {
            PrimaryButton(toBack, onBack)
        }
        form.onePerPerson && form.answered -> StatePage(R.drawable.ic_check, stringResource(R.string.forms_answered_title), stringResource(R.string.forms_answered_text)) {
            PrimaryButton(toBack, onBack)
        }
        else -> FormBody(form, values, send, onText, onFlag, onToggle, onSubmit, onDismiss)
    }
}

@Composable
private fun FormBody(
    form: FormDefinition,
    values: Map<String, FormValue>,
    send: FormsViewModel.SendState,
    onText: (String, String) -> Unit,
    onFlag: (String, Boolean) -> Unit,
    onToggle: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val problems = send.problems.orEmpty()
    val unsupported = FormRules.unsupported(form)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        SendBanner(send.failure, onSubmit, onDismiss)
        if (problems.isNotEmpty() && send.failure == null) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.forms_check))
        if (unsupported.isNotEmpty()) NoticeBanner(NoticeKind.WARNING, stringResource(R.string.forms_unsupported))
        if (form.answered) NoticeBanner(NoticeKind.INFO, stringResource(R.string.forms_answered_again))
        form.intro?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        form.fields.forEach { field ->
            FieldInput(field, values[field.id], problems[field.id], !send.running, onText, onFlag, onToggle)
        }
        if (FormRules.canSend(form)) {
            NoticeBanner(NoticeKind.INFO, stringResource(R.string.forms_no_health))
            if (form.privacy.isNotBlank()) {
                Text(stringResource(R.string.forms_privacy_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(form.privacy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(
                form.submitLabel.ifBlank { stringResource(R.string.forms_submit_default) },
                onSubmit,
                Modifier.fillMaxWidth(),
                loading = send.running,
                loadingText = stringResource(R.string.forms_sending),
            )
            SlowHint(send.running)
        }
        Spacer(Modifier.height(Spacing.s5))
    }
}

/** Fehler und Rückfragen verschwinden nie von selbst; «Nochmals versuchen» gibt es nur, wo ein zweiter Versuch Sinn hat, und sendet nur auf Tipp. */
@Composable
private fun SendBanner(failure: SendFailure?, onRetry: () -> Unit, onClose: () -> Unit) {
    if (failure == null) return
    val text = when (failure) {
        SendFailure.NETWORK -> R.string.forms_fail_network
        SendFailure.TOO_FAST -> R.string.forms_fail_too_fast
        SendFailure.ALREADY -> R.string.forms_fail_already
        SendFailure.FULL -> R.string.forms_fail_full
        SendFailure.CLOSED -> R.string.forms_fail_closed
        SendFailure.TOO_OFTEN -> R.string.forms_fail_too_often
        SendFailure.LOCKED -> R.string.forms_fail_locked
        SendFailure.MAINTENANCE -> R.string.forms_fail_maintenance
        SendFailure.FORBIDDEN -> R.string.forms_fail_forbidden
        SendFailure.NOT_FOUND -> R.string.forms_fail_not_found
        SendFailure.INVALID -> R.string.forms_fail_invalid
        SendFailure.OTHER -> R.string.forms_fail_other
    }
    val retry = failure.canRetry()
    ActionBanner(NoticeKind.ERROR, stringResource(text), if (retry) stringResource(R.string.retry) else null, if (retry) onRetry else null, onClose)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldInput(
    field: FormField,
    value: FormValue?,
    problem: FormProblem?,
    enabled: Boolean,
    onText: (String, String) -> Unit,
    onFlag: (String, Boolean) -> Unit,
    onToggle: (String, String) -> Unit,
) {
    val label = if (field.required || field.type == "zustimmung") field.label else stringResource(R.string.forms_optional, field.label)
    val text = (value as? FormValue.Text)?.text.orEmpty()
    var pickingDate by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        when (field.type) {
            "text" -> LabeledTextField(label, text, { onText(field.id, it) }, field.maxLength ?: TEXT_LIMIT, hint = field.help, enabled = enabled)
            "textbereich" -> LabeledTextField(label, text, { onText(field.id, it) }, field.maxLength ?: AREA_LIMIT, hint = field.help, minLines = 4, enabled = enabled)
            "email" -> LabeledTextField(label, text, { onText(field.id, it) }, EMAIL_LIMIT, hint = field.help, keyboardType = KeyboardType.Email, enabled = enabled)
            "telefon" -> LabeledTextField(label, text, { onText(field.id, it) }, PHONE_LIMIT, hint = field.help, keyboardType = KeyboardType.Phone, enabled = enabled)
            "zahl" -> LabeledTextField(
                label, text, { onText(field.id, it) }, NUMBER_LIMIT,
                hint = field.help ?: stringResource(if (field.wholeNumber) R.string.forms_whole_hint else R.string.forms_number_hint),
                keyboardType = if (field.wholeNumber) KeyboardType.Number else KeyboardType.Decimal, enabled = enabled,
            )
            "datum" -> {
                DateField(label, runCatching { LocalDate.parse(text) }.getOrNull(), enabled) { pickingDate = true }
                field.help?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            "auswahl", "optionsfelder" -> {
                Label(label, field.help)
                FlowRow(
                    Modifier.selectableGroup().semantics { contentDescription = label },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    field.options.forEach { option -> PickPill(option, text == option, { onText(field.id, if (text == option && !field.required) "" else option) }, enabled = enabled) }
                }
            }
            "mehrfachwahl" -> {
                Label(label, field.help)
                val chosen = (value as? FormValue.Multi)?.chosen.orEmpty()
                choiceHint(field)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FlowRow(
                    Modifier.selectableGroup().semantics { contentDescription = label },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    field.options.forEach { option -> PickPill(option, option in chosen, { onToggle(field.id, option) }, role = Role.Checkbox, enabled = enabled) }
                }
            }
            "janein" -> {
                Label(label, field.help)
                val flag = (value as? FormValue.Flag)?.on
                FlowRow(
                    Modifier.selectableGroup().semantics { contentDescription = label },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    PickPill(stringResource(R.string.forms_yes), flag == true, { onFlag(field.id, true) }, enabled = enabled)
                    PickPill(stringResource(R.string.forms_no), flag == false, { onFlag(field.id, false) }, enabled = enabled)
                }
            }
            "zustimmung" -> {
                Label(label, field.help)
                val on = (value as? FormValue.Flag)?.on == true
                PickPill(stringResource(R.string.forms_consent_yes), on, { onFlag(field.id, !on) }, role = Role.Checkbox, enabled = enabled)
            }
            else -> UnsupportedField(field)
        }
        if (problem != null) ProblemText(problemText(field, problem))
    }
    if (pickingDate) {
        DateDialog(
            initial = text.ifEmpty { null },
            onPick = { picked -> onText(field.id, picked.orEmpty()); pickingDate = false },
            onDismiss = { pickingDate = false },
            clearText = R.string.forms_date_clear,
        )
    }
}

@Composable
private fun Label(label: String, help: String?) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    help?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun choiceHint(field: FormField): String? {
    val least = field.minChoices
    val most = field.maxChoices
    return when {
        least != null && most != null -> stringResource(R.string.forms_choose_between, least, most)
        least != null -> stringResource(R.string.forms_choose_at_least, least)
        most != null -> stringResource(R.string.forms_choose_at_most, most)
        else -> null
    }
}

/** Ein Feld, dessen Typ die App nicht kennt: Name und Satz, kein Eingabefeld. */
@Composable
private fun UnsupportedField(field: FormField) {
    val shape = MaterialTheme.shapes.small
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.s3)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(field.label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.forms_unsupported_field), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Der Hinweis am Feld: Icon und Text, nicht nur Farbe; er erscheint nach dem Senden und die Live-Region sagt ihn an. */
@Composable
private fun ProblemText(message: String) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun problemText(field: FormField, problem: FormProblem): String = when (problem) {
    FormProblem.REQUIRED -> stringResource(if (field.type == "zustimmung") R.string.forms_problem_consent else R.string.forms_problem_required)
    FormProblem.FORMAT -> stringResource(R.string.forms_problem_format)
    FormProblem.TOO_LONG -> field.maxLength?.let { stringResource(R.string.forms_problem_too_long_n, it) } ?: stringResource(R.string.forms_problem_too_long)
    FormProblem.TOO_SHORT -> field.minLength?.let { stringResource(R.string.forms_problem_too_short_n, it) } ?: stringResource(R.string.forms_problem_too_short)
    FormProblem.RANGE -> stringResource(R.string.forms_problem_range)
    FormProblem.OPTION -> stringResource(R.string.forms_problem_option)
    FormProblem.COUNT -> stringResource(R.string.forms_problem_count)
    FormProblem.CONTROL -> stringResource(R.string.forms_problem_control)
    FormProblem.UNKNOWN -> stringResource(R.string.forms_problem_unknown)
}

private const val TEXT_LIMIT = 500
private const val AREA_LIMIT = 4000
private const val EMAIL_LIMIT = 200
private const val PHONE_LIMIT = 40
private const val NUMBER_LIMIT = 20
