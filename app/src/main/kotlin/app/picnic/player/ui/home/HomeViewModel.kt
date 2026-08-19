package app.picnic.player.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.jellyfin.serverErrorMessage
import app.picnic.player.data.media.HomeContent
import app.picnic.player.data.media.HomeContentLoader
import app.picnic.player.data.media.HomeResult
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.planHeroStreamPrefetch
import app.picnic.player.data.media.seriesNeedingSeasonCount
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
    settingsStore: SettingsStore,
    val ambientLoader: AmbientPaletteLoader,
    private val navRail: NavRailState,
    private val seerrRepository: SeerrRepository,
    private val navLayoutStore: NavLayoutStore
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val rows: List<HomeRow> = emptyList(),
        val session: UserSession? = null,
        val error: String? = null,
        val sessionExpiredServerId: String? = null,
        /** One-shot: (serverId, errorText) when a cold load failed to reach the server — the host
         *  routes to Select Server. Distinct from [sessionExpiredServerId] (token rejected). */
        val serverUnreachable: Pair<String, String>? = null,
        val focusedRowIndex: Int = 0,
        val focusedItemId: UUID? = null,
        /** Per-row last-focused card — survives vertical moves and tab switches. */
        val rowFocusedItemIds: Map<Int, UUID> = emptyMap(),
        val seasonCounts: Map<UUID, Int> = emptyMap(),
        /** Video libraries surfaced as drawer destinations (movies / shows / mixed). */
        val libraries: List<BrowseDest.Library> = emptyList(),
        /** Whether the server exposes a playlists view (drives the Playlists drawer destination). */
        val playlistsAvailable: Boolean = false,
        val heroStreams: Map<UUID, List<MediaStream>> = emptyMap()
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

    /** One immediate channel publish per Home session; periodic work covers later refreshes. */
    private var channelSyncRequested = false

    /** Last network fetch of per-library latest items — reused when only pin/order changes. */
    private var latestByLibrary: List<Pair<BaseItemDto, List<BaseItemDto>>> = emptyList()
    private var lastResume: List<BaseItemDto> = emptyList()
    private var lastNextUp: List<BaseItemDto> = emptyList()

    init {
        load()
        // Any library change (watched/favorite/progress here or in another screen, plus broad
        // content hints) re-runs load(). Debounced so a burst collapses into one refresh, and
        // load() keeps the current rows on screen until fresh data arrives (no flash to empty).
        viewModelScope.launch {
            changeBus.events.debounce(400).collectLatest { refresh() }
        }
        viewModelScope.launch {
            focusChangedFlow.debounce(200).collectLatest { prefetchStreamsAhead() }
        }
        // Pin/reorder/unpin: rebuild home rows from the cached latest fetch.
        viewModelScope.launch {
            navRail.layoutEpoch.drop(1).collectLatest { rebuildRowsFromCache() }
        }
        // Seerr link/unlink mid-session: add/remove Discover without a full home reload.
        viewModelScope.launch {
            seerrRepository.state
                .map { it.linkState == SeerrLinkState.Linked }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { linked -> republishNavLayout(linked) }
        }
    }

    private suspend fun republishNavLayout(discoverAvailable: Boolean) {
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

    /**
     * Re-fetch home rows without tearing down state (covers app-foreground returns and library
     * changes). Bypasses the prefetch memo — the rows are already on screen, so this always hits
     * the network to re-check staleness, and keeps the current rows until fresh data lands.
     */
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
                ?.let { return it }
        }
        return rows.firstOrNull()?.items?.firstOrNull()
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

    /**
     * Resolves the "N seasons" hero label for every series shown on home, off the critical
     * path. Each lookup is a count-only query (no season DTOs), run with bounded concurrency,
     * and only series not already counted are fetched — so a refresh re-uses prior counts.
     */
    private suspend fun resolveSeasonCounts(session: UserSession, rows: List<HomeRow>) {
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
            // Publish / refresh Android TV home channels once we have a session.
            // Periodic work alone can delay the first insert for a long time.
            if (!channelSyncRequested) {
                channelSyncRequested = true
                TvChannelReceiver.enqueueImmediateSync(appContext)
            }
            // The startup splash already fired this fetch, so it's usually done by the time Home
            // composes — awaiting it then lands fresh rows immediately. We await the whole fetch
            // (no time gate): the spinner shows until fresh data is ready, so the pane never opens
            // on stale content. The disk cache is a fallback for a FAILED fetch only (see below).
            val deferred = homeLoader.prefetch(session)
            applyOrError(runCatching { deferred.await() }, session)
        }
    }

    /** Applies a fetched result, or routes its failure through the session/error handling. */
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
                    // Cold load with nothing on screen and the server errored/unreachable: route to
                    // Select Server with the error flagged on its tile. Never a stale disk paint. A
                    // failed REFRESH (rows already on screen) keeps those live rows and routes nowhere.
                    _state.update {
                        it.copy(
                            loading = true,
                            serverUnreachable = session.server.id to e.serverErrorMessage()
                        )
                    }
                }
            }
    }

    /** Swaps fresh rows into state, then resolves season counts off the critical path. */
    private suspend fun applyFresh(result: HomeResult) {
        lastResume = result.resume
        lastNextUp = result.nextUp
        latestByLibrary = result.latestByLibrary
        val firstItem = result.rows.firstOrNull()?.items?.firstOrNull()
        _state.update { current ->
            val rowIds = current.rowFocusedItemIds.ifEmpty {
                firstItem?.let { mapOf(0 to it.id) } ?: emptyMap()
            }
            current.copy(
                loading = false,
                rows = result.rows,
                session = result.session,
                libraries = result.libraries,
                playlistsAvailable = result.playlistsAvailable,
                error = if (result.rows.isEmpty()) "Nothing to watch yet." else null,
                focusedItemId = current.focusedItemId ?: firstItem?.id,
                focusedRowIndex = if (current.focusedItemId == null) 0 else current.focusedRowIndex,
                rowFocusedItemIds = rowIds
            )
        }
        // Season counts feed only the hero's "N seasons" line for the focused series; resolve
        // them off the critical path (count-only per-series queries) so rows paint immediately,
        // then patch labels in as they land.
        resolveSeasonCounts(result.session, result.rows)
        focusChangedFlow.tryEmit(Unit)
    }

    /** Re-apply pin order to the last fetched home payload (no network). */
    private fun rebuildRowsFromCache() {
        if (latestByLibrary.isEmpty() && lastResume.isEmpty() && lastNextUp.isEmpty()) return
        val pinnedIds = navRail.pinnedLibraries().map { it.id }
        val rows = HomeContent.buildHomeRows(lastResume, lastNextUp, latestByLibrary, pinnedIds)
        _state.update { current ->
            current.copy(
                rows = rows,
                error = if (rows.isEmpty()) "Nothing to watch yet." else null
            )
        }
    }

    private suspend fun prefetchStreamsAhead() {
        val stateSnapshot = _state.value
        val session = stateSnapshot.session ?: return
        val plan = planHeroStreamPrefetch(
            rows = stateSnapshot.rows,
            focusedRowIndex = stateSnapshot.focusedRowIndex,
            rowFocusedItemIds = stateSnapshot.rowFocusedItemIds,
            alreadyFetched = stateSnapshot.heroStreams.keys
        )
        if (plan.isEmpty) return

        val newStreams = mutableMapOf<UUID, List<MediaStream>>()

        if (plan.streamIds.isNotEmpty()) {
            val fetched = mediaRepository.itemStreams(plan.streamIds)
            newStreams.putAll(fetched)
        }

        if (plan.seriesIds.isNotEmpty()) {
            val gate = Semaphore(SEASON_COUNT_CONCURRENCY)
            coroutineScope {
                plan.seriesIds.map { seriesId ->
                    async {
                        val streams = runCatching { gate.withPermit { mediaRepository.seriesLeadStreams(seriesId) } }.getOrDefault(emptyList())
                        if (streams.isNotEmpty()) {
                            newStreams[seriesId] = streams
                        }
                    }
                }.awaitAll()
            }
        }

        if (newStreams.isNotEmpty()) {
            _state.update { it.copy(heroStreams = it.heroStreams + newStreams) }
        }
    }

    private companion object {
        /** Parallel in-flight season-count lookups — bounded so a big home doesn't flood OkHttp. */
        const val SEASON_COUNT_CONCURRENCY = 6
    }
}
