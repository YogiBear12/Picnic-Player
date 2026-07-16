package app.picnic.player.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@HiltViewModel
class GlobalContextMenuViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    fun toggleWatched(item: BaseItemDto, currentPlayed: Boolean) {
        viewModelScope.launch {
            val session = authRepository.activeSession() ?: return@launch
            runCatching {
                mediaRepository.setWatched(
                    session = session,
                    itemId = item.id,
                    played = !currentPlayed,
                    seriesId = item.seriesId
                )
            }
        }
    }

    fun toggleFavorite(item: BaseItemDto, currentFavorite: Boolean) {
        viewModelScope.launch {
            val session = authRepository.activeSession() ?: return@launch
            runCatching {
                mediaRepository.setFavorite(
                    session = session,
                    itemId = item.id,
                    favorite = !currentFavorite,
                    seriesId = item.seriesId
                )
            }
        }
    }
}
