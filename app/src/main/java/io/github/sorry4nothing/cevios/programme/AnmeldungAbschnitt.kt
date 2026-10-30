package io.github.sorry4nothing.cevios.programme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R

/**
 * Ja/Nein für die eigenen Kinder und, falls vorhanden, für die eigene Anmeldung als Leitung.
 * Die Knöpfe erscheinen nur, solange die API `teilnahmeOffen` meldet; sonst steht nur der Stand da.
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.abschnitt_anmeldung),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (!programm.teilnahmeOffen) Text(stringResource(R.string.anmeldung_geschlossen))

        programm.kinder.forEach { kind ->
            AntwortZeile(
                titel = kind.vorname,
                stand = kind.status,
                offen = programm.teilnahmeOffen,
                gesperrt = antwort.laeuft,
                jaText = stringResource(R.string.knopf_ja),
                jaBeschreibung = stringResource(R.string.knopf_kind_ja, kind.vorname),
                neinText = stringResource(R.string.knopf_nein),
                neinBeschreibung = stringResource(R.string.knopf_kind_nein, kind.vorname),
                onAntwort = { onKind(kind.id, it) },
            )
        }
        if (leitung != null) {
            AntwortZeile(
                titel = stringResource(R.string.abschnitt_anmeldung_leitung),
                stand = leitung.takeIf { it == "ja" || it == "nein" },
                offen = programm.teilnahmeOffen,
                gesperrt = antwort.laeuft,
                jaText = stringResource(R.string.knopf_leitung_ja),
                jaBeschreibung = stringResource(R.string.knopf_leitung_ja),
                neinText = stringResource(R.string.knopf_leitung_nein),
                neinBeschreibung = stringResource(R.string.knopf_leitung_nein),
                onAntwort = onLeitung,
            )
        }

        if (antwort.laeuft) {
            Text(
                stringResource(R.string.anmeldung_speichert),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        antwort.fehler?.let {
            Text(stringResource(it.text), Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
    }
}

@Composable
private fun AntwortZeile(
    titel: String,
    stand: String?,
    offen: Boolean,
    gesperrt: Boolean,
    jaText: String,
    jaBeschreibung: String,
    neinText: String,
    neinBeschreibung: String,
    onAntwort: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titel, style = MaterialTheme.typography.titleMedium)
        // Der Stand steht als Text da, nicht nur als gefüllter Knopf.
        Text(
            when (stand) {
                "ja" -> stringResource(R.string.antwort_ja)
                "nein" -> stringResource(R.string.antwort_nein)
                else -> stringResource(R.string.antwort_offen)
            },
        )
        if (offen) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AntwortKnopf(jaText, jaBeschreibung, gewaehlt = stand == "ja", aktiv = !gesperrt) { onAntwort(true) }
                AntwortKnopf(neinText, neinBeschreibung, gewaehlt = stand == "nein", aktiv = !gesperrt) { onAntwort(false) }
            }
        }
    }
}

@Composable
private fun AntwortKnopf(text: String, beschreibung: String, gewaehlt: Boolean, aktiv: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.semantics {
        contentDescription = beschreibung
        selected = gewaehlt
    }
    if (gewaehlt) {
        Button(onClick = onClick, enabled = aktiv, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = aktiv, modifier = modifier) { Text(text) }
    }
}
