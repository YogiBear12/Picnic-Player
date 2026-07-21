package app.picnic.player.ui.browse

import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NavLayout
import app.picnic.player.data.nav.NavLayoutResolver
import app.picnic.player.data.nav.NavLayoutStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which page of the side nav drawer is showing. */
enum class NavDrawerPage { Primary, More }

/**
 * App-scoped state for the persistent navigation rail. The rail outlives any one
 * nav destination — it renders at the nav-host level over Browse AND pushed item screens
 * (Detail/Collection/Genre) — so its selection, destinations, and focus targets live here
 * rather than in [BrowseShellHost].
 *
 * Customisable destinations (libraries + Discover) are split into pinned (primary page)
 * and unpinned (More page) via [NavLayout] (#88).
 */
@Singleton
class NavRailState @Inject constructor() {

    private val _session = MutableStateFlow<UserSession?>(null)
    val session = _session.asStateFlow()

    /** Destinations shown on the current drawer page (excludes Settings / avatar / More chrome). */
    private val _drawerDestinations = MutableStateFlow<List<BrowseDest>>(
        listOf(BrowseDest.Search, BrowseDest.Home)
    )
    val drawerDestinations = _drawerDestinations.asStateFlow()

    /** Every browsable destination (for pane resolution), including unpinned. */
    private val _allDestinations = MutableStateFlow<List<BrowseDest>>(
        listOf(BrowseDest.Search, BrowseDest.Home)
    )
    val allDestinations = _allDestinations.asStateFlow()

    private val _layout = MutableStateFlow(NavLayout())
    val layout = _layout.asStateFlow()

    private val _drawerPage = MutableStateFlow(NavDrawerPage.Primary)
    val drawerPage = _drawerPage.asStateFlow()

    /** True when More should appear on the primary page. */
    private val _moreVisible = MutableStateFlow(false)
    val moreVisible = _moreVisible.asStateFlow()

    /** Key currently in reorder mode, or null. */
    private val _reorderKey = MutableStateFlow<String?>(null)
    val reorderKey = _reorderKey.asStateFlow()

    /** Bumps when layout changes so Home can rebuild rows without a network round-trip. */
    private val _layoutEpoch = MutableStateFlow(0)
    val layoutEpoch = _layoutEpoch.asStateFlow()

    private var lastLibraries: List<BrowseDest.Library> = emptyList()
    private var discoverAvailable: Boolean = false
    private var playlistsAvailable: Boolean = false
    private var customById: Map<String, BrowseDest> = emptyMap()

    private val _selectedKey = MutableStateFlow(BrowseDest.Home.key)
    val selectedKey = _selectedKey.asStateFlow()

    private val _pendingCommit = MutableStateFlow(false)
    val pendingCommit = _pendingCommit.asStateFlow()

    private val _chromeFocused = MutableStateFlow(false)
    val chromeFocused = _chromeFocused.asStateFlow()

    private val requesters = mutableMapOf<String, FocusRequester>()
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun destinationFor(key: String): BrowseDest? = _allDestinations.value.firstOrNull { it.key == key }

    /** Pinned library destinations in pin order — drives Home "Recently added" rows. */
    fun pinnedLibraries(): List<BrowseDest.Library> = _layout.value.pinnedIds.mapNotNull { customById[it] as? BrowseDest.Library }

    fun lastPublishedLibraries(): List<BrowseDest.Library> = lastLibraries

    fun isDiscoverAvailable(): Boolean = discoverAvailable

    /**
     * Publish session + libraries + whether Discover (Seerr linked) and Playlists exist.
     * [layout] is the reconciled persisted layout for this user.
     */
    fun publish(
        session: UserSession,
        libraries: List<BrowseDest.Library>,
        discoverAvailable: Boolean,
        playlistsAvailable: Boolean,
        layout: NavLayout
    ) {
        _session.value = session
        lastLibraries = libraries
        this.discoverAvailable = discoverAvailable
        this.playlistsAvailable = playlistsAvailable
        customById = buildCustomMap(libraries, discoverAvailable, playlistsAvailable)
        _layout.value = layout
        rebuildDestinations()
        ensureSelectionValid()
        _layoutEpoch.update { it + 1 }
    }

    fun applyLayout(layout: NavLayout) {
        _layout.value = layout
        rebuildDestinations()
        if (_drawerPage.value == NavDrawerPage.More && !_moreVisible.value) {
            _drawerPage.value = NavDrawerPage.Primary
            rebuildDestinations()
        }
        ensureSelectionValid()
        _layoutEpoch.update { it + 1 }
    }

    fun openMorePage() {
        if (!_moreVisible.value) return
        _drawerPage.value = NavDrawerPage.More
        _reorderKey.value = null
        rebuildDestinations()
    }

    fun openPrimaryPage() {
        _drawerPage.value = NavDrawerPage.Primary
        _reorderKey.value = null
        rebuildDestinations()
    }

    fun enterReorder(key: String) {
        _reorderKey.value = key
    }

    fun exitReorder() {
        _reorderKey.value = null
    }

