package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChatFolder
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.MemoryCategory
import com.example.data.model.ModelTier
import com.example.data.model.ScheduledTask
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class BdAiFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName.ifBlank { "BD AI User" }.take(100),
                    email = email.take(150)
                )
                docRef.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfileFields(
        userId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        emitAll(
            db.collection("users").document(uid).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        emitAll(
            db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    suspend fun createChat(
        userId: String,
        title: String,
        folder: ChatFolder = ChatFolder.GENERAL,
        modelMode: ModelTier = ModelTier.FLASH
    ): Result<String> {
        val basePath = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid).collection("chats").document()
            val cleanTitle = title.trim().ifBlank { "New Chat" }.take(120)
            val payload = mapOf(
                "title" to cleanTitle,
                "folder" to folder.label,
                "modelMode" to modelMode.name,
                "archived" to false,
                "pinned" to false,
                "lastMessagePreview" to "",
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, basePath)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        userId: String,
        chatId: String,
        message: ChatMessage,
        newTitle: String? = null
    ): Result<Unit> {
        val msgPath = "users/$userId/chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(chatId.isNotBlank()) { "Chat ID cannot be blank." }
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()
            msgRef.set(message.toCreateMap()).await()

            val preview = message.content.replace("\n", " ").trim().take(280)
            val chatUpdates = mutableMapOf<String, Any>(
                "lastMessagePreview" to preview,
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newTitle.isNullOrBlank()) {
                chatUpdates["title"] = newTitle.trim().take(120)
            }
            chatRef.update(chatUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgPath)
            Result.failure(e)
        }
    }

    suspend fun renameChat(userId: String, chatId: String, newTitle: String): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val clean = newTitle.trim().ifBlank { "Untitled Chat" }.take(120)
            db.document(path).update(
                mapOf("title" to clean, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setChatArchived(userId: String, chatId: String, archived: Boolean): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).update(
                mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setChatPinned(userId: String, chatId: String, pinned: Boolean): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).update(
                mapOf("pinned" to pinned, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setChatFolder(userId: String, chatId: String, folder: ChatFolder): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).update(
                mapOf("folder" to folder.label, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(userId: String, chatId: String): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val chatRef = db.document(path)
            val msgs = chatRef.collection("messages").get().await()
            if (!msgs.isEmpty) {
                val batch = db.batch()
                msgs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllChats(userId: String): Result<Unit> {
        val path = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val chats = db.collection("users").document(uid).collection("chats").get().await()
            for (chatDoc in chats.documents) {
                deleteChat(uid, chatDoc.id)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeMemories(userId: String): Flow<List<UserMemory>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserMemory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    suspend fun addMemory(
        userId: String,
        content: String,
        category: MemoryCategory
    ): Result<Unit> {
        val path = "users/$userId/memories"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val clean = content.trim().take(500)
            require(clean.isNotEmpty()) { "Memory text cannot be empty." }
            db.collection("users").document(uid).collection("memories").document().set(
                mapOf(
                    "content" to clean,
                    "category" to category.label,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
        val path = "users/$userId/memories/$memoryId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllMemories(userId: String): Result<Unit> {
        val path = "users/$userId/memories"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docs = db.collection("users").document(uid).collection("memories").get().await()
            if (!docs.isEmpty) {
                val batch = db.batch()
                docs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeScheduledTasks(userId: String): Flow<List<ScheduledTask>> = flow {
        val uid = requireUserId()
        check(uid == userId)
        emitAll(
            db.collection("users").document(uid).collection("scheduled_tasks")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ScheduledTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    suspend fun createScheduledTask(
        userId: String,
        title: String,
        prompt: String,
        scheduleLabel: String
    ): Result<Unit> {
        val path = "users/$userId/scheduled_tasks"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val cleanTitle = title.trim().take(120)
            val cleanPrompt = prompt.trim().take(1000)
            val cleanSchedule = scheduleLabel.trim().ifBlank { "Daily 9:00 AM" }.take(80)
            require(cleanTitle.isNotEmpty() && cleanPrompt.isNotEmpty()) {
                "Task title and prompt are required."
            }
            db.collection("users").document(uid).collection("scheduled_tasks").document().set(
                mapOf(
                    "title" to cleanTitle,
                    "prompt" to cleanPrompt,
                    "scheduleLabel" to cleanSchedule,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleScheduledTask(userId: String, taskId: String, active: Boolean): Result<Unit> {
        val path = "users/$userId/scheduled_tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).update(
                mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateScheduledTaskOutput(
        userId: String,
        taskId: String,
        output: String
    ): Result<Unit> {
        val path = "users/$userId/scheduled_tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).update(
                mapOf(
                    "lastOutput" to output.take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteScheduledTask(userId: String, taskId: String): Result<Unit> {
        val path = "users/$userId/scheduled_tasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
