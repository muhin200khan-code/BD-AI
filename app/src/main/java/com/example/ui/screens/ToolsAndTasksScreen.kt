package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatFolder
import com.example.data.model.ScheduledTask
import com.example.ui.components.openCalendarEventDraft
import com.example.ui.components.openEmailDraft
import com.example.ui.components.openMapsSearch
import com.example.viewmodel.SessionUiState
import com.example.viewmodel.UserSessionViewModel
import java.util.Locale

private data class ToolTemplate(
    val title: String,
    val folder: ChatFolder,
    val prompt: String
)

private val exploreTemplates = listOf(
    ToolTemplate(
        "Python & Data Analysis",
        ChatFolder.CODING,
        "Write clean, well-commented Python code using pandas to clean a CSV dataset, compute summary statistics, and detect outliers."
    ),
    ToolTemplate(
        "Web App (HTML/CSS/JS)",
        ChatFolder.CODING,
        "Create a responsive modern landing page using semantic HTML5, CSS Grid/Flexbox, and vanilla JavaScript with dark mode toggle."
    ),
    ToolTemplate(
        "Java & C++ Algorithms",
        ChatFolder.CODING,
        "Explain and implement Dijkstra's shortest path algorithm in both Java and C++ with time/space complexity analysis."
    ),
    ToolTemplate(
        "Math & Science Tutor",
        ChatFolder.EDUCATION,
        "Act as my patient Math & Physics tutor. Explain quadratic equations and Newton's second law step-by-step with practice problems."
    ),
    ToolTemplate(
        "English & Translation",
        ChatFolder.EDUCATION,
        "Help me improve my English grammar and translate key professional expressions between Bengali and English."
    ),
    ToolTemplate(
        "Fact Check & Research",
        ChatFolder.RESEARCH,
        "Give me a balanced, structured research briefing and fact-check framework on renewable solar adoption in South Asia."
    ),
    ToolTemplate(
        "Video Script & Story",
        ChatFolder.CREATIVE,
        "Write a compelling 60-second visual storyboard and voiceover script for a short documentary video about Dhaka's tech ecosystem."
    ),
    ToolTemplate(
        "BD AI Work & Dots Agent",
        ChatFolder.WORK,
        "Act as my executive productivity agent: organize a weekly project execution roadmap with milestones, risks, and action items."
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsAndTasksScreen(
    sessionViewModel: UserSessionViewModel,
    onLaunchPromptInChat: (prompt: String, folder: ChatFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tasksState by sessionViewModel.scheduledTasksState.collectAsStateWithLifecycle()
    val runningTaskId by sessionViewModel.runningTaskId.collectAsStateWithLifecycle()

    var showNewTaskDialog by remember { mutableStateOf(false) }

    // Unit & Currency Converter state
    var converterValue by remember { mutableStateOf("100") }
    var selectedUnitMode by remember { mutableStateOf("KM -> Miles") }
    val numericInput = converterValue.toDoubleOrNull() ?: 0.0
    val convertedResult = when (selectedUnitMode) {
        "KM -> Miles" -> String.format(Locale.US, "%.2f miles", numericInput * 0.621371)
        "Celsius -> F" -> String.format(Locale.US, "%.2f °F", (numericInput * 9.0 / 5.0) + 32.0)
        "KG -> Pounds" -> String.format(Locale.US, "%.2f lbs", numericInput * 2.20462)
        "USD -> BDT (est)" -> String.format(Locale.US, "৳%.2f (120 rate)", numericInput * 120.0)
        else -> ""
    }

    var mapsQuery by remember { mutableStateOf("Dhaka, Bangladesh") }

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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capabilities, Tools & Tasks", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Launch coding, education, research, connected system actions, converters, and automated scheduled prompts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Specialized AI Launchpad
            SectionCard(title = "Coding, Education, Research & Work Launchpad") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exploreTemplates.forEach { tpl ->
                        AssistChip(
                            onClick = { onLaunchPromptInChat(tpl.prompt, tpl.folder) },
                            label = { Text(tpl.title, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                val icon = when (tpl.folder) {
                                    ChatFolder.CODING -> Icons.Rounded.Code
                                    ChatFolder.EDUCATION -> Icons.Rounded.School
                                    ChatFolder.RESEARCH -> Icons.Rounded.Public
                                    else -> Icons.Rounded.Schedule
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // 2. Scheduled Tasks (Firestore-backed + live Gemini execution)
            SectionCard(
                title = "Scheduled AI Tasks",
                action = {
                    OutlinedButton(
                        onClick = { showNewTaskDialog = true },
                        modifier = Modifier.testTag("add_scheduled_task_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Task")
                    }
                }
            ) {
                when (val state = tasksState) {
                    is SessionUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    is SessionUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    is SessionUiState.Success -> {
                        if (state.data.isEmpty()) {
                            Text(
                                "No scheduled tasks yet. Create one to save recurring briefings, study drills, or daily summaries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.data.forEach { task ->
                                    ScheduledTaskRow(
                                        task = task,
                                        isRunning = runningTaskId == task.id,
                                        onToggle = { sessionViewModel.toggleScheduledTask(task.id, it) },
                                        onRunNow = { sessionViewModel.runScheduledTaskNow(task) },
                                        onDelete = { sessionViewModel.deleteScheduledTask(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Connected System Services & Utilities
            SectionCard(title = "Connected Apps, Maps & Weather") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            openEmailDraft(
                                context,
                                "Drafted with BD AI",
                                "Hello,\n\nPrepared using BD AI (built by Muhin Hasan Khan).\n"
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compose Gmail")
                    }
                    OutlinedButton(
                        onClick = {
                            openCalendarEventDraft(
                                context,
                                "BD AI Study / Work Session",
                                "Scheduled via BD AI"
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Calendar")
                    }
                    OutlinedButton(
                        onClick = {
                            onLaunchPromptInChat(
                                "Give me a detailed seasonal weather, clothing, and travel guide for $mapsQuery.",
                                ChatFolder.RESEARCH
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Weather Guide")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mapsQuery,
                        onValueChange = { mapsQuery = it },
                        label = { Text("Location / Maps search") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { openMapsSearch(context, mapsQuery) },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = "Open Maps")
                    }
                }
            }

            // 4. Instant Unit & Currency Converter
            SectionCard(title = "Unit & Currency Converter") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = converterValue,
                        onValueChange = { converterValue = it },
                        label = { Text("Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(130.dp)
                    )
                    Column {
                        Text("Result", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = convertedResult,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KM -> Miles", "Celsius -> F", "KG -> Pounds", "USD -> BDT (est)").forEach { mode ->
                        FilterChip(
                            selected = selectedUnitMode == mode,
                            onClick = { selectedUnitMode = mode },
                            label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        onLaunchPromptInChat(
                            "Explain the exact conversion formula and live context for $converterValue in $selectedUnitMode.",
                            ChatFolder.EDUCATION
                        )
                    }
                ) {
                    Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask BD AI for live calculation & breakdown")
                }
            }
        }
    }

    if (showNewTaskDialog) {
        NewScheduledTaskDialog(
            onDismiss = { showNewTaskDialog = false },
            onSave = { title, prompt, schedule ->
                sessionViewModel.createScheduledTask(title, prompt, schedule)
                showNewTaskDialog = false
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ScheduledTaskRow(
    task: ScheduledTask,
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        task.scheduleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(checked = task.active, onCheckedChange = onToggle)
            }
            Text(
                task.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!task.lastOutput.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = task.lastOutput,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete task")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(onClick = onRunNow, enabled = !isRunning) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun NewScheduledTaskDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, prompt: String, schedule: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("Daily 9:00 AM") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Scheduled AI Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("AI Prompt") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Schedule Cadence") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, prompt, schedule) }) {
                Text("Save Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
