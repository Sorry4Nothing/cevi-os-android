package io.github.sorry4nothing.cevios.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Das Aussehen «Nachthimmel» aus dem Design:
 * - Design System (Farben, Schrift, Bausteine): https://claude.ai/artifact/9mAd5832pLn8DCnG8AAY8e
 * - Bildschirme (Nacht und Tag, alle Zustände): https://claude.ai/artifact/3590108e-92e4-4a95-8442-a12e98ac9287
 *
 * Zwei Themen, [CeviNight] (Dunkelmodus, Hauptrichtung) und [CeviDay] (Hellmodus). Die App folgt dem System, es gibt
 * kein Dynamic Color. Die Zuordnung Baustein → Compose steht in `docs/design.md`.
 */
@Composable
fun CeviTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) CeviNight else CeviDay
    CompositionLocalProvider(LocalCeviColors provides palette.extra) {
        MaterialTheme(
            colorScheme = palette.scheme,
            typography = CeviTypography,
            shapes = CeviShapes,
            content = content,
        )
    }
}
