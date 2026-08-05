package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
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
import de.tododl.shared.remote.GroupDto
import de.tododl.shared.remote.GroupMemberDto
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.ServerConnectionRepository
import kotlinx.coroutines.launch

@Composable
fun GruppenScreen(connectionId: String) {
    val connectionRepo = remember { koinGet<ServerConnectionRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

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
            errorText = e.message ?: "Laden fehlgeschlagen"
        }
    }

    LaunchedEffect(connectionId) { refresh() }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Group,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Server-Gruppen",
            subtitle = "Verwalte Gruppen wie Vorstand oder IT-Team zum schnellen Freigeben von Projekten",
            onAddAction = { showNewGroupDialog = true },
            addActionLabel = "Gruppe erstellen",
            badgeLabel = "SERVER"
        )

        errorText?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (groups.isEmpty() && errorText == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Noch keine Gruppen angelegt",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Erstelle eine Gruppe wie \"Vorstand\" oder \"IT-Team\" mit \"Gruppe erstellen\".",
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
                items(groups, key = { it.id }) { group ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, notionColors.border, RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(notionColors.badgeTodoList),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    group.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { addMemberTarget = group },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mitglied", fontSize = 12.sp)
                                }
                            }

                            val members = membersByGroup[group.id] ?: emptyList()
                            if (members.isNotEmpty()) {
                                HorizontalDivider(
                                    color = notionColors.border,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                                members.forEach { member ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${member.name ?: member.email} (${member.role})",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                                        )
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val connection = connectionRepo.getConnection(connectionId) ?: return@launch
                                                    syncManager.removeGroupMember(connection, group.id, member.userId)
                                                    refresh()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Entfernen",
                                                tint = notionColors.textSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
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
                            errorText = e.message ?: "Anlegen fehlgeschlagen"
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
                            errorText = e.message ?: "Hinzufügen fehlgeschlagen"
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
        title = { Text("Neue Gruppe anlegen", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (z. B. Vorstand, IT-Team)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Anlegen") }
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
        title = { Text("Mitglied zu \"$groupName\" hinzufügen", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail der Person") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Rolle in der Gruppe:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = role == "MEMBER", onClick = { role = "MEMBER" })
                    Text("Mitglied", modifier = Modifier.padding(end = 12.dp))
                    RadioButton(selected = role == "ADMIN", onClick = { role = "ADMIN" })
                    Text("Admin")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (email.isNotBlank()) onConfirm(email, role) }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
