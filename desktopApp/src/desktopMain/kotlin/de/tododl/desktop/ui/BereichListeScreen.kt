package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
import de.tododl.shared.model.Bereich
import de.tododl.shared.repository.BereichRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun BereichListeScreen(onBereichClick: (id: String, titel: String) -> Unit) {
    val repo = remember { koinGet<BereichRepository>() }
    val scope = rememberCoroutineScope()
    val bereiche by repo.observeBereiche().collectAsState(initial = emptyList())

    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Bereich hinzufügen")
            }
        }
    ) { innerPadding ->
        if (bereiche.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Noch keine Bereiche – lege mit + einen an (z. B. Verein, Privat, Arbeit).")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bereiche, key = { it.id }) { bereich ->
                    BereichCard(bereich) { onBereichClick(bereich.id, bereich.title) }
                }
            }
        }
    }

    if (showDialog) {
        NeuerBereichDialog(
            onDismiss = { showDialog = false },
            onConfirm = { titel ->
                scope.launch {
                    repo.upsert(
                        Bereich(
                            id = Uuid.random().toString(),
                            title = titel,
                            position = bereiche.size
                        )
                    )
                }
                showDialog = false
            }
        )
    }
}

@Composable
private fun BereichCard(bereich: Bereich, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Text(bereich.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NeuerBereichDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Bereich") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name (z. B. Verein, Privat, Arbeit)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
