package app.picnic.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.batches
import app.picnic.player.data.media.chooseWatchSeeds
import app.picnic.player.data.media.fetchHeroPrefetch
import app.picnic.player.data.media.planHeroStreamPrefetch
import app.picnic.player.data.media.seedId
import app.picnic.player.data.media.seedName
import app.picnic.player.data.media.seriesNeedingSeasonCount
import app.picnic.player.data.media.withSeriesFallback
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val changeBus: LibraryChangeBus,
    val ambientLoader: AmbientPaletteLoader
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val session: UserSession? = null,
        val rows: List<HomeRow> = emptyList(),
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

    private var bound = false
    private var libraryId: UUID? = null
    private var kinds: List<BaseItemKind> = emptyList()

    private val focusChangedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        viewModelScope.launch {
            focusChangedFlow.debounce(200).collectLatest { prefetchStreamsAhead() }
        }
        viewModelScope.launch {
            changeBus.batches().collect { batch -> patchCards(batch.itemIds) }
        }
    }

    private suspend fun patchCards(changedIds: Set<String>) {
        val fresh = mediaRepository.refreshChanged(_state.value.rows.flatMap { it.items }, changedIds)
        if (fresh.isEmpty()) return
        _state.update { state ->
            state.copy(rows = state.rows.map { row -> row.copy(items = row.items.map { fresh[it.id] ?: it }) })
        }
    }

    fun bind(libraryId: UUID, kinds: List<BaseItemKind>) {
        if (bound) return
        bound = true
        this.libraryId = libraryId
        this.kinds = kinds
        load(libraryId, kinds)
    }

    fun retry() {
        val libraryId = libraryId ?: return
        bound = true
        _state.update { it.copy(loading = true) }
        load(libraryId, kinds)
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

    fun consumeSessionExpired() {
        _state.update { it.copy(sessionExpiredServerId = null) }
    }

    private fun load(libraryId: UUID, kinds: List<BaseItemKind>) {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                bound = false
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            try {
                val pool = mediaRepository.recentlyWatched(libraryId, kinds, SEED_POOL_SIZE)
                val rows = becauseYouWatchedRows(kinds, chooseWatchSeeds(pool))
                publishRows(session, rows)
                focusChangedFlow.tryEmit(Unit)
                resolveSeasonCounts(rows)
            } catch (e: Exception) {
                if (e.isAuthFailure()) {
                    authRepository.expireStoredSession(session.server.id, session.userId)
                    _state.update {
                        it.copy(loading = true, session = null, sessionExpiredServerId = session.server.id)
                    }
                } else {
                    bound = false
                    _state.update { it.copy(loading = false, error = "Could not load suggestions") }
                }
            }
        }
    }

    private fun publishRows(session: UserSession, rows: List<HomeRow>) {
        _state.update {
            it.copy(loading = false, session = session, error = null, rows = rows)
        }
    }

    private suspend fun becauseYouWatchedRows(
        kinds: List<BaseItemKind>,
        seeds: List<BaseItemDto>
    ): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()
        for (batch in seeds.chunked(MAX_BECAUSE_ROWS)) {
            if (rows.size >= MAX_BECAUSE_ROWS) break
            coroutineScope {
                batch.map { seed -> seed to async { becauseYouWatchedItems(seed, kinds) } }
                    .forEach { (seed, pending) ->
                        val items = pending.await()
                        if (items.isEmpty() || rows.size >= MAX_BECAUSE_ROWS) return@forEach
                        rows += HomeRow(
                            title = "Because you watched ${seed.seedName.orEmpty()}",
                            items = items,
                            continueWatching = false
                        )
                    }
            }
        }
        return rows
    }

    private suspend fun becauseYouWatchedItems(
        seed: BaseItemDto,
        kinds: List<BaseItemKind>
    ): List<BaseItemDto> {
        val similar = runCatching { mediaRepository.similarItems(seed.seedId) }
            .getOrDefault(emptyList())
        val items = similar
            .filter { it.type in kinds && it.id != seed.seedId }
            .take(ROW_ITEM_LIMIT)
        return if (items.size < MIN_ROW_ITEMS) emptyList() else items
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
        const val SEED_POOL_SIZE = 120
        const val MAX_BECAUSE_ROWS = 10
        const val ROW_ITEM_LIMIT = 12

        const val MIN_ROW_ITEMS = 3
        const val STREAM_PREFETCH_CONCURRENCY = 4
    }
}
