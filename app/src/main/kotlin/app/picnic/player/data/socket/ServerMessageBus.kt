package app.picnic.player.data.socket

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A transient on-screen notice pushed from the server over the socket.
 *
 * Two sources feed it: remote `GeneralCommand`s that carry text to display
 * (`DisplayMessage` / `SendString`) and server-lifecycle events
 * (`ServerRestarting` / `ServerShuttingDown` / `RestartRequired`). The app-level
 * notice host (in `MainActivity`) is the sole collector and shows each briefly.
 */
data class ServerNotice(
    val text: String,
    /** Optional bold heading (e.g. the `DisplayMessage` header). */
    val header: String? = null
)

/**
 * Singleton fan-in for [ServerNotice]s, mirroring
 * [LibraryChangeBus][app.picnic.player.data.media.LibraryChangeBus].
 */
@Singleton
class ServerMessageBus @Inject constructor() {
    private val _messages = MutableSharedFlow<ServerNotice>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<ServerNotice> = _messages.asSharedFlow()

    fun emit(notice: ServerNotice) {
        _messages.tryEmit(notice)
    }
}
