package io.github.sorry4nothing.cevios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sorry4nothing.cevios.chat.ChatApi
import io.github.sorry4nothing.cevios.chat.ChatListeScreen
import io.github.sorry4nothing.cevios.chat.ChatVerlaufScreen
import io.github.sorry4nothing.cevios.chat.ChatViewModel
import io.github.sorry4nothing.cevios.chat.HilfeScreen
import io.github.sorry4nothing.cevios.chat.chatTitel
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programme.ProgrammDetailScreen
import io.github.sorry4nothing.cevios.programme.ProgrammeApi
import io.github.sorry4nothing.cevios.programme.ProgrammeListeScreen
import io.github.sorry4nothing.cevios.programme.ProgrammeViewModel
import io.github.sorry4nothing.cevios.ui.theme.CeviTheme
import kotlinx.coroutines.delay

/** So oft fragt ein offener Chat den Server nach neuen Nachrichten, solange die App im Vordergrund ist. */
private const val CHAT_TAKT_MS = 15_000L

private const val TAB_PROGRAMME = 0
private const val TAB_CHAT = 1

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val client = ApiClient.erstellen(applicationContext)
        setContent { CeviTheme { Haupt(ProgrammeApi(client), ChatApi(client)) } }
    }
}

@Composable
private fun Haupt(programmeApi: ProgrammeApi, chatApi: ChatApi) {
    val programme = viewModel { ProgrammeViewModel(programmeApi) }
    val chat = viewModel { ChatViewModel(chatApi) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_PROGRAMME) }
    var offenesProgramm by rememberSaveable { mutableStateOf<String?>(null) }
    var offenerChat by rememberSaveable { mutableStateOf<String?>(null) }
    var hilfeOffen by rememberSaveable { mutableStateOf(false) }

    // Auch nach Drehen oder Prozess-Ende lädt das Offene neu: Daten bleiben nur im Arbeitsspeicher.
    LaunchedEffect(offenesProgramm) { offenesProgramm?.let(programme::detailLaden) }
    LaunchedEffect(hilfeOffen) { if (hilfeOffen) chat.hilfeLaden() }
    LaunchedEffect(offenerChat) {
        val id = offenerChat
        if (id == null) chat.chatSchliessen() else chat.chatOeffnen(id)
    }
    LaunchedEffect(tab, offenerChat) { if (tab == TAB_CHAT && offenerChat == null) chat.listeLaden(still = true) }

    Surface(Modifier.fillMaxSize()) {
        val programmId = offenesProgramm
        val chatId = offenerChat
        when {
            programmId != null -> {
                BackHandler { offenesProgramm = null }
                ProgrammDetailScreen(
                    programme.detail.collectAsState().value,
                    programme.antwort.collectAsState().value,
                    onKind = { kindId, ja -> programme.kindAntworten(programmId, kindId, ja) },
                    onLeitung = { ja -> programme.leitungAntworten(programmId, ja) },
                    onZurueck = { offenesProgramm = null },
                    onNochmals = { programme.detailLaden(programmId) },
                )
            }
            chatId != null -> {
                BackHandler { offenerChat = null }
                val lebenszyklus = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(chatId) {
                    lebenszyklus.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            delay(CHAT_TAKT_MS)
                            chat.aktualisieren()
                        }
                    }
                }
                val liste by chat.liste.collectAsState()
                val profil by chat.profil.collectAsState()
                val chatEintrag = (liste as? ApiResult.Ok)?.wert?.chats?.firstOrNull { it.id == chatId }
                ChatVerlaufScreen(
                    titel = chatTitel(chatEintrag),
                    chat = chatEintrag,
                    profil = (profil as? ApiResult.Ok)?.wert,
                    zustand = chat.verlauf.collectAsState().value,
                    aeltereLaedt = chat.aeltereLaedt.collectAsState().value,
                    senden = chat.senden.collectAsState().value,
                    aktion = chat.aktion.collectAsState().value,
                    onSenden = chat::senden,
                    onAeltere = chat::aeltereLaden,
                    onAktualisieren = chat::aktualisieren,
                    onAusblenden = chat::ausblenden,
                    onMelden = chat::melden,
                    onEinzelchat = { stufeId, elternId -> chat.einzelchatOeffnen(stufeId, elternId) { offenerChat = it } },
                    onZurueck = { offenerChat = null },
                    onNochmals = { chat.chatOeffnen(chatId) },
                )
            }
            hilfeOffen -> {
                BackHandler { hilfeOffen = false }
                HilfeScreen(
                    chat.hilfe.collectAsState().value,
                    onZurueck = { hilfeOffen = false },
                    onNochmals = chat::hilfeLaden,
                )
            }
            else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == TAB_PROGRAMME,
                        onClick = { tab = TAB_PROGRAMME },
                        text = { Text(stringResource(R.string.tab_programme)) },
                    )
                    Tab(
                        selected = tab == TAB_CHAT,
                        onClick = { tab = TAB_CHAT },
                        text = { Text(stringResource(R.string.tab_chat)) },
                    )
                }
                if (tab == TAB_PROGRAMME) {
                    ProgrammeListeScreen(
                        programme.liste.collectAsState().value,
                        onOeffnen = { offenesProgramm = it },
                        onNochmals = programme::listeLaden,
                    )
                } else {
                    ChatListeScreen(
                        chat.liste.collectAsState().value,
                        (chat.profil.collectAsState().value as? ApiResult.Ok)?.wert,
                        chat.aktion.collectAsState().value,
                        onOeffnen = { offenerChat = it },
                        onEinzelchat = { stufeId -> chat.einzelchatOeffnen(stufeId, null) { offenerChat = it } },
                        onHilfe = { hilfeOffen = true },
                        onNochmals = { chat.listeLaden() },
                    )
                }
            }
        }
    }
}
