package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatFolder
import com.example.data.model.ChatThread
import java.util.Calendar

@Composable
fun ChatDrawerContent(
    chats: List<ChatThread>,
    activeChatId: String?,
    searchQuery: String,
    filterFolder: String,
    onSearchChange: (String) -> Unit,
    onFilterFolderChange: (String) -> Unit,
    onNewChat: (temporary: Boolean) -> Unit,
    onSelectChat: (ChatThread) -> Unit,
    onRenameChat: (ChatThread, String) -> Unit,
    onToggleArchive: (ChatThread) -> Unit,
    onTogglePin: (ChatThread) -> Unit,
    onMoveFolder: (ChatThread, ChatFolder) -> Unit,
    onDeleteChat: (ChatThread) -> Unit
) {
    var renamingChat by remember { mutableStateOf<ChatThread?>(null) }
    var folderDialogChat by remember { mutableStateOf<ChatThread?>(null) }

    ModalDrawerSheet(
        modifier = Modifier
            .width(316.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onNewChat(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Chat")
                }
                OutlinedButton(
                    onClick = { onNewChat(true) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = "Temporary chat", modifier = Modifier.size(18.dp))
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search chats...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf("ALL") + ChatFolder.entries.map { it.label } + listOf("ARCHIVED")
                filters.forEach { tag ->
                    FilterChip(
                        selected = filterFolder == tag,
                        onClick = { onFilterFolderChange(tag) },
                        label = {
                            Text(
                                text = when (tag) {
                                    "ALL" -> "All"
                                    "ARCHIVED" -> "Archived"
                                    else -> tag
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val filtered = chats.filter { chat ->
                val matchesSearch = searchQuery.isBlank() ||
                    chat.title.contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                val matchesFolder = when (filterFolder) {
                    "ALL" -> !chat.archived
                    "ARCHIVED" -> chat.archived
                    else -> !chat.archived && chat.folder.equals(filterFolder, ignoreCase = true)
                }
                matchesSearch && matchesFolder
            }

            val pinned = filtered.filter { it.pinned }
            val unpinned = filtered.filter { !it.pinned }
            val grouped = groupChatsByTime(unpinned)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = if (filterFolder == "ARCHIVED") "No archived chats." else "No matching conversations yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    item { GroupHeader("Pinned") }
                    items(pinned, key = { "pin_${it.id}" }) { chat ->
                        ChatHistoryRow(
                            chat = chat,
                            selected = chat.id == activeChatId,
                            onClick = { onSelectChat(chat) },
                            onRename = { renamingChat = chat },
                            onTogglePin = { onTogglePin(chat) },
                            onToggleArchive = { onToggleArchive(chat) },
                            onChangeFolder = { folderDialogChat = chat },
                            onDelete = { onDeleteChat(chat) }
                        )
                    }
                }
                grouped.forEach { (label, itemsInGroup) ->
                    item { GroupHeader(label) }
                    items(itemsInGroup, key = { it.id }) { chat ->
                        ChatHistoryRow(
                            chat = chat,
                            selected = chat.id == activeChatId,
                            onClick = { onSelectChat(chat) },
                            onRename = { renamingChat = chat },
                            onTogglePin = { onTogglePin(chat) },
                            onToggleArchive = { onToggleArchive(chat) },
                            onChangeFolder = { folderDialogChat = chat },
                            onDelete = { onDeleteChat(chat) }
                        )
                    }
                }
            }
        }
    }

    renamingChat?.let { target ->
        var editedTitle by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renamingChat = null },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameChat(target, editedTitle)
                    renamingChat = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingChat = null }) { Text("Cancel") }
            }
        )
    }

    folderDialogChat?.let { target ->
        AlertDialog(
            onDismissRequest = { folderDialogChat = null },
            title = { Text("Move to Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChatFolder.entries.forEach { folder ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (target.folderEnum() == folder) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveFolder(target, folder)
                                    folderDialogChat = null
                                }
                        ) {
                            Text(
                                text = folder.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderDialogChat = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun ChatHistoryRow(
    chat: ChatThread,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onChangeFolder: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${chat.folder} • ${chat.messageCount} msgs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Chat options",
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.pinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                    onClick = { menuExpanded = false; onTogglePin() }
                )
                DropdownMenuItem(
                    text = { Text("Folder") },
                    leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                    onClick = { menuExpanded = false; onChangeFolder() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.archived) "Unarchive" else "Archive") },
                    leadingIcon = {
                        Icon(if (chat.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null)
                    },
                    onClick = { menuExpanded = false; onToggleArchive() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

private fun groupChatsByTime(chats: List<ChatThread>): List<Pair<String, List<ChatThread>>> {
    if (chats.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - 86_400_000L
    val weekStart = todayStart - 7L * 86_400_000L

    val today = mutableListOf<ChatThread>()
    val yesterday = mutableListOf<ChatThread>()
    val week = mutableListOf<ChatThread>()
    val older = mutableListOf<ChatThread>()

    chats.forEach { chat ->
        val millis = (chat.updatedAt ?: chat.createdAt)?.toDate()?.time ?: todayStart
        when {
            millis >= todayStart -> today.add(chat)
            millis >= yesterdayStart -> yesterday.add(chat)
            millis >= weekStart -> week.add(chat)
            else -> older.add(chat)
        }
    }

    return buildList {
        if (today.isNotEmpty()) add("Today" to today)
        if (yesterday.isNotEmpty()) add("Yesterday" to yesterday)
        if (week.isNotEmpty()) add("Previous 7 Days" to week)
        if (older.isNotEmpty()) add("Older" to older)
    }
}
