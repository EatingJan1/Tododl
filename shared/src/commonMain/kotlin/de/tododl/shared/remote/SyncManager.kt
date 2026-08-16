package de.tododl.shared.remote

import de.tododl.shared.model.MindCard
import de.tododl.shared.model.Node
import de.tododl.shared.model.NodeType
import de.tododl.shared.model.ProjectSource
import de.tododl.shared.model.Projekt
import de.tododl.shared.model.ServerConnection
import de.tododl.shared.model.TodoItem
import de.tododl.shared.repository.MindCardRepository
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
 */
class SyncManager(
    private val api: TododlApiClient,
    private val projektRepository: ProjektRepository,
    private val nodeRepository: NodeRepository,
    private val todoItemRepository: TodoItemRepository,
    private val mindCardRepository: MindCardRepository,
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
                    type = NodeType.valueOf(dto.type),
                    title = dto.title,
                    icon = dto.icon,
                    position = dto.position
                )
            )
        }

        remoteNodes.filter { it.type == NodeType.PANEL_TODOLIST.name }.forEach { panel ->
            api.listTodoItems(connection, panel.id).forEach { dto ->
                todoItemRepository.upsert(
                    TodoItem(
                        id = dto.id,
                        panelId = panel.id,
                        text = dto.text,
                        done = dto.done,
                        dueDate = dto.dueDate,
                        position = dto.position
                    )
                )
            }
        }
        remoteNodes.filter { it.type == NodeType.PANEL_MINDBOARD.name }.forEach { panel ->
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
                type = node.type.name,
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
}
