package app.picnic.player.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserScope
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.jellyfin.serverErrorMessage
import app.picnic.player.data.media.DISCOVERY_FIELDS
import app.picnic.player.data.media.DISCOVERY_PAGE_SIZE
import app.picnic.player.data.media.DiscoveryCandidate
import app.picnic.player.data.media.DiscoveryPlan
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
import app.picnic.player.data.media.nextDiscoveryBatch
import app.picnic.player.data.media.pendingDiscoveryCandidates
import app.picnic.player.data.media.planHeroStreamPrefetch
import app.picnic.player.data.media.retryDiscovery
import app.picnic.player.data.media.seriesNeedingSeasonCount
import app.picnic.player.data.media.shouldRenewDiscovery
import app.picnic.player.data.media.shouldShow
import app.picnic.player.data.media.withSeriesFallback
import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.data.tvprovider.TvChannelReceiver
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.browse.NavRailState
import app.picnic.player.ui.detail.RowCursor
import app.picnic.player.ui.detail.loadOrderedPage
import app.picnic.player.ui.detail.needsNextPage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.job
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
    data class PendingDiscoveryRow(val key: String, val title: String)

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
    private var discoveryJob: Job? = null
    private var discoveryPaused = false
    private var discoveryPlan: DiscoveryPlan? = null
    private var nextDiscoveryPlan: DiscoveryPlan? = null
    private var discoveryRows: List<HomeRow> = emptyList()
    private val discoveryShown = mutableListOf<DiscoveryCandidate>()
    private val unpublishedDiscoveryChanges = mutableSetOf<String>()
    private var discoveryPublishIndex = 0
    private val discoveryCursors = mutableMapOf<String, RowCursor>()
    private val discoveryPageJobs = mutableMapOf<String, Job>()
    private val stoppedDiscoveryRows = mutableSetOf<String>()

    init {
        load()
        viewModelScope.launch {
            changeBus.batches().collect { batch ->
                if (discoveryJob?.isActive == true) unpublishedDiscoveryChanges += batch.itemIds
                refresh()
                patchDiscoveryCards(batch.itemIds)
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
            discoveryPaused = false
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
        _state.value.rows.getOrNull(rowIndex)?.let { row -> loadMoreDiscovery(row, row.items.indexOfFirst { it.id == item.id }) }
    }

    private fun loadMoreDiscovery(row: HomeRow, focusedIndex: Int) {
        val cursor = discoveryCursors[row.key] ?: return
        val ids = cursor.ids ?: return
        val endReached = cursor.next >= ids.size || row.key in stoppedDiscoveryRows
        if (!needsNextPage(focusedIndex, row.items.size, endReached, row.key in discoveryPageJobs)) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = loadOrderedPage(cursor, { ids }, { mediaRepository.itemsOrThrow(it, DISCOVERY_FIELDS) })
                appendDiscoveryPage(row.key, page.items)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                stoppedDiscoveryRows += row.key
                Log.w(TAG, "Discovery page failed for ${row.key}", failure)
            } finally {
                discoveryPageJobs.remove(row.key, coroutineContext.job)
            }
        }
        discoveryPageJobs[row.key] = job
        job.start()
    }

    private fun appendDiscoveryPage(key: String, page: List<BaseItemDto>) {
        _state.update { current ->
            discoveryRows = discoveryRows.map { row -> if (row.key == key) row.copy(items = row.items + page) else row }
            current.withRows(mainRows)
        }
        val appended = discoveryRows.filter { it.key == key }
        viewModelScope.launch { resolveSeasonCounts(appended) }
    }

    private fun resetDiscoveryPaging() {
        discoveryPageJobs.values.forEach { it.cancel() }
        discoveryPageJobs.clear()
        discoveryCursors.clear()
        stoppedDiscoveryRows.clear()
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
        val combined = mainRows + if (loading) emptyList() else discoveryRows
        val indices = combined.withIndex().associate { it.value.key to it.index }
        val focusedIndex = rows.getOrNull(focusedRowIndex)?.key?.let(indices::get)
            ?: focusedRowIndex.coerceIn(0, maxOf(combined.lastIndex, 0))
        val focusedItems = combined.getOrNull(focusedIndex)?.items.orEmpty()
        val focusedRowKept = rows.getOrNull(focusedRowIndex)?.key in indices
        // A card removed from a surviving row keeps its id so the scaffold recovers to the neighboring slot.
        val focusedId = focusedItemId?.takeIf { id -> focusedRowKept || focusedItems.any { it.id == id } }
            ?: focusedItems.firstOrNull()?.id
        val remembered = rowFocusedItemIds.mapNotNull { (index, id) ->
            val newIndex = rows.getOrNull(index)?.key?.let(indices::get) ?: return@mapNotNull null
            (newIndex to id).takeIf { combined[newIndex].items.any { it.id == id } }
        }.toMap()
        return copy(
            rows = combined,
            pendingDiscoveryRows = if (loading || discoveryPaused) {
                emptyList()
            } else {
                pendingDiscoveryCandidates(discoveryPlan?.candidates.orEmpty().drop(discoveryPublishIndex), discoveryShown)
                    .map { PendingDiscoveryRow(it.rowKey, it.title) }
            },
            focusedItemId = focusedId,
            focusedRowIndex = focusedIndex,
            rowFocusedItemIds = remembered.ifEmpty {
                focusedId?.takeIf { id -> focusedItems.any { it.id == id } }?.let { mapOf(focusedIndex to it) } ?: emptyMap()
            },
            error = if (combined.isEmpty() && !loading) "Nothing to watch yet." else null
        )
    }

    private suspend fun rebuildRowsFromCache() {
        val rows = rowsFor(lastResult ?: return)
        _state.update { it.withRows(rows) }
        requestDiscovery()
    }

    private fun requestDiscovery() {
        val session = _state.value.session ?: return
        if (discoveryJob?.isActive == true || discoveryPaused) return
        discoveryJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val today = LocalDate.now()
                val current = discoveryPlan
                val focusedDiscovery = _state.value.focusedRowIndex >= mainRows.size && discoveryRows.isNotEmpty()
                if (current == null || shouldRenewDiscovery(current, today, focusedDiscovery)) {
                    val fresh = nextDiscoveryPlan?.takeIf { it.date == today.toString() } ?: retryDiscovery { homeDiscovery.plan(session.scope, session.userUuid, today) }
                    if (current != null && _state.value.focusedRowIndex >= mainRows.size && discoveryRows.isNotEmpty()) {
                        nextDiscoveryPlan = fresh
                        return@launch
                    }
                    nextDiscoveryPlan = null
                    discoveryPlan = fresh
                    discoveryRows = emptyList()
                    resetDiscoveryPaging()
                    discoveryShown.clear()
                    discoveryPublishIndex = 0
                    _state.update { it.withRows(mainRows) }
                }
                if (nextDiscoveryPlan == null) fillDiscoveryRows(session.scope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                discoveryPaused = true
                _state.update { it.withRows(mainRows) }
                Log.w(TAG, "Discovery load failed; retry on resume", failure)
            }
        }.also { it.start() }
    }

    private suspend fun fillDiscoveryRows(scope: UserScope) = try {
        publishDiscoveryRows(scope)
    } finally {
        unpublishedDiscoveryChanges.clear()
    }

    private suspend fun publishDiscoveryRows(scope: UserScope) = coroutineScope {
        val initialPlan = discoveryPlan ?: return@coroutineScope
        val remaining = initialPlan.candidates.drop(discoveryPublishIndex)
        if (nextDiscoveryBatch(remaining, discoveryShown, DISCOVERY_BATCH_SIZE).isEmpty()) return@coroutineScope
        val loader = homeDiscovery.loader(this, initialPlan, remaining) { _state.first { !it.loading } }
        while (true) {
            val plan = discoveryPlan ?: return@coroutineScope
            val batch = nextDiscoveryBatch(plan.candidates.drop(discoveryPublishIndex), discoveryShown, DISCOVERY_BATCH_SIZE)
            if (batch.isEmpty()) return@coroutineScope
            val eligible = batch.filter { shouldShow(it, discoveryShown) }
            val rows = if (eligible.isEmpty()) {
                emptyList()
            } else {
                val (latest, filled) = homeDiscovery.fill(scope, plan, eligible, discoveryRows.map { it.items }, loader)
                discoveryPlan = latest
                filled
            }
            discoveryPublishIndex += batch.size
            val savedIds = discoveryPlan?.candidates.orEmpty().associate { it.key to it.itemIds.orEmpty() }
            val added = eligible.zip(rows).mapNotNull { (candidate, items) ->
                if (items.isEmpty()) return@mapNotNull null
                discoveryShown += candidate
                discoveryCursors[candidate.rowKey] = RowCursor(DISCOVERY_PAGE_SIZE, savedIds[candidate.key].orEmpty().map(UUID::fromString), DISCOVERY_PAGE_SIZE)
                HomeRow(candidate.title, items, continueWatching = false, key = candidate.rowKey)
            }
            discoveryRows = discoveryRows + added
            _state.update { it.withRows(mainRows) }
            if (added.isNotEmpty()) {
                focusChangedFlow.tryEmit(Unit)
                viewModelScope.launch { resolveSeasonCounts(added) }
                val stale = added.flatMap { row -> row.items.map { it.id.toString() } }.filter { it in unpublishedDiscoveryChanges }.toSet()
                if (stale.isNotEmpty()) viewModelScope.launch { patchDiscoveryCards(stale) }
            }
        }
    }

    private suspend fun patchDiscoveryCards(changedIds: Set<String>) {
        try {
            val fresh = mediaRepository.refreshChanged(discoveryRows.flatMap { it.items }, changedIds, DISCOVERY_FIELDS)
            if (fresh.isEmpty()) return
            _state.update { current ->
                discoveryRows = discoveryRows.map { row -> row.copy(items = row.items.map { fresh[it.id] ?: it }) }
                current.withRows(mainRows)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Discovery card refresh failed", failure)
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
        const val TAG = "HomeViewModel"
        const val DISCOVERY_BATCH_SIZE = 4
        const val SEASON_COUNT_CONCURRENCY = 6
    }
}

private val DiscoveryCandidate.rowKey: String get() = "discovery-$key"
