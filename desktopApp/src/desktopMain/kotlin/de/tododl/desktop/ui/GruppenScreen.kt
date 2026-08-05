package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
import de.tododl.shared.remote.GroupDto
import de.tododl.shared.remote.GroupMemberDto
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.ServerConnectionRepository
import kotlinx.coroutines.launch

/**
 * Gruppen sind serverweit (nicht projektgebunden) - z. B. "Vorstand" oder
 * "IT-Team" - und können anschließend beim Teilen eines Projekts als Ganzes
 * eine Rolle bekommen (siehe ProjektListeScreen -> Einladen-Dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GruppenScreen(connectionId: String) {
    val connectionRepo = remember { koinGet<ServerConnectionRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<GroupDto>>(emptyList()) }
    var membersByGroup by remember { mutableStateOf<Map<String, List<GroupMemberDto>>>(emptyMap()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var addMemberTarget by remember { mutableStateOf<GroupDto?>(null) }

    suspend fun refresh() {
        val connection = connectionRepo.getConnection(connectionId) ?: return
        try {
            val list = syncManager.listGroups(connection)
            groups = list
            membersByGroup = list.associate { it.id to syncManager.listGroupMembers(connection, it.id) }
            errorText = null
        } catch (e: Exception) {
            errorText = "Laden fehlgeschlagen: ${e.message}"
        }
    }

    LaunchedEffect(connectionId) { refresh() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewGroupDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Gruppe anlegen")
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 8.dp))
            }

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Gruppen – mit + z. B. \"Vorstand\" oder \"IT-Team\" anlegen.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { addMemberTarget = group }) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = "Mitglied hinzufügen")
                                    }
                                }
                                (membersByGroup[group.id] ?: emptyList()).forEach { member ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${member.name ?: member.email} (${member.role})",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(onClick = {
                                            scope.launch {
                                                val connection = connectionRepo.getConnection(connectionId) ?: return@launch
                                                syncManager.removeGroupMember(connection, group.id, member.userId)
                                                refresh()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Entfernen")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewGroupDialog) {
        NeueGruppeDialog(
            onDismiss = { showNewGroupDialog = false },
            onConfirm = { groupName ->
                scope.launch {
                    val connection = connectionRepo.getConnection(connectionId)
                    if (connection != null) {
                        try {
                            syncManager.createGroup(connection, groupName)
                            refresh()
                        } catch (e: Exception) {
                            errorText = "Anlegen fehlgeschlagen: ${e.message}"
                        }
                    }
                }
                showNewGroupDialog = false
            }
        )
    }

    addMemberTarget?.let { group ->
        MitgliedHinzufuegenDialog(
            groupName = group.name,
            onDismiss = { addMemberTarget = null },
            onConfirm = { email, role ->
                scope.launch {
                    val connection = connectionRepo.getConnection(connectionId)
                    if (connection != null) {
                        try {
                            syncManager.addGroupMember(connection, group.id, email, role)
                            refresh()
                        } catch (e: Exception) {
                            errorText = "Hinzufügen fehlgeschlagen: ${e.message}"
                        }
                    }
                }
                addMemberTarget = null
            }
        )
    }
}

@Composable
private fun NeueGruppeDialog(onDismiss: () -> Unit, onConfirm: (name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Gruppe") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (z. B. Vorstand, IT-Team)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Anlegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun MitgliedHinzufuegenDialog(
    groupName: String,
    onDismiss: () -> Unit,
    onConfirm: (email: String, role: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("MEMBER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mitglied zu \"$groupName\" hinzufügen") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail (muss bereits registriert sein)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Rolle in der Gruppe:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "MEMBER", onClick = { role = "MEMBER" })
                    Text("Mitglied", modifier = Modifier.padding(end = 12.dp))
                    RadioButton(selected = role == "ADMIN", onClick = { role = "ADMIN" })
                    Text("Admin (kann Gruppe verwalten)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (email.isNotBlank()) onConfirm(email, role) }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
