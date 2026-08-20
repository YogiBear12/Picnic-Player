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
        val rowFocusedItemIds: Map<Int, UUID> = emptyMap(),
        val seasonCounts: Map<UUID, Int> = emptyMap(),
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
                    val topPicks = async { topPicksRow(libraryId, kinds) }
                    val becauseRows = async { becauseYouWatchedRows(libraryId, kinds) }
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
                resolveSeasonCounts(rows)
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

    private suspend fun topPicksRow(
        libraryId: UUID,
        kinds: List<BaseItemKind>
    ): HomeRow? {
        val suggested = runCatching { mediaRepository.suggestions(SUGGESTION_FETCH_LIMIT) }
            .getOrDefault(emptyList())
            .filter { it.type in kinds }
        val inLibrary = filterToLibrary(libraryId, suggested).take(ROW_ITEM_LIMIT)
        if (inLibrary.size < MIN_ROW_ITEMS) return null
        return HomeRow(title = "Top picks for you", items = inLibrary, continueWatching = false)
    }

    private suspend fun becauseYouWatchedRows(
        libraryId: UUID,
        kinds: List<BaseItemKind>
    ): List<HomeRow> {
        val seeds = runCatching {
            mediaRepository.randomWatched(libraryId, kinds, SEED_FETCH_LIMIT)
        }.getOrDefault(emptyList())
        if (seeds.isEmpty()) return emptyList()
        val gate = Semaphore(ROW_BUILD_CONCURRENCY)
        return coroutineScope {
            seeds.map { seed ->
                async {
                    gate.withPermit {
                        val similar = runCatching { mediaRepository.similarItems(seed.id) }
                            .getOrDefault(emptyList())
                        val items = filterToLibrary(libraryId, similar)
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

    private suspend fun filterToLibrary(
        libraryId: UUID,
        candidates: List<BaseItemDto>
    ): List<BaseItemDto> {
        if (candidates.isEmpty()) return emptyList()
        val inLibrary = runCatching {
            mediaRepository.itemsInLibrary(libraryId, candidates.map { it.id })
        }.getOrDefault(emptyList()).associateBy { it.id }
        return candidates.mapNotNull { inLibrary[it.id] }
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
            val fetched = runCatching {
                mediaRepository.itemStreams(plan.streamIds)
            }.getOrDefault(emptyMap())
            newStreams.putAll(fetched)
        }

        if (plan.seriesIds.isNotEmpty()) {
            val gate = Semaphore(ROW_BUILD_CONCURRENCY)
            coroutineScope {
                plan.seriesIds.map { seriesId ->
                    async {
                        val streams = runCatching {
                            gate.withPermit { mediaRepository.seriesLeadStreams(seriesId) }
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

    private suspend fun resolveSeasonCounts(rows: List<HomeRow>) {
        val seriesIds = seriesNeedingSeasonCount(rows, _state.value.seasonCounts.keys)
        if (seriesIds.isEmpty()) return
        val gate = Semaphore(ROW_BUILD_CONCURRENCY)
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
        const val SEED_FETCH_LIMIT = 8
        const val MAX_BECAUSE_ROWS = 5
        const val ROW_ITEM_LIMIT = 12

        const val MIN_ROW_ITEMS = 3
        const val SUGGESTION_FETCH_LIMIT = 40
        const val ROW_BUILD_CONCURRENCY = 4
    }
}
