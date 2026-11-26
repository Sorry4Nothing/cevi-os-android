package io.github.sorry4nothing.cevios.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.termine.eventDeadlineText
import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.contacts.ContactCard
import io.github.sorry4nothing.cevios.contacts.ContactList
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.GroupChip
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LoadingSkeleton
import io.github.sorry4nothing.cevios.ui.EmptyState
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val CARD = RoundedCornerShape(16.dp)

@Composable
fun ProgramListScreen(
    state: ApiResult<ProgramList>?,
    refreshing: Boolean,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    val refreshLabel = stringResource(R.string.refresh)
    // Das Herunterziehen erreichen nicht alle; für TalkBack und Schalterzugriff gibt es dieselbe Aktion als Menüeintrag.
    Column(Modifier.fillMaxSize().semantics { customActions = listOf(CustomAccessibilityAction(refreshLabel) { onRefresh(); true }) }) {
        Header(title = stringResource(R.string.programs_title), focusOnTitle = true)
        when (state) {
            null -> LoadingSkeleton(stringResource(R.string.programs_loading))
            is ApiResult.Failure -> ErrorState(state.error, onRetry)
            is ApiResult.Ok ->
                if (state.value.programs.isEmpty()) {
                    EmptyState(
                        R.drawable.ic_calendar,
                        stringResource(R.string.programs_empty_title),
                        stringResource(R.string.programs_empty_text),
                        onRetry,
                    )
                } else {
                    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
                        Timeline(groupByDay(state.value.programs), onOpen)
                    }
                }
        }
    }
}

