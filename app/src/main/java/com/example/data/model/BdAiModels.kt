package com.example.data.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PersonalityStyle(val label: String, val instruction: String) {
    BALANCED("Balanced", "Be clear, thoughtful, accurate, and naturally helpful."),
    FRIENDLY("Friendly & Warm", "Use an encouraging, warm, conversational tone with approachable explanations."),
    CONCISE("Concise & Direct", "Keep answers crisp, bulleted, and to the point without unnecessary filler."),
    TUTOR("Education Tutor", "Teach step-by-step like an expert tutor, checking understanding and giving clear examples."),
    CREATIVE("Creative Writer", "Use vivid imagery, imaginative storytelling, and expressive prose."),
    DEVELOPER("Senior Engineer", "Focus on clean architecture, idiomatic code, edge cases, and practical debugging.")
}

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AccentOption(val label: String, val hex: Long) {
    EMERALD("Emerald", 0xFF10B981),
    INDIGO("Indigo", 0xFF6366F1),
    CYAN("Cyan", 0xFF06B6D4),
    ROSE("Rose", 0xFFF43F5E),
    AMBER("Amber", 0xFFF59E0B)
}

enum class ModelTier(val apiName: String, val displayName: String, val subtitle: String) {
    FLASH("gemini-3.8-flash", "BD AI Flash 3.8", "Fast, multimodal, all-around intelligence"),
    REASONING("gemini-4-argon", "BD AI Reasoning 4", "Deep math, STEM, and complex code reasoning"),
    LITE("gemini-3.5-flash-lite", "BD AI Lite 3.5", "Ultra-fast responses for quick tasks")
}

enum class ChatFolder(val label: String) {
    GENERAL("General"),
    CODING("Coding"),
    EDUCATION("Education"),
    CREATIVE("Creative"),
    RESEARCH("Research"),
    DATA("Data"),
    WORK("Work")
}

enum class MemoryCategory(val label: String) {
    PREFERENCE("Preference"),
    FACT("Fact"),
    GOAL("Goal"),
    LANGUAGE("Language"),
    SKILL("Skill")
}

enum class AttachmentKind {
    IMAGE,
    VIDEO,
    DOCUMENT,
    CHART,
    GENERATED_IMAGE
}

inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}

fun safeChatFolder(raw: String?): ChatFolder {
    if (raw.isNullOrBlank()) return ChatFolder.GENERAL
    return ChatFolder.entries.firstOrNull {
        it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
    } ?: ChatFolder.GENERAL
}

fun safeMemoryCategory(raw: String?): MemoryCategory {
    if (raw.isNullOrBlank()) return MemoryCategory.PREFERENCE
    return MemoryCategory.entries.firstOrNull {
        it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
    } ?: MemoryCategory.PREFERENCE
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val personality: String = PersonalityStyle.BALANCED.name,
    val customInstructionsAbout: String = "",
    val customInstructionsStyle: String = "",
    val memoryEnabled: Boolean = true,
    val themeMode: String = ThemePreference.SYSTEM.name,
    val accentColor: String = AccentOption.EMERALD.name,
    val compactChat: Boolean = false,
    val showTimestamps: Boolean = true,
    val preferredLanguage: String = "Auto",
    val voiceSpeed: Double = 1.0,
    val autoSpeakVoice: Boolean = false,
    val saveChatHistory: Boolean = true,
    val safeContentFilter: Boolean = true,
    val notificationsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun personalityEnum(): PersonalityStyle = safeEnum(personality, PersonalityStyle.BALANCED)
    fun themeEnum(): ThemePreference = safeEnum(themeMode, ThemePreference.SYSTEM)
    fun accentEnum(): AccentOption = safeEnum(accentColor, AccentOption.EMERALD)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(100),
        "email" to email.take(150),
        "personality" to personalityEnum().name,
        "customInstructionsAbout" to customInstructionsAbout.take(1000),
        "customInstructionsStyle" to customInstructionsStyle.take(1000),
        "memoryEnabled" to memoryEnabled,
        "themeMode" to themeEnum().name,
        "accentColor" to accentEnum().name,
        "compactChat" to compactChat,
        "showTimestamps" to showTimestamps,
        "preferredLanguage" to preferredLanguage.ifBlank { "Auto" }.take(40),
        "voiceSpeed" to voiceSpeed.coerceIn(0.5, 2.0),
        "autoSpeakVoice" to autoSpeakVoice,
        "saveChatHistory" to saveChatHistory,
        "safeContentFilter" to safeContentFilter,
        "notificationsEnabled" to notificationsEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val title: String = "New Chat",
    val folder: String = ChatFolder.GENERAL.label,
    val modelMode: String = ModelTier.FLASH.name,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun folderEnum(): ChatFolder = safeChatFolder(folder)
    fun modelEnum(): ModelTier = safeEnum(modelMode, ModelTier.FLASH)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val content: String = "",
    val attachmentLabel: String? = null,
    val attachmentType: String? = null,
    val chartJson: String? = null,
    val modelUsed: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "role" to if (role == "model") "model" else "user",
            "content" to content.take(32000),
            "attachmentLabel" to attachmentLabel?.take(200),
            "attachmentType" to attachmentType,
            "chartJson" to chartJson?.take(16000),
            "modelUsed" to modelUsed?.take(60),
            "createdAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class UserMemory(
    @DocumentId val id: String = "",
    val content: String = "",
    val category: String = MemoryCategory.PREFERENCE.label,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScheduledTask(
    @DocumentId val id: String = "",
    val title: String = "",
    val prompt: String = "",
    val scheduleLabel: String = "Daily 9:00 AM",
    val active: Boolean = true,
    val lastOutput: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class PendingAttachment(
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val bytes: ByteArray,
    val previewBitmap: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingAttachment) return false
        return displayName == other.displayName && mimeType == other.mimeType && kind == other.kind && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class ChartSpec(
    val title: String,
    val chartType: String,
    val summary: String,
    val labels: List<String>,
    val values: List<Double>,
    val tableHeaders: List<String>,
    val tableRows: List<List<String>>
)
