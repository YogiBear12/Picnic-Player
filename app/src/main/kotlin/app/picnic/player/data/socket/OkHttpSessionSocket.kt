package app.picnic.player.data.socket

import android.util.Log
import kotlin.math.pow
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.ForceKeepAliveMessage
import org.jellyfin.sdk.model.api.InboundKeepAliveMessage
import org.jellyfin.sdk.model.api.OutboundKeepAliveMessage
import org.jellyfin.sdk.model.api.OutboundWebSocketMessage

// The SDK's socket passes frames through a StateFlow, which drops back-to-back frames; every frame is queued here.
class OkHttpSessionSocket(
    private val okHttp: OkHttpClient,
    private val request: Request,
    private val scope: CoroutineScope,
    private val register: suspend () -> Unit
) : SessionSocket {
    private val _state = MutableStateFlow<SocketApiState>(SocketApiState.Disconnected())
    override val state: StateFlow<SocketApiState> = _state.asStateFlow()

    // One socket per session: the server delivers a session's messages to its newest socket only.
    private val shared = flow {
        var attempt = 0
        while (true) {
            var error: Throwable? = null
            emitAll(connection().catch { error = it })
            if (_state.value == SocketApiState.Connected) attempt = 0
            _state.value = SocketApiState.Disconnected(error)
            delay(reconnectDelay(attempt++))
        }
    }
        .onCompletion { _state.value = SocketApiState.Disconnected() }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS))

    override fun <T : OutboundWebSocketMessage> messages(type: KClass<T>): Flow<T> = shared.filterIsInstance(type)

    override suspend fun registerCapabilities() = register()

    override fun close() = scope.cancel()

    private fun connection(): Flow<OutboundWebSocketMessage> = callbackFlow {
        var keepAlive: Job? = null
        _state.value = SocketApiState.Connecting
        val socket = okHttp.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _state.value = SocketApiState.Connected
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    when (val message = decode(text)) {
                        null, is OutboundKeepAliveMessage -> Unit
                        is ForceKeepAliveMessage -> {
                            keepAlive?.cancel()
                            keepAlive = launch { sendKeepAlive(webSocket, message.data.seconds / 2) }
                        }
                        else -> trySend(message)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(CLOSE_NORMAL, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    channel.close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    channel.close(t)
                }
            }
        )
        awaitClose { socket.close(CLOSE_NORMAL, null) }
    }.buffer(Channel.UNLIMITED)

    private suspend fun sendKeepAlive(socket: WebSocket, every: Duration) {
        val frame = ApiSerializer.encodeSocketMessage(InboundKeepAliveMessage())
        while (true) {
            socket.send(frame)
            delay(every)
        }
    }

    private fun decode(text: String): OutboundWebSocketMessage? = runCatching { ApiSerializer.decodeSocketMessage(text) }
        .onFailure { Log.w(TAG, "Dropping unreadable socket frame", it) }
        .getOrNull()

    private companion object {
        const val TAG = "PicnicSocket"
        const val CLOSE_NORMAL = 1000
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

// Same curve as the SDK's default reconnect policy, so a long outage backs off the same way it did.
internal fun reconnectDelay(attempt: Int): Duration = (1.seconds.inWholeMilliseconds * 1.5.pow(attempt)).milliseconds
    .coerceIn(1.seconds, 5.minutes)
