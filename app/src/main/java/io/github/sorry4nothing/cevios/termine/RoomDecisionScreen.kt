package io.github.sorry4nothing.cevios.termine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.lager.ConfirmDialog
import io.github.sorry4nothing.cevios.lager.TextDialog
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.Spinner
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

/**
 * «Raumanfragen entscheiden» für das zuständige Team. Jede Karte hat «Bestätigen» (mit Rückfrage, weil es sich nicht zurücknehmen lässt)
 * und «Ablehnen» (mit Begründung, der Vertrag verlangt sie). Meldet der Server «belegt», bleibt an der Karte nur «Ablehnen».
 * Die Hinweise bleiben, bis die Person sie schliesst.
 */
@Composable
fun RoomDecisionScreen(
    open: ApiResult<RoomRequestPage>?,
    state: RoomDecisionsViewModel.DecideState,
    blocked: Set<String>,
    onDecide: (RoomRequest, Boolean, String?) -> Unit,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.room_back_profile), onBack)
    var confirming by remember { mutableStateOf<RoomRequest?>(null) }
    var rejecting by remember { mutableStateOf<RoomRequest?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.decide_title), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        Box(Modifier.weight(1f)) {
            when (open) {
                null -> CardSkeleton(stringResource(R.string.room_list_loading))
                is ApiResult.Failure -> ErrorState(open.error, onRetry, onToPrograms = onBack, homeLabel = R.string.room_back_profile)
                is ApiResult.Ok -> {
                    val requests = open.value.requests
                    if (requests.isEmpty() && state.notice == null) {
                        StatePage(R.drawable.ic_door, stringResource(R.string.decide_empty_title), stringResource(R.string.decide_empty_text))
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                        ) {
                            DecisionBanner(state.notice, onCheck, onDismiss)
                            if (open.value.more) NoticeBanner(NoticeKind.INFO, stringResource(R.string.decide_more))
                            requests.forEach { request ->
                                DecisionCard(
                                    request,
                                    running = state.deciding == request.id,
                                    enabled = state.deciding == null,
                                    mayConfirm = request.id !in blocked,
                                    onConfirm = { confirming = request },
                                    onReject = { rejecting = request },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    confirming?.let { request ->
        ConfirmDialog(
            stringResource(R.string.decide_confirm_title),
            stringResource(R.string.decide_confirm_text, request.roomName, roomWhenSpoken(request.start, request.end)),
            stringResource(R.string.decide_confirm),
            onConfirm = { confirming = null; onDecide(request, true, null) },
            onDismiss = { confirming = null },
        )
    }
    rejecting?.let { request ->
        TextDialog(
            title = stringResource(R.string.decide_reject_title),
            label = stringResource(R.string.decide_reject_label),
            initial = "",
            maxChars = ROOM_REASON_MAX,
            confirmText = stringResource(R.string.decide_reject),
            onConfirm = { text -> rejectionReason(text)?.let { rejecting = null; onDecide(request, false, it) } },
            onDismiss = { rejecting = null },
            multiline = true,
        )
    }
}

@Composable
private fun DecisionBanner(notice: DecisionNotice?, onCheck: () -> Unit, onClose: () -> Unit) {
    val (kind, text) = when (notice) {
        null -> return
        DecisionNotice.CONFIRMED -> NoticeKind.SUCCESS to R.string.decide_notice_confirmed
        DecisionNotice.REJECTED -> NoticeKind.SUCCESS to R.string.decide_notice_rejected
        DecisionNotice.ALREADY_DECIDED -> NoticeKind.ERROR to R.string.decide_notice_already
        DecisionNotice.BUSY -> NoticeKind.ERROR to R.string.decide_notice_busy
        DecisionNotice.GONE -> NoticeKind.ERROR to R.string.decide_notice_gone
        DecisionNotice.SECOND_FACTOR -> NoticeKind.ERROR to R.string.decide_notice_second_factor
        DecisionNotice.NOT_ALLOWED -> NoticeKind.ERROR to R.string.decide_notice_not_allowed
        DecisionNotice.OFFLINE -> NoticeKind.WARNING to R.string.decide_notice_offline
        DecisionNotice.FAILED -> NoticeKind.ERROR to R.string.decide_notice_failed
    }
    // Nur wo der Ausgang offen ist, führt der Knopf zur Liste; nochmals senden bietet die App nie an.
    val check = notice == DecisionNotice.OFFLINE || notice == DecisionNotice.FAILED
    ActionBanner(kind, stringResource(text), if (check) stringResource(R.string.decide_check) else null, if (check) onCheck else null, onClose)
}

@Composable
private fun DecisionCard(
    request: RoomRequest,
    running: Boolean,
    enabled: Boolean,
    mayConfirm: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val whenSpoken = roomWhenSpoken(request.start, request.end)
    val row = "${request.roomName}, $whenSpoken"
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Der Text der Karte ist ein Satz für den Screenreader; die Knöpfe darunter bleiben eigenständig.
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(request.roomName, style = MaterialTheme.typography.titleMedium)
            request.requesterName?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.decide_from, it), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                roomWhen(request.start, request.end),
                Modifier.semantics { contentDescription = whenSpoken },
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(request.purpose, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (running) {
            Row(
                Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spinner(size = 20.dp, color = MaterialTheme.cevi.accent)
                Text(stringResource(R.string.decide_deciding), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            if (mayConfirm) {
                PrimaryButton(stringResource(R.string.decide_confirm), onConfirm, Modifier.fillMaxWidth().rowAction(stringResource(R.string.decide_confirm), row), enabled = enabled)
            }
            OutlinedPillButton(stringResource(R.string.decide_reject), onReject, Modifier.fillMaxWidth().rowAction(stringResource(R.string.decide_reject), row), enabled = enabled)
        }
    }
}
