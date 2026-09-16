package de.tododl.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.connectors.ConnectorProvider
import de.tododl.desktop.connectors.ConnectorRegistry
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.ConnectorAccount
import de.tododl.shared.repository.ConnectorAccountRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Basis-Bildschirm zur Verwaltung verbundener externer Konten (GitHub, Bring,
 * Google Drive, ...). Zeigt pro ConnectorProvider (siehe ConnectorRegistry)
 * die bereits verbundenen Accounts und erlaubt, neue anzulegen.
 *
 * NICHT enthalten (bewusst, siehe Anforderung "nur Basis"):
 *  - echter Login/OAuth-Flow je Provider (aktuell werden die Zugangsdaten nur
 *    als Text abgefragt und lokal gespeichert)
 *  - das Verknüpfen einzelner Nodes mit einer externen Ressource (ConnectorLink) -
 *    dafür bräuchte es einen "Verknüpfen"-Dialog direkt am jeweiligen Node/Panel
 *  - den Action-Regel-Editor für Todolisten (ActionRule) - dafür bräuchte es
 *    einen Dialog direkt am Todolisten-Panel ("Regel hinzufügen")
 *
 * Beides ist als nächster Ausbauschritt gedacht, sobald klar ist, wie diese
 * Dialoge ins bestehende Node-Baum-UI passen sollen.
 */
@Composable
fun ConnectorsScreen() {
    val repo = remember { koinGet<ConnectorAccountRepository>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    var accounts by remember { mutableStateOf<List<ConnectorAccount>>(emptyList()) }
    var providerForNewAccount by remember { mutableStateOf<ConnectorProvider?>(null) }
    var showGitHubLogin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.observeAccounts().collectLatest { accounts = it }
    }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Cable,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Verbindungen",
            subtitle = "Externe Dienste wie GitHub, Bring oder Google Drive verbinden",
            badgeLabel = "CONNECTORS"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(ConnectorRegistry.all) { provider ->
                ConnectorProviderCard(
                    provider = provider,
                    accounts = accounts.filter { it.providerId == provider.id },
                    onAddAccount = {
                        // GitHub hat einen echten Login-Flow (Device Flow, kein
                        // manuelles Token-Copy-Paste nötig) - andere Provider
                        // (Bring, Google Drive) haben das noch nicht, daher
                        // dort weiterhin das generische Formular.
                        if (provider.id == "github") showGitHubLogin = true
                        else providerForNewAccount = provider
                    },
                    onDeleteAccount = { account -> scope.launch { repo.delete(account.id) } }
                )
            }
        }
    }

    if (showGitHubLogin) {
        GitHubLoginDialog(
            onDismiss = { showGitHubLogin = false },
            onConnected = { }
        )
    }

    providerForNewAccount?.let { provider ->
        AddConnectorAccountDialog(
            provider = provider,
            onDismiss = { providerForNewAccount = null },
            onConfirm = { label, credentialsJson ->
                val newAccountId = "acc_${Clock.System.now().toEpochMilliseconds()}"
                scope.launch {
                    repo.upsert(
                        ConnectorAccount(
                            id = newAccountId,
                            providerId = provider.id,
                            label = label,
                            credentialsJson = credentialsJson
                        )
                    )
                    provider.onAccountConnected(newAccountId)
                }
                providerForNewAccount = null
            }
        )
    }
}

@Composable
private fun ConnectorProviderCard(
    provider: ConnectorProvider,
    accounts: List<ConnectorAccount>,
    onAddAccount: () -> Unit,
    onDeleteAccount: (ConnectorAccount) -> Unit
) {
    val notionColors = LocalNotionColors.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, notionColors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(provider.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.displayName, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(provider.description, fontSize = 12.sp, color = notionColors.textSecondary)
                }
                OutlinedButton(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verbinden")
                }
            }

            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("• ${account.label}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = { onDeleteAccount(account) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Trennen", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Noch nicht verbunden.", fontSize = 12.sp, color = notionColors.textSecondary)
            }
        }
    }
}

@Composable
private fun AddConnectorAccountDialog(
    provider: ConnectorProvider,
    onDismiss: () -> Unit,
    onConfirm: (label: String, credentialsJson: String) -> Unit
) {
    var label by remember { mutableStateOf(provider.displayName) }
    val fieldValues = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${provider.displayName} verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Anzeigename") },
                    singleLine = true
                )
                provider.credentialFields.forEach { field ->
                    OutlinedTextField(
                        value = fieldValues[field.key] ?: "",
                        onValueChange = { fieldValues[field.key] = it },
                        label = { Text(field.label) },
                        singleLine = true,
                        visualTransformation = if (field.isSecret)
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        else
                            androidx.compose.ui.text.input.VisualTransformation.None
                    )
                }
                Text(
                    "Hinweis: Dies ist noch ein Basis-Formular ohne echten Login-Flow - " +
                        "die Werte werden lokal gespeichert, aber (noch) nicht gegen ${provider.displayName} geprüft.",
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val credentialsJson = fieldValues.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
                    "\"$k\":\"${v.replace("\"", "\\\"")}\""
                }
                onConfirm(label, credentialsJson)
            }) { Text("Verbinden") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

