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
