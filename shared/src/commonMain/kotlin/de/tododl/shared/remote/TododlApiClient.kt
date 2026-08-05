package de.tododl.shared.remote

import de.tododl.shared.model.ServerConnection
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Dünner Wrapper um den Ktor-HttpClient. Jede Methode (außer register/login,
 * die es ja erst ermöglichen, eine ServerConnection zu erzeugen) bekommt die
 * Ziel-ServerConnection explizit übergeben - so kann man mit Firmen-, Privat-
 * und Vereins-Server gleichzeitig arbeiten, ohne einen globalen "aktiven
 * Server" pflegen zu müssen.
 */
class TododlApiClient(private val httpClient: HttpClient) {

    // ---------- Auth (baseUrl explizit, da vor dem Login noch keine ServerConnection existiert) ----------

    suspend fun register(baseUrl: String, email: String, password: String, name: String): AuthResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, name))
        }.body()

    suspend fun login(baseUrl: String, email: String, password: String): AuthResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body()

    // ---------- Projects ----------

    suspend fun listProjects(c: ServerConnection): List<ProjectDto> =
        httpClient.get("${c.baseUrl}/projects") { auth(c) }.body()

    suspend fun createProject(c: ServerConnection, title: String, description: String? = null): ProjectDto =
        httpClient.post("${c.baseUrl}/projects") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(title, description))
        }.body()

    // ---------- Berechtigungen (User + Gruppen) ----------

    suspend fun listAccess(c: ServerConnection, projectId: String): List<ProjectAccessDto> =
        httpClient.get("${c.baseUrl}/projects/$projectId/access") { auth(c) }.body()

    suspend fun grantUserAccess(c: ServerConnection, projectId: String, email: String, role: String) {
        httpClient.post("${c.baseUrl}/projects/$projectId/access/user") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(GrantUserAccessRequest(email, role))
        }
    }

    suspend fun grantGroupAccess(c: ServerConnection, projectId: String, groupId: String, role: String) {
        httpClient.post("${c.baseUrl}/projects/$projectId/access/group") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(GrantGroupAccessRequest(groupId, role))
        }
    }

    suspend fun revokeAccess(c: ServerConnection, projectId: String, accessId: String) {
        httpClient.delete("${c.baseUrl}/projects/$projectId/access/$accessId") { auth(c) }
    }

    // ---------- Gruppen ----------

    suspend fun listGroups(c: ServerConnection): List<GroupDto> =
        httpClient.get("${c.baseUrl}/groups") { auth(c) }.body()

    suspend fun createGroup(c: ServerConnection, name: String): GroupDto =
        httpClient.post("${c.baseUrl}/groups") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to name))
        }.body()

    suspend fun listGroupMembers(c: ServerConnection, groupId: String): List<GroupMemberDto> =
        httpClient.get("${c.baseUrl}/groups/$groupId/members") { auth(c) }.body()

    suspend fun addGroupMember(c: ServerConnection, groupId: String, email: String, role: String = "MEMBER") {
        httpClient.post("${c.baseUrl}/groups/$groupId/members") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(AddGroupMemberRequest(email, role))
        }
    }

    suspend fun removeGroupMember(c: ServerConnection, groupId: String, userId: String) {
        httpClient.delete("${c.baseUrl}/groups/$groupId/members/$userId") { auth(c) }
    }

    // ---------- Nodes ----------

    suspend fun listNodes(c: ServerConnection, projectId: String): List<NodeDto> =
        httpClient.get("${c.baseUrl}/projects/$projectId/nodes") { auth(c) }.body()

    suspend fun upsertNode(c: ServerConnection, projectId: String, node: NodeDto): NodeDto =
        httpClient.put("${c.baseUrl}/projects/$projectId/nodes/${node.id}") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(node)
        }.body()

    suspend fun moveNode(c: ServerConnection, projectId: String, nodeId: String, parentId: String?, position: Int) {
        httpClient.patch("${c.baseUrl}/projects/$projectId/nodes/$nodeId") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(mapOf("parentId" to parentId, "position" to position))
        }
    }

    suspend fun deleteNode(c: ServerConnection, projectId: String, nodeId: String) {
        httpClient.delete("${c.baseUrl}/projects/$projectId/nodes/$nodeId") { auth(c) }
    }

    // ---------- TodoItems ----------

    suspend fun listTodoItems(c: ServerConnection, panelId: String): List<TodoItemDto> =
        httpClient.get("${c.baseUrl}/panels/$panelId/todo-items") { auth(c) }.body()

    suspend fun upsertTodoItem(c: ServerConnection, panelId: String, item: TodoItemDto): TodoItemDto =
        httpClient.put("${c.baseUrl}/panels/$panelId/todo-items/${item.id}") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(item)
        }.body()

    suspend fun deleteTodoItem(c: ServerConnection, panelId: String, itemId: String) {
        httpClient.delete("${c.baseUrl}/panels/$panelId/todo-items/$itemId") { auth(c) }
    }

    // ---------- MindCards ----------

    suspend fun listMindCards(c: ServerConnection, panelId: String): List<MindCardDto> =
        httpClient.get("${c.baseUrl}/panels/$panelId/mind-cards") { auth(c) }.body()

    suspend fun upsertMindCard(c: ServerConnection, panelId: String, card: MindCardDto): MindCardDto =
        httpClient.put("${c.baseUrl}/panels/$panelId/mind-cards/${card.id}") {
            auth(c)
            contentType(ContentType.Application.Json)
            setBody(card)
        }.body()

    suspend fun deleteMindCard(c: ServerConnection, panelId: String, cardId: String) {
        httpClient.delete("${c.baseUrl}/panels/$panelId/mind-cards/$cardId") { auth(c) }
    }

    private fun HttpRequestBuilder.auth(c: ServerConnection) {
        header("Authorization", "Bearer ${c.accessToken}")
    }
}
