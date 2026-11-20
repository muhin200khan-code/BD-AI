package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.ChatInferenceState
import com.example.voice.VoiceController

@Composable
fun LiveVoiceOverlay(
    voiceController: VoiceController,
    inferenceState: ChatInferenceState,
    preferredLanguage: String,
    voiceSpeed: Float,
    onVoicePromptCaptured: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val isListening by voiceController.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceController.isSpeaking.collectAsStateWithLifecycle()
    val partialTranscript by voiceController.partialTranscript.collectAsStateWithLifecycle()
    val amplitude by voiceController.voiceAmplitude.collectAsStateWithLifecycle()
    val speechError by voiceController.speechError.collectAsStateWithLifecycle()

    var selectedLang by remember(preferredLanguage) { mutableStateOf(preferredLanguage) }
    var lastReply by remember { mutableStateOf("") }

    if (inferenceState is ChatInferenceState.Streaming) {
        lastReply = inferenceState.partialText
    }

    val animatedAmp by animateFloatAsState(
        targetValue = if (isListening) (0.25f + amplitude * 0.75f) else if (isSpeaking) 0.55f else 0.12f,
        animationSpec = tween(160),
        label = "voiceAmp"
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening(selectedLang) { finalText ->
                onVoicePromptCaptured(finalText)
            }
        }
    }

    fun startMicFlow() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.startListening(selectedLang) { finalText ->
                onVoicePromptCaptured(finalText)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("BD AI Live Voice", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Speed ${String.format("%.2fx", voiceSpeed)} • $selectedLang",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        voiceController.stopListening()
                        voiceController.stopSpeaking()
                        onClose()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Live Voice")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Auto", "Bengali", "English", "Arabic").forEach { lang ->
                    FilterChip(
                        selected = selectedLang == lang,
                        onClick = { selectedLang = lang },
                        label = { Text(lang, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(220.dp)
            ) {
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.tertiary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseR = size.minDimension * 0.28f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(primary.copy(alpha = 0.35f), primary.copy(alpha = 0f))
                        ),
                        radius = baseR * (1.2f + animatedAmp * 1.4f)
                    )
                    drawCircle(
                        color = secondary.copy(alpha = 0.25f),
                        radius = baseR * (1.0f + animatedAmp * 0.9f)
                    )
                    drawCircle(
                        color = primary,
                        radius = baseR * (0.78f + animatedAmp * 0.2f)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val statusLabel = when {
                    isListening -> "Listening..."
                    inferenceState is ChatInferenceState.Thinking -> "BD AI is thinking..."
                    inferenceState is ChatInferenceState.Streaming -> "Responding..."
                    isSpeaking -> "Speaking..."
                    else -> "Tap the mic and speak in any language"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (partialTranscript.isNotBlank()) {
                    Text(
                        text = "\"$partialTranscript\"",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (lastReply.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = lastReply,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!speechError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = speechError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (isSpeaking) {
                    FilledIconButton(
                        onClick = { voiceController.stopSpeaking() },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.VolumeOff, contentDescription = "Mute voice output")
                    }
                }

                FilledIconButton(
                    onClick = {
                        if (isListening) voiceController.stopListening() else startMicFlow()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("live_voice_mic_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Start listening",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
