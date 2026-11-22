package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.sorry4nothing.cevios.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.cevi

/**
 * Der Bereichswechsel im Kopf («Bereich: Sommerlager …»): Programme oder ein Lager. `news` setzt den Punkt, wenn es im anderen Bereich
 * Neues gibt; er steht immer auch im Satz für den Screenreader.
 */
class ModeSwitch(val name: String, val otherAreaName: String, val news: Boolean, val onClick: () -> Unit)

/** Den Bereichswechsel zeigen die Hauptbildschirme (ohne Zurück-Knopf); `MainContent` setzt ihn nur dort. */
val LocalModeSwitch = compositionLocalOf<ModeSwitch?> { null }

/** Runder Knopf im Kopf. Die Beschreibung ist Pflicht: Der Knopf hat kein Wort. */
class HeaderButton(@DrawableRes val icon: Int, val description: String, val onClick: () -> Unit)

/**
 * Derselbe Kopf auf jedem Screen: oben, nur mit Knopf, eine Zeile (48 dp) mit Zurück links und höchstens einer Aktion
 * rechts, darunter der Titel mit Untertitel und Gruppen-Chips.
 * Der Titel ist eine Überschrift; nach dem Öffnen springt der Fokus auf ihn (`focusOnTitle`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Header(
    title: String?,
    modifier: Modifier = Modifier,
    /** Kontext als Satzteil unter dem Titel (Datum, Programm); keine Überzeile über dem Titel. */
    subtitle: String? = null,
    /** Stufe, deren Logo vor dem Untertitel steht (dekorativ, der Name steht im Untertitel). */
    subtitleStageId: String? = null,
    /** Gruppen (Stufe, Team) als Chips unter dem Untertitel. */
    groups: List<GroupChip> = emptyList(),
    back: HeaderButton? = null,
    action: HeaderButton? = null,
    /** Eine zweite Aktion links neben `action` (zum Beispiel «Merken» vor «Weitere Aktionen»). */
    extraAction: HeaderButton? = null,
    mode: ModeSwitch? = if (back == null) LocalModeSwitch.current else null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
    focusOnTitle: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().padding(start = Spacing.s3, top = Spacing.s3, end = Spacing.s3, bottom = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Ohne Knopf fällt die Zeile weg: Auf den Startscreens steht der Titel direkt oben.
        if (back != null || action != null || extraAction != null || mode != null) {
            Row(Modifier.fillMaxWidth().height(Sizes.touchTarget), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                back?.let { RoundButton(it) }
                mode?.let { ModePill(it, Modifier.weight(1f, fill = false)) }
                Spacer(Modifier.weight(1f))
                extraAction?.let { RoundButton(it) }
                action?.let { RoundButton(it) }
            }
        }
        if (title != null) {
            Column(Modifier.padding(horizontal = Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                val focus = remember { FocusRequester() }
                if (focusOnTitle) LaunchedEffect(title) { focus.requestFocus() }
                Text(
                    title,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .then(if (focusOnTitle) Modifier.focusRequester(focus).focusTarget() else Modifier)
                        .semantics { heading() },
                )
                subtitle?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                        subtitleStageId?.let { id -> StageLogo(id, size = 24.dp) }
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
            if (groups.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(horizontal = Spacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) { groups.forEach { StatusChip(ChipKind.AGE_GROUP, it.name, stageId = it.stageId) } }
            }
        }
    }
}

@Composable
fun RoundButton(button: HeaderButton, modifier: Modifier = Modifier) {
    Surface(
        onClick = button.onClick,
        modifier = modifier.size(Sizes.touchTarget).focusRing(Pill).semantics { contentDescription = button.description },
        shape = Pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Icon(
            painterResource(button.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Der Bereichswechsel als Pille: Zelt, Name, Pfeil; ein roter Punkt oben rechts, wenn es im anderen Bereich Neues gibt. */
@Composable
private fun ModePill(mode: ModeSwitch, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.mode_description, mode.name) +
        if (mode.news) stringResource(R.string.mode_news, mode.otherAreaName) else ""
    Box(modifier.heightIn(min = Sizes.touchTarget)) {
        Row(
            Modifier
                .heightIn(min = Sizes.touchTarget)
                .focusRing(Pill)
                .clip(Pill)
                .background(scheme.surfaceContainerLow)
                .border(1.dp, scheme.outlineVariant, Pill)
                .clickable(role = Role.Button, onClick = mode.onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_tent), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(20.dp))
            Text(mode.name, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
        }
        if (mode.news) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(10.dp).clip(Pill).background(scheme.primary).border(2.dp, scheme.surfaceContainerLow, Pill))
        }
    }
}
