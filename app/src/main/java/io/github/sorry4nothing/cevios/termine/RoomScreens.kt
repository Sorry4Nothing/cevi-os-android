package io.github.sorry4nothing.cevios.termine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import io.github.sorry4nothing.cevios.lager.ConfirmDialog
import io.github.sorry4nothing.cevios.lager.DateDialog
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.SlowHint
import io.github.sorry4nothing.cevios.ui.Spinner
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

private const val BUSY = "RAUM_BELEGT"

/**
 * «Meine Raumanfragen» (Entwurf K3). Die Karten zeigen Raum, Zeit, Zweck und den Stand mit Icon und Wort. «Raum anfragen» steht
 * immer unten, ausser wenn das Laden fehlschlug. Zurückgezogene Anfragen (`storniert`) zeigt die Liste nicht.
 */
@Composable
fun RoomListScreen(
    mine: ApiResult<RoomRequestPage>?,
    list: RoomsViewModel.ListState,
    onAsk: () -> Unit,
    onCancel: (RoomRequest) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.room_back_profile), onBack)
    var confirming by remember { mutableStateOf<RoomRequest?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.room_list_title), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        Box(Modifier.weight(1f)) {
            when (mine) {
                null -> CardSkeleton(stringResource(R.string.room_list_loading))
                is ApiResult.Failure -> ErrorState(mine.error, onRetry, onToPrograms = onBack, homeLabel = R.string.room_back_profile)
                is ApiResult.Ok -> {
                    val shown = mine.value.requests.filter { roomStatus(it.status) != RoomStatus.CANCELLED }
                    if (shown.isEmpty() && list.notice == null) {
                        StatePage(R.drawable.ic_door, stringResource(R.string.room_list_empty_title), stringResource(R.string.room_list_empty_text))
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                        ) {
                            ListBanner(list.notice, onDismiss)
                            shown.forEach { request ->
                                RequestCard(request, running = list.cancelling == request.id, onCancel = {
                                    // Eine bestätigte Buchung abzusagen fragt nach; eine offene Anfrage zurückzuziehen nicht (sie lässt sich neu stellen).
                                    if (roomStatus(request.status) == RoomStatus.CONFIRMED) confirming = request else onCancel(request)
                                })
                            }
                            Spacer(Modifier.height(Spacing.s2))
                        }
                    }
                }
            }
        }
        if (mine !is ApiResult.Failure) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.s5, vertical = Spacing.s3)) {
                PrimaryButton(stringResource(R.string.room_ask), onAsk, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
            }
        }
    }
    confirming?.let { request ->
        ConfirmDialog(
            stringResource(R.string.room_cancel_title),
            stringResource(R.string.room_cancel_text, request.roomName, roomWhenSpoken(request.start, request.end)),
            stringResource(R.string.room_cancel_booking),
            onConfirm = { confirming = null; onCancel(request) },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun ListBanner(notice: RoomsViewModel.ListNotice?, onClose: () -> Unit) {
    when (notice) {
        null -> Unit
        RoomsViewModel.ListNotice.WITHDRAWN -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.room_notice_withdrawn), null, null, onClose)
        RoomsViewModel.ListNotice.CANCELLED -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.room_notice_cancelled), null, null, onClose)
        RoomsViewModel.ListNotice.FAILED -> ActionBanner(NoticeKind.ERROR, stringResource(R.string.room_notice_failed), null, null, onClose)
        RoomsViewModel.ListNotice.NOT_POSSIBLE -> ActionBanner(NoticeKind.ERROR, stringResource(R.string.room_notice_not_possible), null, null, onClose)
    }
}

