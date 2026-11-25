package io.github.sorry4nothing.cevios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import kotlinx.coroutines.delay

/** So lange läuft eine Anfrage, bevor der Ladezustand die App sperrt: Schnelle Antworten sollen nicht flackern. */
private const val SHOW_AFTER_MS = 250L

/**
 * Der Ladezustand der ganzen App: Solange eine Anfrage der Person läuft (`busy`), liegt er über allem, fängt alle
 * Berührungen ab und zeigt den Ladekreis. Der Fokus verlässt das Feld, damit die Tastatur nichts mehr annimmt. Der
 * Screenreader hört «Einen Moment …» einmal; was dahinter liegt (`content`), ist in dieser Zeit für Bedienhilfen
 * und Tastatur verborgen, nicht nur für Berührungen gesperrt.
 */
@Composable
fun BusyOverlay(busy: Boolean, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) {
            delay(SHOW_AFTER_MS)
            shown = true
        } else {
            shown = false
        }
    }
    // Der Inhalt bleibt in der Zusammensetzung (Zustand, Scrollstand); nur für Bedienhilfen und Tastatur ist er in dieser Zeit weg.
    Box(Modifier.fillMaxSize()) {
        Box(
            if (shown) Modifier.fillMaxSize().focusProperties { canFocus = false }.clearAndSetSemantics { } else Modifier.fillMaxSize(),
        ) { content() }
        if (shown) Overlay()
    }
}

@Composable
private fun Overlay() {
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus() }
    val description = stringResource(R.string.working)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Spinner(size = 32.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