    fun select(dest: BrowseDest) {
        _selectedKey.value = dest.key
        _pendingCommit.value = true
        val onUnpinnedPage = dest.key in _layout.value.unpinnedIds
        if (onUnpinnedPage) {
            if (_drawerPage.value != NavDrawerPage.More) openMorePage()
        } else if (_drawerPage.value != NavDrawerPage.Primary) {
            openPrimaryPage()
        }
    }

    fun setSelected(key: String) {
        _selectedKey.value = key
    }

    fun consumeCommit(): Boolean = _pendingCommit.value.also { _pendingCommit.value = false }

    fun setChromeFocused(focused: Boolean) {
        _chromeFocused.value = focused
    }

    fun clear() {
        _session.value = null
        lastLibraries = emptyList()
        discoverAvailable = false
        playlistsAvailable = false
        customById = emptyMap()
        _layout.value = NavLayout()
        _drawerDestinations.value = listOf(BrowseDest.Search, BrowseDest.Home)
        _allDestinations.value = listOf(BrowseDest.Search, BrowseDest.Home)
        _moreVisible.value = false
        _drawerPage.value = NavDrawerPage.Primary
        _reorderKey.value = null
        _selectedKey.value = BrowseDest.Home.key
        _pendingCommit.value = false
        _chromeFocused.value = false
    }

    private fun ensureSelectionValid() {
        if (!discoverAvailable && _selectedKey.value == BrowseDest.Discover.key) {
            _selectedKey.value = BrowseDest.Home.key
            if (_drawerPage.value == NavDrawerPage.More) {
                _drawerPage.value = NavDrawerPage.Primary
                rebuildDestinations()
            }
        }
        if (!playlistsAvailable && _selectedKey.value == BrowseDest.Playlists.key) {
            _selectedKey.value = BrowseDest.Home.key
            if (_drawerPage.value == NavDrawerPage.More) {
                _drawerPage.value = NavDrawerPage.Primary
                rebuildDestinations()
            }
        }
        if (_allDestinations.value.none { it.key == _selectedKey.value }) {
            _selectedKey.value = BrowseDest.Home.key
            if (_drawerPage.value == NavDrawerPage.More) {
                _drawerPage.value = NavDrawerPage.Primary
                rebuildDestinations()
            }
        }
    }

    private fun rebuildDestinations() {
        val layout = _layout.value
        val pinned = layout.pinnedIds.mapNotNull { customById[it] }
        val unpinned = layout.unpinnedIds.mapNotNull { customById[it] }
        _moreVisible.value = unpinned.isNotEmpty()
        _allDestinations.value = buildList {
            add(BrowseDest.Search)
            add(BrowseDest.Home)
            addAll(pinned)
            addAll(unpinned)
        }
        _drawerDestinations.value = when (_drawerPage.value) {
            NavDrawerPage.Primary -> buildList {
                add(BrowseDest.Search)
                add(BrowseDest.Home)
                addAll(pinned)
            }
            NavDrawerPage.More -> unpinned
        }
    }

    private fun buildCustomMap(
        libraries: List<BrowseDest.Library>,
        discoverAvailable: Boolean,
        playlistsAvailable: Boolean
    ): Map<String, BrowseDest> = buildMap {
        libraries.forEach { put(it.key, it) }
        if (playlistsAvailable) put(BrowseDest.Playlists.key, BrowseDest.Playlists)
        if (discoverAvailable) put(BrowseDest.Discover.key, BrowseDest.Discover)
    }
}

/** Thin handle so the nav host (a plain composable) can reach the singleton via Hilt. */
@HiltViewModel
class NavRailViewModel @Inject constructor(
    val rail: NavRailState,
    private val authRepository: AuthRepository,
    private val navLayoutStore: NavLayoutStore
) : ViewModel() {

    fun softLogout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun pin(dest: BrowseDest) {
        mutateLayout { NavLayoutResolver.pin(it, dest.key) }
        if (rail.selectedKey.value == dest.key) {
            rail.openPrimaryPage()
        }
    }

    fun unpin(dest: BrowseDest) {
        mutateLayout { NavLayoutResolver.unpin(it, dest.key) }
        if (rail.selectedKey.value == dest.key) {
            rail.openMorePage()
        }
    }

    fun moveReorder(delta: Int) {
        val key = rail.reorderKey.value ?: return
        val session = rail.session.value ?: return
        val next = NavLayoutResolver.move(rail.layout.value, key, delta)
        // Apply synchronously so the focused row identity ([key]) stays under focus
        // before the next D-pad event; persist off the UI path.
        rail.applyLayout(next)
        viewModelScope.launch {
            navLayoutStore.save(session.server.id, session.userId, next)
        }
    }

    private fun mutateLayout(transform: (NavLayout) -> NavLayout) {
        viewModelScope.launch {
            val session = rail.session.value ?: return@launch
            val next = transform(rail.layout.value)
            navLayoutStore.save(session.server.id, session.userId, next)
            rail.applyLayout(next)
        }
    }
}
