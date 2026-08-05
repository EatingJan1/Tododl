package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.Node
import de.tododl.shared.model.NodeType
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.NodeRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun NodeBaumScreen(
    projectId: String,
    ordnerId: String?,
    ordnerTitel: String?,
    projektTitel: String,
    onOrdnerClick: (id: String, titel: String) -> Unit,
    onTodoListPanelClick: (id: String, titel: String) -> Unit,
    onMindboardPanelClick: (id: String, titel: String) -> Unit
) {
    val repo = remember { koinGet<NodeRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    val nodes by remember(ordnerId) {
        if (ordnerId == null) repo.observeRootNodes(projectId) else repo.observeChildNodes(ordnerId)
    }.collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = if (ordnerId == null) Icons.Default.FolderSpecial else Icons.Default.FolderOpen,
            iconColor = MaterialTheme.colorScheme.primary,
            title = ordnerTitel ?: projektTitel,
            subtitle = if (ordnerId != null) "Ordner in $projektTitel" else "Projektinhalte & Dokumente",
            onAddAction = { showDialog = true },
            addActionLabel = "Element hinzufügen",
            badgeLabel = if (ordnerId == null) "PROJEKT" else "ORDNER"
        )

        if (nodes.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PostAdd,
                        contentDescription = null,
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Dieses Verzeichnis ist noch leer",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Lege mit \"Element hinzufügen\" einen Unterordner, eine Todoliste oder ein Mindboard an.",
                        style = MaterialTheme.typography.bodySmall,
                        color = notionColors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes, key = { it.id }) { node ->
                    NotionNodeCard(
                        node = node,
                        onClick = {
                            when (node.type) {
                                NodeType.ORDNER -> onOrdnerClick(node.id, node.title)
                                NodeType.PANEL_TODOLIST -> onTodoListPanelClick(node.id, node.title)
                                NodeType.PANEL_MINDBOARD -> onMindboardPanelClick(node.id, node.title)
                            }
                        },
                        onDelete = {
                            scope.launch { repo.delete(node.id) }
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        NeuesElementDialog(
            onDismiss = { showDialog = false },
            onConfirm = { titel, type ->
                scope.launch {
                    val node = Node(
                        id = Uuid.random().toString(),
                        projectId = projectId,
                        parentId = ordnerId,
                        type = type,
                        title = titel,
                        position = nodes.size
                    )
                    repo.upsert(node)
                    try {
                        syncManager.pushNodeIfNeeded(node)
                    } catch (_: Exception) {}
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun NotionNodeCard(
    node: Node,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val notionColors = LocalNotionColors.current

    val (icon, badgeBg, badgeText, badgeTitle) = when (node.type) {
        NodeType.ORDNER -> Quadruple(
            Icons.Default.Folder,
            notionColors.badgeFolder,
            notionColors.textSecondary,
            "ORDNER"
        )
        NodeType.PANEL_TODOLIST -> Quadruple(
            Icons.Default.Checklist,
            notionColors.badgeTodoList,
            MaterialTheme.colorScheme.primary,
            "TODOLISTE"
        )
        NodeType.PANEL_MINDBOARD -> Quadruple(
            Icons.Default.Lightbulb,
            notionColors.badgeMindboard,
            notionColors.badgeMindboard,
            "MINDBOARD"
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, notionColors.border, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeText,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                color = notionColors.hoverHighlight,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = badgeTitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = notionColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Löschen",
                    tint = notionColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun NeuesElementDialog(
    onDismiss: () -> Unit,
    onConfirm: (titel: String, type: NodeType) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(NodeType.ORDNER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Element anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Titel / Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Element-Typ:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.selectableGroup()) {
                    TypeOption("Ordner (enthält weitere Unterordner/Panels)", NodeType.ORDNER, selectedType) { selectedType = it }
                    TypeOption("Todoliste (Panel mit Aufgaben)", NodeType.PANEL_TODOLIST, selectedType) { selectedType = it }
                    TypeOption("Mindboard (Panel für freie Notizen)", NodeType.PANEL_MINDBOARD, selectedType) { selectedType = it }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text, selectedType) }) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun TypeOption(label: String, value: NodeType, selected: NodeType, onSelect: (NodeType) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp)
    }
}
