package app.picnic.player.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.jellyfin.serverErrorMessage
import app.picnic.player.data.media.HiddenResumeStore
import app.picnic.player.data.media.HomeContent
import app.picnic.player.data.media.HomeContentLoader
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
import app.picnic.player.data.nav.NAV_ID_DISCOVER
import app.picnic.player.data.nav.NAV_ID_PLAYLISTS
import app.picnic.player.data.nav.NavLayoutStore
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.tvprovider.TvChannelReceiver
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.browse.BrowseDest
import app.picnic.player.ui.browse.NavRailState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val homeLoader: HomeContentLoader,
    private val changeBus: LibraryChangeBus,
    private val hiddenResumeStore: HiddenResumeStore,
    settingsStore: SettingsStore,
    val ambientLoader: AmbientPaletteLoader,
    private val navRail: NavRailState,
    private val seerrRepository: SeerrRepository,
    private val navLayoutStore: NavLayoutStore
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val rows: List<HomeRow> = emptyList(),
        val pendingSlots: List<HomeSlot>? = null,
        val session: UserSession? = null,
        val error: String? = null,
        val sessionExpiredServerId: String? = null,
        val serverUnreachable: Pair<String, String>? = null,
        val focusedRowIndex: Int = 0,
        val focusedItemId: UUID? = null,
        val rowFocusedItemIds: Map<Int, UUID> = emptyMap(),
        val seasonCounts: Map<UUID, Int> = emptyMap(),
        val libraries: List<BrowseDest.Library> = emptyList(),
        val playlistsAvailable: Boolean = false,
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

    val alternateNavigation = settingsStore.settings
        .map { it.alternateNavigation }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val focusChangedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    private var channelSyncRequested = false

    private var lastResult: HomeResult? = null

    init {
        load()
        viewModelScope.launch {
            changeBus.batches().collect { refresh() }
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
        viewModelScope.launch {
            seerrRepository.state
                .map { it.linkState == SeerrLinkState.Linked }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { linked -> republishNavLayout(linked) }
        }
    }

    private suspend fun republishNavLayout(discoverAvailable: Boolean) {
        if (_state.value.pendingSlots == null) return
        val session = _state.value.session ?: return
        val libraries = _state.value.libraries
        val playlistsAvailable = _state.value.playlistsAvailable
        val availableIds = buildList {
            addAll(libraries.map { it.key })
            if (playlistsAvailable) add(NAV_ID_PLAYLISTS)
            if (discoverAvailable) add(NAV_ID_DISCOVER)
        }
        val layout = navLayoutStore.resolve(session.server.id, session.userId, availableIds)
        navRail.publish(session, libraries, discoverAvailable, playlistsAvailable, layout)
    }

    fun refresh() {
        viewModelScope.launch {
            val session = _state.value.session ?: authRepository.activeSession() ?: return@launch
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
    }

    private suspend fun resolveSeasonCounts(rows: List<HomeRow>) {
        val seriesIds = seriesNeedingSeasonCount(rows, _state.value.seasonCounts.keys)
        if (seriesIds.isEmpty()) return
        val gate = Semaphore(SEASON_COUNT_CONCURRENCY)
        val counts = coroutineScope {
            seriesIds.map { id ->
                async {
                    id to runCatching { gate.withPermit { mediaRepository.seasonCount(id) } }
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
            current.withRows(revealed.rows).copy(
                pendingSlots = revealed.pending,
                libraries = load.libraries,
                playlistsAvailable = load.playlistsAvailable
            )
        }
    }

    private suspend fun applyOrError(result: Result<HomeResult>, session: UserSession) {
        result
            .onSuccess { applyFresh(it) }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
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
            current.withRows(rows).copy(
                loading = false,
                pendingSlots = emptyList(),
                session = result.session,
                libraries = result.libraries,
                playlistsAvailable = result.playlistsAvailable,
                error = if (rows.isEmpty()) "Nothing to watch yet." else null
            )
        }
        resolveSeasonCounts(rows)
        focusChangedFlow.tryEmit(Unit)
    }

    private fun UiState.withRows(rows: List<HomeRow>): UiState {
        val firstItem = rows.firstOrNull()?.items?.firstOrNull()
        return copy(
            rows = rows,
            focusedItemId = focusedItemId ?: firstItem?.id,
            focusedRowIndex = if (focusedItemId == null) 0 else focusedRowIndex,
            rowFocusedItemIds = rowFocusedItemIds.ifEmpty {
                firstItem?.let { mapOf(0 to it.id) } ?: emptyMap()
            }
        )
    }

    private suspend fun rebuildRowsFromCache() {
        val rows = rowsFor(lastResult ?: return)
        val visibleIds = rows.flatMap { row -> row.items.map { it.id } }.toSet()
        _state.update { current ->
            current.copy(
                rows = rows,
                focusedRowIndex = current.focusedRowIndex.coerceIn(0, maxOf(rows.lastIndex, 0)),
                rowFocusedItemIds = current.rowFocusedItemIds.filterValues { it in visibleIds },
                error = if (rows.isEmpty()) "Nothing to watch yet." else null
            )
        }
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
        const val SEASON_COUNT_CONCURRENCY = 6
    }
}
