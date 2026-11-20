package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AttachmentKind
import com.example.data.model.ChatFolder
import com.example.data.model.ChatMessage
import com.example.data.model.ModelTier
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.example.ui.components.MessageActionRow
import com.example.ui.components.RichAiMessageBody
import com.example.ui.components.copyToClipboard
import com.example.ui.components.readPendingAttachment
import com.example.ui.components.shareText
import com.example.viewmodel.ChatInferenceState
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.SessionUiState
import com.example.voice.VoiceController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private data class QuickStarter(val label: String, val folder: ChatFolder, val prompt: String)

private val starterCards = listOf(
    QuickStarter("বাংলায় প্রশ্ন করুন", ChatFolder.GENERAL, "আসসালামু আলাইকুম BD AI! তোমাকে কে বানাইছে এবং তুমি কী কী করতে পারো?"),
    QuickStarter("Write Python / Web Code", ChatFolder.CODING, "Build a clean Python FastAPI endpoint and matching HTML/JS frontend card."),
    QuickStarter("Math & Science Help", ChatFolder.EDUCATION, "Solve and explain step-by-step: find the derivative of f(x) = 3x^2 * sin(x)."),
    QuickStarter("Data & Chart Analysis", ChatFolder.DATA, "Compare renewable energy capacity (GW) across 5 Asian countries with a chart and table.")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatWorkspaceScreen(
    profile: UserProfile,
    memories: List<UserMemory>,
    chatViewModel: ChatViewModel,
    voiceController: VoiceController,
    prefillPrompt: String?,
    onPrefillConsumed: () -> Unit,
    onOpenLiveVoice: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val chatsState by chatViewModel.chatsState.collectAsStateWithLifecycle()
    val activeChatId by chatViewModel.activeChatId.collectAsStateWithLifecycle()
    val isTemporary by chatViewModel.isTemporaryChat.collectAsStateWithLifecycle()
    val tempMessages by chatViewModel.temporaryMessages.collectAsStateWithLifecycle()
    val persistedMessages by chatViewModel.persistedMessages.collectAsStateWithLifecycle()
    val selectedModel by chatViewModel.selectedModel.collectAsStateWithLifecycle()
    val chartMode by chatViewModel.chartModeEnabled.collectAsStateWithLifecycle()
    val pendingAttachment by chatViewModel.pendingAttachment.collectAsStateWithLifecycle()
    val inferenceState by chatViewModel.inferenceState.collectAsStateWithLifecycle()
    val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()
    val filterFolder by chatViewModel.filterFolder.collectAsStateWithLifecycle()

    val isListening by voiceController.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceController.isSpeaking.collectAsStateWithLifecycle()
    val speechError by voiceController.speechError.collectAsStateWithLifecycle()

    val messages = if (isTemporary || !profile.saveChatHistory) tempMessages else persistedMessages
    val chats = (chatsState as? SessionUiState.Success)?.data ?: emptyList()

    var inputText by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    LaunchedEffect(prefillPrompt) {
        if (!prefillPrompt.isNullOrBlank()) {
            inputText = prefillPrompt
            onPrefillConsumed()
        }
    }

    LaunchedEffect(speechError) {
        if (!speechError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(speechError.orEmpty())
            voiceController.clearError()
        }
    }

    LaunchedEffect(messages.size, inferenceState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readPendingAttachment(context, uri, AttachmentKind.IMAGE)
                    .onSuccess { chatViewModel.setPendingAttachment(it) }
                    .onFailure { snackbarHostState.showSnackbar(it.localizedMessage ?: "Image error") }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readPendingAttachment(context, uri, AttachmentKind.VIDEO)
                    .onSuccess { chatViewModel.setPendingAttachment(it) }
                    .onFailure { snackbarHostState.showSnackbar(it.localizedMessage ?: "Video error") }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readPendingAttachment(context, uri, AttachmentKind.DOCUMENT)
                    .onSuccess { chatViewModel.setPendingAttachment(it) }
                    .onFailure { snackbarHostState.showSnackbar(it.localizedMessage ?: "File error") }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening(profile.preferredLanguage) { text ->
                inputText = if (inputText.isBlank()) text else "$inputText $text"
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission is needed for voice input.") }
        }
    }

    fun triggerMic() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.startListening(profile.preferredLanguage) { text ->
                inputText = if (inputText.isBlank()) text else "$inputText $text"
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun submitCurrentPrompt(overrideText: String? = null) {
        val toSend = overrideText ?: inputText
        if (toSend.isBlank() && pendingAttachment == null) return
        if (overrideText == null) inputText = ""
        chatViewModel.sendMessage(
            rawPrompt = toSend,
            profile = profile,
            memories = memories,
            currentHistory = messages,
            onReplyComplete = { reply ->
                if (profile.autoSpeakVoice) {
                    voiceController.speak(
                        text = reply,
                        speed = profile.voiceSpeed.toFloat(),
                        languageTag = profile.preferredLanguage
                    )
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawerContent(
                chats = chats,
                activeChatId = activeChatId,
                searchQuery = searchQuery,
                filterFolder = filterFolder,
                onSearchChange = chatViewModel::setSearchQuery,
                onFilterFolderChange = chatViewModel::setFilterFolder,
                onNewChat = { temp ->
                    chatViewModel.startNewChat(temporary = temp)
                    scope.launch { drawerState.close() }
                },
                onSelectChat = { chat ->
                    chatViewModel.openChat(chat)
                    scope.launch { drawerState.close() }
                },
                onRenameChat = { chat, newTitle -> chatViewModel.renameChat(chat.id, newTitle) },
                onToggleArchive = { chat -> chatViewModel.toggleArchive(chat) },
                onTogglePin = { chat -> chatViewModel.togglePin(chat) },
                onMoveFolder = { chat, folder -> chatViewModel.moveChatToFolder(chat.id, folder) },
                onDeleteChat = { chat -> chatViewModel.deleteChat(chat.id) }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("open_chat_drawer_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open chat history")
                        }
                    },
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { showModelMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isTemporary) "Temp Chat" else selectedModel.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Select AI model")
                            }
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                ModelTier.entries.forEach { tier ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(tier.displayName, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    tier.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            chatViewModel.selectModel(tier)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { chatViewModel.startNewChat(temporary = !isTemporary) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = "Toggle Temporary Chat",
                                tint = if (isTemporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val transcript = messages.joinToString("\n\n") { m ->
                                        val speaker = if (m.role == "user") "You" else "BD AI"
                                        "$speaker:\n${m.content}"
                                    }
                                    shareText(context, "BD AI Conversation", transcript)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share conversation")
                            }
                        }
                        IconButton(
                            onClick = onOpenLiveVoice,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("open_live_voice_button")
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = "Live Voice Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { chatViewModel.startNewChat(temporary = false) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Rounded.AddComment, contentDescription = "New Chat")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (messages.isEmpty() && inferenceState is ChatInferenceState.Idle) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_bdai_hero),
                                contentDescription = "BD AI Workspace Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(MaterialTheme.shapes.large)
                            )
                            Text(
                                text = if (isTemporary) "Temporary Incognito Chat" else "How can BD AI help today?",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Ask in বাংলা, English, or any language • Analyze PDFs, images, videos & charts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                starterCards.forEach { card ->
                                    AssistChip(
                                        onClick = {
                                            chatViewModel.selectFolder(card.folder)
                                            if (card.folder == ChatFolder.DATA) {
                                                chatViewModel.toggleChartMode()
                                            }
                                            submitCurrentPrompt(card.prompt)
                                        },
                                        label = { Text(card.label, style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 780.dp)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (profile.compactChat) 8.dp else 14.dp)
                    ) {
                        items(messages, key = { it.id.ifBlank { "${it.role}_${it.hashCode()}" } }) { msg ->
                            MessageBubbleItem(
                                message = msg,
                                compact = profile.compactChat,
                                showTimestamp = profile.showTimestamps,
                                chartSpec = chatViewModel.parseChartSpec(msg.chartJson),
                                isSpeaking = isSpeaking,
                                onCopy = {
                                    copyToClipboard(context, "BD AI", msg.content)
                                    scope.launch { snackbarHostState.showSnackbar("Copied") }
                                },
                                onSpeakToggle = {
                                    if (isSpeaking) voiceController.stopSpeaking()
                                    else voiceController.speak(
                                        msg.content,
                                        profile.voiceSpeed.toFloat(),
                                        profile.preferredLanguage
                                    )
                                },
                                onShare = { shareText(context, "BD AI Reply", msg.content) }
                            )
                        }

                        when (val state = inferenceState) {
                            is ChatInferenceState.Thinking -> {
                                item(key = "thinking_row") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("BD AI is thinking...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            is ChatInferenceState.Streaming -> {
                                item(key = "streaming_row") {
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            RichAiMessageBody(
                                                text = state.partialText,
                                                chartSpec = null,
                                                onCopied = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                            )
                                        }
                                    }
                                }
                            }
                            is ChatInferenceState.Error -> {
                                item(key = "error_row") {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                            ChatInferenceState.Idle -> Unit
                        }
                    }
                }

                // Composer Card
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .widthIn(max = 780.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (pendingAttachment != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val bmp = pendingAttachment?.previewBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Attached image preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                                    )
                                } else {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = null)
                                }
                                Text(
                                    text = pendingAttachment?.displayName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { chatViewModel.setPendingAttachment(null) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove attachment")
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showAttachmentSheet = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("attach_media_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Attach photo, video, or document")
                            }

                            FilterChip(
                                selected = chartMode,
                                onClick = { chatViewModel.toggleChartMode() },
                                label = { Text("Chart", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.BarChart, null, modifier = Modifier.size(16.dp))
                                }
                            )

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        if (chartMode) "Describe data to chart..."
                                        else "Message BD AI in any language..."
                                    )
                                },
                                maxLines = 4,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_prompt_input"),
                                shape = MaterialTheme.shapes.large
                            )

                            IconButton(
                                onClick = {
                                    if (isListening) voiceController.stopListening() else triggerMic()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_voice_mic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Voice dictation",
                                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val isGenerating = inferenceState is ChatInferenceState.Thinking ||
                                inferenceState is ChatInferenceState.Streaming
                            FilledIconButton(
                                onClick = {
                                    if (isGenerating) chatViewModel.stopGeneration()
                                    else submitCurrentPrompt()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("send_message_button"),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = if (isGenerating) "Stop generating" else "Send message"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Attach to BD AI", style = MaterialTheme.typography.titleMedium)
                AttachmentOptionRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Photo (Image Analysis)",
                    subtitle = "Analyze, extract text, or solve math from photos"
                ) {
                    showAttachmentSheet = false
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                AttachmentOptionRow(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "Video (Video Understanding)",
                    subtitle = "Analyze short videos under 18MB"
                ) {
                    showAttachmentSheet = false
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
                AttachmentOptionRow(
                    icon = Icons.Rounded.Description,
                    title = "PDF, Spreadsheet, or Code File",
                    subtitle = "Read PDFs, CSVs, TXT, HTML, Python, Java, C++"
                ) {
                    showAttachmentSheet = false
                    documentPicker.launch(
                        arrayOf(
                            "application/pdf",
                            "text/*",
                            "text/csv",
                            "application/json",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    compact: Boolean,
    showTimestamp: Boolean,
    chartSpec: com.example.data.model.ChartSpec?,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onSpeakToggle: () -> Unit,
    onShare: () -> Unit
) {
    val isUser = message.role == "user"
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = if (isUser) 2.dp else 1.dp,
            modifier = Modifier
                .widthIn(max = if (isUser) 520.dp else 720.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compact) 12.dp else 16.dp,
                    vertical = if (compact) 9.dp else 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!message.attachmentLabel.isNullOrBlank()) {
                    Text(
                        text = "Attached: ${message.attachmentLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    RichAiMessageBody(
                        text = message.content,
                        chartSpec = chartSpec,
                        onCopied = { onCopy() }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            if (showTimestamp && message.createdAt != null) {
                Text(
                    text = timeFormat.format(message.createdAt.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isUser) {
                MessageActionRow(
                    isSpeaking = isSpeaking,
                    onCopy = onCopy,
                    onSpeakToggle = onSpeakToggle,
                    onShare = onShare
                )
            }
        }
    }
}
