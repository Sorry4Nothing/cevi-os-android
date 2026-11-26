package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.Avatar
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private data class SeatedPassenger(val trip: Trip, val passenger: TripPerson)

/** Wohin die Person Leute ohne Fahrt setzt: eine Richtung und, wenn die Person eine Fahrt gewählt hat, diese Fahrt. */
private data class TripAssign(val direction: TripDirection, val tripId: String?)

/**
 * Fahrten (Entwurf L21): Hin- und Rückfahrt mit Autos und ÖV-Gruppen, Abfahrt, Treffpunkt und Belegung («3 von 5 Plätzen belegt»).
 * Wer einteilen darf, weist Personen ohne Fahrt zu, nimmt sie heraus oder verschiebt sie («Verschieben nach …»). Das Team sieht nur
 * die eigene Fahrt. Mehr Mitfahrende als Plätze ist eine Warnung, keine Sperre.
 */
@Composable
fun TripsScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.trips.collectAsState()
    val without by vm.withoutTrip.collectAsState()
    val people by vm.people.collectAsState()
    var editor by remember { mutableStateOf<Trip?>(null) }
    var creating by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<TripAssign?>(null) }
    var passengerActions by remember { mutableStateOf<SeatedPassenger?>(null) }
    var moving by remember { mutableStateOf<SeatedPassenger?>(null) }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(camp?.id) { vm.loadTrips() }

    CampPage(
        title = stringResource(R.string.trips_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.trips_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadTrips() })
            is ApiResult.Ok -> {
                val trips = result.value
                if (trips.isEmpty()) {
                    StatePage(R.drawable.ic_location, stringResource(R.string.trips_empty_title), stringResource(if (rights.edit) R.string.trips_empty_text else R.string.trips_empty_team)) {
                        if (rights.edit) PrimaryButton(stringResource(R.string.trips_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.edit) 96.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        TripDirection.entries.forEach { direction ->
                            val list = PlanRules.tripsOf(trips, direction)
                            val missing = without[direction].orEmpty()
                            if (list.isEmpty() && missing.isEmpty()) return@forEach
                            item(key = "h-" + direction.wire) {
                                SectionTitle(stringResource(if (direction == TripDirection.THERE) R.string.trips_there else R.string.trips_back), Modifier.padding(top = Spacing.s2))
                            }
                            if (missing.isNotEmpty() && rights.seeAll) item(key = "o-" + direction.wire) {
                                Column {
                                    NoticeBanner(NoticeKind.WARNING, pluralStringResource(R.plurals.trips_without, missing.size, missing.size))
                                    TextActionButton(stringResource(if (rights.edit) R.string.trips_assign else R.string.trips_show_without), { assigning = TripAssign(direction, null) })
                                }
                            }
                            items(list.size, key = { list[it].id }) { index ->
                                val trip = list[index]
                                TripCard(
                                    trip, rights,
                                    onAssign = { assigning = TripAssign(direction, trip.id) },
                                    onEdit = { editor = trip },
                                    onPassenger = { passengerActions = SeatedPassenger(trip, it) },
                                )
                            }
                        }
                    }
                    if (rights.edit) {
                        PrimaryButton(stringResource(R.string.trips_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }

    assigning?.let { target ->
        val trips = (state as? ApiResult.Ok)?.value.orEmpty()
        AssignToTripSheet(vm, target, trips, without[target.direction].orEmpty(), rights.edit, onDismiss = { assigning = null })
    }
    passengerActions?.let { seated ->
        val others = (state as? ApiResult.Ok)?.value.orEmpty().filter { it.direction == seated.trip.direction && it.id != seated.trip.id }
        val entries = buildList {
            add(stringResource(R.string.trips_take_out) to { vm.removePassenger(seated.trip, seated.passenger) })
            if (others.isNotEmpty()) add(stringResource(R.string.rooms_move) to { moving = seated })
        }
        ActionSheet(seated.passenger.name, entries) { passengerActions = null }
    }
    moving?.let { seated ->
        val choices = (state as? ApiResult.Ok)?.value.orEmpty()
            .filter { it.direction == seated.trip.direction && it.id != seated.trip.id }
            .map { Choice(it.id, tripTitle(it)) }
        ChoiceSheet(
            stringResource(R.string.rooms_move_title, seated.passenger.name), choices, initial = null,
            onApply = { key ->
                moving = null
                val target = key?.let { id -> (state as? ApiResult.Ok)?.value.orEmpty().firstOrNull { it.id == id } }
                if (target != null) vm.movePassenger(seated.trip, seated.passenger, target)
            },
            onDismiss = { moving = null },
        )
    }
    if (creating || editor != null) {
        TripEditorSheet(
            editor, people,
            onCreate = { body -> creating = false; vm.createTrip(body) },
            onChange = { trip, fields -> editor = null; vm.changeTrip(trip, fields, trip.name ?: trip.driver?.name.orEmpty()) },
            onDelete = editor?.let { trip -> { deleting = trip; editor = null } },
            onDismiss = { creating = false; editor = null },
        )
    }
    deleting?.let { trip ->
        ConfirmDialog(
            stringResource(R.string.trips_delete_title), stringResource(R.string.trips_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; vm.deleteTrip(trip) }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun tripTitle(trip: Trip): String {
    val who = trip.name?.takeIf { it.isNotBlank() } ?: trip.driver?.name.orEmpty()
    return stringResource(if (trip.isCar) R.string.trips_car else R.string.trips_pt, who).trim()
}

@Composable
private fun TripCard(trip: Trip, rights: PlanRights, onAssign: () -> Unit, onEdit: () -> Unit, onPassenger: (TripPerson) -> Unit) {
    val title = tripTitle(trip)
    val fill = if (trip.isCar) trip.seats?.let { stringResource(R.string.trips_fill, trip.occupied, it) } else pluralStringResource(R.plurals.trips_people, trip.occupied, trip.occupied)
    val over = stringResource(R.string.rooms_overbooked)
    val departure = joinDetail(listOfNotNull(trip.date?.let(::formatMeetingDay), trip.time).joinToString(" ").ifBlank { null })?.let { stringResource(R.string.trips_departure, it) }
    val route = listOfNotNull(trip.from?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.trips_from, it) }, trip.to?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.trips_to, it) }).joinToString(" ").ifBlank { null }
    val meeting = trip.meetingPoint?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.trips_meeting_point, it) }
    val spoken = joinDetail(title, fill, if (trip.overbooked) over else null, departure, route, meeting).orEmpty()
    val openLink = rememberLinkOpener()
    InfoCard {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (trip.overbooked) StatusChip(ChipKind.REVIEW, over)
            }
            listOfNotNull(fill, departure, route, meeting).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trip.passengers.forEach { passenger ->
            val shape = MaterialTheme.shapes.small
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .focusRing(shape)
                    .then(if (rights.edit) Modifier.clickable(onClickLabel = stringResource(R.string.rooms_member_actions), role = Role.Button) { onPassenger(passenger) } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = joinDetail(passenger.name, passenger.stage).orEmpty() },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(passenger.name, imageUrl = passenger.imageUrl)
                Text(passenger.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
        val link = trip.link?.takeIf { it.startsWith("https://") }
        if (link != null || rights.edit) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                if (rights.edit) {
                    val assign = stringResource(R.string.trips_assign)
                    OutlinedPillButton(assign, onAssign, Modifier.rowAction(assign, title), icon = R.drawable.ic_plus)
                }
                link?.let {
                    val timetable = stringResource(R.string.trips_open_timetable)
                    OutlinedPillButton(timetable, { openLink(it) }, Modifier.rowAction(timetable, title))
                }
                if (rights.edit) {
                    val edit = stringResource(R.string.trips_edit)
                    TextActionButton(edit, onEdit, Modifier.rowAction(edit, title))
                }
            }
        }
    }
}

