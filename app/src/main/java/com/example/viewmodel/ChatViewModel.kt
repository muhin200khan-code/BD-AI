package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AttachmentKind
import com.example.data.model.ChartSpec
import com.example.data.model.ChatFolder
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ModelTier
import com.example.data.model.PendingAttachment
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.example.data.repository.BdAiFirestoreRepository
import com.example.data.repository.BdAiGenerativeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface ChatInferenceState {
    data object Idle : ChatInferenceState
    data object Thinking : ChatInferenceState
    data class Streaming(val partialText: String) : ChatInferenceState
    data class Error(val message: String) : ChatInferenceState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val firestoreRepo: BdAiFirestoreRepository,
    private val aiRepo: BdAiGenerativeRepository,
    private val currentUserId: String
) : ViewModel() {

    val chatsState: StateFlow<SessionUiState<List<ChatThread>>> =
        firestoreRepo.observeChats(currentUserId)
            .map<List<ChatThread>, SessionUiState<List<ChatThread>>> { SessionUiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Chats stream error", root)
                emit(SessionUiState.Error(root.localizedMessage ?: "Failed to load chats"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = SessionUiState.Loading
            )

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _isTemporaryChat = MutableStateFlow(false)
    val isTemporaryChat: StateFlow<Boolean> = _isTemporaryChat.asStateFlow()

    private val _temporaryMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val temporaryMessages: StateFlow<List<ChatMessage>> = _temporaryMessages.asStateFlow()

    val persistedMessages: StateFlow<List<ChatMessage>> =
        _activeChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                firestoreRepo.observeMessages(currentUserId, chatId)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        Log.w(TAG, "Messages stream error", fsErr ?: error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedModel = MutableStateFlow(ModelTier.FLASH)
    val selectedModel: StateFlow<ModelTier> = _selectedModel.asStateFlow()

    private val _selectedFolder = MutableStateFlow(ChatFolder.GENERAL)
    val selectedFolder: StateFlow<ChatFolder> = _selectedFolder.asStateFlow()

    private val _chartModeEnabled = MutableStateFlow(false)
    val chartModeEnabled: StateFlow<Boolean> = _chartModeEnabled.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _inferenceState = MutableStateFlow<ChatInferenceState>(ChatInferenceState.Idle)
    val inferenceState: StateFlow<ChatInferenceState> = _inferenceState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterFolder = MutableStateFlow<String>("ALL")
    val filterFolder: StateFlow<String> = _filterFolder.asStateFlow()

    private var activeGenerationJob: Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterFolder(folder: String) {
        _filterFolder.value = folder
    }

    fun selectModel(tier: ModelTier) {
        _selectedModel.value = tier
    }

    fun selectFolder(folder: ChatFolder) {
        _selectedFolder.value = folder
    }

    fun toggleChartMode() {
        _chartModeEnabled.value = !_chartModeEnabled.value
    }

    fun setPendingAttachment(attachment: PendingAttachment?) {
        _pendingAttachment.value = attachment
    }

    fun startNewChat(temporary: Boolean = false, folder: ChatFolder = ChatFolder.GENERAL) {
        activeGenerationJob?.cancel()
        _inferenceState.value = ChatInferenceState.Idle
        _activeChatId.value = null
        _isTemporaryChat.value = temporary
        _temporaryMessages.value = emptyList()
        _selectedFolder.value = folder
        _pendingAttachment.value = null
    }

    fun openChat(chat: ChatThread) {
        activeGenerationJob?.cancel()
        _inferenceState.value = ChatInferenceState.Idle
        _isTemporaryChat.value = false
        _temporaryMessages.value = emptyList()
        _activeChatId.value = chat.id
        _selectedModel.value = chat.modelEnum()
        _selectedFolder.value = chat.folderEnum()
        _pendingAttachment.value = null
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _inferenceState.value = ChatInferenceState.Idle
    }

    fun clearError() {
        if (_inferenceState.value is ChatInferenceState.Error) {
            _inferenceState.value = ChatInferenceState.Idle
        }
    }

    fun sendMessage(
        rawPrompt: String,
        profile: UserProfile,
        memories: List<UserMemory>,
        currentHistory: List<ChatMessage>,
        onReplyComplete: (String) -> Unit = {}
    ) {
        val attachment = _pendingAttachment.value
        val promptText = rawPrompt.trim().ifBlank {
            when (attachment?.kind) {
                AttachmentKind.IMAGE -> "Analyze this image in detail."
                AttachmentKind.VIDEO -> "Summarize and analyze what happens in this video."
                AttachmentKind.DOCUMENT -> "Read and analyze this attached file (${attachment.displayName})."
                else -> ""
            }
        }
        if (promptText.isBlank()) return

        val shouldPersist = !_isTemporaryChat.value && profile.saveChatHistory
        val tier = _selectedModel.value
        val folder = _selectedFolder.value
        val isChartMode = _chartModeEnabled.value

        val userMsg = ChatMessage(
            role = "user",
            content = promptText,
            attachmentLabel = attachment?.displayName,
            attachmentType = attachment?.kind?.name
        )

        _pendingAttachment.value = null
        _chartModeEnabled.value = false

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _inferenceState.value = ChatInferenceState.Thinking

            var targetChatId = _activeChatId.value
            if (shouldPersist) {
                if (targetChatId == null) {
                    val titleCandidate = promptText.take(48).ifBlank { "New Chat" }
                    val created = firestoreRepo.createChat(
                        userId = currentUserId,
                        title = titleCandidate,
                        folder = folder,
                        modelMode = tier
                    )
                    targetChatId = created.getOrNull()
                    if (targetChatId == null) {
                        val err = created.exceptionOrNull()?.localizedMessage ?: "Failed to create chat."
                        _inferenceState.value = ChatInferenceState.Error(err)
                        return@launch
                    }
                    _activeChatId.value = targetChatId
                }
                val appendUser = firestoreRepo.appendMessage(
                    userId = currentUserId,
                    chatId = targetChatId,
                    message = userMsg
                )
                if (appendUser.isFailure) {
                    _inferenceState.value = ChatInferenceState.Error(
                        appendUser.exceptionOrNull()?.localizedMessage ?: "Failed to save message."
                    )
                    return@launch
                }
            } else {
                _temporaryMessages.value = _temporaryMessages.value + userMsg
            }

            try {
                if (isChartMode) {
                    val (chartJson, chartSpec) = withTimeout(60_000L) {
                        aiRepo.generateDataChart(promptText)
                    }
                    val modelMsg = ChatMessage(
                        role = "model",
                        content = "${chartSpec.title}\n\n${chartSpec.summary}",
                        attachmentType = AttachmentKind.CHART.name,
                        chartJson = chartJson,
                        modelUsed = tier.apiName
                    )
                    if (shouldPersist && targetChatId != null) {
                        firestoreRepo.appendMessage(currentUserId, targetChatId, modelMsg)
                    } else {
                        _temporaryMessages.value = _temporaryMessages.value + modelMsg
                    }
                    _inferenceState.value = ChatInferenceState.Idle
                    onReplyComplete(modelMsg.content)
                } else {
                    val systemPrompt = aiRepo.buildSystemInstruction(profile, memories)
                    val buffer = StringBuilder()
                    withTimeout(60_000L) {
                        aiRepo.streamChatReply(
                            tier = tier,
                            systemPrompt = systemPrompt,
                            history = currentHistory,
                            userPrompt = promptText,
                            attachment = attachment
                        ).collect { chunk ->
                            buffer.append(chunk)
                            _inferenceState.value = ChatInferenceState.Streaming(buffer.toString())
                        }
                    }

                    val finalText = buffer.toString().trim()
                    if (finalText.isBlank()) {
                        _inferenceState.value = ChatInferenceState.Error("BD AI returned an empty response.")
                        return@launch
                    }

                    val modelMsg = ChatMessage(
                        role = "model",
                        content = finalText,
                        modelUsed = tier.apiName
                    )
                    if (shouldPersist && targetChatId != null) {
                        firestoreRepo.appendMessage(currentUserId, targetChatId, modelMsg)
                    } else {
                        _temporaryMessages.value = _temporaryMessages.value + modelMsg
                    }
                    _inferenceState.value = ChatInferenceState.Idle
                    onReplyComplete(finalText)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI inference timed out", e)
                _inferenceState.value = ChatInferenceState.Error(
                    "Response timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI inference failed", e)
                _inferenceState.value = ChatInferenceState.Error(
                    e.localizedMessage ?: "BD AI request failed."
                )
            }
        }
    }

    fun renameChat(chatId: String, newTitle: String) {
        viewModelScope.launch {
            firestoreRepo.renameChat(currentUserId, chatId, newTitle)
        }
    }

    fun toggleArchive(chat: ChatThread) {
        viewModelScope.launch {
            firestoreRepo.setChatArchived(currentUserId, chat.id, !chat.archived)
            if (!chat.archived && _activeChatId.value == chat.id) {
                startNewChat()
            }
        }
    }

    fun togglePin(chat: ChatThread) {
        viewModelScope.launch {
            firestoreRepo.setChatPinned(currentUserId, chat.id, !chat.pinned)
        }
    }

    fun moveChatToFolder(chatId: String, folder: ChatFolder) {
        viewModelScope.launch {
            firestoreRepo.setChatFolder(currentUserId, chatId, folder)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteChat(currentUserId, chatId)
            if (_activeChatId.value == chatId) {
                startNewChat()
            }
        }
    }

    fun parseChartSpec(chartJson: String?): ChartSpec? {
        if (chartJson.isNullOrBlank()) return null
        return try {
            val json = JSONObject(chartJson)
            val labelsArr = json.getJSONArray("labels")
            val valuesArr = json.getJSONArray("values")
            val headersArr = json.getJSONArray("tableHeaders")
            val rowsArr = json.getJSONArray("tableRows")
            ChartSpec(
                title = json.getString("title"),
                chartType = json.optString("chartType", "BAR"),
                summary = json.optString("summary", ""),
                labels = (0 until labelsArr.length()).map { labelsArr.getString(it) },
                values = (0 until valuesArr.length()).map { valuesArr.getDouble(it) },
                tableHeaders = (0 until headersArr.length()).map { headersArr.getString(it) },
                tableRows = (0 until rowsArr.length()).map { r ->
                    val rowArr = rowsArr.getJSONArray(r)
                    (0 until rowArr.length()).map { c -> rowArr.getString(c) }
                }
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "ChatVM"

        fun provideFactory(
            firestoreRepo: BdAiFirestoreRepository,
            aiRepo: BdAiGenerativeRepository,
            userId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    firestoreRepo = firestoreRepo,
                    aiRepo = aiRepo,
                    currentUserId = userId
                )
            }
        }
    }
}
