package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private enum class TodoStatusFilter(val label: String) {
    ALL("Alle"),
    OPEN("Offen"),
    DONE("Erledigt")
}

private enum class TodoSortOrder(val label: String) {
    POSITION("Standard (Position)"),
    TITLE_ASC("Titel (A-Z / Aufwärts)"),
    TITLE_DESC("Titel (Z-A / Abwärts)"),
    TERMIN_ASC("Termin (Früheste)"),
    DUE_ASC("Endfrist (Baldige)"),
    PRIORITY_DESC("Priorität (Höchste)")
}

private data class SavedViewPreset(
    val id: String,
    val name: String,
    val searchQuery: String = "",
    val hashtagFilter: String = "",
    val onlyAssignedToMe: Boolean = false,
    val myUsername: String = "",
    val statusFilter: TodoStatusFilter = TodoStatusFilter.ALL,
    val priorityFilter: Priority? = null,
    val onlyDueTodayOrOverdue: Boolean = false,
    val dateFromInput: String = "",
    val dateToInput: String = "",
    val sortOrder: TodoSortOrder = TodoSortOrder.POSITION,
    val isCustom: Boolean = true
)

private data class CustomStatusConfig(
    val id: String,
    val name: String,
    val colorHex: String,
    val isDone: Boolean = false
)

private val defaultListStatuses = listOf(
    CustomStatusConfig(id = "OPEN", name = "Offen", colorHex = "#64B5F6", isDone = false),
    CustomStatusConfig(id = "IN_PROGRESS", name = "In Bearbeitung", colorHex = "#FFB74D", isDone = false),
    CustomStatusConfig(id = "DONE", name = "Erledigt", colorHex = "#81C784", isDone = true)
)

private fun parseHexColor(hex: String): Color {
    return runCatching {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color((0xFF000000 or colorInt).toInt())
        } else {
            Color(colorInt.toInt())
        }
    }.getOrDefault(Color(0xFF64B5F6))
}

// Helper-Funktionen für benutzerdefinierte Zusatzfelder (Custom Fields)
private fun extractCustomFields(text: String): Map<String, String> {
    val regex = Regex("\\[([^:\\]]+):\\s*([^\\]]+)\\]")
    val map = mutableMapOf<String, String>()
    regex.findAll(text).forEach { match ->
        val key = match.groupValues[1].trim()
        val value = match.groupValues[2].trim()
        if (key.isNotEmpty()) {
            map[key] = value
        }
    }
    return map
}

private fun removeCustomField(text: String, keyToRemove: String): String {
    val regex = Regex("\\[${Regex.escape(keyToRemove)}:\\s*([^\\]]+)\\]")
    return text.replace(regex, "").replace(Regex("\\s+"), " ").trim()
}

private fun setCustomField(text: String, key: String, value: String): String {
    val cleaned = removeCustomField(text, key)
    return if (value.isBlank()) cleaned else "$cleaned [$key: ${value.trim()}]"
}

private fun cleanTitleText(text: String): String {
    val regex = Regex("\\[([^:\\]]+):\\s*([^\\]]+)\\]")
    return text.replace(regex, "").replace(Regex("\\s+"), " ").trim()
}

