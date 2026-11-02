package app.picnic.player.data.socket

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

data class Notice(
    val text: String,
    val header: String? = null
)

/** Holds notices sent before the host collects, and hands each to one collector only: a second collector would split the queue. */
@Singleton
class NoticeBus @Inject constructor() {
    private val queue = Channel<Notice>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: Flow<Notice> = queue.receiveAsFlow()

    fun emit(notice: Notice) {
        queue.trySend(notice)
    }
}
