package de.tododl.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.connectors.GitHubConnectorProvider
import de.tododl.desktop.connectors.GitHubSyncService
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.ConnectorAccount
import de.tododl.shared.model.ConnectorLink
import de.tododl.shared.model.GitHubExternalRef
import de.tododl.shared.repository.ConnectorAccountRepository
import de.tododl.shared.repository.ConnectorLinkRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Zeigt am Todolisten-Panel:
 *  - noch keine Verknüpfung: Button zum Verknüpfen mit einem GitHub-Repo
 *    (setzt einen vorher unter "Verbindungen" angelegten GitHub-Account voraus)
 *  - schon verknüpft: welches Repo, plus "Jetzt synchronisieren"-Button, der
 *    offene Issues als neue Todos anlegt (siehe GitHubSyncService).
 *
 * Basis-Implementierung: Sync ist manuell ausgelöst, kein Hintergrund-Job.
 */
@Composable
fun GitHubLinkBar(panelId: String) {
    val accountRepo = remember { koinGet<ConnectorAccountRepository>() }
    val linkRepo = remember { koinGet<ConnectorLinkRepository>() }
    val syncService = remember { koinGet<GitHubSyncService>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    var githubAccounts by remember { mutableStateOf<List<ConnectorAccount>>(emptyList()) }
    var link by remember { mutableStateOf<ConnectorLink?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(panelId) {
        githubAccounts = accountRepo.getAccountsFor(GitHubConnectorProvider.id)
        val githubAccountIds = githubAccounts.map { it.id }.toSet()
        linkRepo.observeLinksForNode(panelId).collectLatest { links ->
            link = links.firstOrNull { it.accountId in githubAccountIds }
        }
    }

    val currentLink = link
    if (currentLink == null && githubAccounts.isEmpty() && !showLinkDialog) {
        // Kein GitHub-Account vorhanden - Leiste bewusst nicht anzeigen, um die
        // Todoliste nicht mit einem Feature zuzumüllen, das noch nicht nutzbar ist.
        // Sobald unter "Verbindungen" ein GitHub-Account existiert, erscheint sie.
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = notionColors.textSecondary)
            Spacer(Modifier.width(8.dp))

            if (currentLink == null) {
                Text("Nicht mit GitHub verknüpft", fontSize = 12.sp, color = notionColors.textSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = { showLinkDialog = true }) { Text("Mit Repo verknüpfen") }
            } else {
                val ref = remember(currentLink) { runCatching { Json.decodeFromString<GitHubExternalRef>(currentLink.externalRefJson) }.getOrNull() }
                Text(
                    ref?.let { "${it.owner}/${it.repo}" } ?: "Verknüpftes Repo",
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                statusMessage?.let {
                    Text(it, fontSize = 11.sp, color = notionColors.textSecondary, modifier = Modifier.padding(end = 8.dp))
                }
                TextButton(
                    onClick = {
                        val account = githubAccounts.firstOrNull { it.id == currentLink.accountId } ?: return@TextButton
                        scope.launch {
                            isSyncing = true
                            statusMessage = null
                            runCatching { syncService.syncIssuesToTodos(currentLink, account) }
                                .onSuccess { count -> statusMessage = "$count neue Todo(s) aus Issues" }
                                .onFailure { statusMessage = "Sync fehlgeschlagen: ${it.message}" }
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSyncing) "Synchronisiert…" else "Jetzt synchronisieren")
                }
            }
        }
    }

    if (showLinkDialog) {
        LinkGitHubRepoDialog(
            accounts = githubAccounts,
            onDismiss = { showLinkDialog = false },
            onConfirm = { account, owner, repo ->
                scope.launch {
                    linkRepo.upsert(
                        ConnectorLink(
                            id = "link_${Clock.System.now().toEpochMilliseconds()}",
                            nodeId = panelId,
                            accountId = account.id,
                            externalRefJson = Json.encodeToString(GitHubExternalRef.serializer(), GitHubExternalRef(owner, repo))
                        )
                    )
                }
                showLinkDialog = false
            }
        )
    }
}

@Composable
private fun LinkGitHubRepoDialog(
    accounts: List<ConnectorAccount>,
    onDismiss: () -> Unit,
    onConfirm: (ConnectorAccount, owner: String, repo: String) -> Unit
) {
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mit GitHub-Repo verknüpfen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.isEmpty()) {
                    Text("Noch kein GitHub-Account verbunden. Zuerst unter 'Verbindungen' einen GitHub-Account anlegen.")
                } else {
                    if (accounts.size > 1) {
                        Text("GitHub-Account:", fontSize = 12.sp)
                        accounts.forEach { account ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedAccount?.id == account.id, onClick = { selectedAccount = account })
                                Text(account.label)
                            }
                        }
                    }
                    OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner (z.B. 'jan')") }, singleLine = true)
                    OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repo (z.B. 'tododl')") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedAccount?.let { onConfirm(it, owner.trim(), repo.trim()) } },
                enabled = selectedAccount != null && owner.isNotBlank() && repo.isNotBlank()
            ) { Text("Verknüpfen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
