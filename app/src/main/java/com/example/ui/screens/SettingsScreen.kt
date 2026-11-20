package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.signOutUser
import com.example.data.model.AccentOption
import com.example.data.model.MemoryCategory
import com.example.data.model.PersonalityStyle
import com.example.data.model.ThemePreference
import com.example.data.model.UserProfile
import com.example.viewmodel.SessionUiState
import com.example.viewmodel.UserSessionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    profile: UserProfile,
    sessionViewModel: UserSessionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser = Firebase.auth.currentUser
    val memoriesState by sessionViewModel.memoriesState.collectAsStateWithLifecycle()

    var aboutInput by remember(profile.customInstructionsAbout) {
        mutableStateOf(profile.customInstructionsAbout)
    }
    var styleInput by remember(profile.customInstructionsStyle) {
        mutableStateOf(profile.customInstructionsStyle)
    }
    var newMemoryText by remember { mutableStateOf("") }
    var newMemoryCategory by remember { mutableStateOf(MemoryCategory.PREFERENCE) }
    var voiceSpeedSlider by remember(profile.voiceSpeed) {
        mutableFloatStateOf(profile.voiceSpeed.toFloat())
    }

    var showClearChatsConfirm by remember { mutableStateOf(false) }
    var showClearMemoriesConfirm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings & Personalization", style = MaterialTheme.typography.headlineSmall)

            // 1. Account Profile
            SettingsGroup(title = "Account Profile") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentUser?.photoUrl != null) {
                        AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = "User profile avatar",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = profile.displayName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.displayName.ifBlank { "BD AI User" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            profile.email.ifBlank { currentUser?.email.orEmpty() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Plan: BD AI Cloud • Google Account Verified",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                ToggleRow(
                    title = "Notifications & Reminders",
                    subtitle = "Alerts for scheduled AI tasks",
                    checked = profile.notificationsEnabled,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("notificationsEnabled" to it))
                    }
                )

                Text("Preferred AI Language", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "Bengali", "English", "Arabic", "Hindi", "Spanish", "French", "Japanese").forEach { lang ->
                        FilterChip(
                            selected = profile.preferredLanguage == lang,
                            onClick = {
                                sessionViewModel.updateProfileField(mapOf("preferredLanguage" to lang))
                            },
                            label = { Text(lang, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // 2. Personalization & Memory
            SettingsGroup(title = "Personalization & Memory") {
                Text("Base Style & Personality", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonalityStyle.entries.forEach { style ->
                        FilterChip(
                            selected = profile.personalityEnum() == style,
                            onClick = { sessionViewModel.setPersonality(style) },
                            label = { Text(style.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = aboutInput,
                    onValueChange = { aboutInput = it },
                    label = { Text("What should BD AI know about you?") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = styleInput,
                    onValueChange = { styleInput = it },
                    label = { Text("How should BD AI respond? (Custom Instructions)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { sessionViewModel.saveCustomInstructions(aboutInput, styleInput) },
                    modifier = Modifier.testTag("save_custom_instructions_button")
                ) {
                    Text("Save Custom Instructions")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ToggleRow(
                    title = "Enable Memory",
                    subtitle = "Remember preferences, goals, and facts across chats",
                    checked = profile.memoryEnabled,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("memoryEnabled" to it))
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newMemoryText,
                        onValueChange = { newMemoryText = it },
                        label = { Text("Add memory note") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            sessionViewModel.addMemory(newMemoryText, newMemoryCategory)
                            newMemoryText = ""
                        },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Save memory")
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = newMemoryCategory == cat,
                            onClick = { newMemoryCategory = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (memoriesState is SessionUiState.Success) {
                    val memories = (memoriesState as SessionUiState.Success).data
                    Text(
                        "Memory Summary (${memories.size} saved)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    memories.forEach { mem ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mem.content, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    mem.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { sessionViewModel.deleteMemory(mem.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete memory", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // 3. Appearance Settings
            SettingsGroup(title = "Appearance & Chat UI") {
                Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.entries.forEach { theme ->
                        FilterChip(
                            selected = profile.themeEnum() == theme,
                            onClick = { sessionViewModel.setTheme(theme) },
                            label = { Text(theme.label) }
                        )
                    }
                }

                Text("Accent Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentOption.entries.forEach { accent ->
                        val selected = profile.accentEnum() == accent
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(accent.hex))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { sessionViewModel.setAccent(accent) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "${accent.label} selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                ToggleRow(
                    title = "Compact Chat Density",
                    subtitle = "Tighter message spacing for long coding/research threads",
                    checked = profile.compactChat,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("compactChat" to it))
                    }
                )
                ToggleRow(
                    title = "Show Message Timestamps",
                    subtitle = "Display date/time under messages",
                    checked = profile.showTimestamps,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("showTimestamps" to it))
                    }
                )
            }

            // 4. Voice Settings
            SettingsGroup(title = "Voice Settings") {
                ToggleRow(
                    title = "Auto-Speak AI Replies",
                    subtitle = "Automatically read aloud responses from voice prompts",
                    checked = profile.autoSpeakVoice,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("autoSpeakVoice" to it))
                    }
                )
                Text(
                    "Voice Playback Speed: ${String.format(Locale.US, "%.2fx", voiceSpeedSlider)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = voiceSpeedSlider,
                    onValueChange = { voiceSpeedSlider = it },
                    onValueChangeFinished = {
                        sessionViewModel.updateProfileField(mapOf("voiceSpeed" to voiceSpeedSlider.toDouble()))
                    },
                    valueRange = 0.75f..1.75f,
                    steps = 3
                )
            }

            // 5. Security & Privacy
            SettingsGroup(title = "Security, Privacy & Data Controls") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Protected by Google Sign-In + Firebase App Check",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ToggleRow(
                    title = "Save Chat History",
                    subtitle = "When off, new chats default to Temporary Incognito mode",
                    checked = profile.saveChatHistory,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("saveChatHistory" to it))
                    }
                )
                ToggleRow(
                    title = "Parental & Safe Content Filter",
                    subtitle = "Enforce family-safe responses in BD AI",
                    checked = profile.safeContentFilter,
                    onCheckedChange = {
                        sessionViewModel.updateProfileField(mapOf("safeContentFilter" to it))
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showClearMemoriesConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Memories")
                    }
                    OutlinedButton(
                        onClick = { showClearChatsConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear All Chats")
                    }
                }
            }

            // 6. About & Sign Out
            SettingsGroup(title = "About BD AI & Help") {
                OutlinedButton(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About BD AI • Creator & Help Center")
                }

                Button(
                    onClick = {
                        signOutUser(credentialManager, scope)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClearChatsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatsConfirm = false },
            title = { Text("Delete all chats?") },
            text = { Text("This permanently deletes all saved conversations from Cloud Firestore.") },
            confirmButton = {
                Button(onClick = {
                    sessionViewModel.clearAllChats()
                    showClearChatsConfirm = false
                }) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatsConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearMemoriesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesConfirm = false },
            title = { Text("Clear all memories?") },
            text = { Text("BD AI will forget all saved memory items.") },
            confirmButton = {
                Button(onClick = {
                    sessionViewModel.clearAllMemories()
                    showClearMemoriesConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoriesConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About BD AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BD AI — Multilingual Intelligence Assistant", style = MaterialTheme.typography.titleSmall)
                    Text("Created by Muhin Hasan Khan\n(আমাকে তৈরি করেছেন মুহিন হাসান খান।)")
                    Text(
                        "Supports all world languages, deep reasoning, live voice, image generation & editing, document/PDF/video understanding, coding, education, and cloud sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
