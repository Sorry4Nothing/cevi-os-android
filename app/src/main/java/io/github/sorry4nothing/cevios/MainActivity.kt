package io.github.sorry4nothing.cevios

import io.github.sorry4nothing.cevios.ui.screenTransition
import io.github.sorry4nothing.cevios.lager.AgendaScreen
import io.github.sorry4nothing.cevios.lager.ArchiveApi
import io.github.sorry4nothing.cevios.lager.ArchiveRights
import io.github.sorry4nothing.cevios.lager.ArchiveViewModel
import io.github.sorry4nothing.cevios.lager.SearchDestination
import io.github.sorry4nothing.cevios.lager.sharePdf
import io.github.sorry4nothing.cevios.push.OpenCampObject
import io.github.sorry4nothing.cevios.lager.AndroidCommsTexts
import io.github.sorry4nothing.cevios.lager.AndroidTaskTexts
import io.github.sorry4nothing.cevios.lager.CampTarget
import io.github.sorry4nothing.cevios.lager.CommsApi
import io.github.sorry4nothing.cevios.lager.CommsViewModel
import io.github.sorry4nothing.cevios.lager.InboxScreen
import io.github.sorry4nothing.cevios.lager.LagerNotificationsScreen
import io.github.sorry4nothing.cevios.lager.MeetingListScreen
import io.github.sorry4nothing.cevios.lager.MeetingModeScreen
import io.github.sorry4nothing.cevios.lager.MeetingRights
import io.github.sorry4nothing.cevios.lager.MeetingRules
import io.github.sorry4nothing.cevios.lager.MeetingsApi
import io.github.sorry4nothing.cevios.lager.LiveApi
import io.github.sorry4nothing.cevios.lager.LiveArea
import io.github.sorry4nothing.cevios.lager.LiveFrame
import io.github.sorry4nothing.cevios.lager.LiveNav
import io.github.sorry4nothing.cevios.lager.LiveRights
import io.github.sorry4nothing.cevios.lager.LiveScreen
import io.github.sorry4nothing.cevios.lager.LiveViewModel
import io.github.sorry4nothing.cevios.lager.PlanArea
import io.github.sorry4nothing.cevios.lager.PlanRights
import io.github.sorry4nothing.cevios.lager.PlanScreen
import io.github.sorry4nothing.cevios.lager.PlanningApi
import io.github.sorry4nothing.cevios.lager.PlanningViewModel
import io.github.sorry4nothing.cevios.lager.RessortPerson
import io.github.sorry4nothing.cevios.lager.MeetingsViewModel
import io.github.sorry4nothing.cevios.lager.MentionsScreen
import io.github.sorry4nothing.cevios.lager.MinutesScreen
import io.github.sorry4nothing.cevios.lager.PinboardScreen
import io.github.sorry4nothing.cevios.lager.PostEditorScreen
import io.github.sorry4nothing.cevios.lager.RecipientChoices
import io.github.sorry4nothing.cevios.lager.TaskRules
import io.github.sorry4nothing.cevios.lager.campTargetFor
import io.github.sorry4nothing.cevios.lager.todayInCamp
import io.github.sorry4nothing.cevios.lager.clearSharedFiles
import io.github.sorry4nothing.cevios.lager.shareCalendarFile
import io.github.sorry4nothing.cevios.lager.CampApi
import io.github.sorry4nothing.cevios.lager.NO_PERMISSIONS
import io.github.sorry4nothing.cevios.lager.permissions
import io.github.sorry4nothing.cevios.lager.LeadershipStart
import io.github.sorry4nothing.cevios.lager.RessortListScreen
import io.github.sorry4nothing.cevios.lager.RessortScreen
import io.github.sorry4nothing.cevios.lager.QuickCaptureSheet
import io.github.sorry4nothing.cevios.lager.StartFab
import io.github.sorry4nothing.cevios.lager.TaskDetailScreen
import io.github.sorry4nothing.cevios.lager.TaskPerson
import io.github.sorry4nothing.cevios.lager.TaskRights
import io.github.sorry4nothing.cevios.lager.TaskView
import io.github.sorry4nothing.cevios.lager.TasksApi
import io.github.sorry4nothing.cevios.lager.TasksScreen
import io.github.sorry4nothing.cevios.lager.TasksViewModel
import io.github.sorry4nothing.cevios.lager.TeamStart
import io.github.sorry4nothing.cevios.lager.CampInfo
import io.github.sorry4nothing.cevios.lager.CampListScreen
import io.github.sorry4nothing.cevios.lager.CampStartScreen
import io.github.sorry4nothing.cevios.lager.CampCache
import io.github.sorry4nothing.cevios.lager.CampViewModel
import io.github.sorry4nothing.cevios.lager.LocalStaleSince
import io.github.sorry4nothing.cevios.lager.ChangeState
import io.github.sorry4nothing.cevios.lager.CheckApi
import io.github.sorry4nothing.cevios.lager.CheckRights
import io.github.sorry4nothing.cevios.lager.CheckViewModel
import io.github.sorry4nothing.cevios.lager.ConflictScreen
import io.github.sorry4nothing.cevios.lager.ModeMenu
import io.github.sorry4nothing.cevios.lager.MoreEntry
import io.github.sorry4nothing.cevios.lager.MoreSheet
import io.github.sorry4nothing.cevios.lager.QueueScreen
import io.github.sorry4nothing.cevios.lager.QueueState
import io.github.sorry4nothing.cevios.ui.LocalModeSwitch
import io.github.sorry4nothing.cevios.ui.ModeSwitch
import io.github.sorry4nothing.cevios.ui.rememberMotionEnabled
import io.github.sorry4nothing.cevios.ui.loginTransition
import io.github.sorry4nothing.cevios.ui.tabTransition
import io.github.sorry4nothing.cevios.security.LockOutcome
import io.github.sorry4nothing.cevios.security.BiometricPrompter
import io.github.sorry4nothing.cevios.security.AppLock
import io.github.sorry4nothing.cevios.widget.WidgetPreviews
import io.github.sorry4nothing.cevios.widget.WidgetProvider
import io.github.sorry4nothing.cevios.widget.forgetWidgets
import io.github.sorry4nothing.cevios.auth.LockedScreen
import io.github.sorry4nothing.cevios.security.shouldRelock
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import androidx.compose.runtime.SideEffect
import android.view.WindowManager
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import android.Manifest
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.sorry4nothing.cevios.info.InfoApi
import io.github.sorry4nothing.cevios.info.InfoHost
import io.github.sorry4nothing.cevios.info.InfoPage
import io.github.sorry4nothing.cevios.info.InfoViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.sorry4nothing.cevios.auth.AuthApi
import io.github.sorry4nothing.cevios.auth.PlayIntegrity
import io.github.sorry4nothing.cevios.auth.AuthState
import io.github.sorry4nothing.cevios.auth.AuthViewModel
import io.github.sorry4nothing.cevios.auth.FormState
import io.github.sorry4nothing.cevios.auth.CropImageScreen
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
import io.github.sorry4nothing.cevios.push.Notifications
import io.github.sorry4nothing.cevios.push.OpenChat
import io.github.sorry4nothing.cevios.push.NotificationsScreen
import io.github.sorry4nothing.cevios.push.PushApi
import io.github.sorry4nothing.cevios.push.PushTarget
import io.github.sorry4nothing.cevios.push.WidgetArea
import io.github.sorry4nothing.cevios.push.PushToken
import io.github.sorry4nothing.cevios.push.PushViewModel
import io.github.sorry4nothing.cevios.lager.WriteErrorBanner
import io.github.sorry4nothing.cevios.termine.CalendarLinkSection
import io.github.sorry4nothing.cevios.termine.CalendarLinkViewModel
import io.github.sorry4nothing.cevios.termine.EventScreen
import io.github.sorry4nothing.cevios.termine.EventViewModel
import io.github.sorry4nothing.cevios.termine.GroupsSection
import io.github.sorry4nothing.cevios.termine.ProposalScreen
import io.github.sorry4nothing.cevios.termine.ProposalsViewModel
import io.github.sorry4nothing.cevios.termine.RoomListScreen
import io.github.sorry4nothing.cevios.termine.RoomRequestScreen
import io.github.sorry4nothing.cevios.termine.RoomsViewModel
import io.github.sorry4nothing.cevios.termine.TermineApi
import io.github.sorry4nothing.cevios.auth.SettingsCard
import io.github.sorry4nothing.cevios.auth.SettingsRow
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
import io.github.sorry4nothing.cevios.ui.BusyOverlay
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.theme.CeviTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** So oft fragt ein offener Chat den Server nach neuen Nachrichten, solange die App im Vordergrund ist (Takt laut API). */
private const val CHAT_POLL_INTERVAL_MS = 5_000L

