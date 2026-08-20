package app.picnic.player.data.socket

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ServerNotice(
    val text: String,
    val header: String? = null
)

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
