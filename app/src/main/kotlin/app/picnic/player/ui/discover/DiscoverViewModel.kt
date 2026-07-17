package app.picnic.player.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrDiscoverRow
import app.picnic.player.data.seerr.SeerrMediaType
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.seerr.SeerrSessionState
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    val ambientLoader: AmbientPaletteLoader
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val rows: List<SeerrDiscoverRow> = emptyList(),
        val seerr: SeerrSessionState = SeerrSessionState(),
        val error: String? = null,
        val focusedRowIndex: Int = 0,
        val focusedTmdbId: Int? = null,
        val rowFocusedIds: Map<Int, Int> = emptyMap()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var loaded = false
    private val focusChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            seerrRepository.state.collect { seerr ->
                _state.update { it.copy(seerr = seerr) }
            }
        }
        viewModelScope.launch {
            focusChangedFlow.debounce(FOCUS_DEBOUNCE_MS).collectLatest { prefetchDetailsAhead() }
        }
    }

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { seerrRepository.discoverRows() }
                .onSuccess { rows ->
                    val first = rows.firstOrNull()?.items?.firstOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            rows = rows,
                            error = if (rows.isEmpty()) "Nothing to discover yet." else null,
                            focusedTmdbId = it.focusedTmdbId ?: first?.tmdbId,
                            focusedRowIndex = if (it.focusedTmdbId == null) 0 else it.focusedRowIndex,
                            rowFocusedIds = it.rowFocusedIds.ifEmpty {
                                first?.let { f -> mapOf(0 to f.tmdbId) } ?: emptyMap()
                            }
                        )
                    }
                    // Kick surrounding enrich for the initial focus window.
                    focusChangedFlow.tryEmit(Unit)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Failed to load Discover")
                    }
                }
        }
    }

    fun focusedItem(state: UiState = _state.value): SeerrCatalogItem? {
        val rows = state.rows
        if (rows.isEmpty()) return null
        state.focusedTmdbId?.let { id ->
            rows.asSequence().flatMap { it.items.asSequence() }
                .firstOrNull { it.tmdbId == id }
                ?.let { return it }
        }
        return rows.firstOrNull()?.items?.firstOrNull()
    }

    fun onItemFocused(rowIndex: Int, item: SeerrCatalogItem) {
        _state.update {
            it.copy(
                focusedRowIndex = rowIndex,
                focusedTmdbId = item.tmdbId,
                rowFocusedIds = it.rowFocusedIds + (rowIndex to item.tmdbId)
            )
        }
        focusChangedFlow.tryEmit(Unit)
    }

    /**
     * Surrounding-only detail enrich — mirrors Home [prefetchStreamsAhead]:
     * look-ahead rows (focused ±1) and a sliding card window (−2..+4) around each
     * row's focused item. Skips already-enriched cards; bounded concurrency.
     */
    private suspend fun prefetchDetailsAhead() {
        val snapshot = _state.value
        val rows = snapshot.rows
        if (rows.isEmpty()) return

        val lookAheadRows =
            ((snapshot.focusedRowIndex - 1)..(snapshot.focusedRowIndex + 1))
                .filter { it in rows.indices }

        val pending = mutableListOf<SeerrCatalogItem>()
        val seen = mutableSetOf<Pair<SeerrMediaType, Int>>()
        for (rIdx in lookAheadRows) {
            val row = rows[rIdx]
            val rowFocus = snapshot.rowFocusedIds[rIdx]
            val startIdx = row.items.indexOfFirst { it.tmdbId == rowFocus }.coerceAtLeast(0)
            for (i in row.items.indices) {
                if (i !in (startIdx - WINDOW_BEHIND)..(startIdx + WINDOW_AHEAD)) continue
                val item = row.items[i]
                if (item.detailEnriched) continue
                val key = item.mediaType to item.tmdbId
                if (!seen.add(key)) continue
                pending += item
            }
        }
        if (pending.isEmpty()) return

        val gate = Semaphore(ENRICH_CONCURRENCY)
        coroutineScope {
            pending.map { item ->
                async {
                    val enriched = runCatching {
                        gate.withPermit { seerrRepository.enrichCatalogItem(item) }
                    }.getOrNull() ?: return@async
                    mergeEnriched(enriched)
                }
            }.awaitAll()
        }
    }

    private fun mergeEnriched(enriched: SeerrCatalogItem) {
        _state.update { state ->
            val rows = state.rows.map { row ->
                row.copy(
                    items = row.items.map { existing ->
                        if (existing.tmdbId == enriched.tmdbId &&
                            existing.mediaType == enriched.mediaType
                        ) {
                            existing.copy(
                                overview = enriched.overview ?: existing.overview,
                                lastAirDate = enriched.lastAirDate ?: existing.lastAirDate,
                                seriesStatus = enriched.seriesStatus ?: existing.seriesStatus,
                                genreNames = enriched.genreNames.ifEmpty { existing.genreNames },
                                runtimeMinutes = enriched.runtimeMinutes,
                                seasonCount = enriched.seasonCount,
                                certificate = enriched.certificate,
                                voteAverage = enriched.voteAverage ?: existing.voteAverage,
                                mediaStatus = enriched.mediaStatus ?: existing.mediaStatus,
                                jellyfinMediaId =
                                enriched.jellyfinMediaId ?: existing.jellyfinMediaId,
                                detailEnriched = true
                            )
                        } else {
                            existing
                        }
                    }
                )
            }
            state.copy(rows = rows)
        }
    }

    private companion object {
        const val FOCUS_DEBOUNCE_MS = 200L

        /** Same window as Home series stream prefetch. */
        const val WINDOW_BEHIND = 2
        const val WINDOW_AHEAD = 4
        const val ENRICH_CONCURRENCY = 4
    }
}
