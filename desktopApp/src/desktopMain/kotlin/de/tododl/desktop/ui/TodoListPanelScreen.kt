package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.Priority
import de.tododl.shared.model.TodoItem
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.remote.UserDto
import de.tododl.shared.repository.TodoItemRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TodoListPanelScreen(panelId: String) {
    val repo = remember { koinGet<TodoItemRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    val allItems by repo.observeItems(panelId).collectAsState(initial = emptyList())
    val topLevel = allItems.filter { it.parentId == null }.sortedBy { it.position }
    fun subtasksOf(itemId: String) = allItems.filter { it.parentId == itemId }.sortedBy { it.position }

    var newItemText by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }

    val doneCount = topLevel.count { it.done }
    val progressText = if (topLevel.isNotEmpty()) "$doneCount von ${topLevel.size} erledigt" else "Keine Aufgaben"

    fun save(item: TodoItem) {
        scope.launch {
            repo.upsert(item)
            runCatching { syncManager.pushTodoItemIfNeeded(item) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Checklist,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Todoliste",
            subtitle = progressText,
            badgeLabel = "PANEL"
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            // Neue Aufgabe - schnell, nur Text; Details über Klick auf die Zeile
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
                    Icon(Icons.Default.Add, contentDescription = null, tint = notionColors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("Neue Aufgabe eingeben...", fontSize = 14.sp, color = notionColors.textSecondary) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newItemText.isNotBlank()) {
                                val item = TodoItem(
                                    id = Uuid.random().toString(),
                                    panelId = panelId,
                                    text = newItemText,
                                    position = topLevel.size
                                )
                                save(item)
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

            if (topLevel.isNotEmpty()) {
                TableHeader()
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(topLevel, key = { it.id }) { item ->
                    val subtasks = subtasksOf(item.id)
                    Column {
                        NotionTodoRow(
                            item = item,
                            subtaskCount = subtasks.size,
                            subtaskDoneCount = subtasks.count { it.done },
                            isExpanded = item.id in expandedIds,
                            onToggle = { save(item.copy(done = !item.done)) },
                            onToggleExpand = {
                                expandedIds = if (item.id in expandedIds) expandedIds - item.id else expandedIds + item.id
                            },
                            onOpenDetails = { editingItem = item },
                            onDelete = { scope.launch { repo.delete(item.id) } }
                        )

                        if (item.id in expandedIds) {
                            Column(Modifier.padding(start = 40.dp, top = 2.dp, bottom = 4.dp)) {
                                subtasks.forEach { sub ->
                                    SubtaskRow(
                                        item = sub,
                                        onToggle = { save(sub.copy(done = !sub.done)) },
                                        onDelete = { scope.launch { repo.delete(sub.id) } }
                                    )
                                }
                                InlineAddSubtask { text ->
                                    save(
                                        TodoItem(
                                            id = Uuid.random().toString(),
                                            panelId = panelId,
                                            parentId = item.id,
                                            text = text,
                                            position = subtasks.size
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        TodoDetailDialog(
            panelId = panelId,
            item = item,
            syncManager = syncManager,
            onDismiss = { editingItem = null },
            onSave = { updated ->
                save(updated)
                editingItem = null
            }
        )
    }
}

@Composable
private fun TableHeader() {
    val notionColors = LocalNotionColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(28.dp)) // Checkbox-Breite
        Text("AUFGABE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.weight(1f))
        Text("ZUGEWIESEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(110.dp))
        Text("DRINGLICHKEIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(110.dp))
        Text("TERMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(90.dp))
        Text("ENDFRIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(90.dp))
        Spacer(Modifier.width(64.dp)) // Expand + Löschen
    }
}

@Composable
private fun NotionTodoRow(
    item: TodoItem,
    subtaskCount: Int,
    subtaskDoneCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onToggleExpand: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val notionColors = LocalNotionColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onOpenDetails() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = notionColors.textSecondary)
            )

            Text(
                text = item.text,
                style = if (item.done) {
                    MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough, fontSize = 14.sp)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                },
                color = if (item.done) notionColors.textSecondary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Box(Modifier.width(110.dp)) { AssigneeChip(item.assigneeUsername) }
            Box(Modifier.width(110.dp)) { PriorityChip(item.priority) }
            Box(Modifier.width(90.dp)) { DateText(item.terminDate) }
            Box(Modifier.width(90.dp)) { DateText(item.dueDate) }

            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (subtaskCount > 0) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Unteraufgaben",
                                tint = notionColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            if (subtaskCount > 0) {
                Text("$subtaskDoneCount/$subtaskCount", fontSize = 10.sp, color = notionColors.textSecondary, modifier = Modifier.width(28.dp))
            } else {
                Spacer(Modifier.width(28.dp))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SubtaskRow(item: TodoItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val notionColors = LocalNotionColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = notionColors.textSecondary),
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = item.text,
            fontSize = 13.sp,
            color = if (item.done) notionColors.textSecondary else MaterialTheme.colorScheme.onSurface,
            style = if (item.done) androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Entfernen", tint = notionColors.textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun InlineAddSubtask(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val notionColors = LocalNotionColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Unteraufgabe hinzufügen...", fontSize = 12.sp, color = notionColors.textSecondary) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f).height(40.dp),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                if (text.isNotBlank()) { onAdd(text); text = "" }
            })
        )
        if (text.isNotBlank()) {
            TextButton(onClick = { onAdd(text); text = "" }) { Text("Hinzufügen", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AssigneeChip(username: String?) {
    val notionColors = LocalNotionColors.current
    if (username.isNullOrBlank()) {
        Text("—", fontSize = 12.sp, color = notionColors.textSecondary)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(username.take(1).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(4.dp))
        Text(username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.NONE -> Color(0xFF9E9E9E)
    Priority.LOW -> Color(0xFF64B5F6)
    Priority.MEDIUM -> Color(0xFFFFB74D)
    Priority.HIGH -> Color(0xFFFF7043)
    Priority.URGENT -> Color(0xFFE53935)
}

private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.NONE -> "—"
    Priority.LOW -> "Niedrig"
    Priority.MEDIUM -> "Mittel"
    Priority.HIGH -> "Hoch"
    Priority.URGENT -> "Dringend"
}

@Composable
private fun PriorityChip(priority: Priority) {
    if (priority == Priority.NONE) {
        Text("—", fontSize = 12.sp, color = LocalNotionColors.current.textSecondary)
        return
    }
    val color = priorityColor(priority)
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
        Text(
            priorityLabel(priority),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatDate(epochMs: Long?): String {
    if (epochMs == null) return ""
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}

private fun parseDateInput(input: String): Long? {
    if (input.isBlank()) return null
    return runCatching {
        // Erwartet TT.MM.JJJJ
        val parts = input.trim().split(".")
        val date = LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrNull()
}

@Composable
private fun DateText(epochMs: Long?) {
    val notionColors = LocalNotionColors.current
    val text = formatDate(epochMs)
    Text(
        if (text.isBlank()) "—" else text,
        fontSize = 12.sp,
        color = if (text.isBlank()) notionColors.textSecondary else MaterialTheme.colorScheme.onSurface
    )
}

/** Detailansicht einer Aufgabe: Priorität, Termin, Endfrist, Zuteilung, Unteraufgaben. */
@Composable
private fun TodoDetailDialog(
    panelId: String,
    item: TodoItem,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit
) {
    var text by remember { mutableStateOf(item.text) }
    var priority by remember { mutableStateOf(item.priority) }
    var terminInput by remember { mutableStateOf(formatDate(item.terminDate)) }
    var dueInput by remember { mutableStateOf(formatDate(item.dueDate)) }
    var assignee by remember { mutableStateOf(item.assigneeUsername ?: "") }
    var assigneeSuggestions by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(assignee) {
        assigneeSuggestions = if (assignee.isBlank()) {
            emptyList()
        } else {
            try {
                syncManager.searchUsersForPanel(panelId, assignee)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufgabe bearbeiten", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.widthIn(min = 380.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Aufgabe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text("Dringlichkeit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Priority.entries.forEach { p ->
                        val selected = priority == p
                        Surface(
                            color = if (selected) priorityColor(p).copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) priorityColor(p) else LocalNotionColors.current.border),
                            modifier = Modifier.clickable { priority = p }
                        ) {
                            Text(
                                priorityLabel(p),
                                fontSize = 12.sp,
                                color = if (selected) priorityColor(p) else LocalNotionColors.current.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = terminInput,
                        onValueChange = { terminInput = it },
                        label = { Text("Termin (TT.MM.JJJJ)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueInput,
                        onValueChange = { dueInput = it },
                        label = { Text("Endfrist (TT.MM.JJJJ)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedTextField(
                        value = assignee,
                        onValueChange = { assignee = it },
                        label = { Text("Zugewiesen an (Nutzername)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (assigneeSuggestions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.padding(top = 58.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LocalNotionColors.current.border),
                            shadowElevation = 4.dp
                        ) {
                            Column {
                                assigneeSuggestions.take(5).forEach { user ->
                                    Text(
                                        "${user.username} (${user.name})",
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { assignee = user.username; assigneeSuggestions = emptyList() }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    item.copy(
                        text = text,
                        priority = priority,
                        terminDate = parseDateInput(terminInput),
                        dueDate = parseDateInput(dueInput),
                        assigneeUsername = assignee.ifBlank { null }
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
