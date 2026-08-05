package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
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
    val items by repo.observeItems(panelId).collectAsState(initial = emptyList())

    var newItemText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                label = { Text("Neuer Eintrag") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
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
            }) {
                Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.id }) { item ->
                TodoRow(
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

@Composable
private fun TodoRow(item: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        Text(
            text = item.text,
            style = if (item.done) {
                MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Löschen")
        }
    }
}
