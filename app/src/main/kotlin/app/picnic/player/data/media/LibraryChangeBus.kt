package app.picnic.player.data.media

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    private val _events = MutableSharedFlow<LibraryChange>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<LibraryChange> = _events.asSharedFlow()

    fun emit(change: LibraryChange) {
        _events.tryEmit(change)
    }
}
