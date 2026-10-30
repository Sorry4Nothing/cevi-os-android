package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.Eyebrow
import io.github.sorry4nothing.cevios.ui.theme.Groesse
import io.github.sorry4nothing.cevios.ui.theme.Voll
import io.github.sorry4nothing.cevios.ui.theme.cevi

/** Runder Knopf im Kopf. Die Beschreibung ist Pflicht: Der Knopf hat kein Wort. */
class KopfKnopf(@DrawableRes val icon: Int, val beschreibung: String, val onClick: () -> Unit)

/**
 * Derselbe Kopf auf jedem Screen: oben Zurück links und höchstens eine Aktion rechts, darunter Überzeile und Titel.
 * Der Titel ist eine Überschrift; nach dem Öffnen springt der Fokus auf ihn (`fokusAufTitel`).
 */
@Composable
fun Kopf(
    titel: String?,
    modifier: Modifier = Modifier,
    ueberzeile: String? = null,
    zurueck: KopfKnopf? = null,
    aktion: KopfKnopf? = null,
    titelStil: TextStyle = MaterialTheme.typography.headlineLarge,
    fokusAufTitel: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().padding(start = Abstand.s3, top = Abstand.s3, end = Abstand.s3, bottom = Abstand.s4),
        verticalArrangement = Arrangement.spacedBy(Abstand.s2),
    ) {
        if (zurueck != null || aktion != null) {
            Row(Modifier.fillMaxWidth().height(Groesse.touchZiel), horizontalArrangement = Arrangement.spacedBy(Abstand.s2)) {
                zurueck?.let { RundKnopf(it) }
                Spacer(Modifier.weight(1f))
                aktion?.let { RundKnopf(it) }
            }
        }
        if (titel != null) {
            Column(Modifier.padding(horizontal = Abstand.s2), verticalArrangement = Arrangement.spacedBy(Abstand.s1)) {
                ueberzeile?.let { Text(it.uppercase(), style = Eyebrow, color = MaterialTheme.cevi.accent) }
                val fokus = remember { FocusRequester() }
                if (fokusAufTitel) LaunchedEffect(titel) { fokus.requestFocus() }
                Text(
                    titel,
                    style = titelStil,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .then(if (fokusAufTitel) Modifier.focusRequester(fokus).focusTarget() else Modifier)
                        .semantics { heading() },
                )
            }
        }
    }
}

@Composable
fun RundKnopf(knopf: KopfKnopf, modifier: Modifier = Modifier) {
    Surface(
        onClick = knopf.onClick,
        modifier = modifier.size(Groesse.touchZiel).fokusRing(Voll).semantics { contentDescription = knopf.beschreibung },
        shape = Voll,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Icon(
            painterResource(knopf.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
        )
    }
}
