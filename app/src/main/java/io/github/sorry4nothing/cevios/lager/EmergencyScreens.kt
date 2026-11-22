package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.ZoneId

/**
 * Notfall (Entwurf L30): Nummern zum Anrufen, die Lageradresse mit «Route öffnen» und die Notfall-Liste aus den Infos. Ohne Sicht
 * bedienbar: Jede Zeile ist ein einziger Knopf, der die Telefon-App mit der Nummer öffnet (`ACTION_DIAL`, die Person wählt selbst).
 * Die Notrufnummern der Schweiz (Sanität 144, Rega 1414) stehen immer da, auch wenn der Server nicht antwortet. Ohne Netz zeigt das
 * Fenster den zuletzt geladenen Stand aus dem Arbeitsspeicher; nach dem Ende der App ohne Netz ist nichts da.
 */
@Composable
fun EmergencyScreen(frame: LiveFrame) {
    val vm = frame.vm
    val state by vm.emergency.collectAsState()
    val stale by vm.emergencyStale.collectAsState()
    val context = LocalContext.current
    val open = rememberLinkOpener()
    LaunchedEffect(frame.camp?.id) { vm.loadEmergency(silent = vm.emergency.value is ApiResult.Ok) }

    LivePage(frame, stringResource(R.string.emerg_title)) {
        LiveColumn {
            when (val result = state) {
                null -> CardSkeleton(stringResource(R.string.emerg_loading))
                is ApiResult.Failure -> {
                    NoticeBanner(NoticeKind.ERROR, stringResource(R.string.emerg_failed), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    OutlinedPillButton(stringResource(R.string.retry), { vm.loadEmergency() }, Modifier.fillMaxWidth())
                    FixedNumbers(context)
                }
                is ApiResult.Ok -> {
                    val data = result.value.emergency
                    if (stale || !frame.online) {
                        NoticeBanner(
                            NoticeKind.INFO,
                            stringResource(R.string.queue_offline_title) + ". " + stringResource(R.string.emerg_stale, formatClock(result.value.loadedAt)),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        LiveRules.callable(data.contacts).forEach { contact ->
                            CallRow(contact.role, contact.display, contact.phone.orEmpty()) { dial(context, contact.phone.orEmpty()) }
                        }
                        FixedNumbers(context)
                    }
                    AddressCard(data.address, context)
                    val infos = data.infos.sortedBy { it.order }
                    if (infos.isNotEmpty()) {
                        LiveSection(stringResource(R.string.emerg_list)) {
                            infos.forEach { section ->
                                InfoCard(description = listOfNotNull(section.title, section.text).joinToString(". ")) {
                                    Text(section.title, style = MaterialTheme.typography.bodyLarge)
                                    section.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    section.links.forEach { link ->
                                        androidx.compose.foundation.layout.Box(Modifier.clickable(onClickLabel = link.title, role = Role.Button) { open(link.url) }.heightIn(min = Sizes.touchTarget).focusRing(MaterialTheme.shapes.small)) {
                                            Text(link.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.cevi.accent, modifier = Modifier.padding(vertical = Spacing.s3))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sanität und Rega: feste Nummern der Schweiz, nicht aus der API. */
@Composable
private fun FixedNumbers(context: android.content.Context) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        CallRow(stringResource(R.string.emerg_rescue), stringResource(R.string.emerg_ambulance), SwissEmergency.AMBULANCE) { dial(context, SwissEmergency.AMBULANCE) }
        CallRow(stringResource(R.string.emerg_rega), stringResource(R.string.emerg_rescue_flight), SwissEmergency.RESCUE_FLIGHT) { dial(context, SwissEmergency.RESCUE_FLIGHT) }
    }
}

/**
 * Eine Zeile zum Anrufen: ein einziger Knopf (mindestens 48 dp) mit Funktion, Name und Nummer als ein Satz für den Screenreader.
 * Der Tipp öffnet die Telefon-App mit der Nummer; angerufen wird erst dort.
 */
@Composable
private fun CallRow(role: String, name: String, number: String, onCall: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.emerg_call_named, role, name, number)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.button)
            .focusRing(shape)
            .clip(shape)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(onClickLabel = stringResource(R.string.emerg_call), role = Role.Button, onClick = onCall)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(role, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.cevi.accent)
        }
        Icon(painterResource(R.drawable.ic_phone), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(Sizes.icon))
    }
}

/** Die Lageradresse mit «Route öffnen»: die Karten-App des Geräts, nie ein Kartendienst der App. */
@Composable
private fun AddressCard(address: EmergencyAddress, context: android.content.Context) {
    val route = LiveRules.routeUri(address)
    val lines = listOfNotNull(address.place, address.address).filter { it.isNotBlank() }
    if (lines.isEmpty() && route == null) return
    InfoCard(description = stringResource(R.string.emerg_address) + ": " + lines.joinToString(", ")) {
        Text(stringResource(R.string.emerg_address), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (route != null) OutlinedPillButton(stringResource(R.string.emerg_route), { openRoute(context, route) }, icon = R.drawable.ic_location)
    }
}

/** «18:02» in der Zeit des Lagers. */
private fun formatClock(instant: java.time.Instant): String {
    val time = instant.atZone(ZoneId.of("Europe/Zurich"))
    return "%02d:%02d".format(time.hour, time.minute)
}
