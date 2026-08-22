package app.picnic.player.ui.library

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
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class LibraryPaneViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    data class UiState(
        val selectedTab: LibraryTab = LibraryTab.LIBRARY,
        val collectionsAvailable: Boolean = false,
        val genres: List<BaseItemDto> = emptyList(),
        val genresLoading: Boolean = false,
        val genresError: Boolean = false,
        val focusedGenreIndex: Int = 0
    ) {
        val tabs: List<LibraryTab>
            get() =
                if (collectionsAvailable) {
                    LibraryTab.entries.toList()
                } else {
                    LibraryTab.entries.filterNot { it == LibraryTab.COLLECTIONS }
                }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var libraryId: UUID? = null
    private var kinds: List<BaseItemKind> = emptyList()
    private var bound = false
    private var collectionsProbed = false
    private var genresRequested = false

    fun bind(libraryId: UUID, kinds: List<BaseItemKind>) {
        probeCollections()
        if (bound) return
        bound = true
        this.libraryId = libraryId
        this.kinds = kinds
    }

    fun selectTab(tab: LibraryTab) {
        probeCollections()
        if (_state.value.selectedTab == tab) return
        _state.update { it.copy(selectedTab = tab) }
        if (tab == LibraryTab.GENRES) ensureGenresLoaded()
    }

    private fun probeCollections() {
        if (collectionsProbed) return
        collectionsProbed = true
        viewModelScope.launch {
            val session = authRepository.activeSession()
            val count = session?.let { runCatching { mediaRepository.collectionCount() }.getOrNull() }
            if (count == null) {
                collectionsProbed = false
                return@launch
            }
            _state.update { it.copy(collectionsAvailable = count > 0) }
        }
    }

    fun retryGenres() = ensureGenresLoaded()

    private fun ensureGenresLoaded() {
        if (genresRequested) return
        val libraryId = libraryId ?: return
        genresRequested = true
        viewModelScope.launch {
            _state.update { it.copy(genresLoading = true) }
            val session = authRepository.activeSession()
            val genres = session?.let {
                runCatching { mediaRepository.genres(libraryId, kinds) }.getOrNull()
            }
            if (genres == null) genresRequested = false
            _state.update {
                it.copy(
                    genresLoading = false,
                    genresError = genres == null,
                    genres = genres.orEmpty()
                )
            }
        }
    }

    fun onGenreFocused(index: Int) = _state.update { it.copy(focusedGenreIndex = index) }
}
