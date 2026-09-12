package app.picnic.player.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.seerr.FilmographyTabContent
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.seerr.seerrFilmography
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = FilmographyViewModel.Factory::class)
class FilmographyViewModel @AssistedInject constructor(
    private val seerrRepository: SeerrRepository,
    @Assisted("tmdbId") val tmdbId: Int,
    @Assisted("knownForDepartment") private val knownForDepartment: String?
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("tmdbId") tmdbId: Int,
            @Assisted("knownForDepartment") knownForDepartment: String?
        ): FilmographyViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        val tabs: List<FilmographyTabContent> = emptyList(),
        val error: String? = null,
        val seerrBaseUrl: String? = null,
        val seerrCacheImages: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            val seerrState = seerrRepository.state.value
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    seerrBaseUrl = seerrState.serverUrl,
                    seerrCacheImages = seerrState.cacheImages
                )
            }
            val result = runCatching { seerrRepository.personCombinedCredits(tmdbId) }
            _state.update { current ->
                result.fold(
                    onSuccess = { credits ->
                        current.copy(
                            loading = false,
                            tabs = seerrFilmography(credits, knownForDepartment),
                            error = null
                        )
                    },
                    onFailure = { error ->
                        current.copy(loading = false, error = error.message ?: "Couldn't load filmography")
                    }
                )
            }
        }
    }
}
