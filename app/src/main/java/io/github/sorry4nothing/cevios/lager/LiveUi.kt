package io.github.sorry4nothing.cevios.lager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/** Die Bildschirme von «Im Lager» und «Ablage und Querschnitt», die das Blatt «Mehr» und die Ansicht «Heute» öffnen; je einer ist ein Bildschirm. */
enum class LiveArea { TODAY, DAY, ATTENDANCE, SHOPPING, PACKING, EMERGENCY, INFOS, TIMELINE, DOCUMENTS, ADDRESSBOOK, SEARCH, PRINTOUTS }

/**
 * Alles, was die Bildschirme von «Im Lager» gemeinsam brauchen: das ViewModel, das Lager mit seinen Rechten, die Warteschlange für
 * den Banner «Ohne Netz», die bekannten Personen für Auswahlen und der Weg zurück.
 */
class LiveFrame(
    val vm: LiveViewModel,
    val camp: Camp?,
    val rights: LiveRights,
    val queue: QueueState,
    val online: Boolean,
    val onOpenQueue: () -> Unit,
    val people: List<RessortPerson>,
    val self: TaskPerson,
    val onBack: () -> Unit,
)

/** Wohin die Bildschirme weiter navigieren können (der Rest steht in [LiveFrame]). */
class LiveNav(
    val onOpenArea: (LiveArea) -> Unit,
    val onOpenDay: (String) -> Unit,
    val onOpenTask: (String) -> Unit,
    val onOpenShifts: () -> Unit,
    val onOpenSearchHit: (SearchDestination) -> Unit,
)

/** Zeigt den Bildschirm zu einem Bereich von «Im Lager». `dayId` gehört zur Tagesseite. */
@Composable
fun LiveScreen(
    area: LiveArea,
    dayId: String?,
    frame: LiveFrame,
    nav: LiveNav,
    tasks: TasksViewModel,
    taskList: List<CampTask>,
    archive: ArchiveViewModel,
    archiveRights: ArchiveRights,
) {
    when (area) {
        LiveArea.TODAY -> TodayScreen(frame, nav, taskList, tasks)
        LiveArea.DAY -> if (dayId != null) DayScreen(frame, nav, dayId, tasks)
        LiveArea.ATTENDANCE -> AttendanceScreen(frame)
        LiveArea.SHOPPING -> ShoppingScreen(frame)
        LiveArea.PACKING -> PackingScreen(frame)
        LiveArea.EMERGENCY -> EmergencyScreen(frame)
        LiveArea.INFOS -> InfosScreen(frame)
        LiveArea.TIMELINE -> TimelineScreen(frame, archive, nav.onOpenTask)
        LiveArea.DOCUMENTS -> DocumentsScreen(frame, archive, archiveRights)
        LiveArea.ADDRESSBOOK -> AddressBookScreen(frame, archive, archiveRights)
        LiveArea.SEARCH -> SearchScreen(frame, archive, nav.onOpenSearchHit)
        LiveArea.PRINTOUTS -> PrintoutsScreen(frame, archive)
    }
}

/** Der Rahmen eines Bildschirms von «Im Lager»: `CampPage` mit Zurück-Knopf, Hinweis nach einer Aktion und scrollendem Inhalt. */
@Composable
fun LivePage(
    frame: LiveFrame,
    title: String,
    subtitle: String? = frame.camp?.title,
    back: HeaderButton = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), frame.onBack),
    action: HeaderButton? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    CampPage(
        title = title,
        subtitle = subtitle,
        camp = frame.camp, queue = frame.queue, online = frame.online, onOpenQueue = frame.onOpenQueue, writer = frame.vm.writer,
        back = back,
        action = action,
        banners = { LiveNoticeBanner(frame.vm) },
        content = content,
    )
}

/** Eine scrollende Spalte mit den Rändern der Lagerbildschirme; `bottom` lässt Platz für einen Knopf am unteren Rand. */
@Composable
fun LiveColumn(bottom: androidx.compose.ui.unit.Dp = Spacing.s6, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Spacing.s3, end = Spacing.s3, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        content = content,
    )
}

