package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AccentOption
import com.example.data.model.MemoryCategory
import com.example.data.model.ModelTier
import com.example.data.model.PersonalityStyle
import com.example.data.model.ScheduledTask
import com.example.data.model.ThemePreference
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.example.data.repository.BdAiFirestoreRepository
import com.example.data.repository.BdAiGenerativeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface SessionUiState<out T> {
    data object Loading : SessionUiState<Nothing>
    data class Success<T>(val data: T) : SessionUiState<T>
    data class Error(val message: String) : SessionUiState<Nothing>
}

class UserSessionViewModel(
    private val firestoreRepo: BdAiFirestoreRepository,
    private val aiRepo: BdAiGenerativeRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String
) : ViewModel() {

    val profileState: StateFlow<SessionUiState<UserProfile>> =
        firestoreRepo.observeUserProfile(currentUserId)
            .map<UserProfile?, SessionUiState<UserProfile>> { profile ->
                SessionUiState.Success(
                    profile ?: UserProfile(
                        userId = currentUserId,
                        displayName = initialDisplayName,
                        email = initialEmail
                    )
                )
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Profile stream error", root)
                emit(SessionUiState.Error(root.localizedMessage ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = SessionUiState.Loading
            )

    val memoriesState: StateFlow<SessionUiState<List<UserMemory>>> =
        firestoreRepo.observeMemories(currentUserId)
            .map<List<UserMemory>, SessionUiState<List<UserMemory>>> { SessionUiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                emit(SessionUiState.Error(root.localizedMessage ?: "Failed to load memories"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = SessionUiState.Loading
            )

    val scheduledTasksState: StateFlow<SessionUiState<List<ScheduledTask>>> =
        firestoreRepo.observeScheduledTasks(currentUserId)
            .map<List<ScheduledTask>, SessionUiState<List<ScheduledTask>>> { SessionUiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                emit(SessionUiState.Error(root.localizedMessage ?: "Failed to load scheduled tasks"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = SessionUiState.Loading
            )

    private val _runningTaskId = MutableStateFlow<String?>(null)
    val runningTaskId: StateFlow<String?> = _runningTaskId.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile(currentUserId, initialDisplayName, initialEmail)
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun updateProfileField(updates: Map<String, Any>, successNotice: String? = null) {
        viewModelScope.launch {
            val res = firestoreRepo.updateUserProfileFields(currentUserId, updates)
            res.onSuccess {
                if (successNotice != null) _feedbackMessage.value = successNotice
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not save setting."
            }
        }
    }

    fun setTheme(theme: ThemePreference) = updateProfileField(mapOf("themeMode" to theme.name))
    fun setAccent(accent: AccentOption) = updateProfileField(mapOf("accentColor" to accent.name))
    fun setPersonality(style: PersonalityStyle) = updateProfileField(mapOf("personality" to style.name))

    fun saveCustomInstructions(about: String, style: String) {
        updateProfileField(
            mapOf(
                "customInstructionsAbout" to about.take(1000),
                "customInstructionsStyle" to style.take(1000)
            ),
            successNotice = "Custom instructions saved"
        )
    }

    fun addMemory(content: String, category: MemoryCategory) {
        if (content.isBlank()) return
        viewModelScope.launch {
            firestoreRepo.addMemory(currentUserId, content, category)
                .onSuccess { _feedbackMessage.value = "Memory saved" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to add memory." }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteMemory(currentUserId, memoryId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete memory." }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            firestoreRepo.clearAllMemories(currentUserId)
                .onSuccess { _feedbackMessage.value = "All memories cleared" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to clear memories." }
        }
    }

    fun clearAllChats() {
        viewModelScope.launch {
            firestoreRepo.clearAllChats(currentUserId)
                .onSuccess { _feedbackMessage.value = "Chat history cleared" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to clear chat history." }
        }
    }

    fun createScheduledTask(title: String, prompt: String, scheduleLabel: String) {
        if (title.isBlank() || prompt.isBlank()) {
            _feedbackMessage.value = "Enter a task title and prompt."
            return
        }
        viewModelScope.launch {
            firestoreRepo.createScheduledTask(currentUserId, title, prompt, scheduleLabel)
                .onSuccess { _feedbackMessage.value = "Scheduled task created" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to create task." }
        }
    }

    fun toggleScheduledTask(taskId: String, active: Boolean) {
        viewModelScope.launch {
            firestoreRepo.toggleScheduledTask(currentUserId, taskId, active)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to update task." }
        }
    }

    fun deleteScheduledTask(taskId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteScheduledTask(currentUserId, taskId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete task." }
        }
    }

    fun runScheduledTaskNow(task: ScheduledTask) {
        if (_runningTaskId.value != null) return
        viewModelScope.launch {
            _runningTaskId.value = task.id
            try {
                val reply = withTimeout(60_000L) {
                    aiRepo.generateSimpleText(task.prompt, ModelTier.FLASH)
                }
                if (reply.isNotBlank()) {
                    firestoreRepo.updateScheduledTaskOutput(currentUserId, task.id, reply)
                    _feedbackMessage.value = "Task '${task.title}' completed"
                } else {
                    _feedbackMessage.value = "Model returned an empty response."
                }
            } catch (e: TimeoutCancellationException) {
                _feedbackMessage.value = "Task timed out after 60 seconds."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled task failed", e)
                _feedbackMessage.value = e.localizedMessage ?: "Failed to run task."
            } finally {
                _runningTaskId.value = null
            }
        }
    }

    companion object {
        private const val TAG = "UserSessionVM"

        fun provideFactory(
            firestoreRepo: BdAiFirestoreRepository,
            aiRepo: BdAiGenerativeRepository,
            userId: String,
            displayName: String,
            email: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UserSessionViewModel(
                    firestoreRepo = firestoreRepo,
                    aiRepo = aiRepo,
                    currentUserId = userId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    }
}
