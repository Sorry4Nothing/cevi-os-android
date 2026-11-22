package io.github.sorry4nothing.cevios.lager

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import androidx.compose.ui.unit.dp

/**
 * Der gemeinsame Rahmen der Screens von Sitzungen und Kommunikation: Kopf, Warteschlangen-Banner (Ohne Netz), Hinweis «Lager
 * abgeschlossen», Fehler der letzten Aktion, dann der Inhalt. Wo der Server keine Warteschlange für das Objekt kennt, gehen
 * Änderungen direkt hinaus: Ohne Netz scheitert die Aktion und bietet «Nochmals versuchen» an.
 */
@Composable
fun CampPage(
    title: String?,
    camp: Camp?,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    writer: DirectWriter?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    back: HeaderButton? = null,
    action: HeaderButton? = null,
    extraAction: HeaderButton? = null,
    banners: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Header(title = title, subtitle = subtitle, back = back, action = action, extraAction = extraAction, focusOnTitle = true)
        Column(Modifier.padding(horizontal = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            QueueBanner(queue, online, onOpenQueue)
            if (camp?.readOnly == true) {
                NoticeBanner(
                    NoticeKind.INFO,
                    stringResource(R.string.camp_closed_title) + ". " + stringResource(R.string.camp_closed_text),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            writer?.let { WriteErrorBanner(it) }
            banners()
        }
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

/** Der Fehler der letzten Aktion mit «Nochmals versuchen»; er bleibt, bis die Person ihn schliesst oder die Aktion gelingt. */
@Composable
fun WriteErrorBanner(writer: DirectWriter) {
    val error = writer.error.collectAsState().value ?: return
    val retry = error.retry
    ActionBanner(
        NoticeKind.ERROR, writeErrorText(error),
        retry?.let { stringResource(R.string.retry) }, retry?.let { run -> { writer.dismissError(); run() } },
        writer::dismissError,
    )
}

/** Der Satz zu einem Fehler: bekannte Codes des Servers in Worten, sonst der allgemeine Satz («Deine Eingabe ist noch da»). */
@Composable
fun writeErrorText(error: ActionError): String = stringResource(writeErrorRes(error))

@StringRes
fun writeErrorRes(error: ActionError): Int = when (error.code) {
    "HOECK_HAT_TRAKTANDEN" -> R.string.error_meeting_has_agenda
    "UMFRAGE_GESCHLOSSEN" -> R.string.error_poll_closed
    "UMFRAGE_EXISTIERT" -> R.string.error_poll_exists
    "ZEITFENSTER_UNGUELTIG" -> R.string.error_window_invalid
    "DATEI_UNBEKANNT", "UNGUELTIGER_LINK", "LINK_UNGUELTIG", "HOST_NICHT_ERLAUBT" -> R.string.error_file_link
    "KEIN_MITGLIED" -> R.string.error_not_member
    "SCHON_EINGETEILT" -> R.string.error_already_seated
    "BEREITS_IN_FAHRT" -> R.string.error_already_in_trip
    "ABGEWAEHLT" -> R.string.error_excluded
    "ABWESEND" -> R.string.error_absent
    "DOPPELBELEGT" -> R.string.error_double_booked
    // Gilt für die Anfrage zum Diensttausch und zur Anwesenheit (derselbe Code, dieselbe Aussage).
    "ANFRAGE_OFFEN" -> R.string.error_request_waiting
    "NICHT_DEINE_SCHICHT", "NICHT_DEINE_ANFRAGE" -> R.string.error_not_your_shift
    "BEREITS_ENTSCHIEDEN" -> R.string.error_swap_decided
    "SCHICHT_GEAENDERT" -> R.string.error_shift_changed
    "PERSON_NICHT_VERFUEGBAR" -> R.string.error_person_unavailable
    "MIT_SCHICHT_UNGUELTIG", "MIT_PERSON_UNGUELTIG" -> R.string.error_swap_invalid
    "DIENST_VORHANDEN" -> R.string.error_duty_exists
    "LIMIT_ERREICHT", "ZU_VIELE_GRUPPEN" -> R.string.error_limit_reached
    "ERWAEHNUNG_UNGUELTIG", "ZU_VIELE_MARKEN" -> R.string.error_mention_invalid
    "ANFRAGE_NOETIG" -> R.string.error_attendance_locked
    "KEINE_ANFRAGE_NOETIG" -> R.string.error_attendance_open
    "SEITE_VORHANDEN" -> R.string.error_day_exists
    "IN_VERWENDUNG" -> R.string.error_in_use
    "VORLAGE_UNGUELTIG" -> R.string.error_template_invalid
    else -> when (error.error) {
        ApiError.NETWORK, ApiError.LAGER_CLOSED, ApiError.FORBIDDEN, ApiError.TOO_MANY_REQUESTS, ApiError.VERSION_CONFLICT, ApiError.NOT_FOUND -> error.error.text
        else -> R.string.task_error
    }
}

/** Ein Abschnittstitel (Überschrift für den Screenreader). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** Eine Karte mit feinem Rand; mit `onClick` ein Knopf (mindestens 48 dp), den TalkBack als ganzen Satz liest. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        content = content,
    )
}

/** «Mi 2. Dezember»: Wochentag kurz, Tag und Monat. */
fun formatMeetingDay(date: String): String {
    val day = parseDay(date) ?: return date
    return weekdayName(day).take(2) + " " + formatDay(date)
}

/** «Mi 2. Dezember, 19:30» oder nur der Tag, wenn der Höck keine Zeit hat. */
fun formatMeetingWhen(meeting: Meeting): String = listOfNotNull(formatMeetingDay(meeting.date), meeting.time?.takeIf { it.isNotBlank() }).joinToString(", ")

/** Öffnet einen Link nur, wenn er mit https beginnt (der Server lässt nur diese zu; ein Link von aussen ist trotzdem nicht vertrauenswürdig). */
fun openLink(handler: UriHandler, url: String) {
    if (url.startsWith("https://")) runCatching { handler.openUri(url) }
}

@Composable
fun rememberLinkOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return { url -> openLink(handler, url) }
}

/** Ein Blatt mit einer Liste von Aktionen (ein Eintrag je Zeile, mindestens 48 dp). Aktionen, die es für die Rolle nicht gibt, stehen nicht drin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, entries: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            entries.forEach { (label, action) ->
                OutlinedPillButton(label, { onDismiss(); action() }, Modifier.fillMaxWidth())
            }
        }
    }
}
