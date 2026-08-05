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

enum class NodeType {
    ORDNER,
    PANEL_TODOLIST,
    PANEL_MINDBOARD
}

/**
 * Ein Node ist ein Element im Projekt-Baum: entweder ein Ordner (kann weitere
 * Nodes enthalten) oder ein Panel (Blatt mit typspezifischem Inhalt).
 */
data class Node(
    val id: String,
    val projectId: String,
    val parentId: String?,
    val type: NodeType,
    val title: String,
    val icon: String? = null,
    val position: Int = 0
) {
    val isOrdner: Boolean get() = type == NodeType.ORDNER
    val isPanel: Boolean get() = !isOrdner
}

data class TodoItem(
    val id: String,
    val panelId: String,
    val text: String,
    val done: Boolean = false,
    val dueDate: Long? = null,
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

/** Eine gespeicherte Verbindung zu genau einem Projektserver (Firma/Privat/Verein etc.). */
data class ServerConnection(
    val id: String,
    val name: String,
    val baseUrl: String,
    val accessToken: String,
    val userId: String,
    val userEmail: String,
    val userName: String
)
