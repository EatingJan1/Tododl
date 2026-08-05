package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
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
    onOrdnerClick: (id: String, titel: String) -> Unit,
    onTodoListPanelClick: (id: String, titel: String) -> Unit,
    onMindboardPanelClick: (id: String, titel: String) -> Unit
) {
    val repo = remember { koinGet<NodeRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()

    val nodes by remember(ordnerId) {
        if (ordnerId == null) repo.observeRootNodes(projectId) else repo.observeChildNodes(ordnerId)
    }.collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Element hinzufügen")
            }
        }
    ) { innerPadding ->
        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Leer – füge mit + einen Ordner oder ein Panel (Todoliste, Mindboard) hinzu.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes, key = { it.id }) { node ->
                    NodeCard(node) {
                        when (node.type) {
                            NodeType.ORDNER -> onOrdnerClick(node.id, node.title)
                            NodeType.PANEL_TODOLIST -> onTodoListPanelClick(node.id, node.title)
                            NodeType.PANEL_MINDBOARD -> onMindboardPanelClick(node.id, node.title)
                        }
                    }
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
                    // Bei Server-Projekten sofort synchronisieren; bei lokalen Projekten
                    // ist pushNodeIfNeeded ein No-Op (siehe SyncManager).
                    try {
                        syncManager.pushNodeIfNeeded(node)
                    } catch (e: Exception) {
                        // Bewusst still: lokale Änderung bleibt trotzdem erhalten,
                        // nächster manueller Sync (Projekt-Liste) holt es nach.
                    }
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun NodeCard(node: Node, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (node.type) {
                NodeType.ORDNER -> Icons.Default.Folder
                NodeType.PANEL_TODOLIST -> Icons.Default.Checklist
                NodeType.PANEL_MINDBOARD -> Icons.Default.Lightbulb
            }
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text(node.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NeuesElementDialog(
    onDismiss: () -> Unit,
    onConfirm: (titel: String, type: NodeType) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(NodeType.ORDNER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Element") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name (z. B. Essen, Todoliste)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Typ:", style = MaterialTheme.typography.labelMedium)
                Column(Modifier.selectableGroup()) {
                    TypeOption("Ordner", NodeType.ORDNER, selectedType) { selectedType = it }
                    TypeOption("Todoliste (Panel)", NodeType.PANEL_TODOLIST, selectedType) { selectedType = it }
                    TypeOption("Mindboard (Panel)", NodeType.PANEL_MINDBOARD, selectedType) { selectedType = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text, selectedType) }) { Text("Anlegen") }
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
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}
