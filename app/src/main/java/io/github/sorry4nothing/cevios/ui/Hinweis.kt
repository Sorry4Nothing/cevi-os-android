package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.cevi

enum class HinweisArt(@DrawableRes val icon: Int) {
    /** Bleibt stehen und wird angesagt. */
    FEHLER(R.drawable.ic_warndreieck),
    INFO(R.drawable.ic_info),
    WARNUNG(R.drawable.ic_warndreieck),
}

/** Banner im Inhalt: erklärt, warum etwas so ist, oder sagt, was schiefging. Immer Icon und Text. */
@Composable
fun HinweisBanner(art: HinweisArt, text: String, modifier: Modifier = Modifier) {
    val schema = MaterialTheme.colorScheme
    val (flaeche, inhalt) = when (art) {
        HinweisArt.FEHLER -> schema.errorContainer to schema.onErrorContainer
        HinweisArt.INFO -> schema.secondaryContainer to schema.onSecondaryContainer
        HinweisArt.WARNUNG -> MaterialTheme.cevi.warningContainer to MaterialTheme.cevi.onWarningContainer
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(flaeche, MaterialTheme.shapes.medium)
            .padding(horizontal = Abstand.s4, vertical = Abstand.s3)
            .semantics { if (art == HinweisArt.FEHLER) liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(Abstand.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(art.icon), contentDescription = null, tint = inhalt, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = inhalt)
    }
}
