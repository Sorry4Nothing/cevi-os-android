package io.github.sorry4nothing.cevios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.programme.ProgrammDetailScreen
import io.github.sorry4nothing.cevios.programme.ProgrammeApi
import io.github.sorry4nothing.cevios.programme.ProgrammeListeScreen
import io.github.sorry4nothing.cevios.programme.ProgrammeViewModel
import io.github.sorry4nothing.cevios.ui.theme.CeviTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val api = ProgrammeApi(ApiClient.erstellen(applicationContext))
        setContent { CeviTheme { Programme(api) } }
    }
}

@Composable
private fun Programme(api: ProgrammeApi) {
    val modell = viewModel { ProgrammeViewModel(api) }
    val liste by modell.liste.collectAsState()
    val detail by modell.detail.collectAsState()
    val antwort by modell.antwort.collectAsState()
    var offen by rememberSaveable { mutableStateOf<String?>(null) }

    // Auch nach Drehen oder Prozess-Ende lädt das offene Programm neu: Daten bleiben nur im Arbeitsspeicher.
    LaunchedEffect(offen) { offen?.let(modell::detailLaden) }

    Surface(Modifier.fillMaxSize()) {
        val id = offen
        if (id == null) {
            ProgrammeListeScreen(liste, onOeffnen = { offen = it }, onNochmals = modell::listeLaden)
        } else {
            BackHandler { offen = null }
            ProgrammDetailScreen(
                detail,
                antwort,
                onKind = { kindId, ja -> modell.kindAntworten(id, kindId, ja) },
                onLeitung = { ja -> modell.leitungAntworten(id, ja) },
                onZurueck = { offen = null },
                onNochmals = { modell.detailLaden(id) },
            )
        }
    }
}
