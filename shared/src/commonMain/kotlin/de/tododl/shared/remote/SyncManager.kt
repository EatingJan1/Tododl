package de.tododl.shared.remote

import de.tododl.shared.model.MindCard
import de.tododl.shared.model.MarkdownPage
import de.tododl.shared.model.Node
import de.tododl.shared.model.ProjectSource
import de.tododl.shared.model.Projekt
import de.tododl.shared.model.Priority
import de.tododl.shared.model.ServerConnection
import de.tododl.shared.model.TodoItem
import de.tododl.shared.repository.MindCardRepository
import de.tododl.shared.repository.MarkdownPageRepository
import de.tododl.shared.repository.NodeRepository
import de.tododl.shared.repository.ProjektRepository
import de.tododl.shared.repository.ServerConnectionRepository
import de.tododl.shared.repository.TodoItemRepository

/**
 * Verbindet die lokalen (SQLDelight-) Repositories mit dem/den Projektserver(n).
 *
 * Jedes Server-Projekt merkt sich, über welche ServerConnection es erreichbar
 * ist (Projekt.serverConnectionId) - so kann man gleichzeitig mit einem
 * Firmen-, Privat- und Vereinsserver arbeiten, ohne dass sich Projekte in die
 * Quere kommen.
 *
 * Bewusst einfach gehalten (v1, "Last-Write-Wins", kein Hintergrund-Sync):
 * Push passiert explizit bei UI-Änderungen, Pull über einen "Aktualisieren"-Button.
 *
 * WICHTIG für neue Panel-Typen: Die Panel-UI ist frei erweiterbar (siehe
 * PanelRegistry im Desktop-Modul) - Server-Sync für einen NEUEN Datentyp ist
 * das aber bewusst NICHT automatisch, weil dafür ein eigenes DB-Schema/eigene
 * Server-Endpunkte nötig sind. Ein rein lokales Panel (kein Server-Sync)
 * braucht hier gar nichts. Soll ein neuer Panel-Typ auch über den
 * Projektserver synchronisiert werden, ergänze hier ein analoges
 * push_/pull-Paar (siehe pushMarkdownPageIfNeeded als Vorlage).
 */