/** So oft fragt die sichtbare Chatliste nach: So kommen die Zähler «ungelesen» an (Takt laut API). */
private const val LIST_POLL_INTERVAL_MS = 30_000L

/** So oft liest das offene Programm Stand und Kontakte still neu. */
private const val DETAIL_POLL_INTERVAL_MS = 15_000L

class MainActivity : FragmentActivity() {
    /** Wohin das Tippen auf einen Push führt; bleibt stehen, bis die Person angemeldet ist und der Bildschirm es aufgreift. */
    private var pushTarget by mutableStateOf<PushTarget?>(null)

    private fun targetOf(intent: Intent?) = PushTarget.from(intent?.getStringExtra("typ"), intent?.getStringExtra("id"))

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        targetOf(intent)?.let { pushTarget = it }
    }

    /** Das Widget lädt, solange die App im Vordergrund ist: Im Energiesparmodus hat nur sie dann Netz. */
    override fun onStart() {
        super.onStart()
        WidgetProvider.refreshAll(applicationContext)
    }

    /** Anmelden und Abmelden betreffen das Widget; es braucht dafür kein Netz. */
    override fun onStop() {
        super.onStop()
        WidgetProvider.refreshAll(applicationContext)
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
        WidgetPreviews.publish(applicationContext)
        // Nach Drehen oder Prozess-Ende gilt der Push nicht noch einmal: Das Intent ist dann dasselbe wie beim ersten Start.
        if (savedInstanceState == null) pushTarget = targetOf(intent)
        val client = ApiClient.create(applicationContext, CampCache.create(applicationContext))
        val lock = AppLock(applicationContext)
        val prompter = BiometricPrompter(this, lock)
        setContent { CeviTheme { Root(client, lock, prompter, pushTarget) { pushTarget = null } } }
    }
}

