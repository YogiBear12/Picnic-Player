package app.picnic.player.data.socket

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinFactory
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.OutboundWebSocketMessage

/**
 * Seam over one session's websocket, so [WebSocketManager]'s fan-out and lifecycle can be
 * unit-tested against a fake. [OkHttpSessionSocket] owns keep-alive and reconnect.
 */
interface SessionSocket {
    /** Connection state, observed for logging only. */
    val state: StateFlow<SocketApiState>

    /** Typed inbound message stream for [type]. Subscribing (re)opens the socket lazily. */
    fun <T : OutboundWebSocketMessage> messages(type: KClass<T>): Flow<T>

    /** Registers this device as a controllable session ([org.jellyfin.sdk.api.operations.SessionApi.postCapabilities]). */
    suspend fun registerCapabilities()

    /** Closes the socket for good; a new session gets a new socket. */
    fun close()
}

/** Builds a [SessionSocket] for a freshly-active session. */
interface SessionSocketFactory {
    fun open(session: UserSession): SessionSocket
}

@Singleton
class JellyfinSessionSocketFactory @Inject constructor(
    private val jellyfin: JellyfinFactory,
    private val okHttp: Lazy<OkHttpClient>
) : SessionSocketFactory {
    override fun open(session: UserSession): SessionSocket {
        val client = jellyfin.api(session.server.baseUrl, session.accessToken)
        val authorization = AuthorizationHeaderBuilder.buildHeader(
            clientName = client.clientInfo.name,
            clientVersion = client.clientInfo.version,
            deviceId = client.deviceInfo.id,
            deviceName = client.deviceInfo.name,
            accessToken = session.accessToken
        )
        val request = Request.Builder()
            .url(client.createUrl("/socket"))
            .header("Authorization", authorization)
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return OkHttpSessionSocket(okHttp.get(), request, scope) {
            client.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO, MediaType.AUDIO),
                supportedCommands = SocketCapabilities.SUPPORTED_COMMANDS,
                supportsMediaControl = true,
                supportsPersistentIdentifier = true
            )
        }
    }
}
