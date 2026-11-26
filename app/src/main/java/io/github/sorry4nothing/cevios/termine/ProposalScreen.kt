package io.github.sorry4nothing.cevios.termine

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.lager.DateDialog
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
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
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * «Termin vorschlagen» (Entwurf K1) mit «Deine Vorschläge» darunter. Wer nicht vorschlagen darf, sieht «Kein Zugriff» statt des
 * Formulars. Die Eingabe bleibt bei jedem Fehler stehen; eine Meldung bleibt, bis die Person sie schliesst.
 */
@Composable
fun ProposalScreen(
    options: ApiResult<ProposalOptions>?,
    draft: ProposalDraft,
    send: ProposalsViewModel.SendState,
    mine: ApiResult<ProposalPage>?,
    onEdit: ((ProposalDraft) -> ProposalDraft) -> Unit,
    onSubmit: () -> Unit,
    onUndo: () -> Unit,
    onWithdraw: (String) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_proposal), onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.proposal_title), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        when (options) {
            null -> LoadingSkeleton(stringResource(R.string.proposal_loading))
            is ApiResult.Failure -> ErrorState(options.error, onRetry, onToPrograms = onBack, homeLabel = R.string.back_to_proposal)
            is ApiResult.Ok ->
                if (!options.value.mayPropose) {
                    StatePage(R.drawable.ic_lock, stringResource(R.string.proposal_denied_title), stringResource(R.string.proposal_denied_text))
                } else {
                    ProposalForm(options.value.tags, draft, send, mine, onEdit, onSubmit, onUndo, onWithdraw, onDismiss)
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalForm(
    tags: List<ProposalTag>,
    draft: ProposalDraft,
    send: ProposalsViewModel.SendState,
    mine: ApiResult<ProposalPage>?,
    onEdit: ((ProposalDraft) -> ProposalDraft) -> Unit,
    onSubmit: () -> Unit,
    onUndo: () -> Unit,
    onWithdraw: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val problems = send.problems.orEmpty()
    var pickingDay by remember { mutableStateOf<DayTarget?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        SendBanner(send, onSubmit, onUndo, onDismiss)
        if (problems.isNotEmpty()) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.proposal_check))
        NoticeBanner(NoticeKind.INFO, stringResource(R.string.proposal_info))
        LabeledTextField(stringResource(R.string.proposal_field_title), draft.title, { v -> onEdit { it.copy(title = v) } }, 120, enabled = !send.running)
        FieldError(ProposalProblem.TITLE in problems, R.string.proposal_error_title)
        SwitchField(stringResource(R.string.proposal_field_all_day), draft.allDay, !send.running) { on -> onEdit { it.copy(allDay = on) } }
        DateField(stringResource(R.string.proposal_field_date), draft.day, !send.running) { pickingDay = DayTarget.DAY }
        FieldError(ProposalProblem.DAY in problems, R.string.proposal_error_day)
        if (draft.allDay) {
            DateField(stringResource(R.string.proposal_field_last_day), draft.lastDay ?: draft.day, !send.running) { pickingDay = DayTarget.LAST }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                LabeledTextField(
                    stringResource(R.string.proposal_field_from), draft.from, { v -> onEdit { it.copy(from = v) } }, 5,
                    Modifier.weight(1f), hint = stringResource(R.string.proposal_field_time_hint), keyboardType = KeyboardType.Number, enabled = !send.running,
                )
                LabeledTextField(
                    stringResource(R.string.proposal_field_to), draft.to, { v -> onEdit { it.copy(to = v) } }, 5,
                    Modifier.weight(1f), hint = stringResource(R.string.proposal_field_time_hint), keyboardType = KeyboardType.Number, enabled = !send.running,
                )
            }
            FieldError(ProposalProblem.FROM in problems || ProposalProblem.TO in problems, R.string.proposal_error_time)
        }
        FieldError(ProposalProblem.ORDER in problems, R.string.proposal_error_order)
        FieldError(ProposalProblem.PAST in problems, R.string.proposal_error_past)
        LabeledTextField(stringResource(R.string.proposal_field_place), draft.place, { v -> onEdit { it.copy(place = v) } }, 120, enabled = !send.running)
        val groupsLabel = stringResource(R.string.proposal_field_groups)
        Text(groupsLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.selectableGroup().semantics { contentDescription = groupsLabel },horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            tags.forEach { tag ->
                val chosen = tag.id in draft.tagIds
                PickPill(
                    tag.name, chosen,
                    { onEdit { it.copy(tagIds = if (chosen) it.tagIds - tag.id else it.tagIds + tag.id) } },
                    role = Role.Checkbox, enabled = !send.running,
                )
            }
        }
        FieldError(ProposalProblem.TAGS in problems, R.string.proposal_error_tags)
        LabeledTextField(stringResource(R.string.proposal_field_description), draft.description, { v -> onEdit { it.copy(description = v) } }, 2000, minLines = 3, enabled = !send.running)
        PrimaryButton(
            stringResource(R.string.proposal_send), onSubmit, Modifier.fillMaxWidth(),
            loading = send.running, loadingText = stringResource(R.string.proposal_sending),
        )
        SlowHint(send.running)
        MineSection(mine, onWithdraw)
        Spacer(Modifier.height(Spacing.s5))
    }
    pickingDay?.let { target ->
        val initial = (if (target == DayTarget.DAY) draft.day else draft.lastDay ?: draft.day)?.toString()
        DateDialog(
            initial = initial,
            onPick = { picked ->
                val day = picked?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                onEdit { if (target == DayTarget.DAY) it.copy(day = day) else it.copy(lastDay = day) }
                pickingDay = null
            },
            onDismiss = { pickingDay = null },
        )
    }
}

