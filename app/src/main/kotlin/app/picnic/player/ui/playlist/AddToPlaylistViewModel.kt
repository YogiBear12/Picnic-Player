package app.picnic.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val playlists: List<BaseItemDto> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun load() {
        _state.update { UiState(loading = true) }
        viewModelScope.launch {
            authRepository.activeSession() ?: run {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val playlists = runCatching { mediaRepository.playlists() }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, playlists = playlists) }
        }
    }

    fun addTo(playlistId: String, itemId: String) {
        viewModelScope.launch {
            authRepository.activeSession() ?: return@launch
            runCatching {
                mediaRepository.addToPlaylist(UUID.fromString(playlistId), listOf(UUID.fromString(itemId)))
            }
        }
    }

    fun createAndAdd(name: String, itemId: String) {
        viewModelScope.launch {
            authRepository.activeSession() ?: return@launch
            runCatching {
                mediaRepository.createPlaylist(name.trim(), listOf(UUID.fromString(itemId)))
            }
        }
    }
}
