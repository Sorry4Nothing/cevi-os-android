package io.github.sorry4nothing.cevios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sorry4nothing.cevios.chat.initials
import io.github.sorry4nothing.cevios.ui.theme.Pill

/** Anfangsbuchstabe des ersten Wortes, gross; leer, wenn der Name leer ist. */
fun avatarInitial(name: String): String = initials(name).take(1)

/**
 * Runder Avatar mit der Initiale auf `secondary`. Er ist für den Screenreader dekorativ: Der Name steht immer daneben.
 * `imageUrl` ist vorbereitet, wird aber noch nicht geladen: Die API liefert heute kein Profilbild. Das Bild kommt mit dem
 * Feld der API; bis dahin zeigt der Avatar die Initiale.
 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 32.dp, imageUrl: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(scaledWithFont(size)).background(scheme.secondary, Pill).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { InitialText(avatarInitial(name), 12.sp, scheme.onSecondary) }
}

/** Grösse eines Kreises mit Initiale: wächst mit der Schrift, damit die Initiale bei grösster Schrift hineinpasst. */
@Composable
fun scaledWithFont(size: Dp): Dp = size * LocalDensity.current.fontScale.coerceAtLeast(1f)

/** Initiale im Kreis. Der Kreis wächst mit der Schrift (siehe [scaledWithFont]), die Schrift selbst hat kein Limit. */
@Composable
internal fun InitialText(text: String, fontSize: TextUnit, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize, fontWeight = FontWeight.ExtraBold), color = color)
}