@Composable
private fun RequestCard(request: RoomRequest, running: Boolean, onCancel: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val status = roomStatus(request.status)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        // Der Text der Karte ist ein Satz für den Screenreader; der Knopf darunter bleibt eigenständig.
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
                Text(request.roomName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val (kind, label) = when (status) {
                    RoomStatus.CONFIRMED -> ChipKind.YES to R.string.room_status_confirmed
                    RoomStatus.REJECTED -> ChipKind.NO to R.string.room_status_rejected
                    else -> ChipKind.PENDING to R.string.room_status_open
                }
                StatusChip(kind, stringResource(label))
            }
            val whenSpoken = roomWhenSpoken(request.start, request.end)
            Text(
                roomWhen(request.start, request.end),
                Modifier.semantics { contentDescription = whenSpoken },
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(request.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status == RoomStatus.REJECTED) {
                request.reason?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.room_reason, it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        when {
            running -> Row(
                Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spinner(size = 20.dp, color = MaterialTheme.cevi.accent)
                Text(stringResource(R.string.room_cancelling), style = MaterialTheme.typography.bodyMedium)
            }
            canCancelRequest(request) -> {
                val open = status == RoomStatus.OPEN
                val description = stringResource(if (open) R.string.room_withdraw_label else R.string.room_cancel_booking_label, request.roomName)
                TextActionButton(
                    stringResource(if (open) R.string.room_withdraw else R.string.room_cancel_booking), onCancel,
                    Modifier.semantics { contentDescription = description },
                )
            }
        }
    }
}

/**
 * «Raum anfragen» (Entwurf K2): Raum aus der Liste, Zeit am selben Tag, Zweck. Die Eingabe bleibt bei jedem Fehler stehen; eine
 * Meldung bleibt, bis die Person sie schliesst. Ohne Räume steht ein Satz statt des Formulars.
 */
@Composable
fun RoomRequestScreen(
    rooms: ApiResult<RoomPage>?,
    draft: RoomDraft,
    send: RoomsViewModel.SendState,
    onEdit: ((RoomDraft) -> RoomDraft) -> Unit,
    onSubmit: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.room_back_list), onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.room_ask), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        when (rooms) {
            null -> CardSkeleton(stringResource(R.string.room_loading))
            is ApiResult.Failure -> ErrorState(rooms.error, onRetry, onToPrograms = onBack, homeLabel = R.string.room_back_list)
            is ApiResult.Ok ->
                if (rooms.value.rooms.isEmpty()) {
                    StatePage(R.drawable.ic_door, stringResource(R.string.room_empty_title), stringResource(R.string.room_empty_text))
                } else {
                    RoomForm(rooms.value.rooms, draft, send, onEdit, onSubmit, onUndo, onDismiss)
                }
        }
    }
}

@Composable
private fun RoomForm(
    rooms: List<Room>,
    draft: RoomDraft,
    send: RoomsViewModel.SendState,
    onEdit: ((RoomDraft) -> RoomDraft) -> Unit,
    onSubmit: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val problems = send.problems.orEmpty()
    var pickingDay by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        RoomSendBanner(send, onSubmit, onUndo, onDismiss)
        if (problems.isNotEmpty()) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.room_check))
        NoticeBanner(NoticeKind.INFO, stringResource(R.string.room_info))
        Text(
            stringResource(R.string.room_field_room), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            rooms.forEach { room -> RoomChoice(room, room.id == draft.roomId, !send.running) { onEdit { it.copy(roomId = room.id) } } }
        }
        FieldError(RoomProblem.ROOM in problems, R.string.room_error_room)
        DateField(stringResource(R.string.room_field_date), draft.day, !send.running) { pickingDay = true }
        FieldError(RoomProblem.DAY in problems, R.string.room_error_day)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            LabeledTextField(
                stringResource(R.string.room_field_from), draft.from, { v -> onEdit { it.copy(from = v) } }, 5,
                Modifier.weight(1f), hint = stringResource(R.string.room_field_time_hint), keyboardType = KeyboardType.Number, enabled = !send.running,
            )
            LabeledTextField(
                stringResource(R.string.room_field_to), draft.to, { v -> onEdit { it.copy(to = v) } }, 5,
                Modifier.weight(1f), hint = stringResource(R.string.room_field_time_hint), keyboardType = KeyboardType.Number, enabled = !send.running,
            )
        }
        FieldError(RoomProblem.FROM in problems || RoomProblem.TO in problems, R.string.room_error_time)
        FieldError(RoomProblem.ORDER in problems, R.string.room_error_order)
        FieldError(RoomProblem.PAST in problems, R.string.room_error_past)
        if (send.code == BUSY) busyText(draft)?.let { FieldMessage(it) }
        LabeledTextField(
            stringResource(R.string.room_field_purpose), draft.purpose, { v -> onEdit { it.copy(purpose = oneLine(v)) } }, ROOM_PURPOSE_MAX,
            hint = stringResource(R.string.room_field_purpose_hint), minLines = 2, enabled = !send.running,
        )
        FieldError(RoomProblem.PURPOSE in problems, R.string.room_error_purpose)
        PrimaryButton(
            stringResource(R.string.room_send), onSubmit, Modifier.fillMaxWidth(),
            loading = send.running, loadingText = stringResource(R.string.room_sending),
        )
        SlowHint(send.running)
        Spacer(Modifier.height(Spacing.s5))
    }
    if (pickingDay) {
        DateDialog(
            initial = draft.day?.toString(),
            onPick = { picked ->
                val day = picked?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                onEdit { it.copy(day = day) }
                pickingDay = false
            },
            onDismiss = { pickingDay = false },
        )
    }
}

