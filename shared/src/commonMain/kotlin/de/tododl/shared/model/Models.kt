package de.tododl.shared.model

data class Bereich(
    val id: String,
    val title: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val position: Int = 0
)

enum class ProjectSource { LOCAL, SERVER }

data class Projekt(
    val id: String,
    val bereichId: String,
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val colorHex: String? = null,
    val source: ProjectSource = ProjectSource.LOCAL,
    val serverId: String? = null,
    val serverConnectionId: String? = null,
    val position: Int = 0,
    val archived: Boolean = false
)

/** Feststehende Node-Typen, die kein Panel sind. Panel-Typen sind bewusst
 *  NICHT hier aufgezählt - die sind frei erweiterbar (siehe PanelRegistry im
 *  Desktop-Modul). Ein neuer Panel-Typ braucht hier keine Änderung. */
object BuiltinNodeTypes {
    const val ORDNER = "ORDNER"
}

/**
 * Ein Node ist ein Element im Projekt-Baum: entweder ein Ordner (kann weitere
 * Nodes enthalten) oder ein Panel (Blatt mit typspezifischem Inhalt).
 *
 * `type` ist bewusst ein freier String statt eines Enums: so kann ein neuer
 * Panel-Typ (Plugin) einfach eine neue ID vergeben, ohne dieses Shared-Modul
 * anzufassen. "ORDNER" ist reserviert (siehe BuiltinNodeTypes), alles andere
 * wird von der PanelRegistry im UI-Modul interpretiert.
 */
data class Node(
    val id: String,
    val projectId: String,
    val parentId: String?,
    val type: String,
    val title: String,
    val icon: String? = null,
    val position: Int = 0
) {
    val isOrdner: Boolean get() = type == BuiltinNodeTypes.ORDNER
    val isPanel: Boolean get() = !isOrdner
}

enum class Priority { NONE, LOW, MEDIUM, HIGH, URGENT }

data class TodoItem(
    val id: String,
    val panelId: String,
    val text: String,
    val done: Boolean = false,
    val parentId: String? = null,        // gesetzt = Unteraufgabe von parentId
    val assigneeUsername: String? = null, // Person zugeteilt (Server-Nutzername)
    val priority: Priority = Priority.NONE,
    val terminDate: Long? = null,        // Termin (z. B. geplanter Ausführungstag)
    val dueDate: Long? = null,           // Endfrist (hartes Deadline-Datum)
    val position: Int = 0
)

data class MindCard(
    val id: String,
    val panelId: String,
    val text: String,
    val colorHex: String? = null,
    val posX: Float = 0f,
    val posY: Float = 0f
)

/**
 * Der Markdown-Inhalt eines PANEL_MARKDOWN-Nodes. Ein Panel hat genau eine
 * MarkdownPage (1:1), daher kein eigenes position-Feld nötig.
 * @-Mentions (z. B. "@jan") werden nicht separat gespeichert, sondern beim
 * Rendern aus dem content-Text erkannt (siehe MentionText im Desktop-UI) -
 * so bleibt das Markdown portabel (reiner Text, kein proprietäres Format).
 */
data class MarkdownPage(
    val panelId: String,
    val content: String = ""
)

/** Eine gespeicherte Verbindung zu genau einem Projektserver (Firma/Privat/Verein etc.). */
data class ServerConnection(
    val id: String,
    val name: String,
    val baseUrl: String,
    val accessToken: String,
    val userId: String,
    val username: String,
    val displayName: String
)

// ============================================================================
// Connectors & Actions - BASIS. Bewusst als reines Grundgerüst gehalten (siehe
// desktopApp/.../connectors/ConnectorProvider.kt für die Erweiterungspunkte
// und README dort für Beispiele wie GitHub-Issue-Sync, Bring-Listen, Google-
// Drive-Ordner). Es findet hier (noch) keine echte Netzwerk-Kommunikation
// statt - nur Datenmodell, Speicherung und die Plugin-Registry.
// ============================================================================

/** Ein verbundener Zugang zu einer externen API (z.B. ein GitHub-Token, ein Bring-Login). */
data class ConnectorAccount(
    val id: String,
    val providerId: String,
    val label: String,
    /** Providerspezifisches JSON (Token, E-Mail, ...) - Format siehe ConnectorProvider.credentialFields. */
    val credentialsJson: String
)

/**
 * Verknüpft einen Node (Projekt oder Panel) mit einer externen Ressource eines
 * ConnectorAccounts, z.B. Node <-> GitHub-Repo, Node <-> Bring-Liste, Node <->
 * Google-Drive-Ordner.
 */
data class ConnectorLink(
    val id: String,
    val nodeId: String,
    val accountId: String,
    /** z.B. '{"owner":"jan","repo":"tododl"}' - providerspezifisch. */
    val externalRefJson: String,
    /** providerspezifische Optionen, z.B. ob Issues automatisch als Todos angelegt werden. */
    val optionsJson: String = "{}"
)

/** Status, den eine ActionRule auf einem Todo auslösen kann. */
enum class ActionResultStatus { TODO, IN_BEARBEITUNG, DONE }

/**
 * Eine Bedingung->Effekt-Regel: wenn [fieldName] eines eingehenden Ereignisses
 * (Mail, FTP-Datei, ...) auf [matchTemplate] passt (mit {{todoTitel}} als
 * Platzhalter für den Titel des jeweiligen Todos), wird der Status des Todos
 * auf [resultingStatus] gesetzt.
 *
 * Beispiel Mail: fieldName="Betreff", matchTemplate="{{todoTitel}} erledigt" -> DONE
 * Beispiel FTP:  fieldName="Dateiname", matchTemplate="Rechnung_{{todoTitel}}.pdf" -> IN_BEARBEITUNG
 */
data class ActionRule(
    val id: String,
    val nodeId: String,
    val providerId: String,
    val accountId: String?,
    val fieldName: String,
    val matchTemplate: String,
    val resultingStatus: ActionResultStatus,
    val isEnabled: Boolean = true
)

/** Merkt sich, welches TodoItem bereits aus einer externen Ressource (z.B. einem GitHub-Issue) erzeugt wurde. */
data class ConnectorSyncedItem(
    val id: String,
    val linkId: String,
    val externalId: String,
    val todoItemId: String
)

/** Zugangsdaten für den GitHub-Connector (siehe GitHubConnectorProvider). */
@kotlinx.serialization.Serializable
data class GitHubCredentials(val token: String)

/** Verlinkte Ressource für den GitHub-Connector (siehe GitHubConnectorProvider). */
@kotlinx.serialization.Serializable
data class GitHubExternalRef(val owner: String, val repo: String)
