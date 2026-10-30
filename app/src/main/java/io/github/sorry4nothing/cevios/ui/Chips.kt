package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.Voll
import io.github.sorry4nothing.cevios.ui.theme.cevi

/** Zustand eines Etiketts. Immer Icon und Wort: Ein Zustand steht nie nur als Farbe da. */
enum class ChipArt(@DrawableRes val icon: Int?) {
    JA(R.drawable.ic_haekchen),
    NEIN(R.drawable.ic_kreuz),
    OFFEN(R.drawable.ic_fragezeichen),
    PRUEFEN(R.drawable.ic_warndreieck),
    NICHT_ZUGEORDNET(R.drawable.ic_getrennter_link),
    STUFE(null),
}

/** Kleines Etikett für einen Zustand. Nicht bedienbar; bedienbar ist die Karte, auf der es sitzt. */
@Composable
fun StatusChip(art: ChipArt, text: String, modifier: Modifier = Modifier) {
    val farben = MaterialTheme.cevi
    val schema = MaterialTheme.colorScheme
    val (flaeche, inhalt) = when (art) {
        ChipArt.JA -> farben.successContainer to farben.onSuccessContainer
        ChipArt.NEIN -> schema.errorContainer to schema.onErrorContainer
        ChipArt.OFFEN -> Color.Transparent to schema.onSurfaceVariant
        ChipArt.PRUEFEN -> farben.warningContainer to farben.onWarningContainer
        ChipArt.NICHT_ZUGEORDNET -> schema.secondaryContainer to schema.onSecondaryContainer
        ChipArt.STUFE -> farben.stufeContainer to farben.onStufeContainer
    }
    Row(
        modifier
            .heightIn(min = 28.dp)
            .background(flaeche, Voll)
            .then(if (art == ChipArt.OFFEN) Modifier.border(1.dp, schema.outline, Voll) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Abstand.s1),
    ) {
        art.icon?.let { Icon(painterResource(it), contentDescription = null, tint = inhalt, modifier = Modifier.size(16.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = inhalt)
    }
}
