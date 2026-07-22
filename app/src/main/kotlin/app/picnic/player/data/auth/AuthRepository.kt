package app.picnic.player.data.auth

import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.jellyfin.serverErrorMessage
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.di.IoDispatcher
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto

/** Outcome of checking a stored session against its server. */
sealed interface SessionCheck {
    /** Token accepted — the session is usable. */
    data object Valid : SessionCheck

    /** Server rejected the token (401/403) — re-login required. */
    data object AuthInvalid : SessionCheck

    /** Server couldn't be reached or errored; [message] is the card/label text. */
    data class Unreachable(val message: String) : SessionCheck
}

/**
 * Authentication + onboarded-server/session management, built on the
 * Jellyfin Kotlin SDK. Tokens persist encrypted via [CredentialStore]; the
 * unique DeviceId is supplied by the SDK factory.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val jellyfin: JellyfinFactory,
    private val credentials: CredentialStore,
    private val seerrRepository: Lazy<SeerrRepository>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /** Runs a network + deserialize [block] off the caller's dispatcher — see MediaRepository.onIo. */
    private suspend inline fun <T> onIo(crossinline block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    /**
     * Reactive current active session (or null), emitting on login / quick-switch /
     * logout / session-expiry. Backed by [CredentialStore]; seeded on startup by
     * [warmLocalCache]. Additive to the existing pull-based [activeSession].
     */
    val activeSessionFlow: StateFlow<UserSession?> = credentials.activeSessionFlow

    private data class LocalSnapshot(
        val servers: List<ServerConnection>,
        val sessions: List<StoredSession>,
        val profileOrders: Map<String, List<String>>,
        val publicUsersByServer: Map<String, List<PublicUserInfo>>,
        val sessionErrorsByServer: Map<String, Map<String, String>>,
        val storedTokens: Set<String>
    )

    private var snapshot: LocalSnapshot? = null

    companion object {
        const val SESSION_EXPIRED_MESSAGE = "Sign in again to continue"
    }

    /**
     * Loads servers, sessions, and per-server picker metadata into memory so
     * picker screens can paint from disk on their first frame (no async gap).
     * Call during startup before routing to a picker.
     */
    suspend fun warmLocalCache() {
        val servers = credentials.servers()
        val sessions = credentials.sessions()
        snapshot = LocalSnapshot(
            servers = servers,
            sessions = sessions,
            profileOrders = servers.associate { it.id to credentials.profileOrder(it.id) },
            publicUsersByServer = servers.associate { it.id to credentials.cachedPublicUsers(it.id) },
            sessionErrorsByServer = servers.associate { it.id to credentials.sessionAuthErrors(it.id) },
            storedTokens = sessions.mapNotNull { session ->
                if (credentials.hasToken(session.serverId, session.userId)) {
                    tokenKey(session.serverId, session.userId)
                } else {
                    null
                }
            }.toSet()
        )
        // Seed the reactive active-session signal from the persisted ACTIVE key so
        // observers (the session websocket) see any session restored across cold start.
        credentials.refreshActiveSession()
    }

    private fun tokenKey(serverId: String, userId: String) = "$serverId|$userId"

    /** Synchronous read of warmed picker data; null until [warmLocalCache] has run. */
    fun peekProfilePickerLocal(serverId: String): ProfilePickerLocal? {
        val cache = snapshot ?: return null
        val server = cache.servers.firstOrNull { it.id == serverId } ?: return null
        return ProfilePickerLocal(
            server = server,
            stored = cache.sessions.filter { it.serverId == serverId },
            cachedPublic = cache.publicUsersByServer[serverId].orEmpty(),
            profileOrder = cache.profileOrders[serverId].orEmpty(),
            authErrors = cache.sessionErrorsByServer[serverId].orEmpty()
        )
    }

    /** Lightweight check that the stored access token is still accepted by the server. */
    /**
     * Checks the stored token against the server, distinguishing a rejected token from an
     * unreachable server — so a transient network failure no longer expires a good session.
     */
    suspend fun validateSession(session: UserSession): SessionCheck = onIo {
        runCatching {
            jellyfin.api(session.server.baseUrl, session.accessToken).userApi.getCurrentUser()
        }.fold(
            onSuccess = { SessionCheck.Valid },
            onFailure = { e ->
                if (e.isAuthFailure()) {
                    SessionCheck.AuthInvalid
                } else {
                    SessionCheck.Unreachable(e.serverErrorMessage())
                }
            }
        )
    }

    suspend fun sessionAuthErrors(serverId: String): Map<String, String> = credentials.sessionAuthErrors(serverId)

    /**
     * Clears the active session and drops the stored token, but keeps the username
     * on the profile picker row with an error prompting re-login.
     */
    suspend fun expireStoredSession(
        serverId: String,
        userId: String,
        message: String = SESSION_EXPIRED_MESSAGE
    ) {
        credentials.clearToken(serverId, userId)
        credentials.setSessionAuthError(serverId, userId, message)
        credentials.clearActive()
        snapshot?.let { cache ->
            val serverErrors = cache.sessionErrorsByServer[serverId].orEmpty() + (userId to message)
            snapshot = cache.copy(
                sessionErrorsByServer = cache.sessionErrorsByServer + (serverId to serverErrors),
                storedTokens = cache.storedTokens - tokenKey(serverId, userId)
            )
        }
    }

    fun peekHasStoredToken(serverId: String, userId: String): Boolean = snapshot?.storedTokens?.contains(tokenKey(serverId, userId)) == true

    suspend fun hasStoredToken(serverId: String, userId: String): Boolean = credentials.hasToken(serverId, userId)

    suspend fun activeSession(): UserSession? = credentials.activeSession()

    suspend fun onboardedServers(): List<ServerConnection> = credentials.servers()

    suspend fun storedUsers(serverId: String): List<StoredSession> = credentials.sessionsForServer(serverId)

    /**
     * LAN-discovered Jellyfin servers as UDP replies arrive (SDK Flow + multicast lock).
     * Collect on a background dispatcher; UI should append as items emit.
     */
    fun discoverServers(): Flow<ServerConnection> = jellyfin.discoverLocalServers().map {
        ServerConnection(id = it.id, baseUrl = it.address, name = it.name)
    }

    /**
     * Resolves a typed address into a reachable server. Delegates to the SDK's
     * recommended-server discovery, which expands the input into address
     * candidates (adds `http(s)://`, the default `:8096` port, the `/System/Info`
     * path) and scores each by reachability — so "192.168.1.50" or "myserver.com"
     * resolve without the user knowing the exact URL. Throws if none respond.
     */
    suspend fun resolveServer(input: String): ServerConnection = onIo {
        // (Score enum is ordered best-first: GREAT, GOOD, OK, BAD.)
        val candidates = jellyfin.discovery.getRecommendedServers(input)
        val best = candidates
            .filter { it.score != RecommendedServerInfoScore.BAD }
            .minByOrNull { it.score.ordinal }
        val info = best?.systemInfo?.getOrNull()
        if (best == null || info == null) {
            // Report which addresses were tried and why each failed so errors are
            // diagnosable, not generic.
            val tried = candidates
                .joinToString("; ") { "${it.address} (${issueText(it.issues.firstOrNull())})" }
                .ifEmpty { "no candidate addresses for \"$input\"" }
            throw IllegalStateException("Couldn't connect to \"$input\". Tried: $tried")
        }
        ServerConnection(
            id = info.id ?: best.address,
            baseUrl = best.address,
            name = info.serverName?.takeIf { it.isNotBlank() } ?: best.address
        )
    }

    private fun issueText(issue: RecommendedServerIssue?): String = when (issue) {
        null -> "no system info"
        is RecommendedServerIssue.ServerUnreachable -> "unreachable"
        is RecommendedServerIssue.SecureConnectionFailed -> "TLS failed"
        is RecommendedServerIssue.SlowResponse -> "slow response"
        is RecommendedServerIssue.MissingSystemInfo,
        is RecommendedServerIssue.InvalidProductName -> "not a Jellyfin server"
        RecommendedServerIssue.MissingVersion,
        is RecommendedServerIssue.OutdatedServerVersion,
        is RecommendedServerIssue.UnsupportedServerVersion -> "unsupported version"
    }

    suspend fun publicUsers(server: ServerConnection): List<PublicUserInfo> = onIo {
        val users = jellyfin.api(server.baseUrl).userApi.getPublicUsers().content.map {
            PublicUserInfo(
                id = it.id.toString(),
                name = it.name.orEmpty(),
                primaryImageTag = it.primaryImageTag,
                hasPassword = it.hasPassword ?: true
            )
        }
        credentials.savePublicUsers(server.id, users)
        snapshot?.let { cache ->
            snapshot = cache.copy(
                publicUsersByServer = cache.publicUsersByServer + (server.id to users)
            )
        }
        users
    }

    suspend fun cachedPublicUsers(serverId: String): List<PublicUserInfo> = credentials.cachedPublicUsers(serverId)

    suspend fun loginWithPassword(
        server: ServerConnection,
        username: String,
        password: String
    ): UserSession = onIo {
        val result = jellyfin.api(server.baseUrl)
            .userApi.authenticateUserByName(
                AuthenticateUserByName(username = username, pw = password)
            ).content
        persist(server, result)
    }

    suspend fun startQuickConnect(server: ServerConnection): QuickConnectRequest = onIo {
        val state = jellyfin.api(server.baseUrl).quickConnectApi.initiateQuickConnect().content
        QuickConnectRequest(
            secret = state.secret ?: error("Quick Connect unavailable"),
            code = state.code ?: error("Quick Connect unavailable")
        )
    }

    /** True once the user approves the code on an already-signed-in device. */
    suspend fun isQuickConnectApproved(server: ServerConnection, secret: String): Boolean = onIo {
        jellyfin.api(server.baseUrl).quickConnectApi.getQuickConnectState(secret).content.authenticated
    }

    suspend fun loginWithQuickConnect(server: ServerConnection, secret: String): UserSession = onIo {
        val result = jellyfin.api(server.baseUrl)
            .userApi.authenticateWithQuickConnect(QuickConnectDto(secret = secret)).content
        persist(server, result)
    }

    /**
     * Re-activates a previously stored session (encrypted token still on disk)
     * without a fresh sign-in. Returns null if the token is gone — the caller
     * then routes that user to login. Used by the Profile Picker quick-switch.
     */
    suspend fun useStoredSession(serverId: String, userId: String): UserSession? {
        val session = credentials.session(serverId, userId) ?: return null
        credentials.setActive(serverId, userId)
        seerrRepository.get().attach(session)
        return session
    }

    /** Soft user logout: drop the active session, keep the active server so cold
     *  start resumes on that server's Profile Picker. Seerr secrets stay (soft logout). */
    suspend fun logout() = credentials.clearActive()

    /**
     * Deliberate sign-out from Settings > Account (#135) — a true logout. Unlike [logout]
     * (which only drops the active pointer), this best-effort revokes the access token on
     * the server (`POST /Sessions/Logout`), then [forgetUser]s the profile entirely: token,
     * stored session, seerr link and picker row all removed. The active server is kept, so
     * navigation lands on that server's Profile Picker with this user gone. Signing back in
     * requires the username and password.
     */
    suspend fun signOut() {
        val session = credentials.activeSession()
        if (session == null) {
            credentials.clearActive()
            return
        }
        // Best-effort server-side revoke; local sign-out proceeds regardless (offline,
        // token already invalid, etc.).
        onIo { runCatching { jellyfin.api(session.server.baseUrl, session.accessToken).sessionApi.reportSessionEnded() } }
        forgetUser(session.server.id, session.userId)
    }

    /** Which server cold start resumes to when no session is active (null = show
     *  the Server Picker after a "change server"). */
    suspend fun activeServerId(): String? = credentials.activeServerId()

    suspend fun setActiveServer(serverId: String) = credentials.setActiveServer(serverId)

    /** Soft server logout (from "change server"). */
    suspend fun clearActiveServer() = credentials.clearActiveServer()

    suspend fun forgetUser(serverId: String, userId: String) {
        seerrRepository.get().forgetUser(serverId, userId)
        credentials.forgetUser(serverId, userId)
        patchSnapshotAfterForgetUser(serverId, userId)
    }

    suspend fun forgetServer(serverId: String) {
        val userIds = credentials.sessionsForServer(serverId).map { it.userId }
        seerrRepository.get().forgetServer(serverId, userIds)
        credentials.forgetServer(serverId)
        snapshot?.let { cache ->
            snapshot = cache.copy(
                servers = cache.servers.filterNot { it.id == serverId },
                sessions = cache.sessions.filterNot { it.serverId == serverId },
                profileOrders = cache.profileOrders - serverId,
                publicUsersByServer = cache.publicUsersByServer - serverId,
                sessionErrorsByServer = cache.sessionErrorsByServer - serverId,
                storedTokens = cache.storedTokens.filterNot { it.startsWith("$serverId|") }.toSet()
            )
        }
    }

    suspend fun profileOrder(serverId: String): List<String> = credentials.profileOrder(serverId)
    suspend fun setProfileOrder(serverId: String, userIds: List<String>) = credentials.setProfileOrder(serverId, userIds)
    suspend fun serverOrder(): List<String> = credentials.serverOrder()
    suspend fun setServerOrder(serverIds: List<String>) = credentials.setServerOrder(serverIds)

    private suspend fun persist(
        server: ServerConnection,
        result: AuthenticationResult
    ): UserSession {
        val user = result.user ?: error("Authentication returned no user")
        val token = result.accessToken ?: error("Authentication returned no token")
        val resolved = result.serverId?.let { server.copy(id = it) } ?: server
        val session = UserSession(resolved, user.id.toString(), user.name.orEmpty(), token)
        credentials.saveSession(session)
        credentials.clearSessionAuthError(session.server.id, session.userId)
        patchSnapshotAfterSaveSession(session)
        seerrRepository.get().attach(session)
        return session
    }

    private fun patchSnapshotAfterSaveSession(session: UserSession) {
        val cache = snapshot ?: return
        val stored = StoredSession(session.server.id, session.userId, session.username)
        val servers = cache.servers.filterNot { it.id == session.server.id } + session.server
        val sessions = cache.sessions
            .filterNot { it.serverId == stored.serverId && it.userId == stored.userId } + stored
        val clearedErrors = cache.sessionErrorsByServer.mapValues { (serverId, errors) ->
            if (serverId == session.server.id) errors - session.userId else errors
        }.filterValues { it.isNotEmpty() }
        snapshot = cache.copy(
            servers = servers,
            sessions = sessions,
            sessionErrorsByServer = clearedErrors,
            storedTokens = cache.storedTokens + tokenKey(session.server.id, session.userId)
        )
    }

    private fun patchSnapshotAfterForgetUser(serverId: String, userId: String) {
        val cache = snapshot ?: return
        val serverErrors = cache.sessionErrorsByServer[serverId].orEmpty() - userId
        snapshot = cache.copy(
            sessions = cache.sessions.filterNot { it.serverId == serverId && it.userId == userId },
            profileOrders = cache.profileOrders.mapValues { (id, order) ->
                if (id == serverId) order.filterNot { it == userId } else order
            },
            sessionErrorsByServer = if (serverErrors.isEmpty()) {
                cache.sessionErrorsByServer - serverId
            } else {
                cache.sessionErrorsByServer + (serverId to serverErrors)
            },
            storedTokens = cache.storedTokens - tokenKey(serverId, userId)
        )
    }
}
