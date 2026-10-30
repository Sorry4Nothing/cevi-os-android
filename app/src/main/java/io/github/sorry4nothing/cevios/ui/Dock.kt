package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.BreitAb
import io.github.sorry4nothing.cevios.ui.theme.Groesse

/** Die Hauptziele des Docks. Profil kommt mit dem Profil-Issue dazu. */
enum class Ziel(@StringRes val text: Int, @DrawableRes val icon: Int) {
    PROGRAMME(R.string.tab_programme, R.drawable.ic_kalender),
    CHAT(R.string.tab_chat, R.drawable.ic_chat),
}

private val DockForm = RoundedCornerShape(24.dp)
private val ZielForm = RoundedCornerShape(18.dp)

/**
 * Gerüst mit schwebendem Dock: unten im Hochformat, ab [BreitAb] senkrecht links (Querformat, Tablet).
 * Das Dock sitzt unter dem Inhalt, nichts scrollt dahinter durch.
 */
@Composable
fun HauptGeruest(aktiv: Ziel, onZiel: (Ziel) -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (maxWidth >= BreitAb) {
            Row(Modifier.fillMaxSize().padding(start = Abstand.s3)) {
                Dock(aktiv, onZiel, senkrecht = true, Modifier.padding(vertical = Abstand.s3).width(Groesse.dockBreite))
                Spacer(Modifier.width(Abstand.s4))
                content()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) { content() }
                Dock(
                    aktiv, onZiel, senkrecht = false,
                    Modifier.padding(start = Abstand.s3, end = Abstand.s3, top = Abstand.s2, bottom = Abstand.s3),
                )
            }
        }
    }
}

@Composable
private fun Dock(aktiv: Ziel, onZiel: (Ziel) -> Unit, senkrecht: Boolean, modifier: Modifier) {
    val huelle = modifier
        .clip(DockForm)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, DockForm)
        .padding(6.dp)
        .selectableGroup()
    if (senkrecht) {
        Column(huelle, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Ziel.entries.forEach { DockZiel(it, it == aktiv, onZiel, Modifier.fillMaxWidth().height(64.dp)) }
        }
    } else {
        Row(huelle.height(Groesse.footer), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Ziel.entries.forEach { DockZiel(it, it == aktiv, onZiel, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/** Ein Ziel über die ganze Fläche (mindestens 48 dp): aktiv Cevi-Blau mit weissem Wort, inaktiv ruhig. Das Wort steht immer da. */
@Composable
private fun DockZiel(ziel: Ziel, aktiv: Boolean, onZiel: (Ziel) -> Unit, modifier: Modifier) {
    val farbe = if (aktiv) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fokusRing(ZielForm)
            .clip(ZielForm)
            .then(if (aktiv) Modifier.background(MaterialTheme.colorScheme.secondary) else Modifier)
            .selectable(selected = aktiv, role = Role.Tab, onClick = { onZiel(ziel) }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(ziel.icon), contentDescription = null, tint = farbe, modifier = Modifier.padding(0.dp))
        Text(
            stringResource(ziel.text),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (aktiv) FontWeight.Bold else FontWeight.Normal,
            color = farbe,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
