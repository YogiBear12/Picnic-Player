package app.picnic.player.ui.detail

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.UserDataRepository
import app.picnic.player.data.media.batches
import app.picnic.player.data.paging.EpisodePagingSource
import app.picnic.player.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto

private const val TAG = "SeriesEpisodes"
private const val SeasonSettleMs = 200L

private fun <T> Flow<T>.collapseBursts(windowMs: Long): Flow<T> = channelFlow {
    var lastValueAt = 0L
    var trailing: Job? = null
    collect { value ->
        trailing?.cancel()
        val now = SystemClock.uptimeMillis()
        val quiet = now - lastValueAt >= windowMs
        lastValueAt = now
        if (quiet) {
            send(value)
        } else {
            trailing = launch {
                delay(windowMs)
                send(value)
            }
        }
    }
}

@HiltViewModel
class SeriesEpisodesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val jellyfin: JellyfinFactory,
    private val changeBus: LibraryChangeBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    var seasons by mutableStateOf<List<BaseItemDto>>(emptyList())
        private set
    var session by mutableStateOf<UserSession?>(null)
        private set
    var seriesItem by mutableStateOf<BaseItemDto?>(null)
        private set
    var seasonsError by mutableStateOf<Throwable?>(null)
        private set

    var seasonsLoading by mutableStateOf(false)
        private set

    private var currentSeriesId: String? = null

    private val _selectedSeasonId = MutableStateFlow<String?>(null)
    private val _refreshTrigger = MutableStateFlow(0)
    private val _visibleSeasonIndices = MutableStateFlow<List<Int>>(emptyList())

    private val episodeMutations = MutableStateFlow<Map<String, (BaseItemDto) -> BaseItemDto>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val episodes: Flow<PagingData<BaseItemDto>> = combine(
        _selectedSeasonId.filterNotNull().distinctUntilChanged().collapseBursts(SeasonSettleMs),
        _refreshTrigger
    ) { seasonId, _ -> seasonId }
        .flatMapLatest { seasonId ->
            val currentSession = session
            val seriesId = currentSeriesId
            if (currentSession == null || seriesId == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    config = PagingConfig(pageSize = 50, enablePlaceholders = false, initialLoadSize = 50),
                    pagingSourceFactory = {
                        val api = jellyfin.api(currentSession.server.baseUrl, currentSession.accessToken)
                        EpisodePagingSource(
                            api = api,
                            ioDispatcher = ioDispatcher,
                            seriesId = seriesId,
                            seasonId = seasonId,
                            userId = currentSession.userUuid,
                            onTotalRecordCount = { count ->
                                val currentSeasons = seasons
                                val seasonIndex = currentSeasons.indexOfFirst { it.id.toString() == seasonId }
                                if (seasonIndex != -1 && currentSeasons[seasonIndex].childCount != count) {
                                    val updatedSeasons = currentSeasons.toMutableList()
                                    updatedSeasons[seasonIndex] = updatedSeasons[seasonIndex].copy(childCount = count)
                                    seasons = updatedSeasons
                                }
                            }
                        )
                    }
                ).flow.cachedIn(viewModelScope).combine(episodeMutations) { pagingData, mutations ->
                    if (mutations.isEmpty()) {
                        pagingData
                    } else {
                        pagingData.map { item -> mutations[item.id.toString()]?.invoke(item) ?: item }
                    }
                }
            }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            session = authRepository.activeSession()
        }
        viewModelScope.launch {
            _visibleSeasonIndices
                .collapseBursts(SeasonSettleMs)
                .collect { indices -> runCatching { fetchSeasonCounts(indices) } }
        }
        viewModelScope.launch {
            changeBus.batches().collect { batch ->
                val seriesId = currentSeriesId ?: return@collect
                if (batch.contentChanged || seriesId in batch.itemIds) {
                    _refreshTrigger.value++
                    loadSeasons(seriesId)
                }
            }
        }
    }

    fun loadSeasons(seriesId: String) {
        val currentSession = session ?: return
        currentSeriesId = seriesId
        viewModelScope.launch {
            runCatching { mediaRepository.item(UUID.fromString(seriesId)) }
                .onSuccess { seriesItem = it }
                .onFailure { Log.w(TAG, "Series item load failed (series=$seriesId)", it) }
        }
        viewModelScope.launch {
            seasonsLoading = true
            try {
                val fastResponse = withContext(ioDispatcher) {
                    val api = jellyfin.api(currentSession.server.baseUrl, currentSession.accessToken)
                    api.tvShowsApi.getSeasons(
                        seriesId = UUID.fromString(seriesId),
                        userId = currentSession.userUuid,
                        fields = emptyList()
                    )
                }
                val fetchedSeasons = fastResponse.content.items

                val existingCounts = seasons.associate { it.id.toString() to it.childCount }
                val mergedSeasons = fetchedSeasons.map { s ->
                    val existing = existingCounts[s.id.toString()]
                    if (existing != null) s.copy(childCount = existing) else s
                }

                seasons = mergedSeasons.sortedWith(compareBy({ it.indexNumber == 0 }, { it.indexNumber }))
                seasonsError = null
            } catch (e: Exception) {
                Log.e(TAG, "Season list load failed (series=$seriesId)", e)
                seasonsError = e
            } finally {
                seasonsLoading = false
            }
        }
    }

    fun prefetchSeasonCounts(visibleIndices: List<Int>) {
        _visibleSeasonIndices.value = visibleIndices
    }

    private suspend fun fetchSeasonCounts(visibleIndices: List<Int>) {
        val currentSession = session ?: return
        val currentSeasons = seasons
        if (currentSeasons.isEmpty()) return

        val missingIds = mutableListOf<UUID>()
        for (idx in visibleIndices) {
            for (i in (idx - 2)..(idx + 4)) {
                val s = currentSeasons.getOrNull(i)
                if (s != null && s.childCount == null) {
                    missingIds.add(s.id)
                }
            }
        }
        val distinctMissingIds = missingIds.distinct()
        if (distinctMissingIds.isEmpty()) return

        val fetchedCounts = mediaRepository.seasonCounts(distinctMissingIds)
        if (fetchedCounts.isNotEmpty()) {
            seasons = seasons.map { s ->
                val count = fetchedCounts[s.id]
                if (count != null && count > 0) s.copy(childCount = count) else s
            }
        }
    }

    fun loadEpisodes(seriesId: String, seasonId: String) {
        if (session == null) return
        currentSeriesId = seriesId
        _selectedSeasonId.value = seasonId
    }

    fun markWatched(episodeId: String, played: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        val currentMutations = episodeMutations.value.toMutableMap()
        currentMutations[episodeId] = { ep -> ep.copy(userData = ep.userData?.copy(played = played)) }
        episodeMutations.value = currentMutations

        viewModelScope.launch {
            runCatching { userDataRepository.setWatched(UUID.fromString(episodeId), played, series) }
        }
    }

    fun markFavorite(episodeId: String, favorite: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        val currentMutations = episodeMutations.value.toMutableMap()
        currentMutations[episodeId] = { ep -> ep.copy(userData = ep.userData?.copy(isFavorite = favorite)) }
        episodeMutations.value = currentMutations

        viewModelScope.launch {
            runCatching { userDataRepository.setFavorite(UUID.fromString(episodeId), favorite, series) }
        }
    }

    fun markSeasonWatched(seasonId: String, played: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        seasons = seasons.map { s ->
            if (s.id.toString() == seasonId) {
                s.copy(userData = s.userData?.copy(played = played))
            } else {
                s
            }
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.setWatched(UUID.fromString(seasonId), played, series)
            }
            episodeMutations.value = emptyMap()
            _refreshTrigger.value++
            currentSeriesId?.let { loadSeasons(it) }
        }
    }

    fun markSeasonFavorite(seasonId: String, favorite: Boolean) {
        val currentSession = session ?: return
        val series = currentSeriesId?.let(UUID::fromString)

        seasons = seasons.map { s ->
            if (s.id.toString() == seasonId) {
                s.copy(userData = s.userData?.copy(isFavorite = favorite))
            } else {
                s
            }
        }

        viewModelScope.launch {
            runCatching {
                userDataRepository.setFavorite(UUID.fromString(seasonId), favorite, series)
            }
        }
    }
}
