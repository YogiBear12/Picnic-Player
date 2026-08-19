package app.picnic.player.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@HiltViewModel
class PlaylistsPaneViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val changeBus: LibraryChangeBus
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val session: UserSession? = null,
        val playlists: List<BaseItemDto> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var started = false

    init {
        viewModelScope.launch {
            changeBus.events.collect { change ->
                // A playlist created/renamed/reordered, or an item added/removed, changes the set.
                if (change is LibraryChange.LibraryContentChanged || change is LibraryChange.ItemUpdated) {
                    refresh()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val playlists = runCatching { mediaRepository.playlists() }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, session = session, playlists = playlists) }
        }
    }
}
