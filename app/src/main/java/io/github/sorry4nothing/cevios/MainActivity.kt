package io.github.sorry4nothing.cevios

import android.Manifest
import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.sorry4nothing.cevios.auth.AuthApi
import io.github.sorry4nothing.cevios.auth.AuthState
import io.github.sorry4nothing.cevios.auth.AuthViewModel
import io.github.sorry4nothing.cevios.auth.FormState
import io.github.sorry4nothing.cevios.auth.LoginScreen
import io.github.sorry4nothing.cevios.auth.PasswordScreen
import io.github.sorry4nothing.cevios.auth.ProfileScreen
import io.github.sorry4nothing.cevios.auth.StartScreen
import io.github.sorry4nothing.cevios.auth.WelcomeScreen
import io.github.sorry4nothing.cevios.chat.ChatApi
import io.github.sorry4nothing.cevios.chat.ChatListScreen
import io.github.sorry4nothing.cevios.chat.ChatConversationScreen
import io.github.sorry4nothing.cevios.chat.ChatViewModel
import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.chat.chatTitle
import io.github.sorry4nothing.cevios.contacts.PhoneRules
import io.github.sorry4nothing.cevios.images.LocalProfileImages
import io.github.sorry4nothing.cevios.images.ProfileImageApi
import io.github.sorry4nothing.cevios.images.ProfileImageEncoder
import io.github.sorry4nothing.cevios.images.ProfileImages
import io.github.sorry4nothing.cevios.images.LocalStageLogos
import io.github.sorry4nothing.cevios.images.StageLogos
import io.github.sorry4nothing.cevios.contacts.PhoneScreen
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.attendance.AttendanceApi
import io.github.sorry4nothing.cevios.attendance.AttendanceRules
import io.github.sorry4nothing.cevios.attendance.AttendanceScreen
import io.github.sorry4nothing.cevios.attendance.AttendanceViewModel
import io.github.sorry4nothing.cevios.push.Notifications
import io.github.sorry4nothing.cevios.push.NotificationsScreen
import io.github.sorry4nothing.cevios.push.PushApi
import io.github.sorry4nothing.cevios.push.PushTarget
import io.github.sorry4nothing.cevios.push.PushToken
import io.github.sorry4nothing.cevios.push.PushViewModel
import io.github.sorry4nothing.cevios.programs.AssignmentRules
import io.github.sorry4nothing.cevios.programs.AssignmentScreen
import io.github.sorry4nothing.cevios.programs.ProgramDetailScreen
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import io.github.sorry4nothing.cevios.programs.ProgramListScreen
import io.github.sorry4nothing.cevios.programs.RegistrationsRules
import io.github.sorry4nothing.cevios.programs.RegistrationsScreen
import io.github.sorry4nothing.cevios.programs.startDay
import io.github.sorry4nothing.cevios.programs.weekdayShort
import io.github.sorry4nothing.cevios.programs.ProgramsViewModel
import io.github.sorry4nothing.cevios.ui.MainScaffold
import io.github.sorry4nothing.cevios.ui.Destination
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.theme.CeviTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** So oft fragt ein offener Chat den Server nach neuen Nachrichten, solange die App im Vordergrund ist (Takt laut API). */
private const val CHAT_POLL_INTERVAL_MS = 5_000L

/** So oft fragt die sichtbare Chatliste nach: So kommen die Zähler «ungelesen» an (Takt laut API). */
private const val LIST_POLL_INTERVAL_MS = 30_000L

class MainActivity : ComponentActivity() {
    /** Wohin das Tippen auf einen Push führt; bleibt stehen, bis die Person angemeldet ist und der Bildschirm es aufgreift. */
    private var pushTarget by mutableStateOf<PushTarget?>(null)

    private fun targetOf(intent: Intent?) = PushTarget.from(intent?.getStringExtra("typ"), intent?.getStringExtra("id"))

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        targetOf(intent)?.let { pushTarget = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Der Start lief mit dem Theme des Splash (Manifest); ab jetzt gilt das Theme der App.
        setTheme(R.style.Theme_CeviOS)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Auch auf einem englisch gestellten Gerät liest der Screenreader die App mit deutscher Aussprache vor (ab Android 13).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("de")
        }
        Notifications.createChannels(applicationContext)
        // Nach Drehen oder Prozess-Ende gilt der Push nicht noch einmal: Das Intent ist dann dasselbe wie beim ersten Start.
        if (savedInstanceState == null) pushTarget = targetOf(intent)
        val client = ApiClient.create(applicationContext)
        setContent { CeviTheme { Root(client, pushTarget) { pushTarget = null } } }
    }
}

