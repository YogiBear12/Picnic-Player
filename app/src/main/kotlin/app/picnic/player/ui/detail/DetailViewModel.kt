package app.picnic.player.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.BROWSE_FIELDS
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.UserDataRepository
import app.picnic.player.data.media.batches
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
import app.picnic.player.data.socket.ServerMessageBus
import app.picnic.player.data.socket.ServerNotice
import app.picnic.player.ui.common.RowCursor
import app.picnic.player.ui.common.RowPage
import app.picnic.player.ui.common.RowPager
import app.picnic.player.ui.common.loadOrderedPage
import app.picnic.player.ui.navigation.PersonKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream

@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val changeBus: LibraryChangeBus,
    private val settingsStore: SettingsStore,
    private val seerrRepository: SeerrRepository,
    private val serverMessageBus: ServerMessageBus,
    @Assisted private val itemId: String
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(itemId: String): DetailViewModel
    }

    data class UiState(
        val loading: Boolean = true,
        val item: BaseItemDto? = null,
        val collections: List<BaseItemDto> = emptyList(),
        val relatedRows: List<RelatedRow> = emptyList(),
        val session: UserSession? = null,
        val error: String? = null,
        val leadStreams: List<MediaStream>? = null,
        val nextUpEpisode: BaseItemDto? = null,
        val localTrailers: List<BaseItemDto> = emptyList(),
        val trailerYouTubePackage: String? = null,
        val requestMoreTmdbId: Int? = null,
        val requestMoreSeasons: List<SeerrSeasonPickItem> = emptyList(),
        val requestMoreActiveRequest: SeerrMediaRequest? = null,
        val requestMoreCanRequest: Boolean = false,
        val requestMoreError: String? = null,
        val requestMoreBusy: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var lazySpecs = emptyList<RelatedRowSpec>()
    private var lazyRowsRequested = false
    private val relatedPager = RowPager(viewModelScope) { key, page ->
        updateRow(key) { row -> row.copy(items = (row.items.orEmpty() + page).distinctBy { it.id }) }
    }

    init {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            runCatching { mediaRepository.item(UUID.fromString(itemId)) }
                .onSuccess { item ->
                    val relatedSpecs = relatedRowSpecs(item)
                    relatedSpecs.forEach { spec ->
                        relatedPager.add(spec.key, RowCursor(RELATED_PAGE_SIZE)) { cursor -> fetchRelatedPage(spec.query, cursor) }
                    }
                    val (eagerSpecs, lazy) = relatedSpecs.partition { it.eager }
                    lazySpecs = lazy
                    _state.update {
                        it.copy(
                            loading = false,
                            item = item,
                            session = session,
                            relatedRows = relatedSpecs.map { spec ->
                                RelatedRow(spec.key, spec.title)
                            }
                        )
                    }
                    val eagerJob = launch {
                        eagerSpecs.mapNotNull { relatedPager.load(it.key) }.joinAll()
                    }
                    val collectionsJob = launch {
                        runCatching { mediaRepository.collectionsContaining(item.id) }
                            .onSuccess { collections -> _state.update { it.copy(collections = collections) } }
                    }
                    launch {
                        joinAll(eagerJob, collectionsJob)
                        val current = state.value
                        val eagerRowsEmpty = eagerSpecs.all { spec ->
                            current.relatedRows.first { it.key == spec.key }.items.isNullOrEmpty()
                        }
                        if (needsAutoLoad(hasPeople = !item.people.isNullOrEmpty(), eagerRowsEmpty = eagerRowsEmpty, hasCollections = current.collections.isNotEmpty())) {
                            loadRelatedRows()
                        }
                    }
                    launch {
                        settingsStore.settings.collect { settings ->
                            _state.update { it.copy(trailerYouTubePackage = settings.trailerYouTubePackage) }
                        }
                    }
                    if ((item.localTrailerCount ?: 0) > 0) {
                        launch {
                            val trailers = mediaRepository.localTrailers(item.id)
                            _state.update { it.copy(localTrailers = trailers) }
                        }
                    }
                    if (item.type == BaseItemKind.SERIES) {
                        loadSeriesExtras(item)
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Could not load item") } }
        }

        viewModelScope.launch {
            seerrRepository.state
                .map { it.linkState }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    val item = state.value.item ?: return@collect
                    if (item.type == BaseItemKind.SERIES) loadRequestMoreState(item)
                }
        }

        viewModelScope.launch {
            changeBus.batches().collect { batch ->
                if (batch.contentChanged || itemId in batch.itemIds) reload() else patchRails(batch.itemIds)
            }
        }
    }

    private suspend fun patchRails(changedIds: Set<String>) {
        val current = state.value
        val shown = current.collections + current.relatedRows.flatMap { it.items.orEmpty() }
        val fresh = mediaRepository.refreshChanged(shown, changedIds)
        if (fresh.isEmpty()) return
        _state.update { state ->
            state.copy(
                collections = state.collections.map { fresh[it.id] ?: it },
                relatedRows = state.relatedRows.map { row ->
                    row.copy(items = row.items?.map { fresh[it.id] ?: it })
                }
            )
        }
    }

    fun loadRelatedRows() {
        if (lazyRowsRequested || lazySpecs.isEmpty()) return
        lazyRowsRequested = true
        lazySpecs.forEach { relatedPager.load(it.key) }
    }

    fun loadMoreRelated(key: String, focusedIndex: Int) {
        val items = state.value.relatedRows.firstOrNull { it.key == key }?.items ?: return
        relatedPager.onFocused(key, focusedIndex, items.size)
    }

    private suspend fun fetchRelatedPage(query: RelatedRowQuery, cursor: RowCursor): RowPage {
        val currentItemId = UUID.fromString(itemId)
        return when (query) {
            is RelatedRowQuery.Person -> {
                val page = mediaRepository.getItemsByPerson(
                    query.id,
                    listOf(query.type),
                    personType = query.kind,
                    excludeId = currentItemId,
                    startIndex = cursor.next,
                    limit = cursor.pageSize,
                    fields = BROWSE_FIELDS
                )
                cursor.next += page.size
                RowPage(page, page.size < cursor.pageSize)
            }
            RelatedRowQuery.Similar -> loadOrderedPage(
                cursor,
                { mediaRepository.similarItems(currentItemId, SIMILAR_ID_CAP, emptyList()).map { it.id } },
                mediaRepository::items
            )
            is RelatedRowQuery.Genre -> loadOrderedPage(
                cursor,
                { mediaRepository.shuffledGenreIds(query.id, query.type, currentItemId, limit = RELATED_ID_CAP) },
                mediaRepository::items
            )
        }
    }

    private fun updateRow(key: String, transform: (RelatedRow) -> RelatedRow) {
        _state.update { state ->
            state.copy(relatedRows = state.relatedRows.map { row -> if (row.key == key) transform(row) else row })
        }
    }

    private fun CoroutineScope.loadSeriesExtras(item: BaseItemDto) {
        launch { loadRequestMoreState(item) }
        launch {
            val streams = mediaRepository.seriesLeadStreams(item.id)
            _state.update { it.copy(leadStreams = streams) }
        }
        launch {
            val nextUp = mediaRepository.nextEpisodeForSeries(item.id)
            _state.update { it.copy(nextUpEpisode = nextUp) }
        }
    }

    private fun reload() {
        if (state.value.session == null) return
        viewModelScope.launch {
            runCatching { mediaRepository.item(UUID.fromString(itemId)) }
                .onSuccess { item ->
                    _state.update { it.copy(item = item) }
                    if (item.type == BaseItemKind.SERIES) {
                        loadSeriesExtras(item)
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
                userDataRepository.setWatched(
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
                userDataRepository.setFavorite(
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

    fun requestMoreSeasons(seasons: List<Int>) {
        val s = state.value
        val tmdbId = s.requestMoreTmdbId ?: return
        if (!s.canRequestMore || seasons.isEmpty() || s.requestMoreBusy) return
        viewModelScope.launch {
            _state.update { it.copy(requestMoreBusy = true) }
            runCatching {
                val active = s.requestMoreActiveRequest
                val payload = seasonsForTvRequest(seasons, active)
                seerrRepository.requestTv(tmdbId, payload, active?.id)
            }.onSuccess {
                serverMessageBus.emit(ServerNotice(text = "Request submitted"))
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

    fun resolvePersonKey(personId: UUID, onResolved: (PersonKey) -> Unit) {
        val session = state.value.session ?: return
        viewModelScope.launch {
            val tmdb = runCatching {
                tmdbIdFromProviderIds(mediaRepository.getPerson(personId).providerIds)
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
