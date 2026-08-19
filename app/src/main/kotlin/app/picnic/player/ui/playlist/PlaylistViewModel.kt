package app.picnic.player.ui.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val changeBus: LibraryChangeBus
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val session: UserSession? = null,
        val items: List<BaseItemDto> = emptyList(),
        val error: String? = null
    )

    var state by mutableStateOf(UiState())
        private set

    private var playlistId: UUID? = null

    fun load(playlistId: String) {
        val id = UUID.fromString(playlistId)
        if (this.playlistId == id && !state.loading) return
        this.playlistId = id
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                state = UiState(loading = false, error = "Not signed in")
                return@launch
            }
            observeChanges(id)
            refresh(session, id)
        }
    }

    private var observing = false

    private fun observeChanges(id: UUID) {
        if (observing) return
        observing = true
        viewModelScope.launch {
            changeBus.events.collect { change ->
                val session = state.session ?: return@collect
                val touched = change is LibraryChange.ItemUpdated &&
                    (change.itemId == id.toString() || state.items.any { it.id.toString() == change.itemId })
                if (touched) refresh(session, id)
            }
        }
    }

    private suspend fun refresh(session: UserSession, id: UUID) {
        val items = runCatching { mediaRepository.playlistItems(id) }
        state = state.copy(
            loading = false,
            session = session,
            items = items.getOrDefault(emptyList()),
            error = items.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        )
    }

    fun markWatched(itemId: String, played: Boolean, seriesId: String?) {
        val session = state.session ?: return
        viewModelScope.launch {
            runCatching {
                mediaRepository.setWatched(UUID.fromString(itemId), played, seriesId?.let(UUID::fromString))
            }
        }
    }

    fun markFavorite(itemId: String, favorite: Boolean, seriesId: String?) {
        val session = state.session ?: return
        viewModelScope.launch {
            runCatching {
                mediaRepository.setFavorite(UUID.fromString(itemId), favorite, seriesId?.let(UUID::fromString))
            }
        }
    }

    fun removeEntry(playlistItemId: String) {
        val session = state.session ?: return
        val id = playlistId ?: return
        viewModelScope.launch {
            runCatching { mediaRepository.removeFromPlaylist(id, listOf(playlistItemId)) }
        }
    }

    /**
     * Reorders the item at [fromIndex] to [toIndex]. Applies optimistically so the row moves
     * under focus immediately; the server call (and its change-bus refresh) confirm after.
     */
    fun move(fromIndex: Int, toIndex: Int) {
        val session = state.session ?: return
        val id = playlistId ?: return
        val items = state.items
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        val entryId = items[fromIndex].playlistItemId ?: return
        val reordered = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        state = state.copy(items = reordered)
        viewModelScope.launch {
            runCatching { mediaRepository.movePlaylistItem(id, entryId, toIndex) }
        }
    }
}
