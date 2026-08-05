package de.tododl.shared.remote

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(val id: String, val email: String, val name: String)

@Serializable
data class AuthResponseDto(val accessToken: String, val user: UserDto)

@Serializable
data class ProjectDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val colorHex: String? = null,
    val ownerId: String,
    val updatedAt: Long
)

@Serializable
data class MemberDto(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val role: String
)

@Serializable
data class NodeDto(
    val id: String,
    val projectId: String? = null,
    val parentId: String? = null,
    val type: String,
    val title: String,
    val icon: String? = null,
    val position: Int = 0,
    val updatedAt: Long = 0
)

@Serializable
data class TodoItemDto(
    val id: String,
    val panelId: String? = null,
    val text: String,
    val done: Boolean = false,
    val dueDate: Long? = null,
    val position: Int = 0,
    val updatedAt: Long = 0
)

@Serializable
data class MindCardDto(
    val id: String,
    val panelId: String? = null,
    val text: String,
    val colorHex: String? = null,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val updatedAt: Long = 0
)

// Request-Bodies (schlanker als die vollen DTOs oben)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class CreateProjectRequest(val title: String, val description: String? = null)

@Serializable
data class InviteRequest(val email: String, val role: String = "EDITOR")

// ---------- Gruppen & Berechtigungen ----------

@Serializable
data class GroupDto(val id: String, val name: String, val createdBy: String)

@Serializable
data class GroupMemberDto(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val role: String // MEMBER | ADMIN
)

@Serializable
data class ProjectAccessDto(
    val id: String,
    val projectId: String,
    val principalType: String, // USER | GROUP
    val principalId: String,
    val role: String, // OWNER | EDITOR | VIEWER
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class GrantUserAccessRequest(val email: String, val role: String = "EDITOR")

@Serializable
data class GrantGroupAccessRequest(val groupId: String, val role: String = "EDITOR")

@Serializable
data class AddGroupMemberRequest(val email: String, val role: String = "MEMBER")
