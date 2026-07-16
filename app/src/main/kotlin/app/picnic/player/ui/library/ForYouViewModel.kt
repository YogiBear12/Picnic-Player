package app.picnic.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.planHeroStreamPrefetch
import app.picnic.player.data.media.seriesNeedingSeasonCount
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

/**
 * A library's "For you" tab: a Top picks row (server suggestions, filtered to this
 * library) plus "Because you watched …" rows seeded by RANDOM watched items. Seeds are
 * drawn once per ViewModel lifetime — revisiting the tab shows the same rows; the next
 * app session draws fresh ones. A distinct Hilt key owns each library's instance.
 */
@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
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
        /** Per-row last-focused card — survives vertical moves and tab switches. */
        val rowFocusedItemIds: Map<Int, UUID> = emptyMap(),
        val seasonCounts: Map<UUID, Int> = emptyMap(),
        /** Hero technical badges, prefetched around the focused row (as on Home). */
        val heroStreams: Map<UUID, List<MediaStream>> = emptyMap()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var bound = false

    private val focusChangedFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        viewModelScope.launch {
            focusChangedFlow.debounce(200).collectLatest { prefetchStreamsAhead() }
        }
    }

    /** Scope to one library and build the rows. Idempotent — seeds must not redraw. */
    fun bind(libraryId: UUID, kinds: List<BaseItemKind>) {
        if (bound) return
        bound = true
        load(libraryId, kinds)
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
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            try {
                val rows = coroutineScope {
                    val topPicks = async { topPicksRow(session, libraryId, kinds) }
                    val becauseRows = async { becauseYouWatchedRows(session, libraryId, kinds) }
                    listOfNotNull(topPicks.await()) + becauseRows.await()
                }
                _state.update {
                    it.copy(
                        loading = false,
                        session = session,
                        rows = rows,
                        error = if (rows.isEmpty()) {
                            "Watch something from this library and suggestions will appear here."
                        } else {
                            null
                        }
                    )
                }
                resolveSeasonCounts(session, rows)
                // The hero may show before any card takes focus (focus can rest on the
                // tab row) — badge the first rows without waiting for a focus event.
                focusChangedFlow.tryEmit(Unit)
            } catch (e: Exception) {
                if (e.isAuthFailure()) {
                    authRepository.expireStoredSession(session.server.id, session.userId)
                    _state.update {
                        it.copy(loading = true, session = null, sessionExpiredServerId = session.server.id)
                    }
                } else {
                    _state.update { it.copy(loading = false, error = "Could not load suggestions") }
                }
            }
        }
    }

    /**
     * Server suggestions cut down to this library. The suggestions endpoint has no parent
     * scope, so the ids are re-fetched through the library (one call) and re-ordered to
     * the server's suggestion order.
     */
    private suspend fun topPicksRow(
        session: UserSession,
        libraryId: UUID,
        kinds: List<BaseItemKind>
    ): HomeRow? {
        val suggested = runCatching { mediaRepository.suggestions(session, SUGGESTION_FETCH_LIMIT) }
            .getOrDefault(emptyList())
            .filter { it.type in kinds }
        val inLibrary = filterToLibrary(session, libraryId, suggested).take(ROW_ITEM_LIMIT)
        if (inLibrary.size < MIN_ROW_ITEMS) return null
        return HomeRow(title = "Top picks for you", items = inLibrary, continueWatching = false)
    }

    /** One row per watched seed, built concurrently; empty and thin rows are dropped. */
    private suspend fun becauseYouWatchedRows(
        session: UserSession,
        libraryId: UUID,
        kinds: List<BaseItemKind>
    ): List<HomeRow> {
        val seeds = runCatching {
            mediaRepository.randomWatched(session, libraryId, kinds, SEED_FETCH_LIMIT)
        }.getOrDefault(emptyList())
        if (seeds.isEmpty()) return emptyList()
        val gate = Semaphore(ROW_BUILD_CONCURRENCY)
        return coroutineScope {
            seeds.map { seed ->
                async {
                    gate.withPermit {
                        val similar = runCatching { mediaRepository.similarItems(session, seed.id) }
                            .getOrDefault(emptyList())
                        val items = filterToLibrary(session, libraryId, similar)
                            .filter { it.id != seed.id }
                            .take(ROW_ITEM_LIMIT)
                        if (items.size < MIN_ROW_ITEMS) {
                            null
                        } else {
                            HomeRow(
                                title = "Because you watched ${seed.name.orEmpty()}",
                                items = items,
                                continueWatching = false
                            )
                        }
                    }
                }
            }.awaitAll()
        }.filterNotNull()
            .distinctBy { it.title }
            .take(MAX_BECAUSE_ROWS)
    }

    /** Library membership filter (exact, one call), preserving [candidates] order. */
    private suspend fun filterToLibrary(
        session: UserSession,
        libraryId: UUID,
        candidates: List<BaseItemDto>
    ): List<BaseItemDto> {
        if (candidates.isEmpty()) return emptyList()
        val inLibrary = runCatching {
            mediaRepository.itemsInLibrary(session, libraryId, candidates.map { it.id })
        }.getOrDefault(emptyList()).associateBy { it.id }
        return candidates.mapNotNull { inLibrary[it.id] }
    }

    /**
     * Same hero-badge prefetch as Home (see HomeViewModel.prefetchStreamsAhead): batched
     * stream fetch for the focused row ±1, with a sliding window for series (their lead
     * streams cost one call each).
     */
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
            val fetched = runCatching {
                mediaRepository.itemStreams(session, plan.streamIds)
            }.getOrDefault(emptyMap())
            newStreams.putAll(fetched)
        }

        if (plan.seriesIds.isNotEmpty()) {
            val gate = Semaphore(ROW_BUILD_CONCURRENCY)
            coroutineScope {
                plan.seriesIds.map { seriesId ->
                    async {
                        val streams = runCatching {
                            gate.withPermit { mediaRepository.seriesLeadStreams(session, seriesId) }
                        }.getOrDefault(emptyList())
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

    /** Same off-critical-path season labels as Home (see HomeViewModel). */
    private suspend fun resolveSeasonCounts(session: UserSession, rows: List<HomeRow>) {
        val seriesIds = seriesNeedingSeasonCount(rows, _state.value.seasonCounts.keys)
        if (seriesIds.isEmpty()) return
        val gate = Semaphore(ROW_BUILD_CONCURRENCY)
        val counts = coroutineScope {
            seriesIds.map { id ->
                async {
                    id to runCatching { gate.withPermit { mediaRepository.seasonCount(session, id) } }
                        .getOrDefault(0)
                }
            }.awaitAll()
        }.filter { it.second > 0 }.toMap()
        if (counts.isNotEmpty()) {
            _state.update { it.copy(seasonCounts = it.seasonCounts + counts) }
        }
    }

    private companion object {
        /** Watched seeds drawn per session; thin/empty rows are dropped after the fact. */
        const val SEED_FETCH_LIMIT = 8
        const val MAX_BECAUSE_ROWS = 5
        const val ROW_ITEM_LIMIT = 12

        /** Below this a row reads as an apology, not a shelf. */
        const val MIN_ROW_ITEMS = 3
        const val SUGGESTION_FETCH_LIMIT = 40
        const val ROW_BUILD_CONCURRENCY = 4
    }
}
