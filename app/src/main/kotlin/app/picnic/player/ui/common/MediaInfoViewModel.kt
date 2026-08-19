package app.picnic.player.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaSourceInfo

@HiltViewModel
class MediaInfoViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data class Loaded(
            val sources: List<MediaSourceInfo>,
            val chapters: List<ChapterInfo>
        ) : State
        data object Error : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedItemId: UUID? = null

    fun load(itemId: UUID) {
        if (loadedItemId == itemId && _state.value !is State.Error) return
        loadedItemId = itemId
        _state.value = State.Loading
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                _state.value = State.Error
                return@launch
            }
            runCatching { mediaRepository.item(itemId) }
                .onSuccess { item ->
                    val sources = item.mediaSources.orEmpty()
                    _state.value = if (sources.isEmpty()) {
                        State.Error
                    } else {
                        State.Loaded(sources, item.chapters.orEmpty())
                    }
                }
                .onFailure { _state.value = State.Error }
        }
    }
}
