package app.picnic.player.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.seerr.SeerrSeasonPickItem
import app.picnic.player.data.seerr.activeNon4kRequest
import app.picnic.player.data.seerr.buildSeasonPickItems
import app.picnic.player.data.seerr.canRequestMoreSeasons
import app.picnic.player.data.seerr.seasonsForTvRequest
import app.picnic.player.data.seerr.tmdbIdFromProviderIds
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.ui.navigation.PersonKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream

@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val changeBus: LibraryChangeBus,
    private val settingsStore: SettingsStore,
    private val seerrRepository: SeerrRepository,
    @Assisted private val itemId: String
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(itemId: String): DetailViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        val item: BaseItemDto? = null,
        val similarItems: List<BaseItemDto> = emptyList(),
        val collections: List<BaseItemDto> = emptyList(),
        val session: UserSession? = null,
        val error: String? = null,
        /** Lead-episode streams when [item] is a series (its own DTO carries none). */
        val leadStreams: List<MediaStream>? = null,
        val nextUpEpisode: BaseItemDto? = null,
        val localTrailers: List<BaseItemDto> = emptyList(),
        val trailerYouTubePackage: String? = null,
        val requestMoreTmdbId: Int? = null,
        val requestMoreSeasons: List<SeerrSeasonPickItem> = emptyList(),
        val requestMoreActiveRequest: SeerrMediaRequest? = null,
        val requestMoreCanRequest: Boolean = false,
        val requestMoreError: String? = null,
        val showSeasonPicker: Boolean = false,
        val requestMoreBusy: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            runCatching { mediaRepository.item(session, UUID.fromString(itemId)) }
                .onSuccess { item ->
                    _state.update { it.copy(loading = false, item = item, session = session) }
                    // Secondary rows load after the item so the hero shows immediately.
                    launch {
                        runCatching { mediaRepository.similarItems(session, item.id) }
                            .onSuccess { similar -> _state.update { it.copy(similarItems = similar) } }
                    }
                    launch {
                        val collections = mediaRepository.collectionsContaining(session, item.id)
                        _state.update { it.copy(collections = collections) }
                    }
                    launch {
                        settingsStore.settings.collect { settings ->
                            _state.update { it.copy(trailerYouTubePackage = settings.trailerYouTubePackage) }
                        }
                    }
                    if ((item.localTrailerCount ?: 0) > 0) {
                        launch {
                            val trailers = mediaRepository.localTrailers(session, item.id)
                            _state.update { it.copy(localTrailers = trailers) }
                        }
                    }
                    if (item.type == BaseItemKind.SERIES) {
                        launch {
                            loadRequestMoreState(item)
                        }
                        launch {
                            val streams = mediaRepository.seriesLeadStreams(session, item.id)
                            _state.update { it.copy(leadStreams = streams) }
                        }
                        launch {
                            val nextUp = mediaRepository.nextEpisodeForSeries(session, item.id)
                            _state.update { it.copy(nextUpEpisode = nextUp) }
                        }
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Could not load item") } }
        }

        // Re-fetch when this item changes elsewhere (e.g. watched in the player or a child
        // episode), so the badge/resume state stays current. Optimistic toggles below keep this
        // screen instant; the bus keeps it truthful.
        viewModelScope.launch {
            changeBus.events.collect { change ->
                if (change !is LibraryChange.ItemUpdated) return@collect
                if (change.itemId == itemId || change.seriesId == itemId) {
                    reload()
                } else {
                    patchRails(change.itemId)
                }
            }
        }
    }

    private suspend fun patchRails(changedId: String) {
        val session = state.value.session ?: return
        val current = state.value
        val inRails = current.similarItems.any { it.id.toString() == changedId } ||
            current.collections.any { it.id.toString() == changedId }
        if (!inRails) return
        val updated = runCatching { mediaRepository.item(session, UUID.fromString(changedId)) }
            .getOrNull() ?: return
        _state.update { state ->
            state.copy(
                similarItems = state.similarItems.map { if (it.id == updated.id) updated else it },
                collections = state.collections.map { if (it.id == updated.id) updated else it }
            )
        }
    }

    private fun reload() {
        val session = state.value.session ?: return
        viewModelScope.launch {
            runCatching { mediaRepository.item(session, UUID.fromString(itemId)) }
                .onSuccess { item ->
                    _state.update { it.copy(item = item) }
                    if (item.type == BaseItemKind.SERIES) {
                        loadRequestMoreState(item)
                    }
                }
        }
    }

    fun toggleWatched() {
        val currentItem = state.value.item ?: return
        val currentSession = state.value.session ?: return
        val currentPlayed = currentItem.userData?.played ?: false

        viewModelScope.launch {
            runCatching {
                mediaRepository.setWatched(
                    currentSession,
                    currentItem.id,
                    !currentPlayed,
                    currentItem.seriesId
                )
            }
                .onSuccess { newUserData ->
                    _state.update { it.copy(item = it.item?.copy(userData = newUserData)) }
                }
        }
    }

    fun setFavorite(favorite: Boolean) {
        val currentItem = state.value.item ?: return
        val currentSession = state.value.session ?: return

        viewModelScope.launch {
            runCatching {
                mediaRepository.setFavorite(
                    currentSession,
                    currentItem.id,
                    favorite,
                    currentItem.seriesId
                )
            }
                .onSuccess { newUserData ->
                    _state.update { it.copy(item = it.item?.copy(userData = newUserData)) }
                }
        }
    }

    fun showSeasonPicker() {
        if (state.value.requestMoreSeasons.isEmpty()) return
        _state.update { it.copy(showSeasonPicker = true) }
    }

    fun dismissSeasonPicker() {
        _state.update { it.copy(showSeasonPicker = false) }
    }

    fun requestMoreSeasons(seasons: List<Int>) {
        val s = state.value
        val tmdbId = s.requestMoreTmdbId ?: return
        if (!s.canRequestMore || seasons.isEmpty() || s.requestMoreBusy) return
        viewModelScope.launch {
            _state.update { it.copy(showSeasonPicker = false, requestMoreBusy = true) }
            runCatching {
                val active = s.requestMoreActiveRequest
                val payload = seasonsForTvRequest(seasons, active)
                seerrRepository.requestTv(tmdbId, payload, active?.id)
            }.onSuccess {
                state.value.item?.let { loadRequestMoreState(it) }
            }.onFailure {
                _state.update {
                    it.copy(requestMoreError = "Request failed")
                }
            }
            _state.update { it.copy(requestMoreBusy = false) }
        }
    }

    private suspend fun loadRequestMoreState(item: BaseItemDto) {
        if (seerrRepository.state.value.linkState != SeerrLinkState.Linked) return
        val tmdbId = tmdbIdFromProviderIds(item.providerIds) ?: return
        runCatching {
            val tv = seerrRepository.tv(tmdbId)
            val active = activeNon4kRequest(
                tv.mediaInfo?.requests.orEmpty(),
                seerrRepository::isActiveRequest
            )
            val pickItems = buildSeasonPickItems(
                seasons = tv.seasons,
                mediaInfo = tv.mediaInfo,
                isActiveRequest = seerrRepository::isActiveRequest
            )
            val canRequest = canRequestMoreSeasons(
                seasons = pickItems,
                user = seerrRepository.state.value.user,
                mediaStatus = tv.mediaInfo?.status,
                hasActiveRequest = active != null
            )
            _state.update {
                it.copy(
                    requestMoreTmdbId = tmdbId,
                    requestMoreSeasons = pickItems,
                    requestMoreActiveRequest = active,
                    requestMoreCanRequest = canRequest,
                    requestMoreError = null
                )
            }
        }
    }

    private val UiState.canRequestMore: Boolean
        get() = requestMoreCanRequest && requestMoreSeasons.any { it.selectable }

    /**
     * Library Cast → Hybrid Person. Fetches Person entity for ProviderIds.Tmdb when
     * present; otherwise legacy UUID-only Person (no Missing).
     */
    fun resolvePersonKey(personId: UUID, onResolved: (PersonKey) -> Unit) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            val tmdb = runCatching {
                tmdbIdFromProviderIds(mediaRepository.getPerson(session, personId).providerIds)
            }.getOrNull()
            onResolved(
                PersonKey(
                    jellyfinPersonId = personId.toString(),
                    tmdbId = tmdb
                )
            )
        }
    }
}
