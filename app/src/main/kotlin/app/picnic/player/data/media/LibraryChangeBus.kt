package app.picnic.player.data.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer

/**
 * App-wide signal that library data changed, so any screen showing the affected item can
 * recompute instead of holding stale watched/resume/favorite/new-content state. Emitted from
 * the data-layer write choke points ([MediaRepository], `PlaybackRepository`); collected by
 * ViewModels. This is the single shared mechanism — no per-screen refresh band-aids.
 */
sealed interface LibraryChange {
    /** A single item's user data changed (watched/favorite/progress). [seriesId] is set for
     *  episodes so series/season aggregates (Continue Watching, Next Up) also recompute. */
    data class ItemUpdated(val itemId: String, val seriesId: String? = null) : LibraryChange

    /** New items added / a broad refresh hint with no single owning item. */
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