/** Je nach Anmeldestand: Start, Fehler, Anmelden, Passwort wählen, Willkommen oder die App selbst. */
@Composable
private fun Root(client: ApiClient, pushTarget: PushTarget?, onPushTargetHandled: () -> Unit) {
    val authApi = remember(client) { AuthApi(client) }
    val auth = viewModel { AuthViewModel(authApi, client.unauthorized, client.forbidden, client::forgetSession) }
    val state by auth.state.collectAsState()
    val form by auth.form.collectAsState()
    val passwordSaved by auth.passwordSaved.collectAsState()
    val imageForm by auth.imageForm.collectAsState()
    val context = LocalContext.current
    val openPrivacy = { openInBrowser(context, authApi.privacyUrl()) }
    Surface(Modifier.fillMaxSize()) {
        when (val current = state) {
            AuthState.Loading -> StartScreen()
            is AuthState.Failed -> Box(Modifier.safeDrawingPadding()) { ErrorState(current.error, onRetry = auth::start) }
            is AuthState.SignedOut -> {
                var loginRequested by rememberSaveable { mutableStateOf(false) }
                if (current.expired && !loginRequested) {
                    Box(Modifier.safeDrawingPadding()) {
                        ErrorState(ApiError.NOT_SIGNED_IN, onRetry = {}, onSignIn = { loginRequested = true })
                    }
                } else {
                    LoginScreen(form, onSignIn = auth::signIn)
                }
            }
            AuthState.MustChangePassword -> PasswordScreen(first = true, form, onSave = auth::changePassword)
            is AuthState.Welcome -> WelcomeScreen(
                name = current.profile.name,
                passwordSaved = passwordSaved,
                form = form,
                onReadPrivacy = openPrivacy,
                onAccept = auth::acceptPrivacy,
            )
            is AuthState.SignedIn -> CompositionLocalProvider(LocalViewModelStoreOwner provides current.scope) {
                // Die Bilder der Sitzung liegen im Speicher der Sitzung und verschwinden mit ihr.
                val images = viewModel { ProfileImages(ProfileImageApi(client)) }
                val stageLogos by viewModel { StageLogos(ProgramsApi(client)) }.paths.collectAsState()
                CompositionLocalProvider(LocalProfileImages provides images, LocalStageLogos provides stageLogos) {
                    MainContent(
                        ProgramsApi(client), ChatApi(client), PushApi(client), AttendanceApi(client), current.profile,
                        passwordSaved, form, imageForm, auth,
                        pushTarget, onPushTargetHandled,
                        onOpenPrivacy = openPrivacy,
                        onOpenSupport = { openInBrowser(context, authApi.supportUrl()) },
                        onOpenDeleteAccount = { openInBrowser(context, authApi.deleteAccountUrl()) },
                    )
                }
            }
        }
    }
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        // Kein Browser auf dem Gerät: Die Seite lässt sich dann nicht öffnen.
    }
}

