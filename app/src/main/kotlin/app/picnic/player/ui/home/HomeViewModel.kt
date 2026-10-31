package app.picnic.player.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.jellyfin.serverErrorMessage
import app.picnic.player.data.media.DiscoveryScope
import app.picnic.player.data.media.HiddenResumeStore
import app.picnic.player.data.media.HomeContent
import app.picnic.player.data.media.HomeContentLoader
import app.picnic.player.data.media.HomeDiscovery
import app.picnic.player.data.media.HomeLoad
import app.picnic.player.data.media.HomeResult
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.HomeSlot
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.batches
import app.picnic.player.data.media.fetchHeroPrefetch
import app.picnic.player.data.media.planHeroStreamPrefetch
import app.picnic.player.data.media.seriesNeedingSeasonCount
import app.picnic.player.data.media.withSeriesFallback
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.tvprovider.TvChannelReceiver
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.browse.NavRailState
import app.picnic.player.ui.common.BrowseRowFocus
import app.picnic.player.ui.common.DiscoveryFeed
import app.picnic.player.ui.common.PendingDiscoveryRow
import app.picnic.player.ui.common.retarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject internal constructor(
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val homeLoader: HomeContentLoader,
    private val homeDiscovery: HomeDiscovery,
    private val changeBus: LibraryChangeBus,
    private val hiddenResumeStore: HiddenResumeStore,
    settingsStore: SettingsStore,
    val ambientLoader: AmbientPaletteLoader,
    private val navRail: NavRailState
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val rows: List<HomeRow> = emptyList(),
        val pendingSlots: List<HomeSlot>? = null,
        val pendingDiscoveryRows: List<PendingDiscoveryRow> = emptyList(),
        val session: UserSession? = null,
        val error: String? = null,
        val sessionExpiredServerId: String? = null,
        val serverUnreachable: Pair<String, String>? = null,
        val focusedRowIndex: Int = 0,
        val focusedItemId: UUID? = null,
        val rowFocusedItemIds: Map<Int, UUID> = emptyMap(),
        val seasonCounts: Map<UUID, Int> = emptyMap(),
        val heroStreams: Map<UUID, List<MediaStream>> = emptyMap(),
        val seriesItems: Map<UUID, BaseItemDto> = emptyMap(),
        val seasonLeads: Map<UUID, BaseItemDto> = emptyMap()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    val colouredFocus = settingsStore.settings
        .map { it.colouredFocus }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val capBadgeCount = settingsStore.settings
        .map { it.capBadgeCount }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val focusChangedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var channelSyncRequested = false
    private val seasonCountGate = Semaphore(SEASON_COUNT_CONCURRENCY)

    private var lastResult: HomeResult? = null
    private var mainRows: List<HomeRow> = emptyList()
    private val discoveryFeed: DiscoveryFeed = DiscoveryFeed(
        scope = viewModelScope,
        discovery = homeDiscovery,
        mediaRepository = mediaRepository,
        target = DiscoveryScope.HOME,
        focusedInFeed = { _state.value.focusedRowIndex >= mainRows.size && discoveryFeed.rows.isNotEmpty() },
        awaitHostRows = { _state.first { !it.loading } },
        onChanged = { _state.update { it.withRows(mainRows) } },
        onRowsPublished = { added ->
            focusChangedFlow.tryEmit(Unit)
            viewModelScope.launch { resolveSeasonCounts(added) }
        },
        onPageAppended = { appended -> viewModelScope.launch { resolveSeasonCounts(appended) } }
    )

    init {
        load()
        viewModelScope.launch {
            changeBus.batches().collect { batch ->
                discoveryFeed.markChanged(batch.itemIds)
                refresh()
                discoveryFeed.patch(batch.itemIds)
            }
        }
        viewModelScope.launch {
            focusChangedFlow.debounce(200).collectLatest { prefetchStreamsAhead() }
        }
        viewModelScope.launch {
            navRail.layoutEpoch.drop(1).collectLatest { rebuildRowsFromCache() }
        }
        viewModelScope.launch {
            hiddenResumeStore.hidden.drop(1).collectLatest { rebuildRowsFromCache() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val session = _state.value.session ?: authRepository.activeSession() ?: return@launch
            discoveryFeed.resume()
            requestDiscovery()
            applyOrError(runCatching { homeLoader.fetch(session) }, session)
        }
    }

    fun focusedItem(state: UiState = _state.value): BaseItemDto? {
        val rows = state.rows
        if (rows.isEmpty()) return null
        state.focusedItemId?.let { id ->
            rows.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.id == id }
                ?.let { return it.withSeriesFallback(state.seriesItems, state.seasonLeads) }
        }
        return rows.firstOrNull()?.items?.firstOrNull()?.withSeriesFallback(state.seriesItems, state.seasonLeads)
    }

    fun focusedRow(state: UiState = _state.value): HomeRow? = state.rows.getOrNull(state.focusedRowIndex)

    fun onBrowseItemFocused(rowIndex: Int, item: BaseItemDto) {
        _state.update {
            it.copy(
                focusedRowIndex = rowIndex,
                focusedItemId = item.id,
                rowFocusedItemIds = it.rowFocusedItemIds + (rowIndex to item.id)
            )
        }
        focusChangedFlow.tryEmit(Unit)
        requestDiscovery()
        _state.value.rows.getOrNull(rowIndex)?.let { row -> discoveryFeed.onFocused(row, item) }
    }

    private suspend fun resolveSeasonCounts(rows: List<HomeRow>) {
        val seriesIds = seriesNeedingSeasonCount(rows, _state.value.seasonCounts.keys)
        if (seriesIds.isEmpty()) return
        val counts = coroutineScope {
            seriesIds.map { id ->
                async {
                    id to runCatching { seasonCountGate.withPermit { mediaRepository.seasonCount(id) } }
                        .getOrDefault(0)
                }
            }.awaitAll()
        }.filter { it.second > 0 }.toMap()
        if (counts.isNotEmpty()) {
            _state.update { it.copy(seasonCounts = it.seasonCounts + counts) }
        }
    }

    fun consumeSessionExpired() {
        _state.update { it.copy(sessionExpiredServerId = null) }
    }

    fun consumeServerUnreachable() {
        _state.update { it.copy(serverUnreachable = null) }
    }

    fun softLogout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun load() {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            _state.update { it.copy(session = session) }
            requestDiscovery()
            if (!channelSyncRequested) {
                channelSyncRequested = true
                TvChannelReceiver.enqueueImmediateSync(appContext)
            }
            val done = homeLoader.prefetch(session)
                .combine(navRail.layoutEpoch) { home, _ -> home }
                .onEach(::applyProgress)
                .mapNotNull { it.outcome }
                .first()
            applyOrError(done, session)
        }
    }

    private fun applyProgress(load: HomeLoad) {
        val slots = load.slots ?: return
        val visible = HomeContent.visibleSlots(slots, navRail.pinnedLibraryIds())
        val revealed = HomeContent.reveal(visible, load.resolved)
        _state.update { current ->
            if (!current.loading) return@update current
            current.withRows(revealed.rows).copy(pendingSlots = revealed.pending)
        }
    }

    private suspend fun applyOrError(result: Result<HomeResult>, session: UserSession) {
        result
            .onSuccess { applyFresh(it) }
            .onFailure { e ->
                if (e is CancellationException) throw e
                if (e.isAuthFailure()) {
                    authRepository.expireStoredSession(session.server.id, session.userId)
                    _state.update {
                        it.copy(
                            loading = true,
                            session = null,
                            sessionExpiredServerId = session.server.id
                        )
                    }
                } else if (_state.value.rows.isEmpty()) {
                    _state.update {
                        it.copy(
                            loading = true,
                            serverUnreachable = session.server.id to e.serverErrorMessage()
                        )
                    }
                }
            }
    }

    private suspend fun rowsFor(result: HomeResult): List<HomeRow> = HomeContent.buildHomeRows(
        result.resume,
        result.nextUp,
        result.latestByLibrary,
        navRail.pinnedLibraryIds(),
        hiddenResumeStore.snapshot(),
        result.queuedDates
    )

    private suspend fun applyFresh(result: HomeResult) {
        val rows = rowsFor(result)
        lastResult = result
        _state.update { current ->
            current.copy(
                loading = false,
                pendingSlots = emptyList(),
                session = result.session
            ).withRows(rows)
        }
        requestDiscovery()
        resolveSeasonCounts(rows)
        focusChangedFlow.tryEmit(Unit)
    }

    private fun UiState.withRows(freshMainRows: List<HomeRow>): UiState {
        mainRows = freshMainRows
        val combined = mainRows + if (loading) emptyList() else discoveryFeed.rows
        val focus = BrowseRowFocus(focusedRowIndex, focusedItemId, rowFocusedItemIds).retarget(rows, combined)
        return copy(
            rows = combined,
            pendingDiscoveryRows = if (loading) emptyList() else discoveryFeed.pending,
            focusedItemId = focus.itemId,
            focusedRowIndex = focus.rowIndex,
            rowFocusedItemIds = focus.rowItemIds,
            error = if (combined.isEmpty() && !loading) "Nothing to watch yet." else null
        )
    }

    private suspend fun rebuildRowsFromCache() {
        val rows = rowsFor(lastResult ?: return)
        _state.update { it.withRows(rows) }
        requestDiscovery()
    }

    private fun requestDiscovery() {
        discoveryFeed.request(_state.value.session ?: return)
    }

    private suspend fun prefetchStreamsAhead() {
        val stateSnapshot = _state.value
        if (stateSnapshot.session == null) return
        val plan = planHeroStreamPrefetch(
            rows = stateSnapshot.rows,
            focusedRowIndex = stateSnapshot.focusedRowIndex,
            rowFocusedItemIds = stateSnapshot.rowFocusedItemIds,
            alreadyFetched = stateSnapshot.heroStreams.keys,
            knownSeries = stateSnapshot.seriesItems.keys
        )
        if (plan.isEmpty) return

        val data = mediaRepository.fetchHeroPrefetch(plan, stateSnapshot.seriesItems.keys, SEASON_COUNT_CONCURRENCY)
        if (data.isEmpty) return

        _state.update {
            it.copy(
                heroStreams = it.heroStreams + data.streams,
                seriesItems = it.seriesItems + data.seriesItems,
                seasonLeads = it.seasonLeads + data.seasonLeads
            )
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val SEASON_COUNT_CONCURRENCY = 6
    }
}
