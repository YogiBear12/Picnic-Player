package app.picnic.player.data.auth

import kotlinx.serialization.Serializable

/** An onboarded Jellyfin server (persisted; no secrets). */
@Serializable
data class ServerConnection(
    val id: String,
    val baseUrl: String,
    val name: String
)

/** A stored user on a server (persisted; the token lives encrypted, separately). */
@Serializable
data class StoredSession(
    val serverId: String,
    val userId: String,
    val username: String
)

/** A runtime session with a decrypted access token. Never serialized verbatim. */
data class UserSession(
    val server: ServerConnection,
    val userId: String,
    val username: String,
    val accessToken: String
)

/** A public user advertised by a server's login screen. */
@Serializable
data class PublicUserInfo(
    val id: String,
    val name: String,
    val primaryImageTag: String?,
    val hasPassword: Boolean
)

/** An in-progress Quick Connect request. */
data class QuickConnectRequest(
    val secret: String,
    val code: String
)
