package de.tododl.shared.repository

import de.tododl.shared.model.Bereich
import de.tododl.shared.model.MindCard
import de.tododl.shared.model.Node
import de.tododl.shared.model.Projekt
import de.tododl.shared.model.ServerConnection
import de.tododl.shared.model.TodoItem
import kotlinx.coroutines.flow.Flow

/**
 * Verwaltet Bereiche (oberste Ebene: Verein, Privat, Arbeit, ...).
 * Bereiche sind immer lokal (nicht server-synchronisiert) - sie sind reine
 * Organisationsstruktur auf dem jeweiligen Gerät.
 */
interface BereichRepository {
    fun observeBereiche(): Flow<List<Bereich>>
    suspend fun getBereich(id: String): Bereich?
    suspend fun upsert(bereich: Bereich)
    suspend fun delete(id: String)
}

/**
 * Verwaltet Projekte innerhalb eines Bereichs. Ein Projekt kann lokal sein
 * oder von einem Projektserver stammen (siehe Projekt.source).
 */
interface ProjektRepository {
    fun observeProjekte(bereichId: String): Flow<List<Projekt>>
    fun observeArchivedProjekte(bereichId: String): Flow<List<Projekt>>
    suspend fun getProjekt(id: String): Projekt?
    suspend fun upsert(projekt: Projekt)
    suspend fun archive(id: String)
    suspend fun unarchive(id: String)
    suspend fun delete(id: String)
}

/**
 * Verwaltet den Node-Baum (Ordner + Panels) innerhalb eines Projekts.
 */
interface NodeRepository {
    fun observeRootNodes(projectId: String): Flow<List<Node>>
    fun observeChildNodes(parentId: String): Flow<List<Node>>
    suspend fun getNode(id: String): Node?
    suspend fun upsert(node: Node)
    suspend fun move(nodeId: String, newParentId: String?, newPosition: Int)
    suspend fun delete(id: String)
}

interface TodoItemRepository {
    fun observeItems(panelId: String): Flow<List<TodoItem>>
    suspend fun upsert(item: TodoItem)
    suspend fun setDone(id: String, done: Boolean)
    suspend fun delete(id: String)
}

interface MindCardRepository {
    fun observeCards(panelId: String): Flow<List<MindCard>>
    suspend fun upsert(card: MindCard)
    suspend fun updatePosition(id: String, x: Float, y: Float)
    suspend fun delete(id: String)
}

/** Verwaltet die Liste der gespeicherten Server-Verbindungen (Firma/Privat/Verein/...). */
interface ServerConnectionRepository {
    fun observeConnections(): Flow<List<ServerConnection>>
    suspend fun getConnection(id: String): ServerConnection?
    suspend fun upsert(connection: ServerConnection)
    suspend fun delete(id: String)
}