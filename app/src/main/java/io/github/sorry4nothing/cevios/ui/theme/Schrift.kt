package io.github.sorry4nothing.cevios.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.sorry4nothing.cevios.R

/**
 * Archivo, mitgeliefert (variable Schrift, Open Font License, siehe `assets/licenses`). Es wird nichts nachgeladen.
 * Grössen in sp: Die Schriftgrösse des Systems greift bis 200 %.
 */
val Archivo = FontFamily(
    listOf(400, 700, 800).map { gewicht ->
        Font(
            R.font.archivo,
            weight = FontWeight(gewicht),
            variationSettings = FontVariation.Settings(FontVariation.weight(gewicht)),
        )
    },
)

private fun stil(groesse: Int, zeile: Int, gewicht: Int, abstand: Float = 0f) = TextStyle(
    fontFamily = Archivo,
    fontSize = groesse.sp,
    lineHeight = zeile.sp,
    fontWeight = FontWeight(gewicht),
    letterSpacing = abstand.em,
)

/** Die Stile aus dem Design (`tokens.json`, `type`), den Material-Rollen zugeordnet. */
internal val CeviTypografie = Typography(
    displayLarge = stil(40, 44, 800, -0.01f), // display: Begrüssung auf dem Login
    displayMedium = stil(40, 44, 800, -0.01f),
    displaySmall = stil(32, 38, 800),
    headlineLarge = stil(32, 38, 800), // headline: Screen-Titel im Kopf
    headlineMedium = stil(28, 34, 800), // headline-small: Titel auf Detail-Screens
    headlineSmall = stil(28, 34, 800),
    titleLarge = stil(22, 28, 800), // title-large: Dialog-Titel
    titleMedium = stil(16, 22, 700), // title: Kartentitel, Namen, Abschnittstitel
    titleSmall = stil(16, 22, 700),
    bodyLarge = stil(16, 22, 400), // body
    bodyMedium = stil(14, 20, 400), // body-small: Metadaten
    bodySmall = stil(12, 16, 400), // caption
    labelLarge = stil(15, 20, 700), // label: Buttons und Pillen
    labelMedium = stil(12, 16, 700), // label-small: Footer, Chips, Zähler
    labelSmall = stil(12, 16, 700),
)

/** Überzeile über Titeln: in Grossbuchstaben setzen und in `accent` färben. */
val Eyebrow: TextStyle = stil(13, 16, 700, 0.08f)