/** Je nach Anmeldestand: Start, Fehler, Anmelden, Passwort wählen, Willkommen oder die App selbst. */
@Composable
private fun Root(client: ApiClient, lock: AppLock, prompter: BiometricPrompter, pushTarget: PushTarget?, onPushTargetHandled: () -> Unit) {
    val widgetContext = LocalContext.current.applicationContext
    val authApi = remember(client) { AuthApi(client, PlayIntegrity.forBuild(widgetContext)) }
    // Abmelden und Sitzungsende: Auch Stand und Wahl der Widgets und geteilte Dateien im Cache verschwinden.
    val auth = viewModel { AuthViewModel(authApi, client.unauthorized, client.forbidden, { client.forgetSession(); forgetWidgets(widgetContext); clearSharedFiles(widgetContext) }, lock, client::hasSession) }
    val state by auth.state.collectAsState()
    val form by auth.form.collectAsState()
    val passwordSaved by auth.passwordSaved.collectAsState()
    val imageForm by auth.imageForm.collectAsState()
    val pending by client.pending.collectAsState()
    val context = LocalContext.current
    val lockEnabled by auth.lockEnabled.collectAsState()
    var stoppedAt by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = SystemClock.elapsedRealtime() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (shouldRelock(if (stoppedAt == 0L) null else SystemClock.elapsedRealtime() - stoppedAt)) auth.relock()
        stoppedAt = 0L
    }
    val lockAvailable = remember { lock.isAvailable() }
    val onLockChange: (Boolean) -> Unit = { on ->
        if (on) {
            prompter.enable { if (it == LockOutcome.Success) auth.lockTurnedOn() else if (it == LockOutcome.Failed) auth.unlockFailed() }
        } else {
            auth.turnLockOff()
        }
    }
    // Ist das Entsperren an, zeigen weder Bildschirmfotos noch «Zuletzt benutzt» etwas von der App.
    SideEffect {
        val window = (context as? Activity)?.window
        if (lockEnabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    // Datenschutz, Support und «Konto löschen lassen» zeigt die App selbst, auch vor der Anmeldung (die Seiten sind öffentlich).
    val info = viewModel { InfoViewModel(InfoApi(client)) }
    val infoStack by info.stack.collectAsState()
    // Ein anderer Anmeldestand schliesst die Seite: Sie gehörte zum Bildschirm davor.
    LaunchedEffect(state::class) { info.close() }
    val motion = rememberMotionEnabled()
    Surface(Modifier.fillMaxSize()) {
        // Über dem Start gibt es keinen zweiten Ladekreis.
        BusyOverlay(busy = pending > 0 && state !is AuthState.Loading) {
            AnimatedContent(
                state,
                transitionSpec = { loginTransition(initialState, motion) },
                contentKey = { it::class },
                label = "anmeldung",
            ) { current ->
                when (current) {
                    AuthState.Loading -> StartScreen()
                    AuthState.Locked -> LockedScreen(
                        form,
                        onUnlock = {
                            prompter.unlock {
                                when (it) {
                                    LockOutcome.Success -> auth.unlock()
                                    LockOutcome.Invalidated -> auth.lockInvalidated()
                                    LockOutcome.Failed -> auth.unlockFailed()
                                    LockOutcome.Declined -> Unit
                                }
                            }
                        },
                        onUsePassword = auth::signOut,
                    )
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
                    AuthState.MustChangePassword -> PasswordScreen(
                        first = true, form, onSave = auth::changePassword, startPasswordKnown = auth.startPasswordKnown.collectAsState().value,
                    )
                    is AuthState.Welcome -> if (infoStack.isNotEmpty()) {
                        InfoHost(info, stringResource(R.string.back_to_welcome))
                    } else {
                        WelcomeScreen(
                            name = current.profile.name,
                            passwordSaved = passwordSaved,
                            form = form,
                            onReadPrivacy = { info.open(InfoPage.PRIVACY) },
                            onAccept = auth::acceptPrivacy,
                            lockAvailable = lockAvailable,
                            lockEnabled = lockEnabled,
                            onLockChange = onLockChange,
                        )
                    }
                    is AuthState.SignedIn -> CompositionLocalProvider(LocalViewModelStoreOwner provides current.scope) {
                        // Die Bilder der Sitzung liegen im Speicher der Sitzung und verschwinden mit ihr.
                        val images = viewModel { ProfileImages(ProfileImageApi(client)) }
                        val stageLogos by viewModel { StageLogos(ProgramsApi(client)) }.paths.collectAsState()
                        val staleSince by client.staleSince.collectAsState()
                        CompositionLocalProvider(LocalProfileImages provides images, LocalStageLogos provides stageLogos, LocalStaleSince provides staleSince) {
                            MainContent(
                                ProgramsApi(client), ChatApi(client), PushApi(client), CampApi(client), TasksApi(client), MeetingsApi(client), CommsApi(client), PlanningApi(client), LiveApi(client), ArchiveApi(client), CheckApi(client), TermineApi(client), current.profile,
                                passwordSaved, form, imageForm, auth,
                                pushTarget, onPushTargetHandled,
                                info = info,
                                lockAvailable = lockAvailable,
                                lockEnabled = lockEnabled,
                                onLockChange = onLockChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Der Bildschirm, den `MainContent` zeigt; `depth` bestimmt die Richtung des Übergangs (tiefer: von rechts herein). */
private sealed interface Screen {
    val depth: Int

    data object Home : Screen { override val depth = 0 }
    data object Phone : Screen { override val depth = 1 }
    data object Notifications : Screen { override val depth = 1 }
    data object Password : Screen { override val depth = 1 }
    data class CropImage(val uri: Uri) : Screen { override val depth = 1 }
    data class Info(val page: InfoPage) : Screen { override val depth = 1 }
    data class Program(val programId: String) : Screen { override val depth = 1 }
    data class Chat(val chatId: String) : Screen { override val depth = 1 }
    data object Queue : Screen { override val depth = 1 }
    data class Task(val taskId: String) : Screen { override val depth = 1 }
    data object Ressorts : Screen { override val depth = 1 }
    data object Meetings : Screen { override val depth = 1 }
    data class Agenda(val meetingId: String) : Screen { override val depth = 2 }
    data class Minutes(val meetingId: String) : Screen { override val depth = 3 }
    data class MeetingMode(val meetingId: String) : Screen { override val depth = 3 }
    data object Mentions : Screen { override val depth = 1 }
    data class Plan(val area: PlanArea) : Screen { override val depth = 1 }
    data class Live(val area: LiveArea) : Screen { override val depth = if (area == LiveArea.DAY) 2 else 1 }
    data object Inbox : Screen { override val depth = 1 }
    data object LagerNotifications : Screen { override val depth = 1 }
    data class PostEditor(val postId: String?) : Screen { override val depth = 2 }
    data object DirectConflict : Screen { override val depth = 4 }
    data class RessortPage(val ressortId: String) : Screen { override val depth = 2 }
    data class Conflict(val changeId: String) : Screen { override val depth = 2 }
    data class Registrations(val programId: String) : Screen { override val depth = 2 }
    data class Event(val eventId: String) : Screen { override val depth = 2 }
    data object Proposal : Screen { override val depth = 1 }
    data object Rooms : Screen { override val depth = 1 }
    data object RoomRequest : Screen { override val depth = 2 }
}

@Composable
private fun MainContent(
    programsApi: ProgramsApi,
    chatApi: ChatApi,
    pushApi: PushApi,
    campApi: CampApi,
    tasksApi: TasksApi,
    meetingsApi: MeetingsApi,
    commsApi: CommsApi,
    planningApi: PlanningApi,
    liveApi: LiveApi,
    archiveApi: ArchiveApi,
    checkApi: CheckApi,
    termineApi: TermineApi,
    account: Profile,
    passwordSaved: Boolean,
    form: FormState,
    imageForm: FormState,
    auth: AuthViewModel,
    pushTarget: PushTarget?,
    onPushTargetHandled: () -> Unit,
    info: InfoViewModel,
    lockAvailable: Boolean,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val appContext = context.applicationContext
    val programs = viewModel { ProgramsViewModel(programsApi) { WidgetProvider.refreshAll(appContext) } }
    val chat = viewModel { ChatViewModel(chatApi) }
    val push = viewModel { PushViewModel(pushApi, account) }
    val camp = viewModel { CampViewModel(campApi) }
    val tasks = viewModel { TasksViewModel(tasksApi, camp, TaskPerson(account.id, account.name, account.ceviName), AndroidTaskTexts(appContext)) }
    val meetings = viewModel { MeetingsViewModel(meetingsApi, camp, AndroidCommsTexts(appContext)) }
    val comms = viewModel { CommsViewModel(commsApi, camp, AndroidCommsTexts(appContext)) }
    val planning = viewModel { PlanningViewModel(planningApi, camp, AndroidCommsTexts(appContext), account.id) }
    val live = viewModel { LiveViewModel(liveApi, camp, AndroidCommsTexts(appContext), account.id) }
    val archive = viewModel { ArchiveViewModel(archiveApi, meetingsApi, tasksApi, camp, AndroidCommsTexts(appContext)) }
    val check = viewModel { CheckViewModel(checkApi, camp) { AndroidCommsTexts(appContext).fieldLabels() + ("erledigt" to appContext.getString(R.string.field_done)) } }
    val calendarLink = viewModel { CalendarLinkViewModel(termineApi) }
    val proposals = viewModel { ProposalsViewModel(termineApi) }
    val rooms = viewModel { RoomsViewModel(termineApi) }
    val eventForm = viewModel { EventViewModel(termineApi) }
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(Destination.PROGRAMS) }
    // Bereich Lager: `campArea` schaltet das Dock um, `campId` ist das offene Lager (ohne eins zeigt Start die Lagerwahl).
    var campArea by rememberSaveable { mutableStateOf(false) }
    var campId by rememberSaveable { mutableStateOf<String?>(null) }
    var modeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var conflictId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedTask by rememberSaveable { mutableStateOf<String?>(null) }
    var ressortsOpen by rememberSaveable { mutableStateOf(false) }
    var openedRessort by rememberSaveable { mutableStateOf<String?>(null) }
    // Sitzungen und Kommunikation: je ein Bildschirm, der offene Höck und der bearbeitete Beitrag.
    var meetingsOpen by rememberSaveable { mutableStateOf(false) }
    var openedMeeting by rememberSaveable { mutableStateOf<String?>(null) }
    var minutesFor by rememberSaveable { mutableStateOf<String?>(null) }
    var modeFor by rememberSaveable { mutableStateOf<String?>(null) }
    var mentionsOpen by rememberSaveable { mutableStateOf(false) }
    // Einteilung: ein Bildschirm je Bereich (Teilnehmende, Zimmer, Fahrten, Gruppen, Schichtplan, Diensttausch, Abzählen).
    var planArea by rememberSaveable { mutableStateOf<PlanArea?>(null) }
    // Im Lager: ein Bildschirm je Bereich (Heute, Tagesseite, Anwesenheit, Einkauf, Packliste, Notfall, Infos).
    var liveArea by rememberSaveable { mutableStateOf<LiveArea?>(null) }
    var liveDay by rememberSaveable { mutableStateOf<String?>(null) }
    var inboxOpen by rememberSaveable { mutableStateOf(false) }
    var lagerNotificationsOpen by rememberSaveable { mutableStateOf(false) }
    var postEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editedPost by rememberSaveable { mutableStateOf<String?>(null) }
    var openedProgram by rememberSaveable { mutableStateOf<String?>(null) }
    // Termine: die Anmeldung zu einem Anlass (ID des Anlasses) und «Termin vorschlagen».
    var openedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var proposalOpen by rememberSaveable { mutableStateOf(false) }
    // Räume: «Meine Raumanfragen» (K3) und davon aus «Raum anfragen» (K2).
    var roomsOpen by rememberSaveable { mutableStateOf(false) }
    var roomRequestOpen by rememberSaveable { mutableStateOf(false) }
    var openedChat by rememberSaveable { mutableStateOf<String?>(null) }
    var phoneOpen by rememberSaveable { mutableStateOf(false) }
    var viewingRegistrations by rememberSaveable { mutableStateOf(false) }
    // Das gewählte Profilfoto, solange die Person den Ausschnitt wählt.
    var croppingImage by rememberSaveable { mutableStateOf<Uri?>(null) }

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
    // Widget-Ziele im Lager: «Heute», «Meine Aufgaben» (Ansicht «Meine») und «Notfall».
    var pendingArea by remember { mutableStateOf<WidgetArea?>(null) }
    val applyCampArea: (WidgetArea) -> Unit = { area ->
        when (area) {
            WidgetArea.CAMP_TODAY -> liveArea = LiveArea.TODAY
            WidgetArea.CAMP_EMERGENCY -> liveArea = LiveArea.EMERGENCY
            WidgetArea.CAMP_TASKS -> { destination = Destination.TASKS; tasks.setView(TaskView.MINE) }
            else -> Unit
        }
    }
    // Das Tippen auf einen Push öffnet den Chat oder das Programm und schliesst, was davor offen war.
    LaunchedEffect(pushTarget) {
        val target = pushTarget ?: return@LaunchedEffect
        notificationsOpen = false; phoneOpen = false; viewingRegistrations = false; openedEvent = null; proposalOpen = false; roomsOpen = false; roomRequestOpen = false
        info.close()
        when (target) {
            is PushTarget.Proposal -> { campArea = false; destination = Destination.PROFILE; openedChat = null; openedProgram = null; proposalOpen = true }
            is PushTarget.RoomDecision -> { campArea = false; destination = Destination.PROFILE; openedChat = null; openedProgram = null; roomsOpen = true }
            is PushTarget.Chat -> { destination = Destination.CHAT; openedProgram = null; openedChat = target.id }
            is PushTarget.Program -> { campArea = false; destination = Destination.PROGRAMS; openedChat = null; openedProgram = target.id }
            // Widgets: die Liste der Programme, die Chatliste oder eine Lager-Seite (das Lager kennt das Widget, sonst zeigt die Lagerwahl).
            is PushTarget.Area -> {
                openedChat = null; openedProgram = null
                when (target.area) {
                    WidgetArea.PROGRAMS -> { campArea = false; destination = Destination.PROGRAMS }
                    WidgetArea.CHAT -> destination = Destination.CHAT
                    else -> {
                        campArea = true; queueOpen = false; conflictId = null
                        target.campId?.let { campId = it }
                        ressortsOpen = false; openedRessort = null; openedTask = null
                        meetingsOpen = false; openedMeeting = null; minutesFor = null; modeFor = null
                        mentionsOpen = false; inboxOpen = false; lagerNotificationsOpen = false; postEditorOpen = false; editedPost = null
                        planArea = null; liveArea = null; liveDay = null
                        destination = Destination.START
                        // Wechselt das Lager, setzt der Effekt für `campId` alles zurück: Dann gilt die Seite erst danach.
                        if (target.campId != null && target.campId != campId) pendingArea = target.area else applyCampArea(target.area)
                    }
                }
            }
            // Das Lager des Objekts löst der Server auf; die Module öffnen später das Objekt selbst (Schnitte A1 bis A5).
            is PushTarget.Camp -> {
                openedChat = null; openedProgram = null
                camp.resolvePush(target.type, target.id) { found ->
                    campArea = true; campId = found.campId; queueOpen = false; conflictId = null
                    ressortsOpen = false; openedRessort = null
                    meetingsOpen = false; openedMeeting = null; minutesFor = null; modeFor = null
                    mentionsOpen = false; inboxOpen = false; lagerNotificationsOpen = false; postEditorOpen = false; editedPost = null
                    planArea = null; liveArea = null; liveDay = null
                    destination = Destination.START
                    openedTask = null
                    // Das Ziel baut die App aus `typ` und `id`; es gilt auch beim Kaltstart (der Push kommt dann hier an).
                    when (val goal = campTargetFor(target.type, found.objectType, found.objectId)) {
                        is CampTarget.Task -> { destination = Destination.TASKS; openedTask = goal.id }
                        CampTarget.Mentions -> mentionsOpen = true
                        CampTarget.Pinboard -> destination = Destination.PINBOARD
                        CampTarget.Inbox -> inboxOpen = true
                        CampTarget.Notifications -> lagerNotificationsOpen = true
                        CampTarget.Swaps -> planArea = PlanArea.SWAPS
                        CampTarget.Attendance -> liveArea = LiveArea.ATTENDANCE
                        CampTarget.Start -> Unit
                    }
                }
            }
        }
        onPushTargetHandled()
    }
    LaunchedEffect(openedTask) { openedTask?.let(tasks::openTask) ?: tasks.closeTask() }
    // Der Kalendereintrag eines Höcks, den der Server erzeugt hat, geht über die Systemauswahl weiter.
    val calendarFile by meetings.calendar.collectAsState()
    LaunchedEffect(calendarFile) {
        calendarFile?.let { bytes ->
            shareCalendarFile(context, bytes)
            meetings.calendarShared()
        }
    }
    // Der Aushang, den der Server als PDF erzeugt hat, geht über die Systemauswahl weiter.
    val sharedPdf by archive.pdf.collectAsState()
    LaunchedEffect(sharedPdf) {
        sharedPdf?.let { pdf ->
            sharePdf(context, pdf.bytes, pdf.name)
            archive.pdfShared()
        }
    }
    LaunchedEffect(openedRessort) { openedRessort?.let(tasks::openRessort) ?: tasks.closeRessort() }
    // Auch nach Drehen oder Prozess-Ende lädt das Offene neu: Daten bleiben nur im Arbeitsspeicher.
    LaunchedEffect(openedProgram) { openedProgram?.let(programs::loadDetail) }
    LaunchedEffect(openedEvent) { openedEvent?.let(eventForm::load) }
    LaunchedEffect(notificationsOpen) { if (notificationsOpen) comms.loadSettings() }
    LaunchedEffect(proposalOpen) {
        if (proposalOpen) {
            proposals.reloadOptions()
            proposals.loadMine()
        }
    }
    // Die Räume laden beim Öffnen neu: Daten bleiben nur im Arbeitsspeicher.
    LaunchedEffect(roomsOpen) { if (roomsOpen) rooms.reloadMine() }
    LaunchedEffect(roomRequestOpen) { if (roomRequestOpen) rooms.reloadRooms() }
    // Im Profil: der Kalenderlink und, ob die Person Termine vorschlagen darf.
    LaunchedEffect(destination) {
        if (destination == Destination.PROFILE) {
            calendarLink.load()
            proposals.loadOptions()
        }
    }
    LaunchedEffect(viewingRegistrations, openedProgram) {
        val id = openedProgram
        if (viewingRegistrations && id != null) programs.loadRegistrations(id)
    }
    LaunchedEffect(openedChat) {
        val id = openedChat
        if (id == null) chat.closeChat() else chat.openChat(id)
    }
    // Ein Push zum offenen Chat zeigt nichts (siehe PushService).
    DisposableEffect(openedChat) {
        OpenChat.id = openedChat
        onDispose { OpenChat.id = null }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Das offene Lager lädt sein Detail und bleibt im Vordergrund aktuell (Ereignisstrom, Feed, Warteschlange).
    LaunchedEffect(campId) {
        val id = campId
        openedTask = null; ressortsOpen = false; openedRessort = null
        meetingsOpen = false; openedMeeting = null; minutesFor = null; modeFor = null
        mentionsOpen = false; inboxOpen = false; lagerNotificationsOpen = false; postEditorOpen = false; editedPost = null
        planArea = null; liveArea = null; liveDay = null
        if (id == null) {
            camp.close()
            tasks.close()
            meetings.close()
            comms.close()
            planning.close()
            live.close()
            archive.close()
            check.close()
        } else {
            camp.open(id)
            tasks.open(id)
            meetings.open(id)
            comms.open(id)
            planning.open(id)
            live.open(id)
            archive.open(id)
            check.open(id)
            pendingArea?.let { applyCampArea(it); pendingArea = null }
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { camp.follow(id) }
        }
    }
    // Die Lagerliste und die Zähler lesen still neu, wenn der Bereich wechselt.
    LaunchedEffect(campArea) { camp.refreshCamps() }
    // Die Chatliste liest einmal neu, wenn der Reiter wechselt oder ein Chat zugeht: So stimmt der Zähler im Dock. Im Takt
    // fragt sie nur, solange sie sichtbar ist.
    LaunchedEffect(destination, openedChat, phoneOpen) {
        // Der Punkt an der Pille veraltet sonst auf den anderen Reitern: einmal still neu bei jedem Wechsel, ohne eigenen Takt.
        launch { camp.refreshNews() }
        if (openedChat == null && !phoneOpen) {
            chat.loadList(silent = true)
            if (destination == Destination.CHAT) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        delay(LIST_POLL_INTERVAL_MS)
                        chat.loadList(silent = true)
                        camp.refreshNews()
                    }
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
        val chatUnread = (chat.list.collectAsState().value as? ApiResult.Ok)?.value?.chats?.sumOf { it.unread } ?: 0
        val tabState = rememberSaveableStateHolder()
        val cropping = croppingImage
        val camps = (camp.camps.collectAsState().value as? ApiResult.Ok)?.value.orEmpty()
        val campUnread by camp.unread.collectAsState()
        val campNews by camp.news.collectAsState()
        val queueState = campId?.let { camp.queueFor(it).state.collectAsState().value } ?: QueueState()
        val online by camp.online.collectAsState()
        val lostAccess by camp.lostAccess.collectAsState()
        val campDetail = camp.detail.collectAsState().value.let { if (lostAccess != null && lostAccess == campId) ApiResult.Failure(ApiError.FORBIDDEN) else it }
        val conflict = conflictId?.let { id -> queueState.changes.firstOrNull { it.clientOpId == id && it.state == ChangeState.CONFLICT } }
        val openedCamp = (campDetail as? ApiResult.Ok)?.value?.takeIf { it.id == campId }
        val campPermissions = openedCamp?.permissions() ?: NO_PERMISSIONS
        val rights = TaskRights.of(campPermissions)
        val meetingRights = MeetingRights.of(campPermissions)
        val planRights = PlanRights.of(campPermissions)
        val liveRights = LiveRights.of(campPermissions)
        val archiveRights = ArchiveRights.of(campPermissions)
        val checkRights = CheckRights.of(campPermissions)
        SideEffect { check.rights = checkRights; tasks.rights = rights; comms.rights = meetingRights; meetings.rights = meetingRights; planning.rights = planRights; live.rights = liveRights; archive.rights = archiveRights }
        // Wer Mitteilungen schreibt, wählt auch Ressorts und Gruppen als Empfänger; ohne Recht bleiben die Listen leer.
        val recipientRessorts = (tasks.ressorts.collectAsState().value as? ApiResult.Ok)?.value
        val recipientGroups = (planning.groups.collectAsState().value as? ApiResult.Ok)?.value?.groups
        LaunchedEffect(campId, meetingRights.editPinboard, liveArea) {
            if (campId != null && rights.readRessorts && (meetingRights.editPinboard || liveArea == LiveArea.TIMELINE)) tasks.loadRessortsIfNeeded()
        }
        LaunchedEffect(campId, meetingRights.editPinboard) {
            if (campId != null && meetingRights.editPinboard) planning.loadGroups(silent = true)
        }
        LaunchedEffect(recipientRessorts, recipientGroups) {
            comms.setChoices(RecipientChoices(recipientRessorts.orEmpty().map { it.id to it.name }, recipientGroups.orEmpty().map { it.id to it.name }))
        }
        // Wer die Konfliktlösung offen hat: ein Konflikt aus den Sitzungen oder der Kommunikation (ohne Warteschlange).
        val meetingConflict by meetings.writer.conflict.collectAsState()
        val commsConflict by comms.writer.conflict.collectAsState()
        val planConflict by planning.writer.conflict.collectAsState()
        val liveConflict by live.writer.conflict.collectAsState()
        val archiveConflict by archive.writer.conflict.collectAsState()
        val planPeople by planning.people.collectAsState()
        val checkConflict by check.writer.conflict.collectAsState()
        val directConflict = meetingConflict ?: commsConflict ?: planConflict ?: liveConflict ?: archiveConflict ?: checkConflict
        val taskList = (tasks.tasks.collectAsState().value as? ApiResult.Ok)?.value.orEmpty()
        val memberList by meetings.members.collectAsState()
        // Namen für die Marken @{person:…} in Titeln und Texten: bekannte Personen aus Aufgaben und Mitgliedern.
        val personNames = remember(taskList, memberList) {
            buildMap {
                TaskRules.people(taskList, tasks.self).forEach { put(it.id, it.display) }
                memberList.forEach { put(it.id, it.display) }
            }
        }
        // Die Mitglieder des Lagers für die Personenauswahl; ohne das Recht `einteilung.lesen` fragt die App nicht.
        val mayListMembers = campPermissions.may("einteilung.lesen")
        LaunchedEffect(openedCamp?.id, mayListMembers) { if (mayListMembers) planning.loadMembers() }
        val campMembers by planning.members.collectAsState()
        LaunchedEffect(campMembers) { tasks.setMembers(campMembers?.map { TaskPerson(it.id, it.name) }) }
        // Konten, die andere Module schon kennen, ergänzen die Auswahl in der Einteilung (es gibt keine Route mit allen Mitgliedern).
        LaunchedEffect(taskList, memberList) {
            planning.setExtraPeople(
                TaskRules.people(taskList, tasks.self).map { RessortPerson(it.id, it.name, it.ceviName) } +
                    memberList.map { RessortPerson(it.id, it.name, it.ceviName) },
            )
        }
        val modeSwitch = if (camps.isEmpty()) null else {
            val programs = stringResource(R.string.mode_area_programs)
            val lager = stringResource(R.string.mode_area_camp)
            if (campArea) {
                ModeSwitch(openedCamp?.title ?: stringResource(R.string.camps_title), programs, news = campNews?.programs?.fresh == true || chatUnread > 0) { modeMenuOpen = true }
            } else {
                ModeSwitch(stringResource(R.string.department), lager, news = campNews?.anyCamp ?: campUnread.values.any { it > 0 }) { modeMenuOpen = true }
            }
        }
        // Ein Ziel aus einer Benachrichtigung, «Neu für dich» oder einem Push: derselbe Weg überall.
        val openTarget: (CampTarget) -> Unit = { goal ->
            when (goal) {
                is CampTarget.Task -> openedTask = goal.id
                CampTarget.Mentions -> mentionsOpen = true
                CampTarget.Pinboard -> { lagerNotificationsOpen = false; destination = Destination.PINBOARD }
                CampTarget.Inbox -> inboxOpen = true
                CampTarget.Notifications -> lagerNotificationsOpen = true
                CampTarget.Swaps -> { lagerNotificationsOpen = false; planArea = PlanArea.SWAPS }
                CampTarget.Attendance -> { lagerNotificationsOpen = false; liveArea = LiveArea.ATTENDANCE }
                CampTarget.Start -> { lagerNotificationsOpen = false; destination = Destination.START }
            }
        }
        val infoPage = info.stack.collectAsState().value.lastOrNull()
        val target = when {
            cropping != null -> Screen.CropImage(cropping)
            infoPage != null -> Screen.Info(infoPage)
            campArea && campId != null && directConflict != null -> Screen.DirectConflict
            conflict != null -> Screen.Conflict(conflict.clientOpId)
            queueOpen && campArea && campId != null -> Screen.Queue
            campArea && campId != null && planArea != null -> Screen.Plan(planArea!!)
            campArea && campId != null && openedRessort != null -> Screen.RessortPage(openedRessort!!)
            campArea && campId != null && openedTask != null -> Screen.Task(openedTask!!)
            campArea && campId != null && liveArea != null -> Screen.Live(liveArea!!)
            campArea && campId != null && ressortsOpen -> Screen.Ressorts
            campArea && campId != null && postEditorOpen -> Screen.PostEditor(editedPost)
            campArea && campId != null && modeFor != null -> Screen.MeetingMode(modeFor!!)
            campArea && campId != null && minutesFor != null -> Screen.Minutes(minutesFor!!)
            campArea && campId != null && openedMeeting != null -> Screen.Agenda(openedMeeting!!)
            campArea && campId != null && meetingsOpen -> Screen.Meetings
            campArea && campId != null && mentionsOpen -> Screen.Mentions
            campArea && campId != null && inboxOpen -> Screen.Inbox
            campArea && campId != null && lagerNotificationsOpen -> Screen.LagerNotifications
            openedEvent != null -> Screen.Event(openedEvent!!)
            roomRequestOpen -> Screen.RoomRequest
            roomsOpen -> Screen.Rooms
            proposalOpen -> Screen.Proposal
            programId != null && viewingRegistrations -> Screen.Registrations(programId)
            programId != null -> Screen.Program(programId)
            chatId != null -> Screen.Chat(chatId)
            phoneOpen -> Screen.Phone
            notificationsOpen -> Screen.Notifications
            changingPassword -> Screen.Password
            else -> Screen.Home
        }
        // Eine offene Liste braucht keinen Push zu ihrer eigenen Art (Pinnwand, Erwähnungen, Eingang).
        val openPushTypes = when {
            target == Screen.Mentions -> setOf("lager_erwaehnung")
            target == Screen.Inbox -> setOf("lager_eingang")
            // Nur die Leitung sieht Entscheide im Feed; wer nur die eigenen Anfragen sieht, bekommt den Push weiterhin.
            target == Screen.Plan(PlanArea.SWAPS) && planRights.decideSwap -> setOf("lager_einteilung")
            target == Screen.Live(LiveArea.ATTENDANCE) -> setOf("lager_anwesenheit")
            target == Screen.Home && campArea && campId != null && destination == Destination.PINBOARD -> setOf("lager_pinnwand")
            else -> emptySet()
        }
        DisposableEffect(openPushTypes) {
            OpenCampObject.types = openPushTypes
            onDispose { OpenCampObject.types = emptySet() }
        }
        val motion = rememberMotionEnabled()
        AnimatedContent(
            target,
            transitionSpec = { screenTransition(initialState.depth, targetState.depth, motion) },
            label = "bildschirm",
        ) { screen ->
            when (screen) {
                is Screen.Info -> InfoHost(info, stringResource(R.string.back_to_profile))
                is Screen.Event -> {
                    val leave = { openedEvent = null }
                    BackHandler(onBack = leave)
                    EventScreen(
                        form = eventForm.form.collectAsState().value,
                        states = eventForm.states.collectAsState().value,
                        onAnswer = eventForm::setAnswer,
                        onText = eventForm::setText,
                        onSubmit = eventForm::submit,
                        onUnregister = eventForm::unregister,
                        onDismiss = eventForm::dismiss,
                        onBack = leave,
                        onRetry = { eventForm.load(screen.eventId) },
                    )
                }
                Screen.RoomRequest -> {
                    val leave = { roomRequestOpen = false; rooms.dismissNotice() }
                    BackHandler(onBack = leave)
                    RoomRequestScreen(
                        rooms = rooms.rooms.collectAsState().value,
                        draft = rooms.draft.collectAsState().value,
                        send = rooms.send.collectAsState().value,
                        onEdit = rooms::edit,
                        onSubmit = rooms::submit,
                        onUndo = rooms::undo,
                        onDismiss = rooms::dismissNotice,
                        onBack = leave,
                        onRetry = rooms::reloadRooms,
                    )
                }
                Screen.Rooms -> {
                    val leave = { roomsOpen = false; rooms.dismissNotice() }
                    BackHandler(onBack = leave)
                    RoomListScreen(
                        mine = rooms.mine.collectAsState().value,
                        list = rooms.list.collectAsState().value,
                        onAsk = { rooms.dismissNotice(); roomRequestOpen = true },
                        onCancel = { rooms.cancel(it.id, confirmed = it.status == "bestaetigt") },
                        onDismiss = rooms::dismissNotice,
                        onBack = leave,
                        onRetry = rooms::reloadMine,
                    )
                }
                Screen.Proposal -> {
                    val leave = { proposalOpen = false; proposals.dismissNotice() }
                    BackHandler(onBack = leave)
                    ProposalScreen(
                        options = proposals.options.collectAsState().value,
                        draft = proposals.draft.collectAsState().value,
                        send = proposals.send.collectAsState().value,
                        mine = proposals.mine.collectAsState().value,
                        onEdit = proposals::edit,
                        onSubmit = proposals::submit,
                        onUndo = proposals::undo,
                        onWithdraw = proposals::withdraw,
                        onDismiss = proposals::dismissNotice,
                        onBack = leave,
                        onRetry = proposals::reloadOptions,
                    )
                }
                is Screen.Registrations -> {
                    val programId = screen.programId
                    BackHandler { viewingRegistrations = false }
                    MainScaffold(Destination.PROGRAMS, onDestination = { destination = it; openedProgram = null; viewingRegistrations = false }, unread = chatUnread) {
                        RegistrationsScreen(
                            subtitle = assignedProgram?.title,
                            state = programs.registrations.collectAsState().value,
                            onBack = { viewingRegistrations = false },
                            onRetry = { programs.loadRegistrations(programId) },
                        )
                    }
                }
                is Screen.Program -> {
                    val programId = screen.programId
                    BackHandler { openedProgram = null }
                    LaunchedEffect(programId) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            while (true) {
                                delay(DETAIL_POLL_INTERVAL_MS)
                                programs.refreshDetail()
                            }
                        }
                    }
                    MainScaffold(Destination.PROGRAMS, onDestination = { destination = it; openedProgram = null }, unread = chatUnread) {
                        ProgramDetailScreen(
                            detail,
                            programs.rsvp.collectAsState().value,
                            canSeeRegistrations = assignedProgram?.let { RegistrationsRules.maySee(it, programProfile) } == true,
                            contacts = programs.contacts.collectAsState().value,
                            account = account,
                            onChild = { childId, attending -> programs.rsvpChild(programId, childId, attending) },
                            onLeader = { attending -> programs.rsvpLeader(programId, attending) },
                            onRegistrations = { viewingRegistrations = true },
                            onEvent = { openedEvent = it },
                            onBack = { openedProgram = null },
                            onRetry = { programs.loadDetail(programId) },
                        )
                    }
                }
                is Screen.Chat -> {
                    val chatId = screen.chatId
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
                            loadingOlder = chat.loadingOlder.collectAsState().value,
                        send = chat.send.collectAsState().value,
                        action = chat.action.collectAsState().value,
                        draft = remember(chatId) { chat.draft(chatId) },
                        onDraft = { chat.setDraft(chatId, it) },
                        onSend = chat::send,
                        onOlder = chat::loadOlder,
                        onDirectChat = { ageGroupId, parentId -> chat.openDirectChat(ageGroupId, parentId) { openedChat = it } },
                            onBack = { openedChat = null },
                        onRetry = { chat.openChat(chatId) },
                    )
                }
                Screen.Phone -> {
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
                Screen.DirectConflict -> {
                    val pending = directConflict ?: return@AnimatedContent
                    // Ein Konflikt ohne Warteschlange verschwindet nie still: Es gibt keinen Weg zurück, nur die zwei Entscheide.
                    BackHandler { }
                    ConflictScreen(pending.change, onKeepMine = pending.keepMine, onTakeCurrent = pending.takeCurrent, onBack = null)
                }
                Screen.Meetings -> {
                    val leave = { meetingsOpen = false }
                    BackHandler(onBack = leave)
                    MeetingListScreen(
                        meetings, openedCamp, meetingRights, queueState, online,
                        onOpenQueue = { queueOpen = true },
                        onOpen = { openedMeeting = it },
                        onBack = leave,
                    )
                }
                is Screen.Agenda -> {
                    val leave = { openedMeeting = null; meetings.closeMeeting() }
                    BackHandler(onBack = leave)
                    AgendaScreen(
                        meetings, screen.meetingId, openedCamp, meetingRights, queueState, online, taskList, personNames,
                        onOpenQueue = { queueOpen = true },
                        onStartMode = { modeFor = screen.meetingId },
                        onOpenMinutes = { minutesFor = screen.meetingId },
                        onShareCalendar = { meetings.loadCalendar(screen.meetingId) },
                        onBack = leave,
                    )
                }
                is Screen.Minutes -> {
                    val leave = { minutesFor = null }
                    BackHandler(onBack = leave)
                    MinutesScreen(
                        meetings, screen.meetingId, openedCamp, meetingRights, queueState, online, personNames,
                        onOpenQueue = { queueOpen = true },
                        onShareCalendar = { meetings.loadCalendar(screen.meetingId) },
                        onBack = leave,
                    )
                }
                is Screen.MeetingMode -> {
                    val leave = { modeFor = null; meetings.closeMode() }
                    BackHandler(onBack = leave)
                    MeetingModeScreen(
                        meetings, screen.meetingId, openedCamp, meetingRights, queueState, online, personNames,
                        onOpenQueue = { queueOpen = true },
                        onOpenRessort = { openedRessort = it },
                        onOpenTask = { openedTask = it },
                        onEnd = leave,
                    )
                }
                is Screen.Live -> {
                    val leave = if (screen.area == LiveArea.DAY) ({ liveArea = LiveArea.TODAY; liveDay = null; live.closeDay() }) else ({ liveArea = null; live.dismissNotice(); archive.dismissNotice(); archive.clearSearch(); check.dismissNotice() })
                    BackHandler(onBack = leave)
                    LiveScreen(
                        screen.area, liveDay,
                        LiveFrame(live, openedCamp, liveRights, queueState, online, { queueOpen = true }, planPeople, tasks.self, leave),
                        LiveNav(
                            onOpenArea = { liveArea = it },
                            onOpenDay = { liveDay = it; liveArea = LiveArea.DAY },
                            onOpenTask = { openedTask = it },
                            onOpenShifts = { liveArea = null; planArea = PlanArea.SHIFTS },
                            quietRessorts = if (rights.readRessorts) recipientRessorts.orEmpty().filter { it.quiet }.map { it.name } else emptyList(),
                            onOpenRessorts = if (rights.readRessorts) ({ liveArea = null; ressortsOpen = true }) else null,
                            onOpenSearchHit = { hit ->
                                archive.clearSearch()
                                when (hit) {
                                    is SearchDestination.Task -> openedTask = hit.id
                                    is SearchDestination.Live -> liveArea = hit.area
                                    is SearchDestination.Plan -> { liveArea = null; planArea = hit.area }
                                    SearchDestination.Meetings -> { liveArea = null; meetingsOpen = true }
                                    SearchDestination.Pinboard -> { liveArea = null; destination = Destination.PINBOARD }
                                    SearchDestination.Inbox -> { liveArea = null; inboxOpen = true }
                                    SearchDestination.Ressorts -> { liveArea = null; ressortsOpen = true }
                                }
                            },
                        ),
                        tasks, taskList, archive, archiveRights, check, checkRights,
                    )
                }
                is Screen.Plan -> {
                    val leave = { planArea = null; planning.dismissNotice() }
                    BackHandler(onBack = leave)
                    PlanScreen(
                        screen.area, planning, openedCamp, planRights, queueState, online,
                        onOpenQueue = { queueOpen = true },
                        onOpenArea = { planArea = it },
                        onBack = leave,
                    )
                }
                Screen.Mentions -> {
                    val leave = { mentionsOpen = false; meetings.dismissNotice() }
                    BackHandler(onBack = leave)
                    MentionsScreen(
                        meetings, openedCamp, meetingRights, queueState, online, personNames,
                        onOpenQueue = { queueOpen = true },
                        onOpenTask = { openedTask = it },
                        onBack = leave,
                    )
                }
                Screen.Inbox -> {
                    val leave = { inboxOpen = false; comms.dismissNotice() }
                    BackHandler(onBack = leave)
                    LaunchedEffect(Unit) {
                        if (meetingRights.answerInbox) tasks.loadRessortsIfNeeded()
                        if (meetingRights.editMeetings) meetings.loadMeetings(silent = true)
                    }
                    val meetingList = (meetings.meetings.collectAsState().value as? ApiResult.Ok)?.value.orEmpty()
                    InboxScreen(
                        comms, openedCamp, meetingRights, queueState, online,
                        ressorts = (tasks.ressorts.collectAsState().value as? ApiResult.Ok)?.value.orEmpty(),
                        upcoming = MeetingRules.upcoming(meetingList, todayInCamp()),
                        onOpenQueue = { queueOpen = true },
                        onBack = leave,
                    )
                }
                Screen.LagerNotifications -> {
                    val leave = { lagerNotificationsOpen = false }
                    BackHandler(onBack = leave)
                    LagerNotificationsScreen(
                        comms, openedCamp, queueState, online, taskList,
                        onOpenQueue = { queueOpen = true },
                        onOpenTarget = openTarget,
                        onBack = leave,
                    )
                }
                is Screen.PostEditor -> {
                    val leave = { postEditorOpen = false; editedPost = null }
                    BackHandler(onBack = leave)
                    val existing = screen.postId?.let { id -> (comms.posts.collectAsState().value as? ApiResult.Ok)?.value?.firstOrNull { it.id == id } }
                    if (screen.postId != null && existing == null) {
                        SideEffect { leave() }
                    } else {
                        PostEditorScreen(comms, existing, openedCamp, queueState, online, onOpenQueue = { queueOpen = true }, onBack = leave)
                    }
                }
                is Screen.Conflict -> {
                    val change = conflict ?: return@AnimatedContent
                    val leave = { conflictId = null }
                    BackHandler(onBack = leave)
                    val queue = camp.queueFor(campId ?: return@AnimatedContent)
                    ConflictScreen(
                        change,
                        onKeepMine = { queue.keepMine(change.clientOpId); conflictId = null; campId?.let(camp::sendNow) },
                        onTakeCurrent = { queue.discard(change.clientOpId); conflictId = null },
                        onBack = leave,
                    )
                }
                Screen.Queue -> {
                    val id = campId ?: return@AnimatedContent
                    val queue = camp.queueFor(id)
                    val leave = { queue.clearSent(); queueOpen = false }
                    BackHandler(onBack = leave)
                    QueueScreen(
                        queueState, online,
                        onSendNow = { camp.sendNow(id) },
                        onDecide = { conflictId = it.clientOpId },
                        onDiscard = { queue.discard(it.clientOpId) },
                        onBack = leave,
                    )
                }
                is Screen.Task -> {
                    val leave = { openedTask = null }
                    BackHandler(onBack = leave)
                    TaskDetailScreen(
                        tasks, screen.taskId, openedCamp, rights, queueState, online,
                        onBack = leave,
                        onOpenQueue = { openedTask = null; queueOpen = true },
                        onOpenRessort = { openedRessort = it },
                    )
                }
                Screen.Ressorts -> {
                    val leave = { ressortsOpen = false }
                    BackHandler(onBack = leave)
                    RessortListScreen(tasks, onBack = leave, onOpen = { openedRessort = it })
                }
                is Screen.RessortPage -> {
                    val leave = { openedRessort = null }
                    BackHandler(onBack = leave)
                    LaunchedEffect(screen.ressortId) { if (meetingRights.readInbox) comms.loadInbox(silent = true) }
                    val feedback = if (meetingRights.readInbox) {
                        (comms.inbox.collectAsState().value as? ApiResult.Ok)?.value?.filter { it.ressortId == screen.ressortId }
                    } else {
                        null
                    }
                    RessortScreen(tasks, screen.ressortId, rights, onBack = leave, onOpenTask = { openedTask = it; openedRessort = null }, feedback = feedback)
                }
                Screen.Notifications -> {
                    val leave = { notificationsOpen = false; push.clearError() }
                    BackHandler(onBack = leave)
                    NotificationsScreen(
                        switches = push.switches.collectAsState().value,
                        form = push.form.collectAsState().value,
                        onChat = push::setChat,
                        onPrograms = push::setPrograms,
                        onRegistrations = push::setRegistrations,
                        onBack = leave,
                        lager = comms.settings.collectAsState().value,
                        onLager = comms::setSetting,
                        onLagerRetry = comms::loadSettings,
                        lagerBanner = { WriteErrorBanner(comms.writer) },
                    )
                }
                is Screen.CropImage -> {
                    BackHandler { croppingImage = null }
                    CropImageScreen(
                        screen.uri,
                        onCancel = { croppingImage = null },
                        onConfirm = { crop ->
                            croppingImage = null
                            auth.uploadImage { withContext(Dispatchers.Default) { ProfileImageEncoder.encode(resolver, screen.uri, crop) } }
                        },
                    )
                }
                Screen.Password -> {
                    val leave = { changingPassword = false; auth.clearError() }
                    BackHandler(onBack = leave)
                    PasswordScreen(first = false, form, onSave = auth::changePassword, onBack = leave)
                }
                Screen.Home -> CompositionLocalProvider(LocalModeSwitch provides modeSwitch) { MainScaffold(
                    // Ohne gewähltes Lager (Lagerwahl) steht im Dock nichts als aktiv.
                    active = if (campArea && campId == null && destination == Destination.START) null else destination,
                    onDestination = { if (it == Destination.MORE) moreOpen = true else if ((it != Destination.START && it != Destination.TASKS && it != Destination.PINBOARD) || campId != null) destination = it },
                    unread = chatUnread,
                    destinations = if (campArea) Destination.CAMP else Destination.PROGRAM,
                ) {
                    AnimatedContent(
                        destination,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { tabTransition(initialState, targetState, motion) },
                        label = "bereich",
                    ) { tab ->
                        // Der Reiter behält seine Scroll-Position, auch wenn er zwischendurch aus der Komposition fällt.
                        tabState.SaveableStateProvider(tab.name) {
                        if (tab == Destination.PROGRAMS) {
                            ProgramListScreen(
                                programs.list.collectAsState().value,
                                programs.refreshing.collectAsState().value,
                                onOpen = { openedProgram = it },
                                onRetry = programs::loadList,
                                onRefresh = programs::refreshList,
                            )
                        } else if (tab == Destination.PROFILE) {
                            ProfileScreen(
                                account, passwordSaved, form, imageForm,
                                onPickImage = { uri -> croppingImage = uri },
                                onRemoveImage = auth::removeImage,
                                onChangePassword = { changingPassword = true },
                                onOpenNotifications = { notificationsOpen = true },
                                onOpenPrivacy = { info.open(InfoPage.PRIVACY) },
                                onOpenSupport = { info.open(InfoPage.SUPPORT) },
                                onOpenDeleteAccount = { info.open(InfoPage.DELETE_ACCOUNT) },
                                onSignOut = auth::signOut,
                                onLeave = { auth.clearPasswordSaved(); auth.clearImageError(); calendarLink.forget() },
                                termine = {
                                    GroupsSection(account)
                                    CalendarLinkSection(
                                        status = calendarLink.status.collectAsState().value,
                                        hasFresh = calendarLink.fresh.collectAsState().value != null,
                                        busy = calendarLink.busy.collectAsState().value,
                                        url = calendarLink::url,
                                        onCreate = calendarLink::create,
                                        onRevoke = calendarLink::revoke,
                                        onReload = calendarLink::reload,
                                        onDismissError = calendarLink::dismissError,
                                    )
                                    if ((proposals.options.collectAsState().value as? ApiResult.Ok)?.value?.mayPropose == true) {
                                        SettingsCard {
                                            SettingsRow(R.drawable.ic_calendar_plus, stringResource(R.string.proposal_row), onClick = { proposalOpen = true }, chevron = true)
                                        }
                                    }
                                    // Räume anfragen darf jede angemeldete Person der Abteilung (Vertrag `frageRaumAn`).
                                    SettingsCard {
                                        SettingsRow(R.drawable.ic_door, stringResource(R.string.room_row), onClick = { roomsOpen = true }, chevron = true)
                                    }
                                },
                                lockAvailable = lockAvailable,
                                lockEnabled = lockEnabled,
                                onLockChange = onLockChange,
                            )
                        } else if (tab == Destination.TASKS && campId != null) {
                            TasksScreen(
                                tasks, openedCamp, rights, queueState, online,
                                onOpenQueue = { queueOpen = true },
                                onOpenTask = { openedTask = it },
                            )
                        } else if (tab == Destination.PINBOARD && campId != null) {
                            PinboardScreen(
                                comms, openedCamp, meetingRights, queueState, online,
                                onOpenQueue = { queueOpen = true },
                                onNewPost = { editedPost = null; postEditorOpen = true },
                                onEditPost = { editedPost = it.id; postEditorOpen = true },
                            )
                        } else if (tab == Destination.START || tab == Destination.TASKS || tab == Destination.PINBOARD) {
                            val id = campId
                            if (id == null) {
                                CampListScreen(
                                    camp.camps.collectAsState().value,
                                    camp.pushMissing.collectAsState().value,
                                    onDismissPushMissing = camp::dismissPushMissing,
                                    onOpen = { campId = it },
                                    onRetry = camp::loadCamps,
                                )
                            } else {
                                CampStartScreen(
                                    campDetail, queueState, online,
                                    onRetry = { camp.open(id) },
                                    onToCamps = { campId = null },
                                    onOpenQueue = { queueOpen = true },
                                    fab = { StartFab(tasks, rights) },
                                    body = { c ->
                                        if (rights.seeLeadershipStart) {
                                            LeadershipStart(
                                                tasks, c, onOpenTask = { openedTask = it }, onOpenTasks = { destination = Destination.TASKS },
                                                onOpenTarget = openTarget, onOpenMeeting = { openedMeeting = it }, onOpenToday = { liveArea = LiveArea.TODAY },
                                            )
                                        } else {
                                            TeamStart(
                                                tasks, c, onOpenTask = { openedTask = it }, onOpenTasks = { destination = Destination.TASKS }, onOpenTarget = openTarget,
                                                live = live, onOpenToday = { liveArea = LiveArea.TODAY }, onOpenShifts = { planArea = PlanArea.SHIFTS }, onOpenPacking = { liveArea = LiveArea.PACKING },
                                            )
                                        }
                                    },
                                )
                            }
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
                } }
            }
        }
        if (tasks.quickOpen.collectAsState().value && rights.edit) QuickCaptureSheet(tasks)
        if (modeMenuOpen) {
            ModeMenu(
                inCamp = if (campArea) campId ?: "" else null,
                camps = camps,
                unread = campUnread,
                onDepartment = { campArea = false; campId = null; destination = Destination.PROGRAMS; modeMenuOpen = false },
                onCamp = { campArea = true; campId = it; destination = Destination.START; modeMenuOpen = false },
                onAllCamps = { campArea = true; campId = null; destination = Destination.START; modeMenuOpen = false },
                onDismiss = { modeMenuOpen = false },
            )
        }
        if (moreOpen) {
            val you = listOfNotNull(
                MoreEntry(R.string.more_profile, R.drawable.ic_profile) { moreOpen = false; campArea = false; campId = null; destination = Destination.PROFILE },
                if (campId != null) MoreEntry(R.string.mentions_title, R.drawable.ic_chat) { moreOpen = false; mentionsOpen = true } else null,
                if (campId != null && (meetingRights.readInbox || meetingRights.readOwnInbox)) MoreEntry(R.string.inbox_title, R.drawable.ic_send) { moreOpen = false; inboxOpen = true } else null,
                if (campId != null) MoreEntry(R.string.notif_title, R.drawable.ic_bell) { moreOpen = false; lagerNotificationsOpen = true } else null,
                if (campId != null) MoreEntry(R.string.queue_title, R.drawable.ic_clock) { moreOpen = false; queueOpen = true } else null,
            )
            val plan = if (campId != null) {
                listOfNotNull(
                    // Jeder Eintrag steht nur, wenn das Lesen erlaubt ist (Rechte aus dem Lagerdetail); fehlt das Detail, fehlen sie alle.
                    if (campPermissions.may("zeitplan.lesen")) MoreEntry(R.string.timeline_title, R.drawable.ic_calendar) { moreOpen = false; liveArea = LiveArea.TIMELINE } else null,
                    if (campPermissions.may("hoecks.lesen_alle", "hoecks.team_lesen")) MoreEntry(R.string.meetings_title, R.drawable.ic_calendar) { moreOpen = false; meetingsOpen = true } else null,
                    if (checkRights.read) MoreEntry(R.string.check_title, R.drawable.ic_shield) { moreOpen = false; liveArea = LiveArea.CHECK } else null,
                    if (campPermissions.may("einteilung.lesen")) MoreEntry(R.string.rooms_title, R.drawable.ic_tent) { moreOpen = false; planArea = PlanArea.ROOMS } else null,
                    if (campPermissions.may("einteilung.lesen")) MoreEntry(R.string.trips_title, R.drawable.ic_location) { moreOpen = false; planArea = PlanArea.TRIPS } else null,
                    if (campPermissions.may("einteilung.lesen")) MoreEntry(R.string.groups_title, R.drawable.ic_group) { moreOpen = false; planArea = PlanArea.GROUPS } else null,
                    if (campPermissions.may("einteilung.lesen")) MoreEntry(R.string.shifts_title, R.drawable.ic_list_check) { moreOpen = false; planArea = PlanArea.SHIFTS } else null,
                    if (campPermissions.may("diensttausch.anfragen", "diensttausch.entscheiden")) MoreEntry(R.string.swaps_title, R.drawable.ic_refresh) { moreOpen = false; planArea = PlanArea.SWAPS } else null,
                    if (campPermissions.may("teilnehmer.lesen")) MoreEntry(R.string.count_title, R.drawable.ic_check) { moreOpen = false; planArea = PlanArea.COUNT } else null,
                    if (campPermissions.may("teilnehmer.lesen")) MoreEntry(R.string.participants_title, R.drawable.ic_profile) { moreOpen = false; planArea = PlanArea.PARTICIPANTS } else null,
                    if (rights.readRessorts) MoreEntry(R.string.more_ressorts, R.drawable.ic_group) { moreOpen = false; ressortsOpen = true } else null,
                )
            } else {
                emptyList()
            }
            val inCamp = if (campId != null) {
                listOfNotNull(
                    MoreEntry(R.string.today_title, R.drawable.ic_home) { moreOpen = false; liveArea = LiveArea.TODAY },
                    if (liveRights.seesAttendance) MoreEntry(R.string.att_title, R.drawable.ic_check) { moreOpen = false; liveArea = LiveArea.ATTENDANCE } else null,
                    if (liveRights.readShopping) MoreEntry(R.string.shop_title, R.drawable.ic_list_check) { moreOpen = false; liveArea = LiveArea.SHOPPING } else null,
                    if (campPermissions.may("packliste.lesen")) MoreEntry(R.string.pack_title, R.drawable.ic_bring_along) { moreOpen = false; liveArea = LiveArea.PACKING } else null,
                    if (campPermissions.may("notfall.lesen")) MoreEntry(R.string.emerg_title, R.drawable.ic_phone) { moreOpen = false; liveArea = LiveArea.EMERGENCY } else null,
                )
            } else {
                emptyList()
            }
            val files = if (campId != null) {
                listOfNotNull(
                    if (campPermissions.may("infos.lesen")) MoreEntry(R.string.infos_title, R.drawable.ic_info) { moreOpen = false; liveArea = LiveArea.INFOS } else null,
                    if (campPermissions.may("dokumente.lesen_alle", "dokumente.team_lesen")) MoreEntry(R.string.docs_title, R.drawable.ic_file) { moreOpen = false; liveArea = LiveArea.DOCUMENTS } else null,
                    if (archiveRights.readAddressBook) MoreEntry(R.string.ab_title, R.drawable.ic_book) { moreOpen = false; liveArea = LiveArea.ADDRESSBOOK } else null,
                    MoreEntry(R.string.print_title, R.drawable.ic_send) { moreOpen = false; liveArea = LiveArea.PRINTOUTS },
                    MoreEntry(R.string.search_title, R.drawable.ic_search) { moreOpen = false; liveArea = LiveArea.SEARCH },
                )
            } else {
                emptyList()
            }
            MoreSheet(
                listOfNotNull(
                    if (plan.isEmpty()) null else R.string.more_section_plan to plan,
                    if (inCamp.isEmpty()) null else R.string.more_section_live to inCamp,
                    if (files.isEmpty()) null else R.string.more_section_files to files,
                    R.string.more_section_you to you,
                ),
                onDismiss = { moreOpen = false },
            )
        }
    }
}
