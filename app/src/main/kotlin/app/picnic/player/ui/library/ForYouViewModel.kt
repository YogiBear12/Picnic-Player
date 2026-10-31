package app.picnic.player.ui.library

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.media.DISCOVERY_FIELDS
import app.picnic.player.data.media.DISCOVERY_PAGE_SIZE
import app.picnic.player.data.media.DISCOVERY_ROW_CAP
import app.picnic.player.data.media.DiscoveryScope
import app.picnic.player.data.media.HiddenResumeStore
import app.picnic.player.data.media.HomeContent
import app.picnic.player.data.media.HomeDiscovery
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.batches
import app.picnic.player.data.media.fetchHeroPrefetch
import app.picnic.player.data.media.planHeroStreamPrefetch
import app.picnic.player.data.media.seriesNeedingSeasonCount
import app.picnic.player.data.media.withSeriesFallback
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.browse.BrowseDest
import app.picnic.player.ui.common.BrowseRowFocus
import app.picnic.player.ui.common.DiscoveryFeed
import app.picnic.player.ui.common.PendingDiscoveryRow
import app.picnic.player.ui.common.RowCursor
import app.picnic.player.ui.common.RowPager
import app.picnic.player.ui.common.loadOrderedPage
import app.picnic.player.ui.common.retarget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream

