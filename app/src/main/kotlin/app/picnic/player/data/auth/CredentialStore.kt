package app.picnic.player.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.security.SecureStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists onboarded servers and user sessions. Servers and the user
 * list are non-secret JSON in DataStore; **access tokens are encrypted** via
 * [SecureStore] (Keystore AES/GCM) and stored separately, keyed per user. A
 * server is only written once a login on it succeeds.
 */
@Singleton
class CredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val secure: SecureStore
) {
    /**
     * Reactive mirror of the active session (or null). Emits whenever the active user
     * changes — login, quick-switch, logout, session-expiry, or forgetting the active
     * user/server. Seeded from disk by [refreshActiveSession] at startup. Additive: the
     * pull-based [activeSession] read is unchanged; this only adds a push signal for
     * observers that must react to sign-in/out (e.g. the session websocket).
     */
    private val _activeSessionFlow = MutableStateFlow<UserSession?>(null)
    val activeSessionFlow: StateFlow<UserSession?> = _activeSessionFlow.asStateFlow()

    suspend fun servers(): List<ServerConnection> = read(SERVERS)?.let { json.decodeFromString<List<ServerConnection>>(it) } ?: emptyList()

    suspend fun sessions(): List<StoredSession> = read(SESSIONS)?.let { json.decodeFromString<List<StoredSession>>(it) } ?: emptyList()

    suspend fun sessionsForServer(serverId: String): List<StoredSession> = sessions().filter { it.serverId == serverId }

    /** Persists the server (upsert), the user, the encrypted token, and makes it active. */
    suspend fun saveSession(session: UserSession) {
        upsertServer(session.server)
        val stored = StoredSession(session.server.id, session.userId, session.username)
        val updated = sessions()
            .filterNot { it.serverId == stored.serverId && it.userId == stored.userId } + stored
        write(SESSIONS, json.encodeToString(updated))
        write(tokenKey(session.server.id, session.userId), secure.encrypt(session.accessToken))
        setActive(session.server.id, session.userId)
    }

    suspend fun session(serverId: String, userId: String): UserSession? {
        val server = servers().firstOrNull { it.id == serverId } ?: return null
        val stored = sessions().firstOrNull { it.serverId == serverId && it.userId == userId }
            ?: return null
        val token = read(tokenKey(serverId, userId))?.let { secure.decrypt(it) } ?: return null
        return UserSession(server, userId, stored.username, token)
    }

    suspend fun activeSession(): UserSession? {
        val parts = read(ACTIVE)?.split('|') ?: return null
        if (parts.size != 2) return null
        return session(parts[0], parts[1])
    }

    suspend fun setActive(serverId: String, userId: String) {
        write(ACTIVE, "$serverId|$userId")
        write(ACTIVE_SERVER, serverId)
        publishActiveSession()
    }

    /** Clears only the active user session (soft user logout); the active server
     *  is kept so cold start lands on that server's Profile Picker. */
    suspend fun clearActive() {
        remove(ACTIVE)
        publishActiveSession()
    }

    /** Seeds [activeSessionFlow] from disk. Call once at startup (the ACTIVE key
     *  persists across process death, but the reactive flow starts empty). */
    suspend fun refreshActiveSession() = publishActiveSession()

    private suspend fun publishActiveSession() {
        _activeSessionFlow.value = activeSession()
    }

    /** The server cold start should resume to (its Profile Picker) when there is
     *  no active session. Null after a "change server" soft logout. */
    suspend fun activeServerId(): String? = read(ACTIVE_SERVER)

    suspend fun setActiveServer(serverId: String) = write(ACTIVE_SERVER, serverId)

    suspend fun clearActiveServer() = remove(ACTIVE_SERVER)

    suspend fun forgetUser(serverId: String, userId: String) {
        write(
            SESSIONS,
            json.encodeToString(
                sessions().filterNot { it.serverId == serverId && it.userId == userId }
            )
        )
        remove(tokenKey(serverId, userId))
        clearSessionAuthError(serverId, userId)
        setProfileOrder(serverId, profileOrder(serverId).filterNot { it == userId })
        if (read(ACTIVE) == "$serverId|$userId") remove(ACTIVE)
        publishActiveSession()
    }

    suspend fun forgetServer(serverId: String) {
        sessionsForServer(serverId).forEach { remove(tokenKey(serverId, it.userId)) }
        write(SESSIONS, json.encodeToString(sessions().filterNot { it.serverId == serverId }))
        write(SERVERS, json.encodeToString(servers().filterNot { it.id == serverId }))
        remove(profileOrderKey(serverId))
        remove(publicUsersKey(serverId))
        remove(sessionErrorsKey(serverId))
        setServerOrder(serverOrder().filterNot { it == serverId })
        if (read(ACTIVE)?.startsWith("$serverId|") == true) remove(ACTIVE)
        if (read(ACTIVE_SERVER) == serverId) remove(ACTIVE_SERVER)
        publishActiveSession()
    }

    suspend fun profileOrder(serverId: String): List<String> = read(profileOrderKey(serverId))?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()

    suspend fun setProfileOrder(serverId: String, userIds: List<String>) = write(profileOrderKey(serverId), json.encodeToString(userIds))

    suspend fun serverOrder(): List<String> = read(SERVER_ORDER)?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()

    suspend fun setServerOrder(serverIds: List<String>) = write(SERVER_ORDER, json.encodeToString(serverIds))

    /** Last-known public user list for a server (from the most recent successful fetch). */
    suspend fun cachedPublicUsers(serverId: String): List<PublicUserInfo> = read(publicUsersKey(serverId))?.let { json.decodeFromString<List<PublicUserInfo>>(it) }
        ?: emptyList()

    suspend fun savePublicUsers(serverId: String, users: List<PublicUserInfo>) = write(publicUsersKey(serverId), json.encodeToString(users))

    suspend fun hasToken(serverId: String, userId: String): Boolean = read(tokenKey(serverId, userId)) != null

    /** Drops the encrypted token but keeps the stored username for the picker row. */
    suspend fun clearToken(serverId: String, userId: String) = remove(tokenKey(serverId, userId))

    suspend fun sessionAuthErrors(serverId: String): Map<String, String> = read(sessionErrorsKey(serverId))?.let { json.decodeFromString<Map<String, String>>(it) }
        ?: emptyMap()

    suspend fun setSessionAuthError(serverId: String, userId: String, message: String) {
        val updated = sessionAuthErrors(serverId) + (userId to message)
        write(sessionErrorsKey(serverId), json.encodeToString(updated))
    }

    suspend fun clearSessionAuthError(serverId: String, userId: String) {
        val updated = sessionAuthErrors(serverId) - userId
        if (updated.isEmpty()) {
            remove(sessionErrorsKey(serverId))
        } else {
            write(sessionErrorsKey(serverId), json.encodeToString(updated))
        }
    }

    private suspend fun upsertServer(server: ServerConnection) {
        val updated = servers().filterNot { it.id == server.id } + server
        write(SERVERS, json.encodeToString(updated))
    }

    private suspend fun read(key: String): String? = dataStore.data.map { it[stringPreferencesKey(key)] }.first()

    private suspend fun write(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    private suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    private fun tokenKey(serverId: String, userId: String) = "token.$serverId.$userId"
    private fun profileOrderKey(serverId: String) = "profile_order.$serverId"
    private fun publicUsersKey(serverId: String) = "public_users.$serverId"
    private fun sessionErrorsKey(serverId: String) = "session_errors.$serverId"

    private companion object {
        const val SERVERS = "servers"
        const val SESSIONS = "sessions"
        const val ACTIVE = "active_session"
        const val ACTIVE_SERVER = "active_server"
        const val SERVER_ORDER = "server_order"
    }
}
