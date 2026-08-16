package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
import de.tododl.shared.model.ServerConnection
import de.tododl.shared.remote.TododlApiClient
import de.tododl.shared.repository.ServerConnectionRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Zeigt alle gespeicherten Server-Verbindungen (z. B. "Firma", "Privat",
 * "Verein" - jeweils eigener Server, eigener Login) und erlaubt, neue
 * hinzuzufügen oder zu entfernen. Von hier aus geht's auch zur Gruppen-Verwaltung
 * eines Servers.
 *
 * Es gibt hier bewusst KEINE Registrierung mehr - Nutzer werden ausschließlich
 * vom Admin über die /admin-Weboberfläche des jeweiligen Servers angelegt.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(onOpenGruppen: (connectionId: String, connectionName: String) -> Unit) {
    val repo = remember { koinGet<ServerConnectionRepository>() }
    val api = remember { koinGet<TododlApiClient>() }
    val scope = rememberCoroutineScope()

    val connections by repo.observeConnections().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Server hinzufügen")
            }
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 8.dp))
            }

            if (connections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch kein Server verbunden – mit + Firmen-, Privat- oder Vereinsserver hinzufügen.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(connections, key = { it.id }) { connection ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(connection.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${connection.baseUrl} – ${connection.displayName} (${connection.username})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { onOpenGruppen(connection.id, connection.name) }) {
                                    Icon(Icons.Default.Groups, contentDescription = "Gruppen verwalten")
                                }
                                IconButton(onClick = { scope.launch { repo.delete(connection.id) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Verbindung entfernen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, baseUrl, username, password ->
                scope.launch {
                    try {
                        val auth = api.login(baseUrl, username, password)
                        repo.upsert(
                            ServerConnection(
                                id = Uuid.random().toString(),
                                name = name,
                                baseUrl = baseUrl.trimEnd('/'),
                                accessToken = auth.accessToken,
                                userId = auth.user.id,
                                username = auth.user.username,
                                displayName = auth.user.name
                            )
                        )
                        errorText = null
                        showAddDialog = false
                    } catch (e: Exception) {
                        errorText = "Verbindung fehlgeschlagen: ${e.message}"
                    }
                }
            }
        )
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, username: String, password: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("http://127.0.0.1:5001") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server hinzufügen") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Anzeigename (z. B. Firma, Privat, Verein)") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server-URL") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nutzername") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passwort") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Noch keinen Account? Ein Admin muss dich über die /admin-Weboberfläche " +
                        "dieses Servers anlegen.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        onConfirm(name, baseUrl, username, password)
                    }
                }
            ) { Text("Verbinden") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
