package io.github.sorry4nothing.cevios.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Das Design verlangt: Textpaare mindestens 4.5:1, `outline` und `focus` mindestens 3:1 auf allen Flächen (WCAG 2.2 AA). */
class KontrastTest {
    private fun kontrast(a: Color, b: Color): Double {
        val hell = maxOf(a.luminance(), b.luminance()).toDouble()
        val dunkel = minOf(a.luminance(), b.luminance()).toDouble()
        return (hell + 0.05) / (dunkel + 0.05)
    }

    private fun pruefe(name: String, vordergrund: Color, grund: Color, minimum: Double, themen: String) {
        val wert = kontrast(vordergrund, grund)
        assertTrue("$themen: $name hat nur ${"%.2f".format(wert)}:1, verlangt $minimum:1", wert >= minimum)
    }

    private fun alleThemen(block: (Palette, String) -> Unit) {
        block(CeviNacht, "Nacht")
        block(CeviTag, "Tag")
    }

    private fun flaechen(p: Palette) = mapOf(
        "surface" to p.scheme.surface,
        "surface-container-low" to p.scheme.surfaceContainerLow,
        "surface-container" to p.scheme.surfaceContainer,
        "surface-container-high" to p.scheme.surfaceContainerHigh,
    )

    @Test
    fun haupttextUndZweittextAufAllenFlaechen() = alleThemen { p, t ->
        flaechen(p).forEach { (n, grund) ->
            pruefe("on-surface auf $n", p.scheme.onSurface, grund, 4.5, t)
            pruefe("on-surface-variant auf $n", p.scheme.onSurfaceVariant, grund, 4.5, t)
        }
    }

    @Test
    fun rotUndAccentAlsTextAufSurfaceUndKarten() = alleThemen { p, t ->
        listOf("surface" to p.scheme.surface, "surface-container-low" to p.scheme.surfaceContainerLow).forEach { (n, grund) ->
            pruefe("primary-text auf $n", p.extra.primaryText, grund, 4.5, t)
            pruefe("accent auf $n", p.extra.accent, grund, 4.5, t)
        }
    }

    @Test
    fun textAufDenFarbflaechen() = alleThemen { p, t ->
        pruefe("on-primary auf primary", p.scheme.onPrimary, p.scheme.primary, 4.5, t)
        pruefe("on-secondary auf secondary", p.scheme.onSecondary, p.scheme.secondary, 4.5, t)
        pruefe("on-secondary-muted auf secondary", p.extra.onSecondaryMuted, p.scheme.secondary, 4.5, t)
        pruefe("on-secondary-container auf secondary-container", p.scheme.onSecondaryContainer, p.scheme.secondaryContainer, 4.5, t)
        pruefe("on-success-container auf success-container", p.extra.onSuccessContainer, p.extra.successContainer, 4.5, t)
        pruefe("on-error-container auf error-container", p.scheme.onErrorContainer, p.scheme.errorContainer, 4.5, t)
        pruefe("on-warning-container auf warning-container", p.extra.onWarningContainer, p.extra.warningContainer, 4.5, t)
        pruefe("on-stufe-container auf stufe-container", p.extra.onStufeContainer, p.extra.stufeContainer, 4.5, t)
        pruefe("inverse-on-surface auf inverse-surface", p.scheme.inverseOnSurface, p.scheme.inverseSurface, 4.5, t)
    }

    @Test
    fun rahmenUndFokusAufAllenFlaechen() = alleThemen { p, t ->
        flaechen(p).forEach { (n, grund) ->
            pruefe("outline auf $n", p.scheme.outline, grund, 3.0, t)
            pruefe("focus auf $n", p.extra.focus, grund, 3.0, t)
        }
    }

    @Test
    fun dieFormelStimmt() {
        assertTrue(kontrast(Color.White, Color.Black) in 20.9..21.1)
        assertTrue(kontrast(Color.White, Color.White) in 0.99..1.01)
    }

    @Test
    fun beideThemenHabenCeviRotUndCeviBlauGleich() {
        assertTrue(CeviNacht.scheme.primary == CeviTag.scheme.primary)
        assertTrue(CeviNacht.scheme.secondary == CeviTag.scheme.secondary)
    }
}
