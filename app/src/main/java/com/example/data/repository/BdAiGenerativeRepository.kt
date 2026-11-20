package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.model.AttachmentKind
import com.example.data.model.ChartSpec
import com.example.data.model.ChatMessage
import com.example.data.model.ModelTier
import com.example.data.model.PendingAttachment
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

class BdAiGenerativeRepository {

    private val defaultImageModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val wideImageModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    private val chartSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Chart or data analysis title"),
            "chartType" to Schema.enumeration(
                values = listOf("BAR", "LINE"),
                description = "Visualization type"
            ),
            "summary" to Schema.string(description = "Concise analytical summary and key insights"),
            "labels" to Schema.array(
                items = Schema.string(),
                description = "Category labels for X axis (3 to 8 items)"
            ),
            "values" to Schema.array(
                items = Schema.double(),
                description = "Numeric values matching labels in length"
            ),
            "tableHeaders" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 column headers for data table"
            ),
            "tableRows" to Schema.array(
                items = Schema.array(items = Schema.string()),
                description = "Rows of string cells matching tableHeaders count"
            )
        )
    )

    private val chartModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ModelTier.FLASH.apiName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chartSchema
            },
            systemInstruction = content {
                text(
                    "You are BD AI created by Muhin Hasan Khan (মুহিন হাসান খান). " +
                        "Respond strictly matching the JSON schema for data visualization, statistics, and tables. " +
                        "Ensure labels and values have identical lengths (between 3 and 8 items)."
                )
            }
        )
    }

    fun buildSystemInstruction(
        profile: UserProfile,
        memories: List<UserMemory>
    ): String {
        val sb = StringBuilder()
        sb.appendLine(
            "You are BD AI, an intelligent, versatile, and multilingual AI assistant created by Muhin Hasan Khan (মুহিন হাসান খান)."
        )
        sb.appendLine(
            "CRITICAL IDENTITY RULE: Whenever anyone asks who made you, built you, created you, or who your developer is—in Bengali ('তোমাকে কে বানাইছে?', 'কে তৈরি করেছে?'), English, or any other language—you MUST clearly state that Muhin Hasan Khan created you. In Bengali respond: 'আমাকে তৈরি করেছেন মুহিন হাসান খান।' (or 'আমাকে তৈরি করছে মুহিন হাসান খান।'). In any other language, translate naturally while keeping the creator name Muhin Hasan Khan."
        )
        sb.appendLine(
            "MULTILINGUAL MASTERY: You are fluent and highly skilled in ALL languages worldwide, especially Bengali (বাংলা), English, Arabic, Hindi, Urdu, Spanish, French, German, Japanese, Korean, and Chinese. Always respond in the user's language unless they ask for another language."
        )
        if (profile.preferredLanguage != "Auto" && profile.preferredLanguage.isNotBlank()) {
            sb.appendLine("User's preferred default language: ${profile.preferredLanguage}.")
        }
        val personality = profile.personalityEnum()
        sb.appendLine("Style & Tone (${personality.label}): ${personality.instruction}")

        if (profile.safeContentFilter) {
            sb.appendLine("Safety & Parental Control: Keep all content safe, respectful, and family-friendly.")
        }
        if (profile.customInstructionsAbout.isNotBlank()) {
            sb.appendLine("About the user: ${profile.customInstructionsAbout}")
        }
        if (profile.customInstructionsStyle.isNotBlank()) {
            sb.appendLine("User formatting instructions: ${profile.customInstructionsStyle}")
        }
        if (profile.memoryEnabled && memories.isNotEmpty()) {
            sb.appendLine("Saved User Memories (use naturally when relevant):")
            memories.take(20).forEach { mem ->
                sb.appendLine("- [${mem.category}] ${mem.content}")
            }
        }
        sb.appendLine(
            "Capabilities: You excel at reasoning, math, science, homework help, coding (HTML, CSS, JS, Python, Java, C++, Kotlin), debugging, summarization, document/PDF/spreadsheet/image/video analysis, creative writing, and structured problem solving."
        )
        return sb.toString()
    }

    private fun createChatModel(
        tier: ModelTier,
        systemPrompt: String
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = tier.apiName,
            systemInstruction = content { text(systemPrompt) }
        )
    }

    fun streamChatReply(
        tier: ModelTier,
        systemPrompt: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?
    ): Flow<String> {
        val model = createChatModel(tier, systemPrompt)
        val pastContent: List<Content> = history
            .takeLast(16)
            .filter { it.content.isNotBlank() }
            .map { msg ->
                content(role = if (msg.role == "model") "model" else "user") {
                    text(msg.content)
                }
            }

        val chat = model.startChat(history = pastContent)
        val currentContent = content(role = "user") {
            if (attachment != null) {
                when (attachment.kind) {
                    AttachmentKind.IMAGE -> {
                        val bmp = attachment.previewBitmap
                        if (bmp != null) {
                            image(bmp)
                        } else {
                            inlineData(attachment.bytes, attachment.mimeType)
                        }
                    }
                    AttachmentKind.VIDEO,
                    AttachmentKind.DOCUMENT -> {
                        inlineData(attachment.bytes, attachment.mimeType)
                    }
                    AttachmentKind.CHART,
                    AttachmentKind.GENERATED_IMAGE -> Unit
                }
            }
            text(userPrompt)
        }

        return chat.sendMessageStream(currentContent).mapNotNull { chunk -> chunk.text }
    }

    suspend fun generateSimpleText(
        prompt: String,
        tier: ModelTier = ModelTier.FLASH,
        systemPrompt: String = "You are BD AI, created by Muhin Hasan Khan (মুহিন হাসান খান). Be accurate, concise, and helpful in any language."
    ): String {
        val model = createChatModel(tier, systemPrompt)
        val response = model.generateContent(prompt)
        return response.text.orEmpty()
    }

    suspend fun generateDataChart(prompt: String): Pair<String, ChartSpec> {
        val response = chartModel.generateContent(prompt)
        val jsonStr = response.text ?: throw IllegalStateException("Model returned empty chart JSON.")
        val json = JSONObject(jsonStr)
        val labelsArr = json.getJSONArray("labels")
        val valuesArr = json.getJSONArray("values")
        val headersArr = json.getJSONArray("tableHeaders")
        val rowsArr = json.getJSONArray("tableRows")

        val labels = (0 until labelsArr.length()).map { labelsArr.getString(it) }
        val values = (0 until valuesArr.length()).map { valuesArr.getDouble(it) }
        val headers = (0 until headersArr.length()).map { headersArr.getString(it) }
        val rows = (0 until rowsArr.length()).map { r ->
            val rowArr = rowsArr.getJSONArray(r)
            (0 until rowArr.length()).map { c -> rowArr.getString(c) }
        }
        val spec = ChartSpec(
            title = json.getString("title"),
            chartType = json.optString("chartType", "BAR"),
            summary = json.getString("summary"),
            labels = labels,
            values = values,
            tableHeaders = headers,
            tableRows = rows
        )
        return jsonStr to spec
    }

    suspend fun generateImage(prompt: String, wideLandscape: Boolean): Bitmap? {
        val targetModel = if (wideLandscape) wideImageModel else defaultImageModel
        val response = targetModel.generateContent(prompt)
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
    }

    suspend fun editImage(
        inputBitmap: Bitmap,
        editInstruction: String,
        wideLandscape: Boolean
    ): Bitmap? {
        val targetModel = if (wideLandscape) wideImageModel else defaultImageModel
        val response = targetModel.generateContent(
            content {
                image(inputBitmap)
                text(editInstruction)
            }
        )
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
    }

    suspend fun analyzeImage(inputBitmap: Bitmap, prompt: String): String {
        val model = createChatModel(
            ModelTier.FLASH,
            "You are BD AI, created by Muhin Hasan Khan. Analyze images clearly and thoroughly in the user's language."
        )
        val response = model.generateContent(
            content {
                image(inputBitmap)
                text(prompt)
            }
        )
        return response.text.orEmpty()
    }
}
