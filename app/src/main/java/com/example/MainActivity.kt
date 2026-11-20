package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.example.data.repository.BdAiFirestoreRepository
import com.example.data.repository.BdAiGenerativeRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.LiveVoiceOverlay
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolsAndTasksScreen
import com.example.ui.theme.BdAiTheme
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.ImageStudioViewModel
import com.example.viewmodel.SessionUiState
import com.example.viewmodel.UserSessionViewModel
import com.example.voice.VoiceController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHAT("Chat", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    IMAGE_STUDIO("Studio", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    TOOLS("Tools", Icons.Rounded.Explore, Icons.Outlined.Explore),
    SETTINGS("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            BdAiRootApp()
        }
    }
}

@Composable
fun BdAiRootApp() {
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
        BdAiTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
            }
        }
    } else {
        key(user.uid) {
            AuthenticatedBdAiShell(
                currentUserId = user.uid,
                displayName = user.displayName.orEmpty(),
                email = user.email.orEmpty()
            )
        }
    }
}

@Composable
private fun AuthenticatedBdAiShell(
    currentUserId: String,
    displayName: String,
    email: String
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val aiRepo = remember { BdAiGenerativeRepository() }
    val firestoreRepo = remember(app) {
        val dbId = app.getString(R.string.firestore_database_id)
        BdAiFirestoreRepository(FirebaseFirestore.getInstance(dbId))
    }

    val sessionViewModel: UserSessionViewModel = viewModel(
        key = "session_$currentUserId",
        factory = UserSessionViewModel.provideFactory(
            firestoreRepo = firestoreRepo,
            aiRepo = aiRepo,
            userId = currentUserId,
            displayName = displayName,
            email = email
        )
    )
    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$currentUserId",
        factory = ChatViewModel.provideFactory(
            firestoreRepo = firestoreRepo,
            aiRepo = aiRepo,
            userId = currentUserId
        )
    )
    val imageStudioViewModel: ImageStudioViewModel = viewModel(
        key = "studio_$currentUserId",
        factory = ImageStudioViewModel.provideFactory(aiRepo)
    )

    val voiceController = remember(app) { VoiceController(app) }
    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    val profileState by sessionViewModel.profileState.collectAsStateWithLifecycle()
    val memoriesState by sessionViewModel.memoriesState.collectAsStateWithLifecycle()
    val feedbackMsg by sessionViewModel.feedbackMessage.collectAsStateWithLifecycle()
    val inferenceState by chatViewModel.inferenceState.collectAsStateWithLifecycle()
    val persistedMsgs by chatViewModel.persistedMessages.collectAsStateWithLifecycle()
    val tempMsgs by chatViewModel.temporaryMessages.collectAsStateWithLifecycle()
    val isTemp by chatViewModel.isTemporaryChat.collectAsStateWithLifecycle()

    val profile = (profileState as? SessionUiState.Success)?.data ?: UserProfile(
        userId = currentUserId,
        displayName = displayName,
        email = email
    )
    val memories: List<UserMemory> = (memoriesState as? SessionUiState.Success)?.data ?: emptyList()

    var currentTab by remember { mutableStateOf(MainTab.CHAT) }
    var showLiveVoice by remember { mutableStateOf(false) }
    var prefillChatPrompt by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMsg) {
        if (!feedbackMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedbackMsg.orEmpty())
            sessionViewModel.clearFeedback()
        }
    }

    if (currentTab != MainTab.CHAT && !showLiveVoice) {
        BackHandler { currentTab = MainTab.CHAT }
    }

    BdAiTheme(
        themePreference = profile.themeEnum(),
        accentOption = profile.accentEnum()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 720.dp

            if (showLiveVoice) {
                val activeHistory = if (isTemp || !profile.saveChatHistory) tempMsgs else persistedMsgs
                LiveVoiceOverlay(
                    voiceController = voiceController,
                    inferenceState = inferenceState,
                    preferredLanguage = profile.preferredLanguage,
                    voiceSpeed = profile.voiceSpeed.toFloat(),
                    onVoicePromptCaptured = { spokenText ->
                        chatViewModel.sendMessage(
                            rawPrompt = spokenText,
                            profile = profile,
                            memories = memories,
                            currentHistory = activeHistory,
                            onReplyComplete = { reply ->
                                voiceController.speak(
                                    text = reply,
                                    speed = profile.voiceSpeed.toFloat(),
                                    languageTag = profile.preferredLanguage
                                )
                            }
                        )
                    },
                    onClose = { showLiveVoice = false }
                )
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (!isWide) {
                            NavigationBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                MainTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) }
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
                        if (isWide) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.statusBarsPadding()
                            ) {
                                MainTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) }
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .let { if (currentTab != MainTab.CHAT) it.statusBarsPadding() else it }
                        ) {
                            when (currentTab) {
                                MainTab.CHAT -> ChatWorkspaceScreen(
                                    profile = profile,
                                    memories = memories,
                                    chatViewModel = chatViewModel,
                                    voiceController = voiceController,
                                    prefillPrompt = prefillChatPrompt,
                                    onPrefillConsumed = { prefillChatPrompt = null },
                                    onOpenLiveVoice = { showLiveVoice = true }
                                )
                                MainTab.IMAGE_STUDIO -> ImageStudioScreen(
                                    viewModel = imageStudioViewModel
                                )
                                MainTab.TOOLS -> ToolsAndTasksScreen(
                                    sessionViewModel = sessionViewModel,
                                    onLaunchPromptInChat = { prompt, folder ->
                                        chatViewModel.startNewChat(temporary = false, folder = folder)
                                        prefillChatPrompt = prompt
                                        currentTab = MainTab.CHAT
                                    }
                                )
                                MainTab.SETTINGS -> SettingsScreen(
                                    profile = profile,
                                    sessionViewModel = sessionViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