/** Leute ohne Fahrt wählen und einer Fahrt zuteilen; ohne vorgegebene Fahrt wählt die Person sie zuerst. Ohne das Recht zu ändern nur die Liste. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssignToTripSheet(vm: PlanningViewModel, target: TripAssign, trips: List<Trip>, candidates: List<TripCandidate>, canEdit: Boolean, onDismiss: () -> Unit) {
    var tripId by remember { mutableStateOf(target.tripId) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val busy by vm.busy.collectAsState()
    val ofDirection = PlanRules.tripsOf(trips, target.direction)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            val titleRes = if (target.direction == TripDirection.THERE) R.string.trips_without_there else R.string.trips_without_back
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (canEdit) {
                Text(stringResource(R.string.trips_pick_trip), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.pickGroup(stringResource(R.string.trips_pick_trip)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    ofDirection.forEach { trip -> PickPill(tripTitle(trip), tripId == trip.id, { tripId = trip.id }) }
                }
            }
            candidates.forEach { person ->
                if (canEdit) {
                    CheckRow(person.name, joinDetail(person.stage), person.key in picked, { picked = picked.toggled(person.key) })
                } else {
                    Text(joinDetail(person.name, person.stage).orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.heightIn(min = Sizes.touchTarget).padding(top = Spacing.s2))
                }
            }
            if (canEdit) {
                PrimaryButton(
                    stringResource(R.string.rooms_assign_confirm),
                    {
                        val trip = ofDirection.firstOrNull { it.id == tripId } ?: return@PrimaryButton
                        vm.addPassengers(trip, candidates.filter { it.key in picked }, onDismiss)
                    },
                    Modifier.fillMaxWidth().padding(top = Spacing.s2), enabled = picked.isNotEmpty() && tripId != null, loading = busy, loadingText = stringResource(R.string.plan_saving),
                )
            }
            OutlinedPillButton(stringResource(if (canEdit) R.string.cancel else R.string.close), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Neue Fahrt oder Änderung. Richtung und Art lassen sich nach dem Anlegen nicht mehr ändern (der Server verlangt: löschen und neu anlegen). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TripEditorSheet(
    initial: Trip?,
    people: List<RessortPerson>,
    onCreate: (kotlinx.serialization.json.JsonObject) -> Unit,
    onChange: (Trip, Map<String, JsonElement>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var direction by remember { mutableStateOf(initial?.direction ?: TripDirection.THERE) }
    var car by remember { mutableStateOf(initial?.isCar ?: true) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var driver by remember { mutableStateOf(initial?.fahrerPersonId) }
    var seats by remember { mutableStateOf(initial?.seats?.toString().orEmpty()) }
    var date by remember { mutableStateOf(initial?.date) }
    var time by remember { mutableStateOf(initial?.time.orEmpty()) }
    var from by remember { mutableStateOf(initial?.from.orEmpty()) }
    var to by remember { mutableStateOf(initial?.to.orEmpty()) }
    var point by remember { mutableStateOf(initial?.meetingPoint.orEmpty()) }
    var link by remember { mutableStateOf(initial?.link.orEmpty()) }
    var datePicking by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    val seatCount = seats.trim().toIntOrNull()
    val seatsOk = !car || (seatCount != null && seatCount in 1..99)
    val timeOk = isValidTime(time)
    val linkOk = car || link.isBlank() || isValidFileLink(link)
    val isNew = initial == null
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(if (isNew) R.string.trips_add else R.string.trips_edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (isNew) {
                FlowRow(Modifier.pickGroup(stringResource(R.string.group_trip_direction)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    PickPill(stringResource(R.string.trips_there), direction == TripDirection.THERE, { direction = TripDirection.THERE })
                    PickPill(stringResource(R.string.trips_back), direction == TripDirection.BACK, { direction = TripDirection.BACK })
                }
                FlowRow(Modifier.pickGroup(stringResource(R.string.group_trip_kind)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    PickPill(stringResource(R.string.trips_kind_car), car, { car = true })
                    PickPill(stringResource(R.string.trips_kind_pt), !car, { car = false })
                }
            }
            LabeledTextField(stringResource(R.string.trips_field_name), name, { name = it }, 100, Modifier.fillMaxWidth())
            if (car) {
                Text(stringResource(R.string.trips_field_driver), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.pickGroup(stringResource(R.string.trips_field_driver)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    PickPill(stringResource(R.string.trips_no_driver), driver == null, { driver = null })
                    people.forEach { person -> PickPill(person.display, driver == person.id, { driver = person.id }) }
                }
                LabeledTextField(
                    stringResource(R.string.trips_field_seats), seats, { seats = it.filter(Char::isDigit) }, 2, Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number,
                    hint = if (showErrors && !seatsOk) stringResource(R.string.error_trip_seats) else stringResource(R.string.trips_field_seats_hint),
                )
            }
            OutlinedPillButton(date?.let(::formatMeetingDay) ?: stringResource(R.string.trips_field_date), { datePicking = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_calendar)
            LabeledTextField(
                stringResource(R.string.trips_field_time), time, { time = it }, 5, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number,
                hint = if (showErrors && !timeOk) stringResource(R.string.error_time_format) else stringResource(R.string.meeting_field_time_hint),
            )
            LabeledTextField(stringResource(R.string.trips_field_from), from, { from = it }, 200, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.trips_field_to), to, { to = it }, 200, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.trips_field_point), point, { point = it }, 200, Modifier.fillMaxWidth())
            if (!car) {
                LabeledTextField(
                    stringResource(R.string.trips_field_link), link, { link = it }, 2048, Modifier.fillMaxWidth(),
                    hint = if (showErrors && !linkOk) stringResource(R.string.error_file_link) else stringResource(R.string.trips_field_link_hint),
                )
            }
            PrimaryButton(
                stringResource(R.string.save),
                {
                    if (!seatsOk || !timeOk || !linkOk) {
                        showErrors = true
                    } else if (initial == null) {
                        onCreate(
                            buildJsonObject {
                                put("richtung", direction.wire); put("art", if (car) "auto" else "oev")
                                name.takeIf { it.isNotBlank() }?.let { put("name", it.trim()) }
                                if (car) {
                                    driver?.let { put("fahrerPersonId", it) }
                                    put("plaetze", seatCount!!)
                                }
                                date?.let { put("datum", it) }
                                time.takeIf { it.isNotBlank() }?.let { put("zeit", it.trim()) }
                                from.takeIf { it.isNotBlank() }?.let { put("von", it.trim()) }
                                to.takeIf { it.isNotBlank() }?.let { put("nach", it.trim()) }
                                point.takeIf { it.isNotBlank() }?.let { put("treffpunkt", it.trim()) }
                                if (!car) link.takeIf { it.isNotBlank() }?.let { put("link", it.trim()) }
                            },
                        )
                    } else {
                        val fields = buildMap<String, JsonElement> {
                            if (name.trim() != initial.name.orEmpty()) put("name", textField(name))
                            if (car && driver != initial.fahrerPersonId) put("fahrerPersonId", driver?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                            if (car && seatCount != initial.seats) put("plaetze", JsonPrimitive(seatCount))
                            if (date != initial.date) put("datum", date?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                            if (time.trim() != initial.time.orEmpty()) put("zeit", textField(time))
                            if (from.trim() != initial.from.orEmpty()) put("von", textField(from))
                            if (to.trim() != initial.to.orEmpty()) put("nach", textField(to))
                            if (point.trim() != initial.meetingPoint.orEmpty()) put("treffpunkt", textField(point))
                            if (!car && link.trim() != initial.link.orEmpty()) put("link", textField(link))
                        }
                        if (fields.isEmpty()) onDismiss() else onChange(initial, fields)
                    }
                },
                Modifier.fillMaxWidth(),
            )
            onDelete?.let { TextActionButton(stringResource(R.string.trips_delete), it, Modifier.fillMaxWidth()) }
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
    if (datePicking) DateDialog(initial = date, onPick = { date = it; datePicking = false }, onDismiss = { datePicking = false })
}
