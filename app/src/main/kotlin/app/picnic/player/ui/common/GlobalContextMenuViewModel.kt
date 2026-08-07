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

    fun setWatched(item: BaseItemDto, played: Boolean) {
        viewModelScope.launch {
            val session = authRepository.activeSession() ?: return@launch
            runCatching {
                mediaRepository.setWatched(
                    session = session,
                    itemId = item.id,
                    played = played,
                    seriesId = item.seriesId
                )
            }
        }
    }

    fun setFavorite(item: BaseItemDto, favorite: Boolean) {
        viewModelScope.launch {
            val session = authRepository.activeSession() ?: return@launch
            runCatching {
                mediaRepository.setFavorite(
                    session = session,
                    itemId = item.id,
                    favorite = favorite,
                    seriesId = item.seriesId
                )
            }
        }
    }
}