@Composable
private fun MainContent(
    programsApi: ProgramsApi,
    chatApi: ChatApi,
    pushApi: PushApi,
    attendanceApi: AttendanceApi,
    account: Profile,
    passwordSaved: Boolean,
    form: FormState,
    imageForm: FormState,
    auth: AuthViewModel,
    pushTarget: PushTarget?,
    onPushTargetHandled: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val programs = viewModel { ProgramsViewModel(programsApi) }
    val chat = viewModel { ChatViewModel(chatApi) }
    val push = viewModel { PushViewModel(pushApi, account) }
    val attendance = viewModel { AttendanceViewModel(attendanceApi) }
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(Destination.PROGRAMS) }
    var openedProgram by rememberSaveable { mutableStateOf<String?>(null) }
    var openedChat by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneOpen by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf(false) }
    var viewingRegistrations by rememberSaveable { mutableStateOf(false) }
    var viewingAttendance by rememberSaveable { mutableStateOf(false) }

    // Ein gespeichertes Passwort schliesst den Wechsel; der Hinweis erscheint im Profil.
    LaunchedEffect(passwordSaved) { if (passwordSaved) changingPassword = false }
    // Pushes brauchen das Token dieses Geräts beim Server und, ab Android 13, die Erlaubnis (einmal fragen, danach im Profil).
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(account.id) {
        push.registerDevice { PushToken.fetch(context) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Notifications.shouldAskOnce(context)) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Das Tippen auf einen Push öffnet den Chat oder das Programm und schliesst, was davor offen war.
    LaunchedEffect(pushTarget) {
        val target = pushTarget ?: return@LaunchedEffect
        notificationsOpen = false; phoneOpen = false; assigning = false; viewingRegistrations = false
        viewingAttendance = false
        when (target) {
            is PushTarget.Chat -> { destination = Destination.CHAT; openedProgram = null; openedChat = target.id }
            is PushTarget.Program -> { destination = Destination.PROGRAMS; openedChat = null; openedProgram = target.id }
        }
        onPushTargetHandled()
    }
    // Auch nach Drehen oder Prozess-Ende lädt das Offene neu: Daten bleiben nur im Arbeitsspeicher.
    LaunchedEffect(openedProgram) { openedProgram?.let(programs::loadDetail) }
    LaunchedEffect(assigning) { if (assigning) programs.loadAgeGroups() }
    LaunchedEffect(viewingRegistrations, openedProgram) {
        val id = openedProgram
        if (viewingRegistrations && id != null) programs.loadRegistrations(id)
    }
    LaunchedEffect(openedChat) {
        val id = openedChat
        if (id == null) chat.closeChat() else chat.openChat(id)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(destination, openedChat, phoneOpen) {
        if (destination == Destination.CHAT && openedChat == null && !phoneOpen) {
            chat.loadList(silent = true)
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(LIST_POLL_INTERVAL_MS)
                    chat.loadList(silent = true)
                }
            }
        }
    }

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
            programId != null && viewingRegistrations -> {
                BackHandler { viewingRegistrations = false }
                val date = assignedProgram?.startDay()?.let { "${weekdayShort(it)} ${it.dayOfMonth}.${it.monthValue}." }
                MainScaffold(Destination.PROGRAMS, onDestination = { destination = it; openedProgram = null; viewingRegistrations = false }) {
                    RegistrationsScreen(
                        eyebrow = listOfNotNull(assignedProgram?.title, date).joinToString(" · ").ifBlank { null },
                        state = programs.registrations.collectAsState().value,
                        onBack = { viewingRegistrations = false },
                        onRetry = { programs.loadRegistrations(programId) },
                    )
                }
            }
            programId != null && viewingAttendance -> {
                BackHandler { viewingAttendance = false }
                val attendanceGroup = assignedProgram?.let { AttendanceRules.ageGroupFor(it, programProfile) }
                LaunchedEffect(attendanceGroup) { attendanceGroup?.let(attendance::load) }
                val date = assignedProgram?.startDay()?.let { "${weekdayShort(it)} ${it.dayOfMonth}.${it.monthValue}." }
                MainScaffold(Destination.PROGRAMS, onDestination = { destination = it; openedProgram = null; viewingAttendance = false }) {
                    AttendanceScreen(
                        eyebrow = listOfNotNull(assignedProgram?.title, date).joinToString(" · ").ifBlank { null },
                        programId = programId,
                        state = attendance.overview.collectAsState().value,
                        save = attendance.save.collectAsState().value,
                        onRecord = { personId, leader, present ->
                            attendanceGroup?.let { attendance.record(it, programId, personId, leader, present) }
                        },
                        onBack = { viewingAttendance = false; attendance.clearError() },
                        onRetry = { attendanceGroup?.let(attendance::load) },
                    )
                }
            }
            programId != null -> {
                BackHandler { openedProgram = null }
                MainScaffold(Destination.PROGRAMS, onDestination = { destination = it; openedProgram = null }) {
                    ProgramDetailScreen(
                        detail,
                        programs.rsvp.collectAsState().value,
                        canAssign = AssignmentRules.mayAssign(programProfile),
                        canSeeRegistrations = assignedProgram?.let { RegistrationsRules.maySee(it, programProfile) } == true,
                        canRecordAttendance = assignedProgram?.let { AttendanceRules.ageGroupFor(it, programProfile) } != null,
                        contacts = programs.contacts.collectAsState().value,
                        onChild = { childId, attending -> programs.rsvpChild(programId, childId, attending) },
                        onLeader = { attending -> programs.rsvpLeader(programId, attending) },
                        onAssign = { assigning = true },
                        onRegistrations = { viewingRegistrations = true },
                        onAttendance = { viewingAttendance = true },
                        onBack = { openedProgram = null },
                        onRetry = { programs.loadDetail(programId) },
                    )
                }
            }
            chatId != null -> {
                BackHandler { openedChat = null }
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
                    contacts = chat.contacts.collectAsState().value,
                    loadingOlder = chat.loadingOlder.collectAsState().value,
                    send = chat.send.collectAsState().value,
                    action = chat.action.collectAsState().value,
                    onSend = chat::send,
                    onOlder = chat::loadOlder,
                    onDirectChat = { ageGroupId, parentId -> chat.openDirectChat(ageGroupId, parentId) { openedChat = it } },
                    onLoadContacts = chat::loadContacts,
                    onBack = { openedChat = null },
                    onRetry = { chat.openChat(chatId) },
                )
            }
            phoneOpen -> {
                val leave = { phoneOpen = false; chat.resetPhone() }
                BackHandler(onBack = leave)
                val phoneProfile = (chat.profile.collectAsState().value as? ApiResult.Ok)?.value
                PhoneScreen(
                    phone = phoneProfile?.phone,
                    canSet = PhoneRules.maySet(phoneProfile),
                    state = chat.phone.collectAsState().value,
                    onSave = { chat.savePhone(it, leave) },
                    onBack = leave,
                )
            }
            notificationsOpen -> {
                val leave = { notificationsOpen = false; push.clearError() }
                BackHandler(onBack = leave)
                NotificationsScreen(
                    switches = push.switches.collectAsState().value,
                    form = push.form.collectAsState().value,
                    onChat = push::setChat,
                    onPrograms = push::setPrograms,
                    onRegistrations = push::setRegistrations,
                    onBack = leave,
                )
            }
            changingPassword -> {
                val leave = { changingPassword = false; auth.clearError() }
                BackHandler(onBack = leave)
                PasswordScreen(first = false, form, onSave = auth::changePassword, onBack = leave)
            }
            else -> MainScaffold(destination, onDestination = { destination = it }) {
                if (destination == Destination.PROGRAMS) {
                    ProgramListScreen(
                        programs.list.collectAsState().value,
                        onOpen = { openedProgram = it },
                        onRetry = programs::loadList,
                    )
                } else if (destination == Destination.PROFILE) {
                    ProfileScreen(
                        account, passwordSaved, form, imageForm,
                        onPickImage = { uri -> auth.uploadImage { withContext(Dispatchers.Default) { ProfileImageEncoder.encode(resolver, uri) } } },
                        onRemoveImage = auth::removeImage,
                        onChangePassword = { changingPassword = true },
                        onOpenNotifications = { notificationsOpen = true },
                        onOpenPrivacy = onOpenPrivacy,
                        onOpenSupport = onOpenSupport,
                        onOpenDeleteAccount = onOpenDeleteAccount,
                        onSignOut = auth::signOut,
                        onLeave = { auth.clearPasswordSaved(); auth.clearImageError() },
                    )
                } else {
                    ChatListScreen(
                        chat.list.collectAsState().value,
                        (chat.profile.collectAsState().value as? ApiResult.Ok)?.value,
                        chat.action.collectAsState().value,
                        onOpen = { openedChat = it },
                        onDirectChat = { ageGroupId -> chat.openDirectChat(ageGroupId, null) { openedChat = it } },
                        phoneVisible = PhoneRules.mayEdit((chat.profile.collectAsState().value as? ApiResult.Ok)?.value),
                        onPhone = { phoneOpen = true },
                        onRetry = { chat.loadList() },
                    )
                }
            }
        }
    }
}
