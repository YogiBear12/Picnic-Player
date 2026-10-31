package app.picnic.player.data.socket

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.OutboundWebSocketMessage
import org.jellyfin.sdk.model.api.ServerRestartingMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpSessionSocketTest {

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket by lazy {
        OkHttpSessionSocket(OkHttpClient(), Request.Builder().url(server.url("/socket")).build(), scope) {}
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun restarting(id: UUID) = """{"MessageType":"ServerRestarting","MessageId":"$id"}"""

    private fun upgrade(onOpen: (WebSocket) -> Unit, onMessage: (String) -> Unit = {}, onClosing: () -> Unit = {}) = server.enqueue(
        MockResponse().withWebSocketUpgrade(
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(webSocket)
                override fun onMessage(webSocket: WebSocket, text: String) = onMessage(text)
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    onClosing()
                    webSocket.close(code, null)
                }
            }
        )
    )

    private fun ids(messages: List<OutboundWebSocketMessage>) = messages.map { (it as ServerRestartingMessage).messageId }

    @Test
    fun framesSentBackToBackAllArriveInOrder() = runBlocking {
        val sent = List(20) { UUID.randomUUID() }
        upgrade(onOpen = { ws -> sent.forEach { ws.send(restarting(it)) } })

        val received = withTimeout(5_000) { socket.messages(OutboundWebSocketMessage::class).take(20).toList() }

        assertEquals(sent, ids(received))
    }

    @Test
    fun forceKeepAliveIsAnsweredWithKeepAlive() = runBlocking {
        val fromClient = LinkedBlockingQueue<String>()
        upgrade(onOpen = { ws -> ws.send("""{"MessageType":"ForceKeepAlive","Data":2,"MessageId":"${UUID.randomUUID()}"}""") }, onMessage = fromClient::add)

        val job = scope.launch { socket.messages(OutboundWebSocketMessage::class).collect {} }
        val reply = fromClient.poll(5, TimeUnit.SECONDS)
        job.cancel()

        assertNotNull(reply)
        assertEquals("KeepAlive", Regex("\"MessageType\":\"(\\w+)\"").find(reply!!)?.groupValues?.get(1))
    }

    @Test
    fun aServerCloseReconnectsAndLaterFramesArrive() = runBlocking {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        upgrade(onOpen = { ws ->
            ws.send(restarting(first))
            ws.close(1001, "restarting")
        })
        upgrade(onOpen = { ws -> ws.send(restarting(second)) })

        val received = withTimeout(10_000) { socket.messages(OutboundWebSocketMessage::class).take(2).toList() }

        assertEquals(listOf(first, second), ids(received))
        assertEquals(2, server.requestCount)
        withTimeout(5_000) { socket.state.first { it == SocketApiState.Connected } }
        Unit
    }

    @Test
    fun closeDropsTheConnectionWithoutWaitingForTheIdleTimeout() = runBlocking {
        val closedByClient = CountDownLatch(1)
        upgrade(onOpen = { ws -> ws.send(restarting(UUID.randomUUID())) }, onClosing = closedByClient::countDown)
        scope.launch { socket.messages(OutboundWebSocketMessage::class).collect {} }
        withTimeout(5_000) { socket.state.first { it == SocketApiState.Connected } }

        socket.close()

        assertTrue(closedByClient.await(2, TimeUnit.SECONDS))
    }
}
