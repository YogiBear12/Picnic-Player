package app.picnic.player.ui.seerr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.seerr.SeerrCastMember
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrMovieDetails
import app.picnic.player.data.seerr.SeerrPermission
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.seerr.SeerrSeasonPickItem
import app.picnic.player.data.seerr.SeerrTvDetails
import app.picnic.player.data.seerr.SeerrTvSeason
import app.picnic.player.data.seerr.SeerrUser
import app.picnic.player.data.seerr.buildSeasonPickItems
import app.picnic.player.data.seerr.castRow
import app.picnic.player.data.seerr.seasonsForTvRequest
import app.picnic.player.data.seerr.shouldOpenJellyfinDetail
import app.picnic.player.data.seerr.toCatalogItem
import app.picnic.player.data.settings.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SeerrPrimaryAction {
    Play,
    Request,
    RequestMore,

    Pending,

    Unavailable
}

internal fun primaryIcon(action: SeerrPrimaryAction): ImageVector = when (action) {
    SeerrPrimaryAction.Play -> Icons.Default.PlayArrow
    SeerrPrimaryAction.Request,
    SeerrPrimaryAction.RequestMore
    -> Icons.Default.Add
    SeerrPrimaryAction.Pending -> Icons.Default.Check
    SeerrPrimaryAction.Unavailable -> Icons.Default.Close
}

enum class SeerrActionFocusTarget {
    Primary,
    Trailer,
    Cancel,
    Summary
}

data class SeerrActionRow(
    val primary: SeerrPrimaryAction,
    val primaryLabel: String,
    val showCancel: Boolean,
    val cancelLabel: String = "Cancel request",
    val infoMessage: String? = null,
    val initialFocus: SeerrActionFocusTarget
)

