package io.github.sorry4nothing.cevios.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult

/** Ladeanzeige, die der Screenreader ansagt. */
@Composable
fun Laden() {
    val beschreibung = stringResource(R.string.laden)
    Box(
        Modifier.fillMaxWidth().padding(32.dp).semantics {
            contentDescription = beschreibung
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

/** Fehlertext (wird angesagt) mit «Nochmals versuchen». */
@Composable
fun FehlerAnzeige(fehler: ApiResult.Fehler, onNochmals: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(fehler.fehler.text),
            Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Button(onClick = onNochmals) { Text(stringResource(R.string.nochmals)) }
    }
}
