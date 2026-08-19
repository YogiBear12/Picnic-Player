package app.picnic.player.data.auth

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ServerConnection(
    val id: String,
    val baseUrl: String,
    val name: String
)

@Serializable
data class StoredSession(
    val serverId: String,
    val userId: String,
    val username: String
)

const val STORED_SESSIONS = "sessions"

data class UserSession(
    val server: ServerConnection,
    val userId: String,
    val username: String,
    val accessToken: String
) {
    val userUuid: UUID = UUID.fromString(userId)
}

@Serializable
data class PublicUserInfo(
    val id: String,
    val name: String,
    val primaryImageTag: String?,
    val hasPassword: Boolean
)

data class QuickConnectRequest(
    val secret: String,
    val code: String
)