@HiltViewModel(assistedFactory = SeerrDetailViewModel.Factory::class)
class SeerrDetailViewModel @AssistedInject constructor(
    private val seerrRepository: SeerrRepository,
    private val settingsStore: SettingsStore,
    @Assisted private val tmdbId: Int,
    @Assisted private val mediaType: SeerrMediaType
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(tmdbId: Int, mediaType: SeerrMediaType): SeerrDetailViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        val catalog: SeerrCatalogItem? = null,
        val movie: SeerrMovieDetails? = null,
        val tv: SeerrTvDetails? = null,
        val cast: List<SeerrCastMember> = emptyList(),
        val recommended: List<SeerrCatalogItem> = emptyList(),
        val seasons: List<SeerrTvSeason> = emptyList(),
        val user: SeerrUser? = null,
        val serverUrl: String? = null,
        val cacheImages: Boolean = false,
        val trailerYouTubePackage: String? = null,
        val error: String? = null,
        val actionError: String? = null,
        val busy: Boolean = false,
        val showSeasonPicker: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            seerrRepository.state.collect { seerr ->
                _state.update {
                    it.copy(
                        user = seerr.user,
                        serverUrl = seerr.serverUrl,
                        cacheImages = seerr.cacheImages
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(trailerYouTubePackage = settings.trailerYouTubePackage) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadDetail(showLoading = true)
        }
    }

    fun primaryRequest(): SeerrMediaRequest? {
        val requests = (_state.value.movie?.mediaInfo ?: _state.value.tv?.mediaInfo)
            ?.requests
            .orEmpty()
        return requests.firstOrNull { seerrRepository.isActiveRequest(it) }
            ?: requests.firstOrNull()
    }

    fun canRequest(): Boolean {
        val s = _state.value
        return seerrRepository.canRequest(
            user = s.user,
            mediaType = mediaType,
            status = s.catalog?.mediaStatus,
            hasActiveRequest = seerrRepository.isActiveRequest(primaryRequest())
        )
    }

    fun canCancel(): Boolean = seerrRepository.canCancel(_state.value.user, primaryRequest())

    fun actionRow(): SeerrActionRow {
        val s = _state.value
        val catalog = s.catalog
        val active = seerrRepository.isActiveRequest(primaryRequest())
        val allowRequest = canRequest()
        val allowCancel = canCancel()
        val busy = s.busy

        if (catalog != null &&
            shouldOpenJellyfinDetail(catalog.jellyfinMediaId, catalog.jellyfinMediaId4k)
        ) {
            return SeerrActionRow(
                primary = SeerrPrimaryAction.Play,
                primaryLabel = "Play",
                showCancel = false,
                initialFocus = SeerrActionFocusTarget.Primary
            )
        }

        if (allowRequest) {
            val more = mediaType == SeerrMediaType.TV && active
            return SeerrActionRow(
                primary = if (more) SeerrPrimaryAction.RequestMore else SeerrPrimaryAction.Request,
                primaryLabel = when {
                    busy -> "Requesting…"
                    more -> "Request more"
                    else -> "Request"
                },
                showCancel = allowCancel,
                cancelLabel = if (busy) "Cancelling…" else "Cancel request",
                initialFocus = SeerrActionFocusTarget.Primary
            )
        }

        if (active) {
            return SeerrActionRow(
                primary = SeerrPrimaryAction.Pending,
                primaryLabel = "Pending",
                showCancel = allowCancel,
                cancelLabel = if (busy) "Cancelling…" else "Cancel request",
                initialFocus = if (allowCancel) {
                    SeerrActionFocusTarget.Cancel
                } else {
                    SeerrActionFocusTarget.Primary
                }
            )
        }

        val reason = permissionHelper(s.user)
        return SeerrActionRow(
            primary = SeerrPrimaryAction.Unavailable,
            primaryLabel = "Unavailable",
            showCancel = false,
            infoMessage = reason,
            initialFocus = SeerrActionFocusTarget.Primary
        )
    }

    fun onRequestClicked() {
        if (!canRequest() || _state.value.busy) return
        when (mediaType) {
            SeerrMediaType.MOVIE -> requestMovie()
            SeerrMediaType.TV -> _state.update { it.copy(showSeasonPicker = true) }
        }
    }

    fun dismissSeasonPicker() = _state.update { it.copy(showSeasonPicker = false) }

    fun seasonPickItems(): List<SeerrSeasonPickItem> {
        val s = _state.value
        return buildSeasonPickItems(
            seasons = s.seasons,
            mediaInfo = s.tv?.mediaInfo,
            isActiveRequest = seerrRepository::isActiveRequest
        )
    }

    fun requestMovie() {
        if (!canRequest() || _state.value.busy) return
        viewModelScope.launch {
            runAction(failureLabel = "Request failed") {
                seerrRepository.requestMovie(tmdbId)
            }
        }
    }

    fun requestSeasons(seasons: List<Int>) {
        if (seasons.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(showSeasonPicker = false) }
            val active = primaryRequest()?.takeIf { seerrRepository.isActiveRequest(it) }
            val payload = seasonsForTvRequest(seasons, active)
            runAction(failureLabel = "Request failed") {
                seerrRepository.requestTv(tmdbId, payload, active?.id)
            }
        }
    }

    fun cancelRequest() {
        val id = primaryRequest()?.id ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            runAction(failureLabel = "Cancel failed") {
                seerrRepository.cancelRequest(id)
            }
        }
    }

    private suspend fun runAction(failureLabel: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, actionError = null) }
        val actionResult = runCatching { block() }
        if (actionResult.isFailure) {
            val e = actionResult.exceptionOrNull()
            _state.update {
                it.copy(busy = false, actionError = e?.message ?: failureLabel)
            }
            return
        }
        val refreshOk = loadDetail(showLoading = false)
        _state.update {
            it.copy(
                busy = false,
                actionError = if (refreshOk) null else "Updated, but refresh failed"
            )
        }
    }

    private fun permissionHelper(user: SeerrUser?): String {
        if (user == null) return "Sign in to Seerr to request titles"
        val perms = user.permissions
        val canType = when (mediaType) {
            SeerrMediaType.MOVIE ->
                SeerrPermission.has(perms, SeerrPermission.REQUEST) ||
                    SeerrPermission.has(perms, SeerrPermission.REQUEST_MOVIE)
            SeerrMediaType.TV ->
                SeerrPermission.has(perms, SeerrPermission.REQUEST) ||
                    SeerrPermission.has(perms, SeerrPermission.REQUEST_TV)
        }
        return if (!canType) {
            when (mediaType) {
                SeerrMediaType.MOVIE -> "You don't have permission to request movies"
                SeerrMediaType.TV -> "You don't have permission to request TV"
            }
        } else {
            "This title can't be requested"
        }
    }

    private suspend fun loadDetail(showLoading: Boolean): Boolean {
        if (showLoading) {
            _state.update { it.copy(loading = true, error = null) }
        } else {
            _state.update { it.copy(error = null) }
        }
        val detailOk = runCatching {
            when (mediaType) {
                SeerrMediaType.MOVIE -> {
                    val movie = seerrRepository.movie(tmdbId)
                    _state.update {
                        it.copy(
                            loading = false,
                            movie = movie,
                            tv = null,
                            catalog = movie.toCatalogItem(),
                            cast = movie.castRow(),
                            seasons = emptyList()
                        )
                    }
                }
                SeerrMediaType.TV -> {
                    val tv = seerrRepository.tv(tmdbId)
                    _state.update {
                        it.copy(
                            loading = false,
                            tv = tv,
                            movie = null,
                            catalog = tv.toCatalogItem(),
                            cast = tv.castRow(),
                            seasons = tv.seasons.filter { s -> s.seasonNumber > 0 }
                        )
                    }
                }
            }
        }.onFailure { e ->
            _state.update {
                it.copy(loading = false, error = e.message ?: "Failed to load")
            }
        }.isSuccess
        if (!detailOk) return false

        val recommended = runCatching {
            when (mediaType) {
                SeerrMediaType.MOVIE -> seerrRepository.movieRecommendations(tmdbId)
                SeerrMediaType.TV -> seerrRepository.tvRecommendations(tmdbId)
            }
        }.getOrDefault(emptyList())
        _state.update { it.copy(recommended = recommended) }
        return true
    }
}
