package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
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

@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProjektListeScreen(bereichId: String, onProjektClick: (id: String, titel: String) -> Unit) {
    val repo = remember { koinGet<ProjektRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val connectionRepo = remember { koinGet<ServerConnectionRepository>() }
    val scope = rememberCoroutineScope()

    val projekte by repo.observeProjekte(bereichId).collectAsState(initial = emptyList())
    val connections by connectionRepo.observeConnections().collectAsState(initial = emptyList())

    var showNewDialog by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<Projekt?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Projekt hinzufügen")
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 8.dp))
            }

            if (projekte.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Projekte in diesem Bereich – lege mit + eines an.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projekte, key = { it.id }) { projekt ->
                        val connectionName = connections.firstOrNull { it.id == projekt.serverConnectionId }?.name
                        ProjektCard(
                            projekt = projekt,
                            serverName = connectionName,
                            onClick = { onProjektClick(projekt.id, projekt.title) },
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
private fun ProjektCard(
    projekt: Projekt,
    serverName: String?,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onShare: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (projekt.source == ProjectSource.SERVER) Icons.Default.CloudDone else Icons.Default.Computer,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(projekt.title, style = MaterialTheme.typography.titleMedium)
                val subtitle = listOfNotNull(serverName, projekt.description).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (projekt.source == ProjectSource.SERVER) {
                IconButton(onClick = onSync) {
                    Icon(Icons.Default.Sync, contentDescription = "Aktualisieren")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Teilen")
                }
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
        title = { Text("Neues Projekt") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("z. B. Maibaum aufstellen 2026") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Speicherort:", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
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
                if (connections.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kein Server verbunden – über das Server-Icon oben rechts einen hinzufügen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text, selectedConnection) }) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Projekt an eine Person ODER eine ganze Gruppe freigeben, mit wählbarer Rolle. */
@Composable
private fun FreigebenDialog(
    projekt: Projekt,
    connection: ServerConnection,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("PERSON") } // PERSON | GROUP
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("EDITOR") }
    var groups by remember { mutableStateOf<List<GroupDto>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<GroupDto?>(null) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(connection.id) {
        try {
            groups = syncManager.listGroups(connection)
        } catch (_: Exception) {
            // Gruppen-Auswahl bleibt dann einfach leer
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\"${projekt.title}\" teilen") },
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
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-Mail (muss bereits registriert sein)") },
                        singleLine = true
                    )
                } else {
                    Box {
                        OutlinedButton(onClick = { groupDropdownExpanded = true }) {
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
                    Text(
                        "Gruppen zuerst unter \"Meine Server\" -> Gruppen-Icon anlegen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Rolle:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "VIEWER", onClick = { role = "VIEWER" })
                    Text("Nur lesen", modifier = Modifier.padding(end = 8.dp))
                    RadioButton(selected = role == "EDITOR", onClick = { role = "EDITOR" })
                    Text("Bearbeiten", modifier = Modifier.padding(end = 8.dp))
                    RadioButton(selected = role == "OWNER", onClick = { role = "OWNER" })
                    Text("Owner")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        if (mode == "PERSON" && email.isNotBlank()) {
                            syncManager.grantUserAccess(projekt, email, role)
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
