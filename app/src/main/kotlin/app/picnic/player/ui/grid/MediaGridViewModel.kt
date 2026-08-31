package app.picnic.player.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.isAuthFailure
import app.picnic.player.data.media.GridFilterFacets
import app.picnic.player.data.media.GridSortSpec
import app.picnic.player.data.media.LibraryChangeBatch
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.media.MediaGridFilter
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.media.batches
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val changeBus: LibraryChangeBus
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val session: UserSession? = null,
        val totalCount: Int = 0,
        val revision: Int = 0,
        val sort: GridSortSpec = GridSortSpec(),
        val filter: MediaGridFilter = MediaGridFilter(),
        val facets: GridFilterFacets = GridFilterFacets(),
        val pendingScrollIndex: Int = 0,
        val scrollNonce: Int = 0,
        val isJumpingToLetter: Boolean = false,
        val error: String? = null,
        val sessionExpiredServerId: String? = null
    )

    private var lastPagedPage = Int.MIN_VALUE

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var kinds: List<BaseItemKind> = emptyList()
    private var title: String = "library"
    private var bound = false

    private var storeReady = false
    private var facetsLoaded = false
    private lateinit var store: MediaGridStore

    init {
        viewModelScope.launch {
            changeBus.batches().collect { batch -> applyBatch(batch) }
        }
    }

    fun bindLibrary(libraryId: UUID, kinds: List<BaseItemKind>, title: String) {
        if (bound) return
        bound = true
        this.kinds = kinds
        this.title = title
        _state.update { it.copy(filter = it.filter.copy(libraryId = libraryId)) }
        load()
    }

    fun bindGenre(genreId: UUID, title: String, libraryId: UUID? = null) {
        if (bound) return
        bound = true
        kinds = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
        this.title = title
        _state.update {
            it.copy(filter = it.filter.copy(genreId = genreId, libraryId = libraryId))
        }
        load()
    }

    fun bindCollections() {
        if (bound) return
        bound = true
        kinds = listOf(BaseItemKind.BOX_SET)
        this.title = "collections"
        load()
    }

    private fun applyBatch(batch: LibraryChangeBatch) {
        if (!storeReady) return
        viewModelScope.launch {
            val ids = store.loadedIds(batch.itemIds)
            if (batch.contentChanged || (ids.isNotEmpty() && _state.value.filter.tracksUserStatus)) {
                reloadGrid()
                return@launch
            }
            if (ids.isEmpty()) return@launch
            if (store.replaceItems(mediaRepository.items(ids))) {
                _state.update { it.copy(revision = it.revision + 1) }
            }
        }
    }

    private suspend fun reloadGrid() {
        val current = _state.value
        lastPagedPage = Int.MIN_VALUE
        try {
            store.reset(current.sort, current.filter)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return
        }
        _state.update { it.copy(totalCount = store.totalCount, revision = it.revision + 1) }
    }

    fun retry() {
        bound = true
        _state.update { it.copy(loading = true) }
        load()
    }

    fun consumeSessionExpired() {
        _state.update { it.copy(sessionExpiredServerId = null) }
    }

    fun softLogout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun onVisibleIndex(firstVisibleIndex: Int) {
        if (!storeReady) return
        val page = firstVisibleIndex / store.pageSize
        if (page == lastPagedPage) return
        lastPagedPage = page
        viewModelScope.launch {
            var loaded = false
            for (p in (page - 1)..(page + 2)) {
                if (p >= 0 && store.ensureIndex(p * store.pageSize)) loaded = true
            }
            if (loaded) _state.update { it.copy(revision = it.revision + 1) }
        }
    }

    fun applyFilterSort(filter: MediaGridFilter, sort: GridSortSpec) {
        val current = _state.value
        if (current.filter == filter && current.sort == sort) return
        val scopeChanged = current.filter.libraryId != filter.libraryId ||
            current.filter.genreId != filter.genreId
        viewModelScope.launch {
            _state.update { it.copy(filter = filter, sort = sort, loading = true) }
            lastPagedPage = Int.MIN_VALUE
            runCatching { store.reset(sort, filter) }
                .onFailure {
                    _state.update { it.copy(loading = false, error = "Could not apply filters") }
                    return@launch
                }
            _state.update {
                it.copy(
                    loading = false,
                    totalCount = store.totalCount,
                    revision = it.revision + 1,
                    pendingScrollIndex = 0,
                    error = null
                )
            }
            if (scopeChanged) refreshFacets(filter.libraryId)
        }
    }

    private suspend fun refreshFacets(libraryId: UUID?) {
        val session = _state.value.session ?: return
        val facets = runCatching {
            mediaRepository.gridFilterFacets(kinds, libraryId)
        }.getOrNull() ?: return
        _state.update { it.copy(facets = facets) }
        facetsLoaded = true
        val filter = _state.value.filter
        val pruned = filter.copy(
            genreIds = filter.genreIds intersect facets.genres.map { it.id }.toSet(),
            studioIds = filter.studioIds intersect facets.studios.map { it.id }.toSet(),
            parentalRatings = filter.parentalRatings intersect facets.parentalRatings.toSet(),
            decades = filter.decades intersect facets.decades.toSet()
        )
        if (pruned != filter) applyFilterSort(pruned, _state.value.sort)
    }

    fun ensureFacetsLoaded() {
        if (facetsLoaded || !storeReady) return
        viewModelScope.launch {
            refreshFacets(_state.value.filter.libraryId)
        }
    }

    fun jumpToLetter(letter: String) {
        if (!_state.value.sort.supportsLetterJump) return
        viewModelScope.launch {
            _state.update { it.copy(isJumpingToLetter = true) }
            val index = store.jumpToLetter(letter)
            _state.update {
                it.copy(
                    isJumpingToLetter = false,
                    pendingScrollIndex = index,
                    revision = it.revision + 1,
                    scrollNonce = it.scrollNonce + 1
                )
            }
        }
    }

    fun itemAt(index: Int): BaseItemDto? {
        if (!storeReady) return null
        return store.slot(index)
    }

    private fun load() {
        viewModelScope.launch {
            val session = authRepository.activeSession()
            if (session == null) {
                bound = false
                _state.update { it.copy(loading = false, error = "No active session") }
                return@launch
            }
            val initial = _state.value
            store = MediaGridStore(
                kinds = kinds,
                sort = initial.sort,
                filter = initial.filter,
                repository = mediaRepository
            )
            storeReady = true
            try {
                store.reset(initial.sort, initial.filter)
                _state.update {
                    it.copy(
                        loading = false,
                        session = session,
                        totalCount = store.totalCount,
                        revision = it.revision + 1,
                        error = null
                    )
                }
            } catch (e: Exception) {
                if (e.isAuthFailure()) {
                    authRepository.expireStoredSession(session.server.id, session.userId)
                    _state.update {
                        it.copy(loading = true, session = null, sessionExpiredServerId = session.server.id)
                    }
                } else {
                    bound = false
                    _state.update { it.copy(loading = false, error = "Could not load ${title.lowercase()}") }
                }
            }
        }
    }
}
