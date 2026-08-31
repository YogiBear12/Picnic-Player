package app.picnic.player.data.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

sealed interface LibraryChange {
    data class ItemUpdated(val itemId: String, val seriesId: String? = null) : LibraryChange

    data object LibraryContentChanged : LibraryChange
}

@Singleton
class LibraryChangeBus @Inject constructor() {
    private val events = MutableSharedFlow<LibraryChange>(
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun changes(): Flow<LibraryChange> = events.buffer(BUFFER_CAPACITY, BufferOverflow.DROP_OLDEST)

    fun emit(change: LibraryChange) {
        events.tryEmit(change)
    }

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}

data class LibraryChangeBatch(val itemIds: Set<String>, val contentChanged: Boolean)

internal const val COALESCE_WINDOW_MS = 250L

@OptIn(ExperimentalCoroutinesApi::class)
fun LibraryChangeBus.batches(): Flow<LibraryChangeBatch> = flow {
    val pending = mutableSetOf<String>()
    var contentChanged = false
    emitAll(
        changes().transformLatest { change ->
            when (change) {
                is LibraryChange.ItemUpdated -> {
                    pending += change.itemId
                    change.seriesId?.let { pending += it }
                }
                LibraryChange.LibraryContentChanged -> contentChanged = true
            }
            delay(COALESCE_WINDOW_MS)
            val batch = LibraryChangeBatch(pending.toSet(), contentChanged)
            pending.clear()
            contentChanged = false
            emit(batch)
        }
    )
}
