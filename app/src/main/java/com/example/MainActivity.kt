package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOutUser
import com.example.core.AndroidActionExecutor
import com.example.core.DeviceTelemetryHelper
import com.example.core.LocalPreferencesStore
import com.example.core.VoiceCommandBus
import com.example.core.VoiceEngine
import com.example.core.configureAppCheck
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.GeminiAiRepository
import com.example.ui.screens.AuraHomeScreen
import com.example.ui.screens.AutomationsScreen
import com.example.ui.screens.CircleVisionScreen
import com.example.ui.screens.CommsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.SyncSettingsSheet
import com.example.ui.theme.AuraAssistTheme
import com.example.ui.viewmodel.AuraViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val incomingSharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure App Check with launch intent before Compose UI or Firebase calls
        configureAppCheck(this, intent)

        extractSharedText(intent)
        enableEdgeToEdge()

        setContent {
            AuraAssistTheme(darkTheme = true) {
                AuraAppRoot(
                    sharedTextFlow = incomingSharedText,
                    onSharedTextConsumed = { incomingSharedText.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)
    }

    private fun extractSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
                incomingSharedText.value = it
            }
        }
    }
}

private data class NavTabItem(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
private fun AuraAppRoot(
    sharedTextFlow: MutableStateFlow<String?>,
    onSharedTextConsumed: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            AuthenticatedAuraShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Aura User",
                userEmail = user.email ?: "Signed in with Google",
                sharedTextFlow = sharedTextFlow,
                onSharedTextConsumed = onSharedTextConsumed
            )
        }
    }
}

@Composable
private fun AuthenticatedAuraShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
    sharedTextFlow: MutableStateFlow<String?>,
    onSharedTextConsumed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AuraViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AuraViewModel(
                    appContext = app.applicationContext,
                    currentUserId = currentUserId,
                    displayName = userDisplayName,
                    firestoreRepository = FirestoreRepository(db),
                    aiRepository = GeminiAiRepository(),
                    localPrefsStore = LocalPreferencesStore(app),
                    telemetryHelper = DeviceTelemetryHelper(app),
                    actionExecutor = AndroidActionExecutor(app),
                    voiceEngine = VoiceEngine(app)
                )
            }
        }
    )

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()
    val sharedText by sharedTextFlow.collectAsStateWithLifecycle()
    val localPrefs by viewModel.localPrefs.collectAsStateWithLifecycle()
    val lockWakeRunning by viewModel.lockScreenServiceRunning.collectAsStateWithLifecycle()

    // Allow Aura to appear and turn screen on over the lock screen whenever Lock-Screen Wake is active
    LaunchedEffect(localPrefs.lockScreenWakeEnabled, lockWakeRunning) {
        val allowLockOverlay = localPrefs.lockScreenWakeEnabled || lockWakeRunning
        val activity = context as? Activity ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(allowLockOverlay)
            activity.setTurnScreenOn(allowLockOverlay)
        } else {
            @Suppress("DEPRECATION")
            if (allowLockOverlay) {
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            } else {
                activity.window.clearFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        VoiceCommandBus.wakePulse.collect {
            selectedTab = 0
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    LaunchedEffect(requestedTab) {
        val target = requestedTab
        if (target != null) {
            selectedTab = target.coerceIn(0, 4)
            viewModel.consumeRequestedTab()
        }
    }

    LaunchedEffect(sharedText) {
        val incoming = sharedText
        if (!incoming.isNullOrBlank()) {
            viewModel.ingestSharedText(incoming)
            onSharedTextConsumed()
        }
    }

    // BackHandler returns secondary tabs to Home tab cleanly
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val navItems = remember {
        listOf(
            NavTabItem(R.string.nav_aura, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_tab_aura"),
            NavTabItem(R.string.nav_circle, Icons.Rounded.CenterFocusStrong, Icons.Outlined.CenterFocusStrong, "nav_tab_circle"),
            NavTabItem(R.string.nav_studio, Icons.Rounded.Brush, Icons.Outlined.Brush, "nav_tab_studio"),
            NavTabItem(R.string.nav_comms, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_tab_comms"),
            NavTabItem(R.string.nav_control, Icons.Rounded.Bolt, Icons.Outlined.Bolt, "nav_tab_control")
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.titleRes)
                                    )
                                },
                                label = { Text(stringResource(item.titleRes)) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEachIndexed { index, item ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.titleRes)
                                    )
                                },
                                label = { Text(stringResource(item.titleRes)) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> AuraHomeScreen(
                            viewModel = viewModel,
                            onOpenSettings = { showSettingsSheet = true }
                        )
                        1 -> CircleVisionScreen(viewModel = viewModel)
                        2 -> StudioScreen(viewModel = viewModel)
                        3 -> CommsScreen(viewModel = viewModel)
                        4 -> AutomationsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SyncSettingsSheet(
            viewModel = viewModel,
            userEmail = userEmail,
            onDismiss = { showSettingsSheet = false },
            onSignOut = {
                showSettingsSheet = false
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onBeforeSignOut = { viewModel.prepareForSignOut() }
                )
            }
        )
    }
}