/** Der Hinweis nach einer Aktion; er bleibt, bis die Person ihn schliesst, und wird angesagt. */
@Composable
fun LiveNoticeBanner(vm: LiveViewModel) {
    val notice by vm.notice.collectAsState()
    val text = when (notice) {
        null -> return
        LiveNotice.ATTENDANCE_SAVED -> R.string.att_notice_saved
        LiveNotice.REQUEST_SENT -> R.string.att_notice_request_sent
        LiveNotice.REQUESTS_ACCEPTED -> R.string.att_notice_accepted
        LiveNotice.REQUESTS_DECLINED -> R.string.att_notice_declined
        LiveNotice.DEADLINE_SET -> R.string.att_notice_deadline_set
        LiveNotice.DEADLINE_CLEARED -> R.string.att_notice_deadline_cleared
        LiveNotice.ATTENDANCE_RELOADED -> R.string.att_notice_reloaded
    }
    ActionBanner(if (notice == LiveNotice.ATTENDANCE_RELOADED) NoticeKind.WARNING else NoticeKind.INFO, stringResource(text), null, null, vm::dismissNotice)
}

/** Ein Abschnitt mit Überschrift (für den Screenreader) und den Zeilen darunter. */
@Composable
fun LiveSection(title: String, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Öffnet die Telefon-App mit der Nummer vorgewählt (`ACTION_DIAL`): Die Person wählt selbst, die App braucht dafür keine Berechtigung. */
fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, io.github.sorry4nothing.cevios.contacts.PhoneRules.dialUri(number).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
}

/** Öffnet eine Karten-App (oder den Browser) mit dem Ziel; gibt es keine App dafür, passiert nichts. */
fun openRoute(context: Context, uri: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { if (it !is ActivityNotFoundException) throw it }
}

/** Ein Text auf höchstens `max` Zeichen gekürzt (für Titel in Blättern und Fragen). */
fun String.clip(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

/**
 * «Mein Bereich» im Start des Teams (Entwurf L3): Heute, meine Dienste der nächsten Tage und der Stand der Packliste. Was die Rolle
 * nicht lesen darf, fehlt; Zimmer, Fahrt und Gruppe stehen nicht hier (sie gehören zur Einteilung).
 */
@Composable
fun TeamArea(live: LiveViewModel, camp: Camp, onOpenToday: () -> Unit, onOpenShifts: () -> Unit, onOpenPacking: () -> Unit) {
    val week by live.myWeek.collectAsState()
    val pack by live.pack.collectAsState()
    androidx.compose.runtime.LaunchedEffect(camp.id) {
        live.loadMyWeek()
        live.loadPack(silent = live.pack.value is io.github.sorry4nothing.cevios.net.ApiResult.Ok)
    }
    val today = androidx.compose.runtime.remember { todayInCamp() }
    val shifts = week?.shifts.orEmpty().sortedBy { it.date }
    val packCount = (pack as? io.github.sorry4nothing.cevios.net.ApiResult.Ok)?.value?.let(LiveRules::packCount)
    LiveSection(stringResource(R.string.team_my_area)) {
        InfoCard(onClick = onOpenToday, onClickLabel = stringResource(R.string.today_title)) {
            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
            Text(dayTitle(today.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        shifts.firstOrNull()?.let { first ->
            val line = stringResource(R.string.team_duty_line, first.duty?.name ?: stringResource(R.string.today_duty_unknown), dayTitle(first.date))
            InfoCard(description = stringResource(R.string.team_my_duties) + ": " + line, onClick = onOpenShifts, onClickLabel = stringResource(R.string.team_my_duties)) {
                Text(stringResource(R.string.team_my_duties), style = MaterialTheme.typography.titleMedium)
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        InfoCard(onClick = onOpenPacking, onClickLabel = stringResource(R.string.pack_title)) {
            Text(stringResource(R.string.pack_title), style = MaterialTheme.typography.titleMedium)
            packCount?.let { (done, total) -> Text(stringResource(R.string.team_pack_row, done, total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