private enum class DayTarget { DAY, LAST }

@Composable
private fun SendBanner(send: ProposalsViewModel.SendState, onRetry: () -> Unit, onUndo: () -> Unit, onClose: () -> Unit) {
    when {
        send.error != null -> ActionBanner(
            NoticeKind.ERROR,
            if (send.code == "VORSCHLAG_LIMIT") stringResource(R.string.proposal_limit) else stringResource(R.string.proposal_failed),
            // Beim Limit hilft ein zweiter Versuch nicht; bei allem anderen bleibt die Eingabe und «Nochmals versuchen» sendet sie erneut.
            if (send.code == "VORSCHLAG_LIMIT" || send.error == ApiError.FORBIDDEN) null else stringResource(R.string.retry),
            if (send.code == "VORSCHLAG_LIMIT" || send.error == ApiError.FORBIDDEN) null else onRetry,
            onClose,
        )
        send.sentId != null -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.proposal_sent), stringResource(R.string.proposal_undo), onUndo, onClose)
        send.undone -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.proposal_undone), null, null, onClose)
    }
}

@Composable
internal fun FieldError(show: Boolean, text: Int) {
    if (!show) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Ein Schalter in einer Zeile (mindestens 48 dp); der Zustand steht im Text «An»/«Aus» für den Screenreader und in der Stellung. */
@Composable
private fun SwitchField(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(MaterialTheme.shapes.medium)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.cevi.accent, checkedThumbColor = MaterialTheme.cevi.onAccent, checkedBorderColor = MaterialTheme.cevi.accent,
                checkedIconColor = MaterialTheme.cevi.accent,
            ),
            thumbContent = if (checked) {
                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            },
        )
    }
}

/** Ein Feld, das den Datumsdialog öffnet. Der Wert steht als Text im Feld, nicht nur als Platzhalter. */
@Composable
internal fun DateField(label: String, value: LocalDate?, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val text = value?.let { dayText(it.toString()) } ?: stringResource(R.string.proposal_no_date)
    val action = stringResource(R.string.proposal_pick_date)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.textField)
                .focusRing(shape)
                .background(scheme.surface, shape)
                .border(1.dp, scheme.outline, shape)
                .clickable(enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onClick)
                .semantics { stateDescription = text }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = if (value == null) scheme.onSurfaceVariant else scheme.onSurface, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = MaterialTheme.cevi.accent)
        }
    }
}

@Composable
private fun MineSection(mine: ApiResult<ProposalPage>?, onWithdraw: (String) -> Unit) {
    Text(
        stringResource(R.string.proposal_mine),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.s4).semantics { heading() },
    )
    when (mine) {
        // Ein Fehler hier stört das Formular nicht: Die Liste fehlt, mehr nicht.
        null, is ApiResult.Failure -> Unit
        is ApiResult.Ok ->
            if (mine.value.proposals.isEmpty()) {
                Text(stringResource(R.string.proposal_mine_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                mine.value.proposals.forEach { ProposalCard(it, onWithdraw) }
            }
    }
}

@Composable
private fun ProposalCard(proposal: Proposal, onWithdraw: (String) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(proposal.title, style = MaterialTheme.typography.titleMedium)
        Text(proposalWhen(proposal), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val (kind, label) = when (proposal.status) {
            "freigegeben" -> ChipKind.YES to R.string.proposal_status_approved
            "abgelehnt" -> ChipKind.NO to R.string.proposal_status_rejected
            "in_outlook_eingetragen" -> ChipKind.YES to R.string.proposal_status_entered
            else -> ChipKind.PENDING to R.string.proposal_status_open
        }
        StatusChip(kind, stringResource(label), Modifier.padding(top = Spacing.s1))
        proposal.reason?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.proposal_reason, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (proposal.status == "offen") {
            val description = stringResource(R.string.proposal_withdraw_label, proposal.title)
            TextActionButton(stringResource(R.string.proposal_withdraw), { onWithdraw(proposal.id) }, Modifier.semantics { contentDescription = description })
        }
    }
}

/** «28.11.2027, 14:00–17:00» oder «28.11.2027, ganztägig» in Schweizer Zeit. */
@Composable
private fun proposalWhen(proposal: Proposal): String {
    val allDayWord = stringResource(R.string.proposal_all_day_word)
    return runCatching {
    if (proposal.allDay) {
        val from = dayText(proposal.dayFrom)
        val to = dayText(proposal.dayTo)
        "${if (to.isEmpty() || to == from) from else "$from–$to"}, $allDayWord"
    } else {
        val start = Instant.parse(proposal.start!!).atZone(SWISS_ZONE)
        val end = Instant.parse(proposal.end!!).atZone(SWISS_ZONE)
        "${dayText(start.toLocalDate().toString())}, ${TIME_FORMAT.format(start)}–${TIME_FORMAT.format(end)}"
    }
    }.getOrDefault("")
}
