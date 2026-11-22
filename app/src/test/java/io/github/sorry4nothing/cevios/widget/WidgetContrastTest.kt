package io.github.sorry4nothing.cevios.widget

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/** Die Widget-Farben (Tag und Nacht) halten für jedes Textpaar 4.5:1 ein (WCAG 2.2 AA). */
class WidgetContrastTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun colors(path: String): Map<String, Int> =
        Regex("""<color name="(widget_[a-z_]+)">#([0-9A-Fa-f]{6})</color>""").findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt(16) }

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(rgb: Int) = 0.2126 * channel(rgb shr 16 and 0xFF) + 0.7152 * channel(rgb shr 8 and 0xFF) + 0.0722 * channel(rgb and 0xFF)

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    // Vordergrund auf Hintergrund, wie sie in den Layouts vorkommen.
    private val pairs = listOf(
        "widget_text" to "widget_card", "widget_muted" to "widget_card", "widget_accent" to "widget_card",
        "widget_stage_text" to "widget_stage_bg", "widget_yes_text" to "widget_yes_bg", "widget_no_text" to "widget_no_bg",
        "widget_open_text" to "widget_open_bg", "widget_warn_text" to "widget_warn_bg", "widget_on_primary" to "widget_primary",
        "widget_text" to "widget_open_bg",
    )

    @Test
    fun everyTextPairHasAtLeastFourPointFive() {
        for ((name, file) in listOf("Tag" to "src/main/res/values/colors.xml", "Nacht" to "src/main/res/values-night/colors.xml")) {
            val values = colors(file)
            for ((fg, bg) in pairs) {
                val ratio = contrast(values.getValue(fg), values.getValue(bg))
                assertTrue("$name: $fg auf $bg hat nur ${"%.2f".format(ratio)}:1", ratio >= 4.5)
            }
        }
    }
}
