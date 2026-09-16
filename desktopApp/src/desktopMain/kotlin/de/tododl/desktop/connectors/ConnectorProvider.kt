package de.tododl.desktop.connectors

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ein ConnectorProvider beschreibt eine anbindbare externe API (GitHub, Bring,
 * Google Drive, ...). Das ist bewusst nur die BASIS - analog zu PanelPlugin:
 *
 *  1. Neue Datei in diesem Paket anlegen, die dieses Interface implementiert
 *     (siehe GitHubConnectorProvider.kt als Vorlage).
 *  2. Das Objekt in ConnectorRegistry.all eintragen.
 *
 * Ein Provider beschreibt NUR die Metadaten + welche Zugangsdaten er braucht.
 * Die eigentliche Netzwerk-Logik (Login-Flow, API-Aufrufe, Sync) ist hier
 * bewusst noch nicht implementiert und muss je Connector ergänzt werden, z.B.:
 *
 *   - GitHub: Issues eines verlinkten Repos abrufen (REST API "/repos/{owner}/{repo}/issues")
 *     und für neue Issues automatisch TodoItems im verlinkten Panel anlegen.
 *   - Bring: eigenen Bring-Login speichern; bei aktivem Link Todos/Erledigt-Status
 *     mit der Bring-Liste abgleichen (auch rückwirkend, wenn der Bring-Login erst
 *     NACH dem Anlegen des Links hinzukommt - dafür ist syncOnAccountConnected() da).
 *   - Google Drive: OAuth-Flow, Ordner-Picker, den verlinkten Ordner-Inhalt für
 *     alle Projekt-Mitglieder sichtbar machen (ähnlich einem Panel, aber der
 *     Inhalt kommt aus Google statt aus der eigenen DB).
 */
interface ConnectorProvider {
    /** Stabile ID, wird 1:1 als ConnectorAccount.providerId gespeichert. Nie ändern. */
    val id: String

    /** Anzeigename, z.B. "GitHub", "Bring", "Google Drive". */
    val displayName: String

    /** Kurzbeschreibung im Verbindungen-Dialog. */
    val description: String

    /** Icon für die Verbindungen-Übersicht. */
    val icon: ImageVector

    /** Welche Felder für einen neuen Account abgefragt werden müssen (z.B. Access-Token, E-Mail+Passwort). */
    val credentialFields: List<CredentialField>

    /**
     * Was dieser Connector mit einem Node verknüpfen kann (z.B. "GitHub-Repo",
     * "Bring-Liste", "Google-Drive-Ordner"). Bestimmt, was im "Verknüpfen"-Dialog
     * abgefragt wird (siehe ConnectorLink.externalRefJson).
     */
    val linkableResourceLabel: String

    /**
     * Wird aufgerufen, wenn ein neuer ConnectorAccount für diesen Provider
     * angelegt UND es bereits Nodes gibt, die (noch ohne aktiven Account) auf
     * diesen Provider "warten" - z.B. Bring: Todoliste ist schon als
     * "Bring-Liste" markiert, aber der Nutzer meldet sich erst jetzt bei Bring
     * an. Hier greift dann automatisch die Verknüpfung.
     *
     * Bewusst als no-op-Default, damit neue Provider ohne diese Logik
     * kompilieren - Implementierung folgt pro Connector.
     */
    suspend fun onAccountConnected(accountId: String) {}
}

/** Ein einzelnes Zugangsdaten-Feld (z.B. "Access Token", "E-Mail"). */
data class CredentialField(
    val key: String,
    val label: String,
    val isSecret: Boolean = false
)
