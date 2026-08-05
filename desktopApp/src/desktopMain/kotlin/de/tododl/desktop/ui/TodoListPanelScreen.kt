package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.TodoItem
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.TodoItemRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TodoListPanelScreen(panelId: String) {
    val repo = remember { koinGet<TodoItemRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current
    val items by repo.observeItems(panelId).collectAsState(initial = emptyList())

    var newItemText by remember { mutableStateOf("") }

    val doneCount = items.count { it.done }
    val progressText = if (items.isNotEmpty()) "$doneCount von ${items.size} erledigt" else "Keine Aufgaben"

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Checklist,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Todoliste",
            subtitle = progressText,
            badgeLabel = "PANEL"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Notion-Style Inline New Task Input
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, notionColors.border, RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("Neue Aufgabe eingeben...", fontSize = 14.sp, color = notionColors.textSecondary) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newItemText.isNotBlank()) {
                                scope.launch {
                                    val item = TodoItem(
                                        id = Uuid.random().toString(),
                                        panelId = panelId,
                                        text = newItemText,
                                        position = items.size
                                    )
                                    repo.upsert(item)
                                    runCatching { syncManager.pushTodoItemIfNeeded(item) }
                                }
                                newItemText = ""
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Hinzufügen", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    NotionTodoRow(
                        item = item,
                        onToggle = {
                            scope.launch {
                                repo.setDone(item.id, !item.done)
                                runCatching { syncManager.pushTodoItemIfNeeded(item.copy(done = !item.done)) }
                            }
                        },
                        onDelete = { scope.launch { repo.delete(item.id) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotionTodoRow(item: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val notionColors = LocalNotionColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = notionColors.textSecondary
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.text,
                style = if (item.done) {
                    MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = TextDecoration.LineThrough,
                        fontSize = 14.sp
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                },
                color = if (item.done) notionColors.textSecondary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
