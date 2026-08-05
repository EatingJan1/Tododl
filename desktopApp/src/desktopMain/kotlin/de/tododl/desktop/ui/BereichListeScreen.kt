package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
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
    val notionColors = LocalNotionColors.current

    var showDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Category,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Bereiche & Arbeitsbereiche",
            subtitle = "Wähle einen Bereich wie Verein, Privat oder Arbeit",
            onAddAction = { showDialog = true },
            addActionLabel = "Bereich erstellen",
            badgeLabel = "ÜBERSICHT"
        )

        if (bereiche.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Noch keine Bereiche angelegt",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Lege mit \"Bereich erstellen\" deinen ersten Bereich an (z. B. Verein, Privat, Arbeit).",
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
                items(bereiche, key = { it.id }) { bereich ->
                    NotionBereichCard(
                        bereich = bereich,
                        onClick = { onBereichClick(bereich.id, bereich.title) },
                        onDelete = {
                            scope.launch { repo.delete(bereich.id) }
                        }
                    )
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
private fun NotionBereichCard(
    bereich: Bereich,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                    .background(notionColors.badgeFolder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = bereich.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = notionColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NeuerBereichDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Bereich anlegen", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name (z. B. Verein, Privat, Arbeit)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
