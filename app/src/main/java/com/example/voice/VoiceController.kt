package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceController(context: Context) {

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceAmplitude = MutableStateFlow(0f)
    val voiceAmplitude: StateFlow<Float> = _voiceAmplitude.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                Log.w("VoiceController", "TTS init failed with status $status")
            }
        }
    }

    fun startListening(
        languageTag: String = "Auto",
        onFinalResult: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _speechError.value = "Speech recognition service is not available on this device."
            return
        }
        stopSpeaking()
        _speechError.value = null
        _partialTranscript.value = ""

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _voiceAmplitude.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _voiceAmplitude.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _voiceAmplitude.value = 0f
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap mic to speak."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Voice recognition error ($error)."
                    }
                    _speechError.value = msg
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _voiceAmplitude.value = 0f
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    if (text.isNotEmpty()) {
                        _partialTranscript.value = text
                        onFinalResult(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val localeCode = when (languageTag) {
            "Bengali" -> "bn-BD"
            "English" -> "en-US"
            "Arabic" -> "ar-SA"
            "Hindi" -> "hi-IN"
            "Spanish" -> "es-ES"
            "French" -> "fr-FR"
            "Japanese" -> "ja-JP"
            else -> Locale.getDefault().toLanguageTag()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _speechError.value = e.localizedMessage ?: "Unable to start speech recognition."
        }
    }

    fun stopListening() {
        _isListening.value = false
        _voiceAmplitude.value = 0f
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun speak(
        text: String,
        speed: Float = 1.0f,
        languageTag: String = "Auto"
    ) {
        if (!ttsReady || text.isBlank()) return
        val cleanText = text
            .replace("```", " ")
            .replace("**", "")
            .replace("##", "")
            .take(3500)

        val targetLocale = when {
            languageTag == "Bengali" || containsBengali(cleanText) -> Locale("bn", "BD")
            languageTag == "Arabic" -> Locale("ar")
            languageTag == "Hindi" -> Locale("hi", "IN")
            languageTag == "Spanish" -> Locale("es", "ES")
            languageTag == "French" -> Locale.FRENCH
            languageTag == "Japanese" -> Locale.JAPANESE
            else -> Locale.US
        }
        tts?.setLanguage(targetLocale)
        tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "bdai_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun clearError() {
        _speechError.value = null
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun containsBengali(input: String): Boolean {
        return input.any { ch -> ch in '\u0980'..'\u09FF' }
    }
}
