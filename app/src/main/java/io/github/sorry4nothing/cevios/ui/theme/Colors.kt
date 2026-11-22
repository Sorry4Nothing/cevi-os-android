package io.github.sorry4nothing.cevios.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Rollen, die Material 3 nicht kennt (Design System «Cevi OS Android 2», Farbwelt Cevi-Website, `tokens.json`).
 * Kein Zustand nur über Farbe: Jede dieser Farben steht immer mit Icon und Wort.
 */
@Immutable
data class CeviColors(
    /** Links und «hier bist du» (aktiver Dock-Reiter, gewählte Pille, Schalter an), mit [onAccent] darauf. */
    val accent: Color,
    val onAccent: Color,
    /** Rot als Text oder Linie; `primary` ist nur als Fläche gedacht. */
    val primaryText: Color,
    val onSecondaryMuted: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val ageGroupContainer: Color,
    val onAgeGroupContainer: Color,
    /** Fokusring: 3 dp, 2 dp Abstand. */
    val focus: Color,
    /** Skelett-Balken beim Laden (dekorativ). */
    val skeleton: Color,
)

val LocalCeviColors = staticCompositionLocalOf { CeviDay.extra }

/** Die zusätzlichen Rollen des aktuellen Themes: `MaterialTheme.cevi.accent`. */
val MaterialTheme.cevi: CeviColors
    @Composable @ReadOnlyComposable get() = LocalCeviColors.current

internal class Palette(val scheme: ColorScheme, val extra: CeviColors)

private val CeviRed = Color(0xFFD50038)
private val CeviBlue = Color(0xFF013974)
private val PureWhite = Color(0xFFFFFFFF)

/** «Nacht»: neutral dunkel (fast Schwarz, leicht warm), Cevi-Blau führt über `accent`. */
internal val CeviNight: Palette = run {
    val surface = Color(0xFF141412)
    val container = Color(0xFF252523)
    val containerHigh = Color(0xFF30302D)
    Palette(
        scheme = darkColorScheme(
            primary = CeviRed,
            onPrimary = PureWhite,
            primaryContainer = CeviRed,
            onPrimaryContainer = PureWhite,
            inversePrimary = Color(0xFFFF7A93),
            secondary = CeviBlue,
            onSecondary = PureWhite,
            secondaryContainer = Color(0xFF0E2A47),
            onSecondaryContainer = Color(0xFFD5E1EE),
            tertiary = CeviBlue,
            onTertiary = PureWhite,
            background = surface,
            onBackground = Color(0xFFF3F3EF),
            surface = surface,
            onSurface = Color(0xFFF3F3EF),
            surfaceVariant = containerHigh,
            onSurfaceVariant = Color(0xFFBEBEB6),
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFF3F3EF),
            inverseOnSurface = surface,
            error = Color(0xFFFF8E9A),
            onError = Color(0xFF33171B),
            errorContainer = Color(0xFF33171B),
            onErrorContainer = Color(0xFFFF8E9A),
            outline = Color(0xFF8F8F88),
            outlineVariant = Color(0xFF3B3B37),
            scrim = Color(0xB3000000),
            surfaceBright = containerHigh,
            surfaceDim = surface,
            surfaceContainerLowest = surface,
            surfaceContainerLow = Color(0xFF1C1C1A),
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHigh,
        ),
        extra = CeviColors(
            accent = Color(0xFF9DC0EA),
            onAccent = surface,
            primaryText = Color(0xFFFF7A93),
            onSecondaryMuted = Color(0xFFC6D4E4),
            successContainer = Color(0xFF16261B),
            onSuccessContainer = Color(0xFF6FD39A),
            warningContainer = Color(0xFF2C2513),
            onWarningContainer = Color(0xFFF2C063),
            ageGroupContainer = Color(0xFF142A2F),
            onAgeGroupContainer = Color(0xFF7FD0E3),
            focus = Color(0xFF9DC0EA),
            skeleton = Color(0xFF2A2A27),
        ),
    )
}

/** «Tag»: Weiss auf kühlem Blaugrau wie die Website der Abteilung, die Hauptrichtung. */
internal val CeviDay: Palette = run {
    val surface = Color(0xFFF2F5F8)
    val container = Color(0xFFE6EBF1)
    val containerHigh = Color(0xFFD5DFE9)
    Palette(
        scheme = lightColorScheme(
            primary = CeviRed,
            onPrimary = PureWhite,
            primaryContainer = CeviRed,
            onPrimaryContainer = PureWhite,
            inversePrimary = Color(0xFFA30F2A),
            secondary = CeviBlue,
            onSecondary = PureWhite,
            secondaryContainer = container,
            onSecondaryContainer = CeviBlue,
            tertiary = CeviBlue,
            onTertiary = PureWhite,
            background = surface,
            onBackground = Color(0xFF1A1A1A),
            surface = surface,
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = containerHigh,
            onSurfaceVariant = Color(0xFF4D4D4D),
            surfaceTint = Color.Transparent,
            inverseSurface = CeviBlue,
            inverseOnSurface = surface,
            error = Color(0xFF9E1B2C),
            onError = PureWhite,
            errorContainer = Color(0xFFFCE9EC),
            onErrorContainer = Color(0xFF9E1B2C),
            outline = Color(0xFF6B7886),
            outlineVariant = Color(0xFFC9D5E1),
            scrim = Color(0x73013974),
            surfaceBright = PureWhite,
            surfaceDim = containerHigh,
            surfaceContainerLowest = PureWhite,
            surfaceContainerLow = PureWhite,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHigh,
        ),
        extra = CeviColors(
            accent = CeviBlue,
            onAccent = PureWhite,
            primaryText = Color(0xFFD50038),
            onSecondaryMuted = Color(0xFFC6D4E4),
            successContainer = Color(0xFFE7F4EC),
            onSuccessContainer = Color(0xFF17703A),
            warningContainer = Color(0xFFFFF4D6),
            onWarningContainer = Color(0xFF8A5A00),
            ageGroupContainer = Color(0xFFE3F2F6),
            onAgeGroupContainer = Color(0xFF1D6274),
            focus = CeviBlue,
            skeleton = Color(0xFFD7E0EA),
        ),
    )
}