private fun extractItemStatus(item: TodoItem, statuses: List<CustomStatusConfig>): CustomStatusConfig {
    val fields = extractCustomFields(item.text)
    val statusName = fields["Status"]
    if (!statusName.isNullOrBlank()) {
        val found = statuses.find { it.name.equals(statusName, ignoreCase = true) }
        if (found != null) return found
    }
    return if (item.done) {
        statuses.find { it.isDone } ?: statuses.last()
    } else {
        statuses.find { !it.isDone } ?: statuses.first()
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

private fun formatDate(epochMs: Long?): String {
    if (epochMs == null) return ""
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}

private fun parseDateInput(input: String): Long? {
    if (input.isBlank()) return null
    return runCatching {
        val parts = input.trim().split(".")
        val date = LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }.getOrNull()
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TodoListPanelScreen(panelId: String) {
    val repo = remember { koinGet<TodoItemRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    val allItems by repo.observeItems(panelId).collectAsState(initial = emptyList())
    val topLevel = allItems.filter { it.parentId == null }
    fun subtasksOf(itemId: String) = allItems.filter { it.parentId == itemId }.sortedBy { it.position }

    // Status-Verwaltung pro Liste (Panel)
    var listStatuses by remember { mutableStateOf(defaultListStatuses) }
    var lastDoneTimestampMap by remember { mutableStateOf(mapOf<String, Long>()) }
    var showStatusSettingsDialog by remember { mutableStateOf(false) }

    // Alle eindeutigen eigenen Spalten (Custom Fields) in dieser Liste ermitteln
    val customColumnKeys = remember(allItems) {
        allItems.flatMap { extractCustomFields(it.text).keys }.filter { it != "Status" }.distinct()
    }

    var newItemText by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }

    // Preset & Filter-Zustände
    var activePresetId by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var hashtagFilter by remember { mutableStateOf("") }
    var onlyAssignedToMe by remember { mutableStateOf(false) }
    var myUsername by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(TodoStatusFilter.ALL) }
    var priorityFilter by remember { mutableStateOf<Priority?>(null) }
    var onlyDueTodayOrOverdue by remember { mutableStateOf(false) }
    var dateFromInput by remember { mutableStateOf("") }
    var dateToInput by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(TodoSortOrder.POSITION) }

    var customPresets by remember { mutableStateOf<List<SavedViewPreset>>(emptyList()) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val todayMs = remember {
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    // Filter- und Sortier-Logik
    val filteredTopLevel = topLevel.filter { item ->
        val terminDate = item.terminDate
        val dueDate = item.dueDate
        val cleanText = cleanTitleText(item.text)
        val matchesSearch = searchQuery.isBlank() || cleanText.contains(searchQuery, ignoreCase = true)
        val matchesHashtag = hashtagFilter.isBlank() || (
            if (hashtagFilter == "#") cleanText.contains("#")
            else cleanText.contains(hashtagFilter, ignoreCase = true)
        )
        val matchesAssignee = !onlyAssignedToMe || (
            myUsername.isNotBlank() && item.assigneeUsername?.equals(myUsername.trim(), ignoreCase = true) == true
        )
        val matchesStatus = when (statusFilter) {
            TodoStatusFilter.ALL -> true
            TodoStatusFilter.OPEN -> !item.done
            TodoStatusFilter.DONE -> item.done
        }
        val matchesPriority = priorityFilter == null || item.priority == priorityFilter
        val matchesDueTodayOrOverdue = !onlyDueTodayOrOverdue || (
            (terminDate != null && terminDate <= todayMs + 86_400_000L) ||
            (dueDate != null && dueDate <= todayMs + 86_400_000L)
        )
        val dateFromMs = parseDateInput(dateFromInput)
        val dateToMs = parseDateInput(dateToInput)
        val itemDate = terminDate ?: dueDate
        val matchesDateRange = (dateFromMs == null || (itemDate != null && itemDate >= dateFromMs)) &&
                               (dateToMs == null || (itemDate != null && itemDate <= dateToMs + 86_400_000L))

        matchesSearch && matchesHashtag && matchesAssignee && matchesStatus && matchesPriority && matchesDueTodayOrOverdue && matchesDateRange
    }.sortedWith(
        when (sortOrder) {
            TodoSortOrder.POSITION -> compareBy { it.position }
            TodoSortOrder.TITLE_ASC -> compareBy { cleanTitleText(it.text).lowercase() }
            TodoSortOrder.TITLE_DESC -> compareByDescending { cleanTitleText(it.text).lowercase() }
            TodoSortOrder.TERMIN_ASC -> compareBy { it.terminDate ?: Long.MAX_VALUE }
            TodoSortOrder.DUE_ASC -> compareBy { it.dueDate ?: Long.MAX_VALUE }
            TodoSortOrder.PRIORITY_DESC -> compareByDescending { it.priority.ordinal }
        }
    )

    val doneCount = topLevel.count { it.done }
    val progressText = if (topLevel.isNotEmpty()) "$doneCount von ${topLevel.size} erledigt" else "Keine Aufgaben"

    fun save(item: TodoItem) {
        scope.launch {
            repo.upsert(item)
            runCatching { syncManager.pushTodoItemIfNeeded(item) }
        }
    }

    fun applyPreset(preset: SavedViewPreset) {
        activePresetId = preset.id
        searchQuery = preset.searchQuery
        hashtagFilter = preset.hashtagFilter
        onlyAssignedToMe = preset.onlyAssignedToMe
        myUsername = preset.myUsername
        statusFilter = preset.statusFilter
        priorityFilter = preset.priorityFilter
        onlyDueTodayOrOverdue = preset.onlyDueTodayOrOverdue
        dateFromInput = preset.dateFromInput
        dateToInput = preset.dateToInput
        sortOrder = preset.sortOrder
    }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Checklist,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Todoliste",
            subtitle = progressText,
            badgeLabel = "PANEL"
        )

        GitHubLinkBar(panelId = panelId)

        Row(modifier = Modifier.fillMaxSize()) {
            // Hauptinhalt Links
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp)) {
                // Neue Aufgabe - schnell, nur Text
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

                Spacer(Modifier.height(12.dp))

                // Presets & Custom Views Leiste
                SavedViewsPresetsBar(
                    activePresetId = activePresetId,
                    customPresets = customPresets,
                    onSelectPreset = { applyPreset(it) },
                    onDeletePreset = { presetToDelete ->
                        customPresets = customPresets.filter { it.id != presetToDelete.id }
                        if (activePresetId == presetToDelete.id) {
                            activePresetId = "ALL"
                        }
                    },
                    onOpenFilterConfig = { showFilterDialog = true },
                    onOpenStatusSettings = { showStatusSettingsDialog = true }
                )

                Spacer(Modifier.height(8.dp))

                // Schnelle Filter & Suche Toolbar
                TodoListFilterToolbar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    statusFilter = statusFilter,
                    onStatusFilterChange = { statusFilter = it },
                    priorityFilter = priorityFilter,
                    onPriorityFilterChange = { priorityFilter = it },
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrder = it },
                    onOpenAdvancedFilter = { showFilterDialog = true },
                    onResetFilters = {
                        activePresetId = "ALL"
                        searchQuery = ""
                        hashtagFilter = ""
                        onlyAssignedToMe = false
                        statusFilter = TodoStatusFilter.ALL
                        priorityFilter = null
                        onlyDueTodayOrOverdue = false
                        dateFromInput = ""
                        dateToInput = ""
                        sortOrder = TodoSortOrder.POSITION
                    }
                )

                Spacer(Modifier.height(12.dp))

                if (filteredTopLevel.isNotEmpty()) {
                    TableHeader(customKeys = customColumnKeys)
                    Spacer(Modifier.height(4.dp))
                } else if (topLevel.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Keine Aufgaben entsprechen den Filterkriterien", color = notionColors.textSecondary, fontSize = 13.sp)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(filteredTopLevel, key = { it.id }) { item ->
                        val subtasks = subtasksOf(item.id)
                        val isSelected = editingItem?.id == item.id
                        Column {
                            NotionTodoRow(
                                item = item,
                                availableStatuses = listStatuses,
                                lastDoneMs = lastDoneTimestampMap[item.id],
                                customKeys = customColumnKeys,
                                subtaskCount = subtasks.size,
                                subtaskDoneCount = subtasks.count { it.done },
                                isExpanded = item.id in expandedIds,
                                isSelected = isSelected,
                                onStatusChange = { newStatus, doneMs ->
                                    val updatedText = setCustomField(item.text, "Status", newStatus.name)
                                    val updated = item.copy(
                                        done = newStatus.isDone,
                                        text = updatedText
                                    )
                                    save(updated)
                                    if (doneMs != null) {
                                        lastDoneTimestampMap = lastDoneTimestampMap + (item.id to doneMs)
                                    }
                                    if (editingItem?.id == item.id) {
                                        editingItem = updated
                                    }
                                },
                                onToggleExpand = {
                                    expandedIds = if (item.id in expandedIds) expandedIds - item.id else expandedIds + item.id
                                },
                                onOpenDetails = { editingItem = item }
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

            // Rechte Seitenleiste (Inspector) für Details, Eigene Spalten & Bearbeitung
            editingItem?.let { currentEditingItem ->
                key(currentEditingItem.id) {
                    TodoDetailSidebar(
                        panelId = panelId,
                        item = currentEditingItem,
                        subtasks = subtasksOf(currentEditingItem.id),
                        availableStatuses = listStatuses,
                        syncManager = syncManager,
                        onDismiss = { editingItem = null },
                        onSave = { updated ->
                            save(updated)
                            editingItem = updated
                        },
                        onAddSubtask = { text ->
                            val sub = TodoItem(
                                id = Uuid.random().toString(),
                                panelId = panelId,
                                parentId = currentEditingItem.id,
                                text = text,
                                position = subtasksOf(currentEditingItem.id).size
                            )
                            save(sub)
                        },
                        onToggleSubtask = { sub -> save(sub.copy(done = !sub.done)) },
                        onDeleteSubtask = { subId -> scope.launch { repo.delete(subId) } },
                        onDelete = {
                            val toDeleteId = currentEditingItem.id
                            editingItem = null
                            scope.launch { repo.delete(toDeleteId) }
                        }
                    )
                }
            }
        }
    }

    // Erweiterter Filter & Ansichten-Dialog
    if (showFilterDialog) {
        AdvancedFilterConfigDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            hashtagFilter = hashtagFilter,
            onHashtagFilterChange = { hashtagFilter = it },
            onlyAssignedToMe = onlyAssignedToMe,
            onOnlyAssignedToMeChange = { onlyAssignedToMe = it },
            myUsername = myUsername,
            onMyUsernameChange = { myUsername = it },
            statusFilter = statusFilter,
            onStatusFilterChange = { statusFilter = it },
            priorityFilter = priorityFilter,
            onPriorityFilterChange = { priorityFilter = it },
            onlyDueTodayOrOverdue = onlyDueTodayOrOverdue,
            onOnlyDueTodayOrOverdueChange = { onlyDueTodayOrOverdue = it },
            dateFromInput = dateFromInput,
            onDateFromInputChange = { dateFromInput = it },
            dateToInput = dateToInput,
            onDateToInputChange = { dateToInput = it },
            sortOrder = sortOrder,
            onSortOrderChange = { sortOrder = it },
            onSaveAsPreset = { presetName ->
                val newPreset = SavedViewPreset(
                    id = Uuid.random().toString(),
                    name = presetName,
                    searchQuery = searchQuery,
                    hashtagFilter = hashtagFilter,
                    onlyAssignedToMe = onlyAssignedToMe,
                    myUsername = myUsername,
                    statusFilter = statusFilter,
                    priorityFilter = priorityFilter,
                    onlyDueTodayOrOverdue = onlyDueTodayOrOverdue,
                    dateFromInput = dateFromInput,
                    dateToInput = dateToInput,
                    sortOrder = sortOrder,
                    isCustom = true
                )
                customPresets = customPresets + newPreset
                activePresetId = newPreset.id
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    // Listeneinstellungen: Status-Verwaltung & Badge Design Dialog
    if (showStatusSettingsDialog) {
        TodoListStatusSettingsDialog(
            statuses = listStatuses,
            onSaveStatuses = { updatedStatuses ->
                listStatuses = updatedStatuses
                showStatusSettingsDialog = false
            },
            onDismiss = { showStatusSettingsDialog = false }
        )
    }
}

@Composable
private fun SavedViewsPresetsBar(
    activePresetId: String,
    customPresets: List<SavedViewPreset>,
    onSelectPreset: (SavedViewPreset) -> Unit,
    onDeletePreset: (SavedViewPreset) -> Unit,
    onOpenFilterConfig: () -> Unit,
    onOpenStatusSettings: () -> Unit
) {
    val notionColors = LocalNotionColors.current
    val defaultPresets = listOf(
        SavedViewPreset(id = "ALL", name = "Alle Aufgaben", isCustom = false),
        SavedViewPreset(id = "URGENT_MINE", name = "Dringend & Mir zugeteilt", priorityFilter = Priority.URGENT, onlyAssignedToMe = true, isCustom = false),
        SavedViewPreset(id = "DUE_TODAY", name = "Heute & Überfällig", onlyDueTodayOrOverdue = true, sortOrder = TodoSortOrder.TERMIN_ASC, isCustom = false),
        SavedViewPreset(id = "HASHTAGS", name = "#Hashtag Aufgaben", hashtagFilter = "#", sortOrder = TodoSortOrder.TITLE_DESC, isCustom = false)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("ANSICHTEN:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)

        (defaultPresets + customPresets).forEach { preset ->
            val isSelected = activePresetId == preset.id
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else notionColors.border
                ),
                modifier = Modifier.clickable { onSelectPreset(preset) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        preset.name,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else notionColors.textSecondary
                    )
                    if (preset.isCustom) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Ansicht löschen",
                            tint = notionColors.textSecondary,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable { onDeletePreset(preset) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onOpenStatusSettings, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Status & Badges", fontSize = 11.sp)
        }

        TextButton(onClick = onOpenFilterConfig, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Filter / Ansicht anpassen", fontSize = 11.sp)
        }
    }
}

@Composable
private fun TodoListFilterToolbar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    statusFilter: TodoStatusFilter,
    onStatusFilterChange: (TodoStatusFilter) -> Unit,
    priorityFilter: Priority?,
    onPriorityFilterChange: (Priority?) -> Unit,
    sortOrder: TodoSortOrder,
    onSortOrderChange: (TodoSortOrder) -> Unit,
    onOpenAdvancedFilter: () -> Unit,
    onResetFilters: () -> Unit
) {
    val notionColors = LocalNotionColors.current
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, notionColors.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Suchfeld
        Row(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, notionColors.border, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = "Suchen", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Aufgaben filtern...", fontSize = 12.sp, color = notionColors.textSecondary) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)
            )
        }

        // Status Filter Chips
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TodoStatusFilter.entries.forEach { sf ->
                val selected = statusFilter == sf
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else notionColors.border),
                    modifier = Modifier.clickable { onStatusFilterChange(sf) }
                ) {
                    Text(
                        sf.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else notionColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Sortierung Dropdown
        Box {
            Surface(
                color = if (sortOrder != TodoSortOrder.POSITION) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (sortOrder != TodoSortOrder.POSITION) MaterialTheme.colorScheme.primary else notionColors.border),
                modifier = Modifier.clickable { sortDropdownExpanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp), tint = notionColors.textSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sortOrder.label,
                        fontSize = 11.sp,
                        fontWeight = if (sortOrder != TodoSortOrder.POSITION) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortOrder != TodoSortOrder.POSITION) MaterialTheme.colorScheme.primary else notionColors.textSecondary
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = notionColors.textSecondary)
                }
            }

            DropdownMenu(
                expanded = sortDropdownExpanded,
                onDismissRequest = { sortDropdownExpanded = false }
            ) {
                TodoSortOrder.entries.forEach { so ->
                    DropdownMenuItem(
                        text = { Text(so.label, fontSize = 12.sp) },
                        onClick = { onSortOrderChange(so); sortDropdownExpanded = false }
                    )
                }
            }
        }

        // Reset Filter Button if active
        IconButton(
            onClick = onResetFilters,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.FilterListOff, contentDescription = "Filter zurücksetzen", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TableHeader(customKeys: List<String>) {
    val notionColors = LocalNotionColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(36.dp)) // Status Checkbox-Breite
        Text("AUFGABE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.weight(1f))
        Text("ZUGEWIESEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(110.dp))
        Text("DRINGLICHKEIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(110.dp))
        Text("TERMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(90.dp))
        Text("ENDFRIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(90.dp))

        // Dynamische Custom Field Spalten-Header
        customKeys.forEach { key ->
            Text(key.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary, modifier = Modifier.width(100.dp))
        }

        Spacer(Modifier.width(36.dp)) // Expand
    }
}

@Composable
private fun RenderTodoTextWithHashtags(text: String, isDone: Boolean) {
    val notionColors = LocalNotionColors.current
    val cleanText = cleanTitleText(text)
    val words = cleanText.split(" ")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth()) {
        words.forEachIndexed { idx, word ->
            if (word.startsWith("#") && word.length > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        word,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            } else {
                Text(
                    text = word + if (idx < words.size - 1) " " else "",
                    style = if (isDone) MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough, fontSize = 14.sp)
                            else MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = if (isDone) notionColors.textSecondary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SmartStatusCheckbox(
    item: TodoItem,
    availableStatuses: List<CustomStatusConfig>,
    lastDoneMs: Long?,
    onStatusChange: (CustomStatusConfig, Long?) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val currentStatus = extractItemStatus(item, availableStatuses)
    val statusColor = parseHexColor(currentStatus.colorHex)

    Box {
        Surface(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.5.dp, statusColor, RoundedCornerShape(6.dp))
                .pointerInput(item.id, currentStatus) {
                    detectTapGestures(
                        onTap = {
                            val now = Clock.System.now().toEpochMilliseconds()
                            val isDoneState = currentStatus.isDone
                            val isInProgressState = currentStatus.name.equals("In Bearbeitung", ignoreCase = true)

                            val nextStatus: CustomStatusConfig
                            var nextDoneMs: Long? = lastDoneMs

                            if (!isDoneState && !isInProgressState) {
                                // Offen -> Erledigt
                                nextStatus = availableStatuses.find { it.isDone } ?: availableStatuses.last()
                                nextDoneMs = now
                            } else if (isDoneState) {
                                // Erledigt -> check timing
                                if (lastDoneMs != null && (now - lastDoneMs) <= 10_000L) {
                                    // innerhalb 10 Sek -> In Bearbeitung
                                    nextStatus = availableStatuses.find { it.name.equals("In Bearbeitung", ignoreCase = true) }
                                        ?: availableStatuses.find { !it.isDone }
                                        ?: availableStatuses.first()
                                } else {
                                    // Später -> Offen
                                    nextStatus = availableStatuses.find { !it.isDone && !it.name.equals("In Bearbeitung", ignoreCase = true) }
                                        ?: availableStatuses.first()
                                }
                            } else {
                                // In Bearbeitung -> Erledigt
                                nextStatus = availableStatuses.find { it.isDone } ?: availableStatuses.last()
                                nextDoneMs = now
                            }

                            onStatusChange(nextStatus, nextDoneMs)
                        },
                        onLongPress = { dropdownExpanded = true }
                    )
                },
            color = if (currentStatus.isDone) statusColor.copy(alpha = 0.25f) else statusColor.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (currentStatus.isDone) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = currentStatus.name,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (currentStatus.name.equals("In Bearbeitung", ignoreCase = true)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false }
        ) {
            Text(
                "STATUS WÄHLEN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = LocalNotionColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            HorizontalDivider()
            availableStatuses.forEach { st ->
                val col = parseHexColor(st.colorHex)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(col)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(st.name, fontSize = 12.sp, fontWeight = if (st.id == currentStatus.id) FontWeight.Bold else FontWeight.Normal)
                        }
                    },
                    onClick = {
                        dropdownExpanded = false
                        val now = Clock.System.now().toEpochMilliseconds()
                        val nextDoneMs = if (st.isDone) now else lastDoneMs
                        onStatusChange(st, nextDoneMs)
                    }
                )
            }
        }
    }
}

@Composable
private fun NotionTodoRow(
    item: TodoItem,
    availableStatuses: List<CustomStatusConfig>,
    lastDoneMs: Long?,
    customKeys: List<String>,
    subtaskCount: Int,
    subtaskDoneCount: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onStatusChange: (CustomStatusConfig, Long?) -> Unit,
    onToggleExpand: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val notionColors = LocalNotionColors.current
    val customFields = extractCustomFields(item.text)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onOpenDetails() },
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartStatusCheckbox(
                item = item,
                availableStatuses = availableStatuses,
                lastDoneMs = lastDoneMs,
                onStatusChange = onStatusChange
            )

            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                RenderTodoTextWithHashtags(text = item.text, isDone = item.done)
            }

            Box(Modifier.width(110.dp)) { AssigneeChip(item.assigneeUsername) }
            Box(Modifier.width(110.dp)) { PriorityChip(item.priority) }
            Box(Modifier.width(90.dp)) { DateText(item.terminDate) }
            Box(Modifier.width(90.dp)) { DateText(item.dueDate) }

            // Rendern der eigenen Zusatzfeld-Spalten
            customKeys.forEach { key ->
                val valStr = customFields[key]
                Box(Modifier.width(100.dp)) {
                    if (!valStr.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                valStr,
                                fontSize = 11.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text("—", fontSize = 12.sp, color = notionColors.textSecondary)
                    }
                }
            }

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
            text = cleanTitleText(item.text),
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

/**
 * Rechte Seitenleiste (Inspector) für Aufgaben-Details, Einstellungsanpassung, Eigene Spalten & Unteraufgaben.
 */
@Composable
private fun TodoDetailSidebar(
    panelId: String,
    item: TodoItem,
    subtasks: List<TodoItem>,
    availableStatuses: List<CustomStatusConfig>,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit,
    onAddSubtask: (String) -> Unit,
    onToggleSubtask: (TodoItem) -> Unit,
    onDeleteSubtask: (String) -> Unit,
    onDelete: () -> Unit
) {
    val notionColors = LocalNotionColors.current
    var titleText by remember(item.id) { mutableStateOf(cleanTitleText(item.text)) }
    var priority by remember(item.id) { mutableStateOf(item.priority) }
    var terminInput by remember(item.id) { mutableStateOf(formatDate(item.terminDate)) }
    var dueInput by remember(item.id) { mutableStateOf(formatDate(item.dueDate)) }
    var assignee by remember(item.id) { mutableStateOf(item.assigneeUsername ?: "") }
    var newSubtaskText by remember { mutableStateOf("") }
    var assigneeSuggestions by remember { mutableStateOf<List<UserDto>>(emptyList()) }

    // Status Selector im Inspector
    val currentStatus = extractItemStatus(item, availableStatuses)

    // Eigene Zusatzfelder für diese Aufgabe
    var customFieldsMap by remember(item.id) { mutableStateOf(extractCustomFields(item.text)) }
    var newFieldKey by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

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

    fun updateFullText(newTitle: String, fields: Map<String, String>): String {
        var result = newTitle.trim()
        fields.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                result = "$result [$k: ${v.trim()}]"
            }
        }
        return result
    }

    val borderColor = notionColors.border

    Surface(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header: Titel + Schließen Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "DETAILS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = notionColors.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Haupttitel der Aufgabe & Status Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { newT ->
                            titleText = newT
                            onSave(item.copy(text = updateFullText(newT, customFieldsMap)))
                        },
                        placeholder = { Text("Titel der Aufgabe") },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // STATUS WÄHLER IM INSPECTOR
                Text("STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableStatuses.forEach { st ->
                        val selected = st.id == currentStatus.id
                        val col = parseHexColor(st.colorHex)
                        Surface(
                            color = if (selected) col.copy(alpha = 0.25f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) col else notionColors.border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val updatedFields = customFieldsMap + ("Status" to st.name)
                                    customFieldsMap = updatedFields
                                    onSave(item.copy(done = st.isDone, text = updateFullText(titleText, updatedFields)))
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    st.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) col else notionColors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = notionColors.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))

                // Dringlichkeit / Priorität
                Text("DRINGLICHKEIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Priority.entries.forEach { p ->
                        val selected = priority == p
                        Surface(
                            color = if (selected) priorityColor(p).copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) priorityColor(p) else notionColors.border
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    priority = p
                                    onSave(item.copy(priority = p))
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    priorityLabel(p),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) priorityColor(p) else notionColors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Zugewiesen an
                Text("ZUGEWIESEN AN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedTextField(
                        value = assignee,
                        onValueChange = {
                            assignee = it
                            onSave(item.copy(assigneeUsername = it.ifBlank { null }))
                        },
                        placeholder = { Text("Nutzername eingeben...") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (assigneeSuggestions.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.padding(top = 54.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, notionColors.border),
                            shadowElevation = 4.dp
                        ) {
                            Column {
                                assigneeSuggestions.take(5).forEach { user ->
                                    Text(
                                        "${user.username} (${user.name})",
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                assignee = user.username
                                                assigneeSuggestions = emptyList()
                                                onSave(item.copy(assigneeUsername = user.username))
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Datum-Felder: Termin & Endfrist
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TERMIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = terminInput,
                            onValueChange = { newVal ->
                                terminInput = newVal
                                onSave(item.copy(terminDate = parseDateInput(newVal)))
                            },
                            placeholder = { Text("TT.MM.JJJJ") },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("ENDFRIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = dueInput,
                            onValueChange = { newVal ->
                                dueInput = newVal
                                onSave(item.copy(dueDate = parseDateInput(newVal)))
                            },
                            placeholder = { Text("TT.MM.JJJJ") },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = notionColors.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))

                // EIGENE ZUSATZFELDER (Custom Columns)
                Text("EIGENE SPALTEN / ZUSATZFELDER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = notionColors.textSecondary)
                Spacer(Modifier.height(8.dp))

                val editableCustomFields = customFieldsMap.filterKeys { it != "Status" }
                if (editableCustomFields.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        editableCustomFields.forEach { (key, valStr) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(key, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(90.dp))
                                Text(valStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val updatedMap = customFieldsMap - key
                                        customFieldsMap = updatedMap
                                        onSave(item.copy(text = updateFullText(titleText, updatedMap)))
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Entfernen", tint = notionColors.textSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Neues Zusatzfeld hinzufügen
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newFieldKey,
                        onValueChange = { newFieldKey = it },
                        placeholder = { Text("Spaltenname") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = newFieldValue,
                        onValueChange = { newFieldValue = it },
                        placeholder = { Text("Wert") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newFieldKey.isNotBlank() && newFieldValue.isNotBlank()) {
                                val updatedMap = customFieldsMap + (newFieldKey.trim() to newFieldValue.trim())
                                customFieldsMap = updatedMap
                                onSave(item.copy(text = updateFullText(titleText, updatedMap)))
                                newFieldKey = ""
                                newFieldValue = ""
                            }
                        },
                        enabled = newFieldKey.isNotBlank() && newFieldValue.isNotBlank(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Hinzufügen", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = notionColors.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))

                // Unteraufgaben / Subtasks für dieses Todo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "UNTERAUFGABEN (${subtasks.count { it.done }}/${subtasks.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = notionColors.textSecondary
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (subtasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        subtasks.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sub.done,
                                    onCheckedChange = { onToggleSubtask(sub) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = notionColors.textSecondary
                                    ),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = cleanTitleText(sub.text),
                                    fontSize = 13.sp,
                                    color = if (sub.done) notionColors.textSecondary else MaterialTheme.colorScheme.onSurface,
                                    style = if (sub.done) androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteSubtask(sub.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Löschen", tint = notionColors.textSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Neue Unteraufgabe direkt in der Seitenleiste hinzufügen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, notionColors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    TextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        placeholder = { Text("Unteraufgabe hinzufügen...", fontSize = 12.sp, color = notionColors.textSecondary) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                            if (newSubtaskText.isNotBlank()) {
                                onAddSubtask(newSubtaskText)
                                newSubtaskText = ""
                            }
                        })
                    )
                    if (newSubtaskText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onAddSubtask(newSubtaskText)
                                newSubtaskText = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Hinzufügen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = notionColors.border.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            // Footer / Aktionsleiste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Löschen", fontSize = 12.sp)
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Fertig", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Dialog zur Status-Verwaltung & Badge Design pro Liste
 */
@Composable
private fun TodoListStatusSettingsDialog(
    statuses: List<CustomStatusConfig>,
    onSaveStatuses: (List<CustomStatusConfig>) -> Unit,
    onDismiss: () -> Unit
) {
    var statusList by remember { mutableStateOf(statuses) }
    var newName by remember { mutableStateOf("") }
    var newColorHex by remember { mutableStateOf("#9C27B0") }
    var newIsDone by remember { mutableStateOf(false) }

    val presetColors = listOf("#64B5F6", "#FFB74D", "#81C784", "#E53935", "#AB47BC", "#26A69A", "#FF7043", "#78909C")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Status-Verwaltung & Badge Design", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("VERFÜGBARE STATI FÜR DIESE LISTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalNotionColors.current.textSecondary)
                Spacer(Modifier.height(8.dp))

                statusList.forEachIndexed { index, st ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(st.colorHex))
                        )

                        Text(st.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

                        if (st.isDone) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Erledigt-Status", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        if (statusList.size > 1) {
                            IconButton(
                                onClick = { statusList = statusList.filterIndexed { idx, _ -> idx != index } },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Löschen", tint = LocalNotionColors.current.textSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalNotionColors.current.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                Text("NEUEN STATUS HINZUFÜGEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalNotionColors.current.textSecondary)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Status Name (z.B. In Review)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Text("Farbe wählen:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetColors.forEach { hex ->
                        val col = parseHexColor(hex)
                        val selected = newColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(width = if (selected) 2.dp else 0.dp, color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape = CircleShape)
                                .clickable { newColorHex = hex }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = newIsDone, onCheckedChange = { newIsDone = it })
                    Text("Dieser Status gilt als abgeschlossen / erledigt", fontSize = 12.sp)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val newSt = CustomStatusConfig(
                                id = "st_${Clock.System.now().toEpochMilliseconds()}_${(1000..9999).random()}",
                                name = newName.trim(),
                                colorHex = newColorHex,
                                isDone = newIsDone
                            )
                            statusList = statusList + newSt
                            newName = ""
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Status hinzufügen", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveStatuses(statusList) }) {
                Text("Speichern & Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/**
 * Erweiterter Filter- & Ansichten-Konfigurationsdialog
 */
@Composable
private fun AdvancedFilterConfigDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    hashtagFilter: String,
    onHashtagFilterChange: (String) -> Unit,
    onlyAssignedToMe: Boolean,
    onOnlyAssignedToMeChange: (Boolean) -> Unit,
    myUsername: String,
    onMyUsernameChange: (String) -> Unit,
    statusFilter: TodoStatusFilter,
    onStatusFilterChange: (TodoStatusFilter) -> Unit,
    priorityFilter: Priority?,
    onPriorityFilterChange: (Priority?) -> Unit,
    onlyDueTodayOrOverdue: Boolean,
    onOnlyDueTodayOrOverdueChange: (Boolean) -> Unit,
    dateFromInput: String,
    onDateFromInputChange: (String) -> Unit,
    dateToInput: String,
    onDateToInputChange: (String) -> Unit,
    sortOrder: TodoSortOrder,
    onSortOrderChange: (TodoSortOrder) -> Unit,
    onSaveAsPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPresetName by remember { mutableStateOf("") }
    var isSavingPreset by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Erweiterter Filter & Ansicht konfigurieren", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Hashtag Filter
                Text("HASHTAG / PRÄFIX FILTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalNotionColors.current.textSecondary)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = hashtagFilter,
                    onValueChange = onHashtagFilterChange,
                    placeholder = { Text("z.B. #dringend oder # (für alle mit #)") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Zuweisung (Mir zugeteilt)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyAssignedToMe, onCheckedChange = onOnlyAssignedToMeChange)
                    Text("Nur mir zugeteilt", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                if (onlyAssignedToMe) {
                    OutlinedTextField(
                        value = myUsername,
                        onValueChange = onMyUsernameChange,
                        label = { Text("Mein Benutzername") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Datumsfilter (Heute/Überfällig oder Datumsbereich)
                Text("DATUMS-FILTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalNotionColors.current.textSecondary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyDueTodayOrOverdue, onCheckedChange = onOnlyDueTodayOrOverdueChange)
                    Text("Heute noch gültig oder überfällig", fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = dateFromInput,
                        onValueChange = onDateFromInputChange,
                        label = { Text("Von (TT.MM.JJJJ)") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dateToInput,
                        onValueChange = onDateToInputChange,
                        label = { Text("Bis (TT.MM.JJJJ)") },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Sortierung
                Text("SORTIERUNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalNotionColors.current.textSecondary)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TodoSortOrder.entries.forEach { order ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSortOrderChange(order) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = sortOrder == order, onClick = { onSortOrderChange(order) })
                            Spacer(Modifier.width(6.dp))
                            Text(order.label, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalNotionColors.current.border.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))

                // Als benutzerdefinierte Ansicht speichern
                if (!isSavingPreset) {
                    TextButton(onClick = { isSavingPreset = true }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Diese Filterkombination als Ansicht speichern...", fontSize = 12.sp)
                    }
                } else {
                    Column {
                        OutlinedTextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            label = { Text("Name der Ansicht") },
                            placeholder = { Text("z.B. Meine Dringenden #Projekte") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (newPresetName.isNotBlank()) {
                                    onSaveAsPreset(newPresetName.trim())
                                    isSavingPreset = false
                                }
                            },
                            enabled = newPresetName.isNotBlank()
                        ) {
                            Text("Ansicht Speichern", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Übernehmen & Schließen") }
        }
    )
}
