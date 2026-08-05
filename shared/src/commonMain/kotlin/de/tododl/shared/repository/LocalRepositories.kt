package de.tododl.shared.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.tododl.shared.db.TododlDatabase
import de.tododl.shared.model.Bereich
import de.tododl.shared.model.MindCard
import de.tododl.shared.model.Node
import de.tododl.shared.model.NodeType
import de.tododl.shared.model.Projekt
import de.tododl.shared.model.ProjectSource
import de.tododl.shared.model.ServerConnection
import de.tododl.shared.model.TodoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

class LocalBereichRepository(
    private val db: TododlDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : BereichRepository {

    override fun observeBereiche(): Flow<List<Bereich>> =
        db.bereichQueries.selectAllBereiche()
            .asFlow()
            .mapToList(ioDispatcher)
            .let { flow -> flow.mapEach { it.toModel() } }

    override suspend fun getBereich(id: String): Bereich? = withContext(ioDispatcher) {
        db.bereichQueries.selectBereichById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun upsert(bereich: Bereich) = withContext(ioDispatcher) {
        db.bereichQueries.insertBereich(
            id = bereich.id,
            title = bereich.title,
            icon = bereich.icon,
            colorHex = bereich.colorHex,
            position = bereich.position.toLong(),
            createdAt = now(),
            updatedAt = now()
        )
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        db.bereichQueries.deleteBereich(id)
    }

    private fun de.tododl.shared.db.Bereich.toModel() = Bereich(
        id = id, title = title, icon = icon, colorHex = colorHex, position = position.toInt()
    )
}

class LocalProjektRepository(
    private val db: TododlDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ProjektRepository {

    override fun observeProjekte(bereichId: String): Flow<List<Projekt>> =
        db.projektQueries.selectProjekteByBereich(bereichId)
            .asFlow()
            .mapToList(ioDispatcher)
            .mapEach { it.toModel() }

    override suspend fun getProjekt(id: String): Projekt? = withContext(ioDispatcher) {
        db.projektQueries.selectProjektById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun upsert(projekt: Projekt) = withContext(ioDispatcher) {
        db.projektQueries.insertProjekt(
            id = projekt.id,
            bereichId = projekt.bereichId,
            title = projekt.title,
            description = projekt.description,
            icon = projekt.icon,
            colorHex = projekt.colorHex,
            source = projekt.source.name,
            serverId = projekt.serverId,
            serverConnectionId = projekt.serverConnectionId,
            position = projekt.position.toLong(),
            archived = if (projekt.archived) 1L else 0L,
            createdAt = now(),
            updatedAt = now()
        )
    }

    override suspend fun archive(id: String) = withContext(ioDispatcher) {
        db.projektQueries.archiveProjekt(now(), id)
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        db.projektQueries.deleteProjekt(id)
    }

    private fun de.tododl.shared.db.Projekt.toModel() = Projekt(
        id = id,
        bereichId = bereichId,
        title = title,
        description = description,
        icon = icon,
        colorHex = colorHex,
        source = ProjectSource.valueOf(source),
        serverId = serverId,
        serverConnectionId = serverConnectionId,
        position = position.toInt(),
        archived = archived == 1L
    )
}

class LocalNodeRepository(
    private val db: TododlDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : NodeRepository {

    override fun observeRootNodes(projectId: String): Flow<List<Node>> =
        db.nodeQueries.selectRootNodes(projectId)
            .asFlow()
            .mapToList(ioDispatcher)
            .mapEach { it.toModel() }

    override fun observeChildNodes(parentId: String): Flow<List<Node>> =
        db.nodeQueries.selectChildNodes(parentId)
            .asFlow()
            .mapToList(ioDispatcher)
            .mapEach { it.toModel() }

    override suspend fun getNode(id: String): Node? = withContext(ioDispatcher) {
        db.nodeQueries.selectNodeById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun upsert(node: Node) = withContext(ioDispatcher) {
        db.nodeQueries.insertNode(
            id = node.id,
            projectId = node.projectId,
            parentId = node.parentId,
            type = node.type.name,
            title = node.title,
            icon = node.icon,
            position = node.position.toLong(),
            createdAt = now(),
            updatedAt = now()
        )
    }

    override suspend fun move(nodeId: String, newParentId: String?, newPosition: Int) = withContext(ioDispatcher) {
        db.nodeQueries.moveNode(newParentId, newPosition.toLong(), now(), nodeId)
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        db.nodeQueries.deleteNode(id)
    }

    private fun de.tododl.shared.db.Node.toModel() = Node(
        id = id,
        projectId = projectId,
        parentId = parentId,
        type = NodeType.valueOf(type),
        title = title,
        icon = icon,
        position = position.toInt()
    )
}

class LocalTodoItemRepository(
    private val db: TododlDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : TodoItemRepository {

    override fun observeItems(panelId: String): Flow<List<TodoItem>> =
        db.todoItemQueries.selectTodoItemsByPanel(panelId)
            .asFlow()
            .mapToList(ioDispatcher)
            .mapEach { it.toModel() }

    override suspend fun upsert(item: TodoItem) = withContext(ioDispatcher) {
        db.todoItemQueries.insertTodoItem(
            id = item.id,
            panelId = item.panelId,
            text = item.text,
            done = if (item.done) 1L else 0L,
            dueDate = item.dueDate,
            position = item.position.toLong(),
            createdAt = now(),
            updatedAt = now()
        )
    }

    override suspend fun setDone(id: String, done: Boolean) = withContext(ioDispatcher) {
        db.todoItemQueries.setTodoItemDone(if (done) 1L else 0L, now(), id)
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        db.todoItemQueries.deleteTodoItem(id)
    }

    private fun de.tododl.shared.db.TodoItem.toModel() = TodoItem(
        id = id, panelId = panelId, text = text, done = done == 1L, dueDate = dueDate, position = position.toInt()
    )
}

class LocalMindCardRepository(
    private val db: TododlDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : MindCardRepository {

    override fun observeCards(panelId: String): Flow<List<MindCard>> =
        db.mindCardQueries.selectMindCardsByPanel(panelId)
            .asFlow()
            .mapToList(ioDispatcher)
            .mapEach { it.toModel() }

    override suspend fun upsert(card: MindCard) = withContext(ioDispatcher) {
        db.mindCardQueries.insertMindCard(
            id = card.id,
            panelId = card.panelId,
            text = card.text,
            colorHex = card.colorHex,
            posX = card.posX.toDouble(),
            posY = card.posY.toDouble(),
            createdAt = now(),
            updatedAt = now()
        )
    }

    override suspend fun updatePosition(id: String, x: Float, y: Float) = withContext(ioDispatcher) {
        db.mindCardQueries.updateMindCardPosition(x.toDouble(), y.toDouble(), now(), id)
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        db.mindCardQueries.deleteMindCard(id)
    }

    private fun de.tododl.shared.db.MindCard.toModel() = MindCard(
        id = id, panelId = panelId, text = text, colorHex = colorHex,
        posX = posX.toFloat(), posY = posY.toFloat()
    )
}

/** Kleiner Helfer, um Flow<List<T>> elementweise zu mappen ohne extra Dependency. */
private fun <A, B> Flow<List<A>>.mapEach(transform: (A) -> B): Flow<List<B>> =
    this.map { list -> list.map(transform) }

class LocalServerConnectionRepository(
    private val db: TododlDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ServerConnectionRepository {

    override fun observeConnections(): Flow<List<ServerConnection>> =
        db.serverConnectionQueries.selectAllServerConnections()
            .asFlow()
            .mapToList(ioDispatcher)
            .mapEach { it.toModel() }

    override suspend fun getConnection(id: String): ServerConnection? = withContext(ioDispatcher) {
        db.serverConnectionQueries.selectServerConnectionById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun upsert(connection: ServerConnection) = withContext(ioDispatcher) {
        db.serverConnectionQueries.insertServerConnection(
            id = connection.id,
            name = connection.name,
            baseUrl = connection.baseUrl,
            accessToken = connection.accessToken,
            userId = connection.userId,
            userEmail = connection.userEmail,
            userName = connection.userName,
            createdAt = now()
        )
    }

    override suspend fun delete(id: String) = withContext(ioDispatcher) {
        db.serverConnectionQueries.deleteServerConnection(id)
    }

    private fun de.tododl.shared.db.ServerConnection.toModel() = ServerConnection(
        id = id, name = name, baseUrl = baseUrl, accessToken = accessToken,
        userId = userId, userEmail = userEmail, userName = userName
    )
}
