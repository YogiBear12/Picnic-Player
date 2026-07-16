package app.picnic.player.data.socket

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.OutboundWebSocketMessage

/**
 * Thin seam over the SDK's per-session websocket surface ([org.jellyfin.sdk.api.client.ApiClient.webSocket]).
 *
 * Exists so [WebSocketManager]'s message fan-out and connection lifecycle can be
 * unit-tested against a fake, without a live `ApiClient` or a real socket. One
 * instance represents one session's socket; the SDK owns keep-alive and reconnect.
 */
interface SessionSocket {
    /** SDK connection state — observed for logging only (reconnect is SDK-owned). */
    val state: StateFlow<SocketApiState>

    /** Typed inbound message stream for [type]. Subscribing (re)opens the socket lazily. */
    fun <T : OutboundWebSocketMessage> messages(type: KClass<T>): Flow<T>

    /** Registers this device as a controllable session ([org.jellyfin.sdk.api.operations.SessionApi.postCapabilities]). */
    suspend fun registerCapabilities()
}

/** Builds a [SessionSocket] for a freshly-active session. */
interface SessionSocketFactory {
    fun open(session: UserSession): SessionSocket
}

/**
 * Production factory. Mints one long-lived [org.jellyfin.sdk.api.client.ApiClient]
 * for the session (unlike [JellyfinFactory.api], which returns a throwaway client per
 * REST call) so the socket has a single stable client for the session's lifetime.
 */
@Singleton
class JellyfinSessionSocketFactory @Inject constructor(
    private val jellyfin: JellyfinFactory
) : SessionSocketFactory {
    override fun open(session: UserSession): SessionSocket {
        val client = jellyfin.api(session.server.baseUrl, session.accessToken)
        return object : SessionSocket {
            override val state: StateFlow<SocketApiState> = client.webSocket.state

            override fun <T : OutboundWebSocketMessage> messages(type: KClass<T>): Flow<T> = client.webSocket.subscribe(type)

            override suspend fun registerCapabilities() {
                client.sessionApi.postCapabilities(
                    playableMediaTypes = listOf(MediaType.VIDEO, MediaType.AUDIO),
                    supportedCommands = SocketCapabilities.SUPPORTED_COMMANDS,
                    supportsMediaControl = true,
                    supportsPersistentIdentifier = true
                )
            }
        }
    }
}
