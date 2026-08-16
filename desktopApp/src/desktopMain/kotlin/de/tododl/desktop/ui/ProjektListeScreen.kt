package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.ProjectSource
import de.tododl.shared.model.Projekt
import de.tododl.shared.model.ServerConnection
import de.tododl.shared.remote.GroupDto
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.ProjektRepository
import de.tododl.shared.repository.ServerConnectionRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun ProjektListeScreen(
    bereichId: String,
    bereichTitel: String,
    onProjektClick: (id: String, titel: String) -> Unit
) {
    val repo = remember { koinGet<ProjektRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val connectionRepo = remember { koinGet<ServerConnectionRepository>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    val projekte by repo.observeProjekte(bereichId).collectAsState(initial = emptyList())
    val connections by connectionRepo.observeConnections().collectAsState(initial = emptyList())

    var showNewDialog by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Projekt?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Folder,
            iconColor = MaterialTheme.colorScheme.primary,
            title = bereichTitel,
            subtitle = "Alle aktiven Projekte in diesem Bereich",
            onAddAction = { showNewDialog = true },
            addActionLabel = "Projekt erstellen",
            badgeLabel = "BEREICH"
        )

        errorText?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (projekte.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Keine Projekte vorhanden",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Erstelle ein neues Projekt mit \"Projekt erstellen\" oben rechts.",
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
                items(projekte, key = { it.id }) { projekt ->
                    val connectionName = connections.firstOrNull { it.id == projekt.serverConnectionId }?.name
                    NotionProjektCard(
                        projekt = projekt,
                        serverName = connectionName,
                        onClick = { onProjektClick(projekt.id, projekt.title) },
                        onArchive = {
                            scope.launch { repo.archive(projekt.id) }
                        },
                        onSync = {
                            scope.launch {
                                try {
                                    syncManager.pullProject(projekt.id)
                                    errorText = null
                                } catch (e: Exception) {
                                    errorText = "Sync fehlgeschlagen: ${e.message}"
                                }
                            }
                        },
                        onShare = { shareTarget = projekt }
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        NeuesProjektDialog(
            connections = connections,
            onDismiss = { showNewDialog = false },
            onConfirm = { titel, connection ->
                scope.launch {
                    try {
                        if (connection != null) {
                            syncManager.createServerProject(bereichId, titel, connection)
                        } else {
                            repo.upsert(
                                Projekt(
                                    id = Uuid.random().toString(),
                                    bereichId = bereichId,
                                    title = titel,
                                    source = ProjectSource.LOCAL,
                                    position = projekte.size
                                )
                            )
                        }
                        errorText = null
                    } catch (e: Exception) {
                        errorText = "Anlegen fehlgeschlagen: ${e.message}"
                    }
                }
                showNewDialog = false
            }
        )
    }

    shareTarget?.let { projekt ->
        val connection = connections.firstOrNull { it.id == projekt.serverConnectionId }
        if (connection != null) {
            FreigebenDialog(
                projekt = projekt,
                connection = connection,
                syncManager = syncManager,
                onDismiss = { shareTarget = null },
                onError = { errorText = it }
            )
        }
    }
}

@Composable
private fun NotionProjektCard(
    projekt: Projekt,
    serverName: String?,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onSync: () -> Unit,
    onShare: () -> Unit
) {
    val notionColors = LocalNotionColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, notionColors.border, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (projekt.source == ProjectSource.SERVER) notionColors.badgeTodoList else notionColors.badgeFolder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (projekt.source == ProjectSource.SERVER) Icons.Default.CloudDone else Icons.Default.Computer,
                    contentDescription = null,
                    tint = if (projekt.source == ProjectSource.SERVER) MaterialTheme.colorScheme.primary else notionColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = projekt.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                val sourceBadge = if (projekt.source == ProjectSource.SERVER) "Server: ${serverName ?: "Cloud"}" else "Lokal"
                Text(
                    text = sourceBadge,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = notionColors.textSecondary
                )
            }

            if (projekt.source == ProjectSource.SERVER) {
                IconButton(onClick = onSync, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Sync, contentDescription = "Aktualisieren", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Teilen", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
            }

            IconButton(onClick = onArchive, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Archive, contentDescription = "Archivieren", tint = notionColors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NeuesProjektDialog(
    connections: List<ServerConnection>,
    onDismiss: () -> Unit,
    onConfirm: (titel: String, connection: ServerConnection?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedConnection by remember { mutableStateOf<ServerConnection?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Projekt anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Projekt-Titel (z. B. Maibaum 2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text("Speicherort:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(selectedConnection?.name ?: "Nur lokal auf diesem Gerät")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Nur lokal auf diesem Gerät") },
                            onClick = { selectedConnection = null; expanded = false }
                        )
                        connections.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { selectedConnection = c; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text, selectedConnection) }) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun FreigebenDialog(
    projekt: Projekt,
    connection: ServerConnection,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("PERSON") }
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("EDITOR") }
    var groups by remember { mutableStateOf<List<GroupDto>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<GroupDto?>(null) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(connection.id) {
        try {
            groups = syncManager.listGroups(connection)
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\"${projekt.title}\" freigeben", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == "PERSON", onClick = { mode = "PERSON" })
                    Text("Person", modifier = Modifier.padding(end = 12.dp))
                    RadioButton(selected = mode == "GROUP", onClick = { mode = "GROUP" })
                    Text("Gruppe")
                }
                Spacer(Modifier.height(8.dp))

                if (mode == "PERSON") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Nutzername der Person") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { groupDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedGroup?.name ?: "Gruppe wählen")
                        }
                        DropdownMenu(
                            expanded = groupDropdownExpanded,
                            onDismissRequest = { groupDropdownExpanded = false }
                        ) {
                            if (groups.isEmpty()) {
                                DropdownMenuItem(text = { Text("Keine Gruppen vorhanden") }, onClick = {})
                            }
                            groups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g.name) },
                                    onClick = { selectedGroup = g; groupDropdownExpanded = false }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Berechtigung:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "VIEWER", onClick = { role = "VIEWER" })
                    Text("Lesen", modifier = Modifier.padding(end = 8.dp))
                    RadioButton(selected = role == "EDITOR", onClick = { role = "EDITOR" })
                    Text("Bearbeiten", modifier = Modifier.padding(end = 8.dp))
                    RadioButton(selected = role == "OWNER", onClick = { role = "OWNER" })
                    Text("Owner")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    try {
                        if (mode == "PERSON" && username.isNotBlank()) {
                            syncManager.grantUserAccess(projekt, username, role)
                        } else if (mode == "GROUP" && selectedGroup != null) {
                            syncManager.grantGroupAccess(projekt, selectedGroup!!.id, role)
                        }
                    } catch (e: Exception) {
                        onError("Teilen fehlgeschlagen: ${e.message}")
                    }
                }
                onDismiss()
            }) { Text("Freigeben") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
