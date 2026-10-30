package io.github.sorry4nothing.cevios

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sorry4nothing.cevios.chat.ChatApi
import io.github.sorry4nothing.cevios.chat.ChatListScreen
import io.github.sorry4nothing.cevios.chat.ChatConversationScreen
import io.github.sorry4nothing.cevios.chat.ChatViewModel
import io.github.sorry4nothing.cevios.chat.HelpScreen
import io.github.sorry4nothing.cevios.chat.chatTitle
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.AssignmentRules
import io.github.sorry4nothing.cevios.programs.AssignmentScreen
import io.github.sorry4nothing.cevios.programs.ProgramDetailScreen
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import io.github.sorry4nothing.cevios.programs.ProgramListScreen
import io.github.sorry4nothing.cevios.programs.ProgramsViewModel
import io.github.sorry4nothing.cevios.ui.MainScaffold
import io.github.sorry4nothing.cevios.ui.Destination
import io.github.sorry4nothing.cevios.ui.theme.CeviTheme
import kotlinx.coroutines.delay

/** So oft fragt ein offener Chat den Server nach neuen Nachrichten, solange die App im Vordergrund ist. */
private const val CHAT_POLL_INTERVAL_MS = 15_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Auch auf einem englisch gestellten Gerät liest der Screenreader die App mit deutscher Aussprache vor (ab Android 13).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("de")
        }
        val client = ApiClient.create(applicationContext)
        setContent { CeviTheme { MainContent(ProgramsApi(client), ChatApi(client)) } }
    }
}

@Composable
private fun MainContent(programsApi: ProgramsApi, chatApi: ChatApi) {
    val programs = viewModel { ProgramsViewModel(programsApi) }
    val chat = viewModel { ChatViewModel(chatApi) }
    var destination by rememberSaveable { mutableStateOf(Destination.PROGRAMS) }
    var openedProgram by rememberSaveable { mutableStateOf<String?>(null) }
    var openedChat by rememberSaveable { mutableStateOf<String?>(null) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf(false) }

    // Auch nach Drehen oder Prozess-Ende lädt das Offene neu: Daten bleiben nur im Arbeitsspeicher.
    LaunchedEffect(openedProgram) { openedProgram?.let(programs::loadDetail) }
    LaunchedEffect(assigning) { if (assigning) programs.loadAgeGroups() }
    LaunchedEffect(helpOpen) { if (helpOpen) chat.loadHelp() }
    LaunchedEffect(openedChat) {
        val id = openedChat
        if (id == null) chat.closeChat() else chat.openChat(id)
    }
    LaunchedEffect(destination, openedChat) { if (destination == Destination.CHAT && openedChat == null) chat.loadList(silent = true) }

    Surface(Modifier.fillMaxSize()) {
        val programId = openedProgram
        val chatId = openedChat
        val detail by programs.detail.collectAsState()
        val programProfile = (programs.profile.collectAsState().value as? ApiResult.Ok)?.value
        val assignedProgram = (detail as? ApiResult.Ok)?.value?.takeIf { it.id == programId }
        when {
            programId != null && assigning && assignedProgram != null -> {
                val leave = { assigning = false; programs.resetEdit() }
                BackHandler(onBack = leave)
                val edit = programs.edit.collectAsState().value
                AssignmentScreen(
                    assignedProgram, programs.ageGroups.collectAsState().value, programProfile, edit,
                    onSave = { request -> programs.saveAssignment(programId, request, leave) },
                    onBack = leave,
                    onRetry = programs::loadAgeGroups,
                )
            }
            programId != null -> {
                BackHandler { openedProgram = null }
                MainScaffold(Destination.PROGRAMS, onDestination = { destination = it; openedProgram = null }) {
                    ProgramDetailScreen(
                        detail,
                        programs.rsvp.collectAsState().value,
                        canAssign = AssignmentRules.mayAssign(programProfile),
                        onChild = { childId, attending -> programs.rsvpChild(programId, childId, attending) },
                        onLeader = { attending -> programs.rsvpLeader(programId, attending) },
                        onAssign = { assigning = true },
                        onBack = { openedProgram = null },
                        onRetry = { programs.loadDetail(programId) },
                    )
                }
            }
            chatId != null -> {
                BackHandler { openedChat = null }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(chatId) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            delay(CHAT_POLL_INTERVAL_MS)
                            chat.refresh()
                        }
                    }
                }
                val list by chat.list.collectAsState()
                val profile by chat.profile.collectAsState()
                val chatEntry = (list as? ApiResult.Ok)?.value?.chats?.firstOrNull { it.id == chatId }
                ChatConversationScreen(
                    title = chatTitle(chatEntry),
                    chat = chatEntry,
                    profile = (profile as? ApiResult.Ok)?.value,
                    state = chat.conversation.collectAsState().value,
                    loadingOlder = chat.loadingOlder.collectAsState().value,
                    send = chat.send.collectAsState().value,
                    action = chat.action.collectAsState().value,
                    onSend = chat::send,
                    onOlder = chat::loadOlder,
                    onHide = chat::hide,
                    onReport = chat::report,
                    onDirectChat = { ageGroupId, parentId -> chat.openDirectChat(ageGroupId, parentId) { openedChat = it } },
                    onBack = { openedChat = null },
                    onRetry = { chat.openChat(chatId) },
                )
            }
            helpOpen -> {
                BackHandler { helpOpen = false }
                HelpScreen(
                    chat.help.collectAsState().value,
                    onBack = { helpOpen = false },
                    onRetry = chat::loadHelp,
                )
            }
            else -> MainScaffold(destination, onDestination = { destination = it }) {
                if (destination == Destination.PROGRAMS) {
                    ProgramListScreen(
                        programs.list.collectAsState().value,
                        onOpen = { openedProgram = it },
                        onRetry = programs::loadList,
                    )
                } else {
                    ChatListScreen(
                        chat.list.collectAsState().value,
                        (chat.profile.collectAsState().value as? ApiResult.Ok)?.value,
                        chat.action.collectAsState().value,
                        onOpen = { openedChat = it },
                        onDirectChat = { ageGroupId -> chat.openDirectChat(ageGroupId, null) { openedChat = it } },
                        onHelp = { helpOpen = true },
                        onRetry = { chat.loadList() },
                    )
                }
            }
        }
    }
}
