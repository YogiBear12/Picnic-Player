package app.picnic.player.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@HiltViewModel
class GlobalContextMenuViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    suspend fun libraryItem(itemId: String): BaseItemDto? {
        authRepository.activeSession() ?: return null
        return runCatching { mediaRepository.item(UUID.fromString(itemId)) }.getOrNull()
    }

    fun setWatched(item: BaseItemDto, played: Boolean) {
        viewModelScope.launch {
            authRepository.activeSession() ?: return@launch
            runCatching {
                userDataRepository.setWatched(
                    itemId = item.id,
                    played = played,
                    seriesId = item.seriesId
                )
            }
        }
    }

    fun setFavorite(item: BaseItemDto, favorite: Boolean) {
        viewModelScope.launch {
            authRepository.activeSession() ?: return@launch
            runCatching {
                userDataRepository.setFavorite(
                    itemId = item.id,
                    favorite = favorite,
                    seriesId = item.seriesId
                )
            }
        }
    }
}
