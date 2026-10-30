package io.github.sorry4nothing.cevios.programme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.ChipArt
import io.github.sorry4nothing.cevios.ui.HinweisArt
import io.github.sorry4nothing.cevios.ui.HinweisBanner
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.fokusRing
import io.github.sorry4nothing.cevios.ui.theme.Abstand
import io.github.sorry4nothing.cevios.ui.theme.Groesse
import io.github.sorry4nothing.cevios.ui.theme.Voll
import io.github.sorry4nothing.cevios.ui.theme.cevi

/**
 * Ja/Nein für die eigenen Kinder und, falls vorhanden, für die eigene Anmeldung als Leitung.
 * Die Pillen erscheinen nur, solange die API `teilnahmeOffen` meldet; sonst steht nur der Stand da.
 */
@Composable
fun AnmeldungAbschnitt(
    programm: Programm,
    antwort: Antwortstand,
    onKind: (kindId: String, ja: Boolean) -> Unit,
    onLeitung: (ja: Boolean) -> Unit,
) {
    val leitung = programm.eigeneAnmeldung
    if (programm.kinder.isEmpty() && leitung == null) return

    Column(verticalArrangement = Arrangement.spacedBy(Abstand.s3)) {
        Text(
            stringResource(if (programm.kinder.isEmpty()) R.string.abschnitt_anmeldung_leitung else R.string.abschnitt_kinder),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        if (!programm.teilnahmeOffen) HinweisBanner(HinweisArt.INFO, stringResource(R.string.anmeldung_geschlossen))

        programm.kinder.forEach { kind ->
            ZusageZeile(
                name = kind.vorname,
                stand = kind.status,
                offen = programm.teilnahmeOffen,
                gesperrt = antwort.laeuft,
                gesendet = antwort.gesendet[kind.id],
                onAntwort = { onKind(kind.id, it) },
            )
        }
        if (leitung != null) {
            ZusageZeile(
                name = stringResource(R.string.nachricht_du),
                stand = leitung.takeIf { it == "ja" || it == "nein" },
                offen = programm.teilnahmeOffen,
                gesperrt = antwort.laeuft,
                gesendet = antwort.gesendet[SCHLUESSEL_LEITUNG],
                onAntwort = onLeitung,
            )
        }

        if (antwort.laeuft) {
            Text(
                stringResource(R.string.anmeldung_speichert),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        antwort.fehler?.let { HinweisBanner(HinweisArt.FEHLER, stringResource(it.text)) }
    }
}

/** Name und Stand links, daneben die zwei Pillen Ja und Nein. */
@Composable
private fun ZusageZeile(
    name: String,
    stand: String?,
    offen: Boolean,
    gesperrt: Boolean,
    gesendet: String?,
    onAntwort: (Boolean) -> Unit,
) {
    val farben = MaterialTheme.cevi
    val schema = MaterialTheme.colorScheme
    val (statusText, statusFarbe) = when {
        gesendet != null -> stringResource(R.string.zusage_gesendet, gesendet) to farben.onSuccessContainer
        stand == "ja" -> stringResource(R.string.antwort_ja) to farben.onSuccessContainer
        stand == "nein" -> stringResource(R.string.antwort_nein) to schema.onErrorContainer
        else -> stringResource(R.string.zusage_offen) to schema.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusFarbe,
                modifier = Modifier.semantics { if (gesendet != null) liveRegion = LiveRegionMode.Polite },
            )
        }
        if (offen) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Abstand.s2)) {
                Pille(ja = true, gewaehlt = stand == "ja", aktiv = !gesperrt, name = name) { onAntwort(true) }
                Pille(ja = false, gewaehlt = stand == "nein", aktiv = !gesperrt, name = name) { onAntwort(false) }
            }
        } else {
            when (stand) {
                "ja" -> StatusChip(ChipArt.JA, stringResource(R.string.antwort_ja))
                "nein" -> StatusChip(ChipArt.NEIN, stringResource(R.string.antwort_nein))
                else -> StatusChip(ChipArt.OFFEN, stringResource(R.string.zusage_offen))
            }
        }
    }
}

/**
 * Eine Pille: offen mit Rand, gewählt Ja grün mit dickem Häkchen, Nein rot mit Kreuz, beide mit 2-dp-Rand.
 * Der Zustand steht in Form, Icon und Wort, nicht nur in der Farbe. TalkBack: «Lena, Ja, nicht ausgewählt».
 */
@Composable
private fun Pille(ja: Boolean, gewaehlt: Boolean, aktiv: Boolean, name: String, onClick: () -> Unit) {
    val farben = MaterialTheme.cevi
    val schema = MaterialTheme.colorScheme
    val inhalt: Color
    val flaeche: Color
    val icon: Int
    when {
        gewaehlt && ja -> { inhalt = farben.onSuccessContainer; flaeche = farben.successContainer; icon = R.drawable.ic_haekchen_gewaehlt }
        gewaehlt -> { inhalt = schema.onErrorContainer; flaeche = schema.errorContainer; icon = R.drawable.ic_kreuz_gewaehlt }
        else -> { inhalt = schema.onSurface; flaeche = Color.Transparent; icon = if (ja) R.drawable.ic_haekchen else R.drawable.ic_kreuz }
    }
    val rand = if (gewaehlt) 2.dp to inhalt else 1.dp to schema.outline
    val wort = stringResource(if (ja) R.string.knopf_ja else R.string.knopf_nein)
    Row(
        Modifier
            .height(Groesse.touchZiel)
            .fokusRing(Voll)
            .clip(Voll)
            .background(flaeche)
            .border(rand.first, rand.second, Voll)
            .selectable(selected = gewaehlt, enabled = aktiv, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp)
            .semantics { contentDescription = "$name, $wort" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = inhalt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(wort, style = MaterialTheme.typography.labelLarge, color = inhalt, fontWeight = if (gewaehlt) FontWeight.ExtraBold else FontWeight.Bold)
    }
}
