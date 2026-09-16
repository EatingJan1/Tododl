package de.tododl.desktop.connectors

import de.tododl.shared.model.ConnectorAccount
import de.tododl.shared.model.ConnectorLink
import de.tododl.shared.model.GitHubCredentials
import de.tododl.shared.model.GitHubExternalRef
import de.tododl.shared.model.Priority
import de.tododl.shared.model.TodoItem
import de.tododl.shared.remote.GitHubApiClient
import de.tododl.shared.repository.ConnectorSyncedItemRepository
import de.tododl.shared.repository.TodoItemRepository
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Setzt die Idee aus der Anforderung um: "Verlinke Todoliste mit Repo - neue
 * Issues auf GitHub erscheinen automatisch als Todo."
 *
 * Bewusst als expliziter, manuell ausgelöster Sync (Button in der UI) statt
 * eines Hintergrund-Jobs - einfacher zu verstehen/debuggen als erster Schritt.
 * Ein periodischer Poll-Job kann diese Klasse später einfach wiederverwenden
 * (siehe syncIssuesToTodos()).
 */
class GitHubSyncService(
    private val gitHubApi: GitHubApiClient,
    private val todoItemRepository: TodoItemRepository,
    private val syncedItemRepository: ConnectorSyncedItemRepository
) {
    /**
     * Holt alle offenen Issues des in [link] verlinkten Repos und legt für
     * jedes Issue, das noch nicht synchronisiert wurde, ein neues TodoItem in
     * link.nodeId (der Todoliste) an. Bereits erzeugte Todos werden nicht
     * doppelt angelegt (siehe ConnectorSyncedItemRepository).
     *
     * @return Anzahl der neu angelegten Todos.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun syncIssuesToTodos(link: ConnectorLink, account: ConnectorAccount): Int {
        val credentials = Json.decodeFromString<GitHubCredentials>(account.credentialsJson)
        val ref = Json.decodeFromString<GitHubExternalRef>(link.externalRefJson)

        val issues = gitHubApi.listOpenIssues(ref.owner, ref.repo, credentials.token)
        val alreadySynced = syncedItemRepository.getSyncedExternalIds(link.id)

        var created = 0
        for (issue in issues) {
            val externalId = issue.number.toString()
            if (externalId in alreadySynced) continue

            val todoId = Uuid.random().toString()
            todoItemRepository.upsert(
                TodoItem(
                    id = todoId,
                    panelId = link.nodeId,
                    text = "#${issue.number} ${issue.title}",
                    done = false,
                    priority = Priority.NONE
                )
            )
            syncedItemRepository.markSynced(link.id, externalId, todoId)
            created++
        }
        return created
    }
}