@HiltViewModel
class ForYouViewModel @Inject internal constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val homeDiscovery: HomeDiscovery,
    private val hiddenResumeStore: HiddenResumeStore,
    private val changeBus: LibraryChangeBus,
    val ambientLoader: AmbientPaletteLoader
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val session: UserSession? = null,
        val rows: List<HomeRow> = emptyList(),
        val pendingDiscoveryRows: List<PendingDiscoveryRow> = emptyList(),
        val error: String? = null,
        val sessionExpiredServerId: String? = null,
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

    private var library: BrowseDest.Library? = null
    private var loadJob: Job? = null
    private var fixedRows: List<HomeRow> = emptyList()
    private var feed: DiscoveryFeed? = null
    private val fixedPager = RowPager(viewModelScope, ::appendFixedPage)
    private val fixedSettled = MutableStateFlow(false)

    private val focusChangedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        viewModelScope.launch {
            focusChangedFlow.debounce(200).collectLatest { prefetchStreamsAhead() }
        }
        viewModelScope.launch {
            changeBus.batches().collect { batch ->
                val feed = feed ?: return@collect
                feed.markChanged(batch.itemIds)
                refresh(batch.itemIds)
                feed.patch(batch.itemIds)
            }
        }
    }

    fun bind(library: BrowseDest.Library) {
        if (feed == null) {
            this.library = library
            feed = newFeed(DiscoveryScope(library.id, library.kinds))
        }
        load()
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        load()
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
        _state.value.session?.let { feed?.request(it) }
        _state.value.rows.getOrNull(rowIndex)?.let { row ->
            feed?.onFocused(row, item)
            fixedPager.onFocused(row.key, row.items.indexOfFirst { it.id == item.id }, row.items.size)
        }
    }

    fun consumeSessionExpired() {
        _state.update { it.copy(sessionExpiredServerId = null) }
    }

    private fun newFeed(target: DiscoveryScope): DiscoveryFeed = DiscoveryFeed(
        scope = viewModelScope,
        discovery = homeDiscovery,
        mediaRepository = mediaRepository,
        target = target,
        focusedInFeed = { _state.value.focusedRowIndex >= fixedRows.size && feed?.rows.orEmpty().isNotEmpty() },
        awaitHostRows = { fixedSettled.first { it } },
        onChanged = { _state.update { it.withRows(fixedRows) } },
        onRowsPublished = { added ->
            focusChangedFlow.tryEmit(Unit)
            viewModelScope.launch { resolveSeasonCounts(added) }
        },
        onPageAppended = { appended -> viewModelScope.launch { resolveSeasonCounts(appended) } }
    )

    private fun load() {
        val library = library ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { withSession { session -> publishFixedRows(library, session) } }
    }

    // A change refresh patches Recently released in place, so its pages past the first stay loaded.
    private fun refresh(changedIds: Set<String>) {
        val library = library ?: return
        viewModelScope.launch {
            withSession {
                coroutineScope {
                    val shown = releasedRows().flatMap { it.items }
                    val fresh = async { softly(emptyMap()) { mediaRepository.refreshChanged(shown, changedIds, DISCOVERY_FIELDS) } }
                    val top = topRows(library)
                    val byId = fresh.await()
                    val released = releasedRows().map { row -> row.copy(items = row.items.map { byId[it.id] ?: it }) }
                    _state.update { it.withRows(top + released) }
                }
            }
        }
    }

    private suspend fun withSession(block: suspend (UserSession) -> Unit) {
        val session = authRepository.activeSession()
        if (session == null) {
            _state.update { it.copy(loading = false, error = "No active session") }
            return
        }
        try {
            block(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isAuthFailure()) {
                authRepository.expireStoredSession(session.server.id, session.userId)
                _state.update {
                    it.copy(loading = true, session = null, sessionExpiredServerId = session.server.id)
                }
            } else if (_state.value.rows.isEmpty()) {
                _state.update { it.copy(loading = false, error = "Could not load suggestions") }
            }
        }
    }

    // Rows reveal top-down: the slow Recently released row lands before any discovery row below it.
    private suspend fun publishFixedRows(library: BrowseDest.Library, session: UserSession) = coroutineScope {
        val started = SystemClock.elapsedRealtime()
        val released = async { releasedFirstPage(library) }
        val top = topRows(library)
        _state.update { it.copy(loading = false, session = session).withRows(top + releasedRows()) }
        feed?.resume()
        feed?.request(session)
        focusChangedFlow.tryEmit(Unit)
        launch { resolveSeasonCounts(top) }
        val (ids, firstPage) = released.await()
        fixedPager.clear()
        fixedPager.add(RELEASED_KEY, RowCursor(DISCOVERY_PAGE_SIZE, next = DISCOVERY_PAGE_SIZE, ids = ids)) { cursor ->
            loadOrderedPage(cursor, { ids }) { mediaRepository.itemsOrThrow(it, DISCOVERY_FIELDS) }
        }
        val releasedRow = listOf(HomeRow("Recently released", firstPage, continueWatching = false, key = RELEASED_KEY))
            .filter { it.items.isNotEmpty() }
        _state.update { it.withRows(fixedRows.filter { row -> row.key != RELEASED_KEY } + releasedRow) }
        fixedSettled.value = true
        Log.d(TAG, "fixed rows ${SystemClock.elapsedRealtime() - started}ms")
        resolveSeasonCounts(releasedRow)
    }

    private suspend fun topRows(library: BrowseDest.Library): List<HomeRow> = coroutineScope {
        val resume = async { softly(emptyList()) { mediaRepository.resumeItems(parentId = library.id) } }
        val nextUp = async {
            if (BaseItemKind.SERIES in library.kinds) softly(emptyList()) { mediaRepository.nextUp(parentId = library.id) } else emptyList()
        }
        val latest = async { softly(emptyList()) { mediaRepository.latestInLibrary(library.id) } }
        val continueWatching = HomeContent.continueWatchingItems(
            resume.await(),
            nextUp.await(),
            hiddenResumeStore.snapshot(),
            mediaRepository.queuedEpisodeDates(nextUp.await())
        )
        listOf(
            HomeRow("Continue watching", continueWatching, continueWatching = true, key = CONTINUE_KEY),
            HomeRow("Recently added", latest.await(), continueWatching = false, key = ADDED_KEY)
        ).filter { it.items.isNotEmpty() }
    }

    private suspend fun releasedFirstPage(library: BrowseDest.Library): Pair<List<UUID>, List<BaseItemDto>> {
        val ids = softly(emptyList()) { mediaRepository.recentlyReleasedIds(library.id, library.kinds, DISCOVERY_ROW_CAP) }
        val pageIds = ids.take(DISCOVERY_PAGE_SIZE)
        val byId = softly(emptyList()) { mediaRepository.itemsOrThrow(pageIds, DISCOVERY_FIELDS) }.associateBy { it.id }
        return ids to pageIds.mapNotNull(byId::get)
    }

    private fun releasedRows(): List<HomeRow> = fixedRows.filter { it.key == RELEASED_KEY }

    // One failed row must not blank the tab, but a rejected token must reach the session handler.
    private suspend fun <T> softly(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.isAuthFailure()) throw e
        fallback
    }

    private fun appendFixedPage(key: String, page: List<BaseItemDto>) {
        if (page.isEmpty()) return
        fixedRows = fixedRows.map { row -> if (row.key == key) row.copy(items = row.items + page) else row }
        _state.update { it.withRows(fixedRows) }
        viewModelScope.launch { resolveSeasonCounts(fixedRows.filter { it.key == key }) }
    }

    private fun UiState.withRows(freshFixedRows: List<HomeRow>): UiState {
        fixedRows = freshFixedRows
        val feed = feed
        val combined = fixedRows + if (loading) emptyList() else feed?.rows.orEmpty()
        val focus = BrowseRowFocus(focusedRowIndex, focusedItemId, rowFocusedItemIds).retarget(rows, combined)
        return copy(
            rows = combined,
            pendingDiscoveryRows = if (loading) emptyList() else feed?.pending.orEmpty(),
            focusedItemId = focus.itemId,
            focusedRowIndex = focus.rowIndex,
            rowFocusedItemIds = focus.rowItemIds
        )
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

        val data = mediaRepository.fetchHeroPrefetch(plan, stateSnapshot.seriesItems.keys, STREAM_PREFETCH_CONCURRENCY)
        if (data.isEmpty) return

        _state.update {
            it.copy(
                heroStreams = it.heroStreams + data.streams,
                seriesItems = it.seriesItems + data.seriesItems,
                seasonLeads = it.seasonLeads + data.seasonLeads
            )
        }
    }

    private suspend fun resolveSeasonCounts(rows: List<HomeRow>) {
        val seriesIds = seriesNeedingSeasonCount(rows, _state.value.seasonCounts.keys)
        if (seriesIds.isEmpty()) return
        val gate = Semaphore(STREAM_PREFETCH_CONCURRENCY)
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

    private companion object {
        const val TAG = "ForYouViewModel"
        const val STREAM_PREFETCH_CONCURRENCY = 4
        const val CONTINUE_KEY = "continue-watching"
        const val ADDED_KEY = "recently-added"
        const val RELEASED_KEY = "recently-released"
    }
}
