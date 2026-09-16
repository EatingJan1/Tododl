package de.tododl.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.shared.model.ConnectorAccount
import de.tododl.shared.model.GitHubCredentials
import de.tododl.shared.remote.GitHubDeviceFlowClient
import de.tododl.shared.remote.GitHubOAuthConfig
import de.tododl.shared.repository.ConnectorAccountRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.URI

private sealed interface DeviceFlowState {
    data object Idle : DeviceFlowState
    data object RequestingCode : DeviceFlowState
    data class AwaitingConfirmation(val userCode: String, val verificationUri: String) : DeviceFlowState
    data object Success : DeviceFlowState
    data class Error(val message: String) : DeviceFlowState
}

/**
 * Ersetzt die manuelle Token-Eingabe für GitHub: Button -> GitHub öffnet sich
 * im Browser mit einem Code -> sobald dort bestätigt, merkt die App das
 * automatisch (Polling im Hintergrund) und legt den Account selbst an.
 *
 * Voraussetzung: GitHubOAuthConfig.CLIENT_ID muss einmalig gesetzt sein
 * (siehe Kommentar dort) - ist das nicht der Fall, wird das erklärt statt
 * einen kryptischen API-Fehler zu zeigen.
 */
@Composable
fun GitHubLoginDialog(onDismiss: () -> Unit, onConnected: () -> Unit) {
    val deviceFlowClient = remember { koinGet<GitHubDeviceFlowClient>() }
    val accountRepo = remember { koinGet<ConnectorAccountRepository>() }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<DeviceFlowState>(DeviceFlowState.Idle) }

    fun startLogin() {
        if (GitHubOAuthConfig.CLIENT_ID.startsWith("TODO_")) {
            state = DeviceFlowState.Error(
                "Es wurde noch keine GitHub-OAuth-App eingerichtet (GitHubOAuthConfig.CLIENT_ID). " +
                    "Siehe Kommentar in GitHubDeviceFlowClient.kt - ohne diesen einmaligen Schritt " +
                    "kann der Button nicht funktionieren. Alternativ unten weiterhin ein " +
                    "Personal Access Token manuell eintragen."
            )
            return
        }

        state = DeviceFlowState.RequestingCode
        scope.launch {
            try {
                val device = deviceFlowClient.requestDeviceCode(GitHubOAuthConfig.CLIENT_ID)
                state = DeviceFlowState.AwaitingConfirmation(device.user_code, device.verification_uri)

                runCatching {
                    Desktop.getDesktop().browse(URI(device.verification_uri))
                }

                val token = deviceFlowClient.pollForAccessToken(GitHubOAuthConfig.CLIENT_ID, device)

                accountRepo.upsert(
                    ConnectorAccount(
                        id = "acc_github_${Clock.System.now().toEpochMilliseconds()}",
                        providerId = "github",
                        label = "GitHub",
                        credentialsJson = Json.encodeToString(GitHubCredentials.serializer(), GitHubCredentials(token))
                    )
                )
                state = DeviceFlowState.Success
                onConnected()
            } catch (e: Exception) {
                state = DeviceFlowState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mit GitHub anmelden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val s = state) {
                    is DeviceFlowState.Idle -> {
                        Text("Öffnet GitHub im Browser. Dort einfach anmelden und den angezeigten Code bestätigen - die App merkt automatisch, wenn es geklappt hat.")
                    }
                    is DeviceFlowState.RequestingCode -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Code wird angefragt…")
                        }
                    }
                    is DeviceFlowState.AwaitingConfirmation -> {
                        Text("Im geöffneten Browser-Fenster diesen Code eingeben:")
                        Text(
                            s.userCode,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Warte auf Bestätigung…", fontSize = 12.sp)
                        }
                        TextButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(s.verificationUri)) } }) {
                            Text("Browser erneut öffnen (${s.verificationUri})")
                        }
                    }
                    is DeviceFlowState.Success -> {
                        Text("Verbunden! Der GitHub-Account wurde angelegt.")
                    }
                    is DeviceFlowState.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is DeviceFlowState.Idle, is DeviceFlowState.Error ->
                    TextButton(onClick = { startLogin() }) { Text("Mit GitHub anmelden") }
                is DeviceFlowState.Success ->
                    TextButton(onClick = onDismiss) { Text("Fertig") }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (state is DeviceFlowState.Success) "Schließen" else "Abbrechen") }
        }
    )
}