/** Die Liste als Zeitleiste: links das Datum an einer Linie, rechts die Programme dieses Tages als Karten. */
@Composable
private fun Timeline(days: List<Day>, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s5, bottom = Spacing.s4)) {
        itemsIndexed(days, key = { _, day -> day.programs.first().id }) { index, day ->
            val isLast = index == days.lastIndex
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                DateColumn(day, withLine = !isLast)
                Column(
                    Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    day.programs.forEach { ProgramCard(it, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun DateColumn(day: Day, withLine: Boolean) {
    val date = day.date
    // «MI 25» liest TalkBack sonst als Kürzel und Zahl: Der Tag wird ausgeschrieben gesprochen.
    val spoken = date?.let { "${weekdayLong(it)}, ${dayAndMonth(it)}" }
    Column(
        Modifier.width(52.dp).fillMaxHeight().then(if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (date != null) {
            Text(weekdayShort(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.cevi.accent)
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (withLine) {
            Box(
                Modifier.padding(vertical = 6.dp).width(2.dp).weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
            )
        }
    }
}

/**
 * Die Gruppen des Termins für Chips: je eine Kategorie (nur die eigenen), bei einer Stufe mit der ID für das Logo, falls es eins gibt.
 * Der ältere Stand der Programme nennt Stufen statt Kategorien. Einen Chip «Alle Stufen» oder «Nicht zugeordnet» gibt es nicht mehr.
 */
internal fun groupChips(program: Program): List<GroupChip> =
    if (program.tags.isNotEmpty()) {
        program.tags.map { GroupChip(it.name, it.id.takeIf { _ -> it.type == "stufe" }) }
    } else {
        program.ageGroups.map { GroupChip(it.name, it.id) }
    }

/** Uhrzeit mit Symbol, Titel, Ort; darunter die Gruppen und der Stand der eigenen Zusagen als Chips (Zahl, nie Namen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramCard(program: Program, onOpen: (String) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .focusRing(CARD)
            .clip(CARD)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.program_open)) { onOpen(program.id) }
            .padding(horizontal = Spacing.s4, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_clock), contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
            Text(listTime(program), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = muted)
        }
        Text(program.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp))
        program.location?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            groupChips(program).forEach { StatusChip(ChipKind.AGE_GROUP, it.name, stageId = it.stageId) }
            // Bei einem abgesagten Programm steht «Abgesagt» da, Zusagen sind dann ohne Bedeutung.
            if (!program.cancelled) AnswerChips(program)
            if (program.cancelled) StatusChip(ChipKind.NO, stringResource(R.string.status_cancelled))
            if (program.inReview) StatusChip(ChipKind.REVIEW, stringResource(R.string.status_review))
        }
    }
}

/** Der Stand der eigenen Kinder in Zahlen («1 Kind kommt») und bei Leitenden der eigene («Du kommst»): Symbol und Wort, nie nur Farbe. */
@Composable
private fun AnswerChips(program: Program) {
    val counts = program.children.counts()
    if (counts.yes > 0) StatusChip(ChipKind.YES, pluralStringResource(R.plurals.children_yes, counts.yes, counts.yes))
    if (counts.no > 0) StatusChip(ChipKind.NO, pluralStringResource(R.plurals.children_no, counts.no, counts.no))
    if (counts.open > 0) StatusChip(ChipKind.PENDING, pluralStringResource(R.plurals.children_open, counts.open, counts.open))
    when (program.ownRegistration) {
        null -> {}
        "ja" -> StatusChip(ChipKind.YES, stringResource(R.string.own_yes))
        "nein" -> StatusChip(ChipKind.NO, stringResource(R.string.own_no))
        else -> StatusChip(ChipKind.PENDING, stringResource(R.string.own_open))
    }
}

/**
 * Abgesagt oder «wird noch geprüft»: als Etikett mit Icon und Wort. Ein aktives Programm braucht keins.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramStatus(program: Program, modifier: Modifier = Modifier) {
    if (!program.cancelled && !program.inReview) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        if (program.cancelled) StatusChip(ChipKind.NO, stringResource(R.string.status_cancelled))
        if (program.inReview) StatusChip(ChipKind.REVIEW, stringResource(R.string.status_review))
    }
}

/** «14:00–17:00», bei mehreren Tagen der Beginn, bei ganztägigen «Ganztägig». */
@Composable
private fun listTime(program: Program): String = when (val time = program.time()) {
    null -> stringResource(R.string.time_pending)
    is Timing.AllDay ->
        if (time.from == time.until) {
            stringResource(R.string.time_list_all_day)
        } else {
            stringResource(R.string.time_list_until, DateTimeFormatter.ofPattern("d.M.").format(time.until))
        }
    is Timing.Timed ->
        if (time.start.toLocalDate() == time.end.toLocalDate()) {
            "${TIME_FORMAT.format(time.start)}–${TIME_FORMAT.format(time.end)}"
        } else {
            TIME_FORMAT.format(time.start)
        }
}

@Composable
fun ProgramDetailScreen(
    state: ApiResult<Program>?,
    rsvp: RsvpState,
    canSeeRegistrations: Boolean,
    contacts: ApiResult<ContactList>?,
    account: Profile,
    onChild: (childId: String, attending: Boolean) -> Unit,
    onLeader: (attending: Boolean) -> Unit,
    onRegistrations: () -> Unit,
    onEvent: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_programs), onBack)
    Column(Modifier.fillMaxSize()) {
        when (state) {
            null -> {
                Header(title = null, back = back)
                LoadingSkeleton(stringResource(R.string.program_loading))
            }
            is ApiResult.Failure -> {
                Header(title = null, back = back)
                ErrorState(state.error, onRetry, onToPrograms = onBack)
            }
            is ApiResult.Ok -> ProgramContent(state.value, rsvp, canSeeRegistrations, contacts, account, back, onChild, onLeader, onRegistrations, onEvent)
        }
    }
}

@Composable
private fun ProgramContent(
    program: Program,
    rsvp: RsvpState,
    canSeeRegistrations: Boolean,
    contacts: ApiResult<ContactList>?,
    account: Profile,
    back: HeaderButton,
    onChild: (childId: String, attending: Boolean) -> Unit,
    onLeader: (attending: Boolean) -> Unit,
    onRegistrations: () -> Unit,
    onEvent: (String) -> Unit,
) {
    val date = program.startDay()?.let { "${weekdayLong(it)}, ${dayAndMonth(it)}" }
    val context = LocalContext.current
    var noCalendar by remember(program.id) { mutableStateOf(false) }
    // Ohne lesbare Zeit gibt es nichts einzutragen: Der Knopf fehlt.
    val addAction = program.calendarEntry()?.let {
        HeaderButton(R.drawable.ic_calendar_plus, stringResource(R.string.program_add_to_calendar)) { noCalendar = !addToCalendar(context, program) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(
            title = program.title,
            subtitle = date,
            groups = groupChips(program),
            back = back,
            action = addAction,
            titleStyle = MaterialTheme.typography.headlineMedium,
            focusOnTitle = true,
        )
        Column(Modifier.padding(horizontal = Spacing.s5), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            if (noCalendar) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.program_no_calendar_app))
            ProgramStatus(program)
            Tiles(program)
            Facts(program)
            program.event?.let { EventCard(it, program.cancelled, onEvent) }
            RegistrationSection(program, rsvp, account, onChild, onLeader)
            ContactSection(program, contacts)
            LeaderSection(canSeeRegistrations, onRegistrations)
            Box(Modifier.height(Spacing.s4))
        }
    }
}

/** Der Anlass zum Termin: Stand der Anmeldung als Chip (Symbol und Wort) und der Weg zum Formular. */
@Composable
private fun EventCard(event: ProgramEvent, cancelled: Boolean, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CARD)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CARD)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(stringResource(R.string.event_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        when {
            event.registered -> StatusChip(ChipKind.YES, stringResource(R.string.event_registered))
            event.open && !cancelled -> StatusChip(ChipKind.PENDING, stringResource(R.string.event_not_registered))
            else -> StatusChip(ChipKind.NO, stringResource(R.string.event_closed))
        }
        event.deadline?.let { Text(stringResource(R.string.event_deadline, eventDeadlineText(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // Wer angemeldet ist, sieht und ändert die Anmeldung; wer nicht kann, hat keinen Knopf.
        if (event.registered || (event.open && !cancelled)) {
            OutlinedPillButton(
                stringResource(if (event.registered) R.string.event_show else R.string.event_register),
                { onOpen(event.id) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

/** «Kontakt» mit den Stufenleitungen des Programms (wer abgesagt hat, fehlt). Ein Fehler beim Laden zeigt keinen Abschnitt. */
@Composable
private fun ContactSection(program: Program, contacts: ApiResult<ContactList>?) {
    if (contacts !is ApiResult.Ok) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Text(
            stringResource(R.string.contact_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        ContactCard(contacts.value.contacts)
    }
}

/** Aktionen für Leitende: die Anmeldungen ansehen (Abteilungsleitung, Entwickler sowie Leitende und SL der Stufen). */
@Composable
private fun LeaderSection(
    canSeeRegistrations: Boolean,
    onRegistrations: () -> Unit,
) {
    if (!canSeeRegistrations) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Text(
            stringResource(R.string.section_leaders),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        OutlinedPillButton(stringResource(R.string.registrations_action), onRegistrations, Modifier.fillMaxWidth(), icon = R.drawable.ic_group)
    }
}

/** Zwei Kacheln, die Eltern am häufigsten suchen: wann und wo man sich trifft, wann und wo es endet. */
@Composable
private fun Tiles(program: Program) {
    val closing = program.closingTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val meeting = parseMeetingPoint(program.meetingPoint)
    // Nennt die Webseite eine Zeit, gilt sie; sonst der Beginn aus dem Kalender.
    val meetingTime = meeting.time ?: when (val time = program.time()) {
        is Timing.Timed -> TIME_FORMAT.format(time.start)
        is Timing.AllDay -> stringResource(R.string.time_list_all_day)
        null -> null
    }
    val meetingPoint = meeting.place
    val closingLocation = program.closingLocation?.takeIf { it.isNotBlank() }
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        if (meetingTime != null || meetingPoint != null) {
            Tile(stringResource(R.string.section_meeting_point), meetingTime, meetingPoint, Modifier.weight(1f).fillMaxHeight())
        }
        if (closing != null || closingLocation != null) {
            Tile(
                stringResource(R.string.section_closing), closing?.let { TIME_FORMAT.format(it) }, closingLocation,
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Tile(label: String, time: String?, location: String?, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CARD)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CARD)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        time?.let { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")) }
        location?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Liste mit Ort und Mitnehmen, je mit Icon. Felder ohne Inhalt fallen weg. */
@Composable
private fun Facts(program: Program) {
    val rows = listOf(
        Triple(R.drawable.ic_location, R.string.fact_location, program.calendarLocation),
        Triple(R.drawable.ic_bring_along, R.string.section_bring_along, program.bringAlong?.takeIf { it.isNotBlank() }),
    ).filter { it.third != null }
    if (rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CARD)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CARD)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, (icon, label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value.orEmpty(), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
                }
            }
            if (index < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}