class SyncManager(
    private val api: TododlApiClient,
    private val projektRepository: ProjektRepository,
    private val nodeRepository: NodeRepository,
    private val todoItemRepository: TodoItemRepository,
    private val mindCardRepository: MindCardRepository,
    private val markdownPageRepository: MarkdownPageRepository,
    private val serverConnectionRepository: ServerConnectionRepository
) {

    private suspend fun connectionFor(projekt: Projekt): ServerConnection? {
        val connectionId = projekt.serverConnectionId ?: return null
        return serverConnectionRepository.getConnection(connectionId)
    }

    /** Legt ein neues Projekt auf dem angegebenen Server an und spiegelt es sofort lokal. */
    suspend fun createServerProject(
        bereichId: String,
        title: String,
        connection: ServerConnection,
        description: String? = null
    ): Projekt {
        val dto = api.createProject(connection, title, description)
        val projekt = Projekt(
            id = dto.id, // gleiche ID lokal wie auf dem Server - vereinfacht das Mapping
            bereichId = bereichId,
            title = dto.title,
            description = dto.description,
            source = ProjectSource.SERVER,
            serverId = dto.id,
            serverConnectionId = connection.id
        )
        projektRepository.upsert(projekt)
        return projekt
    }

    suspend fun grantUserAccess(projekt: Projekt, username: String, role: String) {
        val connection = connectionFor(projekt) ?: error("Kein Server für dieses Projekt hinterlegt")
        api.grantUserAccess(connection, projekt.id, username, role)
    }

    suspend fun grantGroupAccess(projekt: Projekt, groupId: String, role: String) {
        val connection = connectionFor(projekt) ?: error("Kein Server für dieses Projekt hinterlegt")
        api.grantGroupAccess(connection, projekt.id, groupId, role)
    }

    suspend fun listAccess(projekt: Projekt): List<ProjectAccessDto> {
        val connection = connectionFor(projekt) ?: return emptyList()
        return api.listAccess(connection, projekt.id)
    }

    suspend fun revokeAccess(projekt: Projekt, accessId: String) {
        val connection = connectionFor(projekt) ?: return
        api.revokeAccess(connection, projekt.id, accessId)
    }

    // ---------- Gruppen (serverweit, unabhängig von einzelnen Projekten) ----------

    suspend fun listGroups(connection: ServerConnection): List<GroupDto> = api.listGroups(connection)

    suspend fun createGroup(connection: ServerConnection, name: String): GroupDto =
        api.createGroup(connection, name)

    suspend fun listGroupMembers(connection: ServerConnection, groupId: String): List<GroupMemberDto> =
        api.listGroupMembers(connection, groupId)

    suspend fun addGroupMember(connection: ServerConnection, groupId: String, username: String, role: String = "MEMBER") {
        api.addGroupMember(connection, groupId, username, role)
    }

    suspend fun removeGroupMember(connection: ServerConnection, groupId: String, userId: String) {
        api.removeGroupMember(connection, groupId, userId)
    }

    // ---------- Pull ----------

    /** Holt den kompletten aktuellen Stand vom Server und überschreibt die lokale Kopie. */
    suspend fun pullProject(projektId: String) {
        val projekt = projektRepository.getProjekt(projektId) ?: return
        val connection = connectionFor(projekt) ?: return

        val remoteNodes = api.listNodes(connection, projektId)
        remoteNodes.forEach { dto ->
            nodeRepository.upsert(
                Node(
                    id = dto.id,
                    projectId = projektId,
                    parentId = dto.parentId,
                    type = dto.type,
                    title = dto.title,
                    icon = dto.icon,
                    position = dto.position
                )
            )
        }

        remoteNodes.filter { it.type == BUILTIN_PANEL_TODOLIST }.forEach { panel ->
            api.listTodoItems(connection, panel.id).forEach { dto ->
                todoItemRepository.upsert(
                    TodoItem(
                        id = dto.id,
                        panelId = panel.id,
                        text = dto.text,
                        done = dto.done,
                        parentId = dto.parentId,
                        assigneeUsername = dto.assigneeUsername,
                        priority = runCatching { Priority.valueOf(dto.priority) }.getOrDefault(Priority.NONE),
                        terminDate = dto.terminDate,
                        dueDate = dto.dueDate,
                        position = dto.position
                    )
                )
            }
        }
        remoteNodes.filter { it.type == BUILTIN_PANEL_MINDBOARD }.forEach { panel ->
            api.listMindCards(connection, panel.id).forEach { dto ->
                mindCardRepository.upsert(
                    MindCard(
                        id = dto.id,
                        panelId = panel.id,
                        text = dto.text,
                        colorHex = dto.colorHex,
                        posX = dto.posX,
                        posY = dto.posY
                    )
                )
            }
        }
        remoteNodes.filter { it.type == BUILTIN_PANEL_MARKDOWN }.forEach { panel ->
            val dto = api.getMarkdownPage(connection, panel.id)
            markdownPageRepository.upsert(MarkdownPage(panelId = panel.id, content = dto.content))
        }
    }

    // ---------- Push (No-Op, wenn Projekt kein Server-Projekt ist) ----------

    suspend fun pushNodeIfNeeded(node: Node) {
        val projekt = projektRepository.getProjekt(node.projectId) ?: return
        val connection = connectionFor(projekt) ?: return

        api.upsertNode(
            connection,
            projectId = projekt.id,
            node = NodeDto(
                id = node.id,
                parentId = node.parentId,
                type = node.type,
                title = node.title,
                icon = node.icon,
                position = node.position
            )
        )
    }

    suspend fun pushTodoItemIfNeeded(item: TodoItem) {
        val panel = nodeRepository.getNode(item.panelId) ?: return
        val projekt = projektRepository.getProjekt(panel.projectId) ?: return
        val connection = connectionFor(projekt) ?: return

        api.upsertTodoItem(
            connection,
            panelId = panel.id,
            item = TodoItemDto(
                id = item.id,
                text = item.text,
                done = item.done,
                parentId = item.parentId,
                assigneeUsername = item.assigneeUsername,
                priority = item.priority.name,
                terminDate = item.terminDate,
                dueDate = item.dueDate,
                position = item.position
            )
        )
    }

    suspend fun pushMindCardIfNeeded(card: MindCard) {
        val panel = nodeRepository.getNode(card.panelId) ?: return
        val projekt = projektRepository.getProjekt(panel.projectId) ?: return
        val connection = connectionFor(projekt) ?: return

        api.upsertMindCard(
            connection,
            panelId = panel.id,
            card = MindCardDto(
                id = card.id,
                text = card.text,
                colorHex = card.colorHex,
                posX = card.posX,
                posY = card.posY
            )
        )
    }

    suspend fun pushMarkdownPageIfNeeded(page: MarkdownPage) {
        val panel = nodeRepository.getNode(page.panelId) ?: return
        val projekt = projektRepository.getProjekt(panel.projectId) ?: return
        val connection = connectionFor(projekt) ?: return

        api.upsertMarkdownPage(connection, panelId = panel.id, content = page.content)
    }

    /** Für @-Mention-Autovervollständigung in Markdown-Seiten. */
    suspend fun searchUsers(connection: ServerConnection, query: String): List<UserDto> =
        api.searchUsers(connection, query)

    /** Wie searchUsers, löst die ServerConnection aber selbst über das Panel auf.
     *  Gibt eine leere Liste zurück, wenn das Projekt kein Server-Projekt ist. */
    suspend fun searchUsersForPanel(panelId: String, query: String): List<UserDto> {
        val panel = nodeRepository.getNode(panelId) ?: return emptyList()
        val projekt = projektRepository.getProjekt(panel.projectId) ?: return emptyList()
        val connection = connectionFor(projekt) ?: return emptyList()
        return api.searchUsers(connection, query)
    }
}

// Server-synchronisierte eingebaute Panel-Typen. Ein neuer, rein lokaler
// Panel-Typ (Plugin) braucht diese Konstanten NICHT - nur wenn er auch über
// den Projektserver geteilt werden soll (siehe Klassenkommentar oben).
private const val BUILTIN_PANEL_TODOLIST = "PANEL_TODOLIST"
private const val BUILTIN_PANEL_MINDBOARD = "PANEL_MINDBOARD"
private const val BUILTIN_PANEL_MARKDOWN = "PANEL_MARKDOWN"
