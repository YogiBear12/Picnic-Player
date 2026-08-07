package app.picnic.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Search tab state (lives in the browse shell, so query/results/focus survive
 * navigating into a result and back — same contract as Home's row focus).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val changeBus: LibraryChangeBus
) : ViewModel() {

    /** Which part of the pane held focus last — the seed-on-return target. */
    enum class FocusArea { FIELD, GENRES, RESULTS, DISCOVER }

    data class ResultRow(val kind: BaseItemKind, val title: String, val items: List<BaseItemDto>)

    data class DiscoverResultRow(val title: String, val items: List<SeerrCatalogItem>)

    data class UiState(
        val loading: Boolean = true,
        val session: UserSession? = null,
        val genres: List<BaseItemDto> = emptyList(),
        val query: String = "",
        val searching: Boolean = false,
        /** Non-empty rows only, Movies → Shows → Episodes. */
        val results: List<ResultRow> = emptyList(),
        /** Seerr discover rows appended under library results when linked. */
        val discoverResults: List<DiscoverResultRow> = emptyList(),
        val seerrLinked: Boolean = false,
        val seerrBaseUrl: String? = null,
        val seerrCacheImages: Boolean = false,
        val error: String? = null,
        val focusArea: FocusArea = FocusArea.FIELD,
        val focusedGenreIndex: Int = 0,
        val focusedResultRow: Int = 0,
        val focusedDiscoverRow: Int = 0,
        val rowFocusedItemIds: Map<Int, UUID> = emptyMap(),
        val discoverRowFocusedIds: Map<Int, Int> = emptyMap()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    private var started = false

    fun ensureLoaded() {
        if (started) return
        started = true
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            val genres = runCatching { mediaRepository.genres(session) }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, session = session, genres = genres) }
        }
        viewModelScope.launch {
            seerrRepository.state.collect { seerr ->
                _state.update {
                    it.copy(
                        seerrLinked = seerr.linkState == SeerrLinkState.Linked,
                        seerrBaseUrl = seerr.serverUrl,
                        seerrCacheImages = seerr.cacheImages
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            changeBus.events.collect { change ->
                if (change is LibraryChange.ItemUpdated) {
                    patchResults(setOfNotNull(change.itemId, change.seriesId))
                }
            }
        }
        viewModelScope.launch {
            queryFlow.debounce(QUERY_DEBOUNCE_MS).collectLatest { query ->
                if (query.isBlank()) {
                    _state.update {
                        it.copy(
                            searching = false,
                            results = emptyList(),
                            discoverResults = emptyList()
                        )
                    }
                    return@collectLatest
                }
                val session = _state.value.session ?: return@collectLatest
                _state.update { it.copy(searching = true) }
                val (rows, discover) = coroutineScope {
                    val movies = async { search(session, query, BaseItemKind.MOVIE) }
                    val shows = async { search(session, query, BaseItemKind.SERIES) }
                    val episodes = async { search(session, query, BaseItemKind.EPISODE) }
                    val seerr = async {
                        if (_state.value.seerrLinked) {
                            runCatching { seerrRepository.search(query) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                    val libraryRows = listOf(
                        ResultRow(BaseItemKind.MOVIE, "Movies", movies.await()),
                        ResultRow(BaseItemKind.SERIES, "Shows", shows.await()),
                        ResultRow(BaseItemKind.EPISODE, "Episodes", episodes.await())
                    ).filter { it.items.isNotEmpty() }
                    val seerrItems = seerr.await()
                    val discoverRows = if (seerrItems.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(DiscoverResultRow("Discover", seerrItems))
                    }
                    libraryRows to discoverRows
                }
                _state.update {
                    it.copy(
                        searching = false,
                        results = rows,
                        discoverResults = discover,
                        focusedResultRow = 0,
                        focusedDiscoverRow = 0,
                        rowFocusedItemIds = emptyMap(),
                        discoverRowFocusedIds = emptyMap()
                    )
                }
            }
        }
    }

    /**
     * Refetches only the affected rows rather than re-running the query, so results keep their
     * order and the focused card stays where it is.
     */
    private suspend fun patchResults(changedIds: Set<String>) {
        val session = _state.value.session ?: return
        val affected = _state.value.results
            .flatMap { it.items }
            .filter { it.id.toString() in changedIds }
        if (affected.isEmpty()) return
        val refreshed = affected
            .mapNotNull { runCatching { mediaRepository.item(session, it.id) }.getOrNull() }
            .associateBy { it.id }
        if (refreshed.isEmpty()) return
        _state.update { state ->
            state.copy(
                results = state.results.map { row ->
                    row.copy(items = row.items.map { refreshed[it.id] ?: it })
                }
            )
        }
    }

    private suspend fun search(session: UserSession, query: String, kind: BaseItemKind): List<BaseItemDto> = runCatching { mediaRepository.search(session, query, kind) }
        .getOrDefault(emptyList())
        .sortedWith(compareBy({ relevance(it, query) }, { it.sortName ?: it.name ?: "" }))

    private fun relevance(item: BaseItemDto, query: String): Int {
        val name = item.name?.lowercase() ?: return 4
        val q = query.trim().lowercase()
        return when {
            name == q -> 0
            name.startsWith(q) -> 1
            name.split(' ', ':', '-', '.').any { it.startsWith(q) } -> 2
            name.contains(q) -> 3
            else -> 4
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun clearSearch() {
        queryFlow.value = ""
        _state.update {
            it.copy(
                query = "",
                searching = false,
                results = emptyList(),
                discoverResults = emptyList(),
                focusArea = FocusArea.FIELD,
                focusedGenreIndex = 0,
                focusedResultRow = 0,
                focusedDiscoverRow = 0,
                rowFocusedItemIds = emptyMap(),
                discoverRowFocusedIds = emptyMap()
            )
        }
    }

    fun onFieldFocused() = _state.update { it.copy(focusArea = FocusArea.FIELD) }

    fun onGenreFocused(index: Int) = _state.update {
        it.copy(focusArea = FocusArea.GENRES, focusedGenreIndex = index)
    }

    fun onResultFocused(rowIndex: Int, item: BaseItemDto) = _state.update {
        it.copy(
            focusArea = FocusArea.RESULTS,
            focusedResultRow = rowIndex,
            rowFocusedItemIds = it.rowFocusedItemIds + (rowIndex to item.id)
        )
    }

    fun onDiscoverFocused(rowIndex: Int, item: SeerrCatalogItem) = _state.update {
        it.copy(
            focusArea = FocusArea.DISCOVER,
            focusedDiscoverRow = rowIndex,
            discoverRowFocusedIds = it.discoverRowFocusedIds + (rowIndex to item.tmdbId)
        )
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 600L
    }
}
