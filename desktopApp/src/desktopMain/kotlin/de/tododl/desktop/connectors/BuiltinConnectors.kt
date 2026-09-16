package de.tododl.desktop.connectors

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder

/**
 * GitHub: Idee laut Anforderung - Todoliste mit einem Repo verknüpfen, neue
 * GitHub-Issues erscheinen automatisch als Todos.
 *
 * TODO (noch nicht implementiert, nur Grundgerüst):
 *  - OAuth oder Personal-Access-Token-Flow für credentialFields["token"]
 *  - Periodischer Abruf von GET /repos/{owner}/{repo}/issues (via ConnectorLink.externalRefJson)
 *  - Neue Issues -> TodoItem anlegen (Titel = Issue-Titel, Notiz = Issue-Body/Link)
 *  - Optional: TodoItem erledigt -> GitHub-Issue schließen (umgekehrte Richtung)
 */
object GitHubConnectorProvider : ConnectorProvider {
    override val id = "github"
    override val displayName = "GitHub"
    override val description = "Neue Issues eines Repos automatisch als Todos anlegen."
    override val icon = Icons.Default.Code
    override val credentialFields = listOf(
        CredentialField(key = "token", label = "Personal Access Token", isSecret = true)
    )
    override val linkableResourceLabel = "GitHub-Repository (owner/repo)"
}

/**
 * Bring: Idee laut Anforderung - eine Todoliste "wird" eine Bring-Liste. Ist
 * der Bring-Account schon verbunden, landen alle Todos automatisch in einer
 * gemeinsamen Bring-Liste mit den anderen Projekt-Mitgliedern. Verbindet man
 * Bring erst NACHTRÄGLICH, soll die Verknüpfung rückwirkend greifen - dafür
 * ist ConnectorProvider.onAccountConnected() vorgesehen.
 *
 * TODO (noch nicht implementiert, nur Grundgerüst):
 *  - Bring-Login-Flow für credentialFields["email"]/["password"]
 *  - Bring-Liste anlegen/finden pro verlinktem Node, alle Projekt-Mitglieder einladen
 *  - TodoItem <-> Bring-Item Sync (beide Richtungen, inkl. "erledigt")
 *  - onAccountConnected(): alle Nodes mit providerId == "bring" ohne aktiven
 *    Link durchgehen und nachträglich verknüpfen
 */
object BringConnectorProvider : ConnectorProvider {
    override val id = "bring"
    override val displayName = "Bring"
    override val description = "Todoliste als gemeinsame Bring-Einkaufsliste führen."
    override val icon = Icons.Default.Checklist
    override val credentialFields = listOf(
        CredentialField(key = "email", label = "E-Mail"),
        CredentialField(key = "password", label = "Passwort", isSecret = true)
    )
    override val linkableResourceLabel = "Bring-Liste"

    override suspend fun onAccountConnected(accountId: String) {
        // TODO: alle ConnectorLinks mit providerId == id, deren Bring-Sync noch
        // nicht aktiv war, jetzt nachträglich aktivieren (siehe Klassenkommentar).
    }
}

/**
 * Google Drive: Idee laut Anforderung - ein Google-Ordner wird mit einem
 * Projekt verknüpft und optisch wie ein eigenes Panel dargestellt (ist aber
 * kein PanelPlugin, sondern zeigt den Inhalt des Google-Ordners live an).
 *
 * TODO (noch nicht implementiert, nur Grundgerüst):
 *  - Google OAuth-Flow für credentialFields["oauthToken"]
 *  - Ordner-Picker (Google Drive API) zum Setzen von ConnectorLink.externalRefJson
 *  - Read-only-Ansicht des Ordnerinhalts für alle Projekt-Mitglieder
 */
object GoogleDriveConnectorProvider : ConnectorProvider {
    override val id = "google_drive"
    override val displayName = "Google Drive"
    override val description = "Google-Ordner sichtbar mit einem Projekt verknüpfen."
    override val icon = Icons.Default.Folder
    override val credentialFields = listOf(
        CredentialField(key = "oauthToken", label = "Google-Konto (OAuth)", isSecret = true)
    )
    override val linkableResourceLabel = "Google-Drive-Ordner"
}

/** Alle verfügbaren Connector-Typen. Neuen Typ hinzufügen = Objekt hier eintragen. */
object ConnectorRegistry {
    val all: List<ConnectorProvider> = listOf(
        GitHubConnectorProvider,
        BringConnectorProvider,
        GoogleDriveConnectorProvider
    )

    fun find(providerId: String): ConnectorProvider? = all.firstOrNull { it.id == providerId }
}