/** «Dieser Raum ist am Samstag, 12.12. zwischen 14:00 und 18:00 schon belegt. …» aus der Eingabe; der Server nennt die belegte Zeit nicht. */
@Composable
private fun busyText(draft: RoomDraft): String? {
    val day = draft.day ?: return null
    val from = parseTime(draft.from) ?: return null
    val to = parseTime(draft.to) ?: return null
    return stringResource(R.string.room_busy, roomDayLong(day), CLOCK.format(from), CLOCK.format(to))
}

@Composable
private fun RoomSendBanner(send: RoomsViewModel.SendState, onRetry: () -> Unit, onUndo: () -> Unit, onClose: () -> Unit) {
    when {
        send.error != null -> {
            val (kind, text) = when {
                send.code == BUSY -> NoticeKind.ERROR to R.string.room_busy_banner
                send.code == "ANFRAGE_LIMIT" -> NoticeKind.ERROR to R.string.room_limit
                send.code == "ZEIT_VERGANGEN" -> NoticeKind.ERROR to R.string.room_error_past
                send.code == "ZEIT_ZU_LANG" -> NoticeKind.ERROR to R.string.room_too_long
                send.error == ApiError.NOT_FOUND -> NoticeKind.ERROR to R.string.room_gone
                send.error == ApiError.NETWORK -> NoticeKind.WARNING to R.string.room_offline
                else -> NoticeKind.ERROR to R.string.room_failed
            }
            // Ein zweiter Versuch hilft nur, wenn nichts an der Eingabe zu ändern ist: bei Netz und bei unbekannten Fehlern.
            val retry = send.code == null && send.error != ApiError.NOT_FOUND && send.error != ApiError.FORBIDDEN
            ActionBanner(kind, stringResource(text), if (retry) stringResource(R.string.retry) else null, if (retry) onRetry else null, onClose)
        }
        send.sentId != null -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.room_sent), stringResource(R.string.room_undo), onUndo, onClose)
        send.undone -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.room_undone), null, null, onClose)
    }
}

/** Eine Fehlermeldung mit schon fertigem Text, am Feld, mit Icon und Wort. */
@Composable
private fun FieldMessage(text: String) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Ein Raum als Auswahlzeile: Name, wer bestätigt, und der Ort, wenn es einen gibt. Der Rand in `accent` zeigt die Wahl, der Punkt auch. */
@Composable
private fun RoomChoice(room: Room, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val scheme = MaterialTheme.colorScheme
    val accent = MaterialTheme.cevi.accent
    val approver = room.team?.let { stringResource(R.string.room_confirmed_by_team, it.name) } ?: stringResource(R.string.room_confirmed_by_leadership)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRing(shape)
            .background(scheme.surfaceContainerLow, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else scheme.outlineVariant, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = accent))
        Column(Modifier.weight(1f)) {
            Text(room.name, style = MaterialTheme.typography.titleMedium)
            Text(approver, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            room.place?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
        }
    }
}
