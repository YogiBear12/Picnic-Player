package app.picnic.player.ui.browse

import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.nav.NAV_ID_DISCOVER
import app.picnic.player.data.nav.NAV_ID_PLAYLISTS
import app.picnic.player.data.nav.NavLayout
import app.picnic.player.data.nav.NavLayoutResolver
import app.picnic.player.data.nav.NavLayoutStore
import app.picnic.player.data.seerr.SeerrLinkState
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class NavPanelPage { Primary, More }

sealed interface ChromeFocusTarget {
    data object Settings : ChromeFocusTarget
    data object MoreItem : ChromeFocusTarget
    data object MoreBack : ChromeFocusTarget
    data class Row(val key: String) : ChromeFocusTarget
}

data class ChromeFocusRequest(val seq: Int, val target: ChromeFocusTarget)

internal fun panelNeighbor(destinations: List<BrowseDest>, key: String): String? {
    val index = destinations.indexOfFirst { it.key == key }
    if (index < 0) return null
    return (destinations.getOrNull(index - 1) ?: destinations.getOrNull(index + 1))?.key
}

internal fun pageFocusTarget(page: NavPanelPage, moreVisible: Boolean, selectedKey: String): ChromeFocusTarget = when (page) {
    NavPanelPage.More -> ChromeFocusTarget.MoreBack
    NavPanelPage.Primary -> if (moreVisible) ChromeFocusTarget.MoreItem else ChromeFocusTarget.Row(selectedKey)
}

@Singleton
class NavRailState @Inject constructor(
    private val navLayoutStore: NavLayoutStore,
    private val seerrRepository: SeerrRepository,
    @ApplicationScope appScope: CoroutineScope
) {
    private val _session = MutableStateFlow<UserSession?>(null)
    val session = _session.asStateFlow()

    private val _panelDestinations = MutableStateFlow<List<BrowseDest>>(
        listOf(BrowseDest.Search, BrowseDest.Home)
    )
    val panelDestinations = _panelDestinations.asStateFlow()

    private val _allDestinations = MutableStateFlow<List<BrowseDest>>(
        listOf(BrowseDest.Search, BrowseDest.Home)
    )
    val allDestinations = _allDestinations.asStateFlow()

    private val _layout = MutableStateFlow(NavLayout())
    val layout = _layout.asStateFlow()

    private val _panelPage = MutableStateFlow(NavPanelPage.Primary)
    val panelPage = _panelPage.asStateFlow()

    private val _moreVisible = MutableStateFlow(false)
    val moreVisible = _moreVisible.asStateFlow()

    private val _reorderKey = MutableStateFlow<String?>(null)
    val reorderKey = _reorderKey.asStateFlow()

    private val _layoutEpoch = MutableStateFlow(0)
    val layoutEpoch = _layoutEpoch.asStateFlow()

    @Volatile private var lastLibraries: List<BrowseDest.Library> = emptyList()

    @Volatile private var discoverAvailable: Boolean = false

    @Volatile private var playlistsAvailable: Boolean = false

    @Volatile private var customById: Map<String, BrowseDest> = emptyMap()

    @Volatile private var pinnedDests: List<BrowseDest> = emptyList()

    @Volatile private var unpinnedDests: List<BrowseDest> = emptyList()

    private val _selectedKey = MutableStateFlow(BrowseDest.Home.key)
    val selectedKey = _selectedKey.asStateFlow()

    private val _pendingCommit = MutableStateFlow(false)
    val pendingCommit = _pendingCommit.asStateFlow()

    private val _chromeFocused = MutableStateFlow(false)
    val chromeFocused = _chromeFocused.asStateFlow()

    private val _focusRequest = MutableStateFlow<ChromeFocusRequest?>(null)
    val focusRequest = _focusRequest.asStateFlow()
    private var focusSeq = 0
    private var lastPublishedPage = NavPanelPage.Primary
    private var entryTarget: ChromeFocusTarget? = null
    val hasEntryTarget: Boolean get() = entryTarget != null

    fun requestSettingsReturn() {
        entryTarget = ChromeFocusTarget.Settings
    }

    fun consumeEntryTarget(): ChromeFocusTarget? = entryTarget.also { entryTarget = null }

    fun neighborOf(key: String): String? = panelNeighbor(_panelDestinations.value, key)

    private fun publishFocus(target: ChromeFocusTarget) {
        _focusRequest.value = ChromeFocusRequest(++focusSeq, target)
    }

    private val requesters = mutableMapOf<String, FocusRequester>()
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun destinationFor(key: String): BrowseDest? = _allDestinations.value.firstOrNull { it.key == key }

    fun pinnedLibraryIds(): List<UUID> = _layout.value.pinnedIds.mapNotNull { (customById[it] as? BrowseDest.Library)?.id }

    fun isShown(id: String): Boolean = id in customById

    private val publishLock = Mutex()

    init {
        appScope.launch {
            combine(seerrRepository.state.map { it.linkState == SeerrLinkState.Linked }.distinctUntilChanged(), _layoutEpoch) { linked, _ -> linked }
                .collectLatest { linked -> if (linked != discoverAvailable) republish() }
        }
    }

    suspend fun publish(session: UserSession, libraries: List<BrowseDest.Library>, playlistsAvailable: Boolean) = publishLock.withLock { resolveAndApply(session, libraries, playlistsAvailable) { true } }

    /** A clear or user switch during the layout read wins; the stale republish is dropped. */
    private suspend fun republish() = publishLock.withLock {
        val session = _session.value ?: return@withLock
        resolveAndApply(session, lastLibraries, playlistsAvailable) { _session.value == session }
    }

    private suspend fun resolveAndApply(
        session: UserSession,
        libraries: List<BrowseDest.Library>,
        playlistsAvailable: Boolean,
        stillCurrent: () -> Boolean
    ) {
        val discoverAvailable = seerrRepository.state.value.linkState == SeerrLinkState.Linked
        val availableIds = buildList {
            addAll(libraries.map { it.key })
            if (playlistsAvailable) add(NAV_ID_PLAYLISTS)
            if (discoverAvailable) add(NAV_ID_DISCOVER)
        }
        val layout = navLayoutStore.resolve(session.server.id, session.userId, availableIds)
        if (!stillCurrent()) return
        _session.value = session
        lastLibraries = libraries
        this.discoverAvailable = discoverAvailable
        this.playlistsAvailable = playlistsAvailable
        customById = buildCustomMap(libraries, discoverAvailable, playlistsAvailable)
        _layout.value = layout
        resolveDestinations()
        _layoutEpoch.update { it + 1 }
    }

    suspend fun updateLayout(focusAfter: ChromeFocusTarget? = null, transform: (NavLayout) -> NavLayout) = publishLock.withLock {
        val session = _session.value ?: return@withLock
        val next = transform(_layout.value)
        val page = _panelPage.value
        applyLayout(next)
        if (focusAfter != null && page == _panelPage.value) {
            publishFocus(focusAfter)
        }
        _reorderKey.value?.let { publishFocus(ChromeFocusTarget.Row(it)) }
        navLayoutStore.save(session.server.id, session.userId, next)
    }

    private fun applyLayout(layout: NavLayout) {
        _layout.value = layout
        resolveDestinations()
        _layoutEpoch.update { it + 1 }
    }

    fun openMorePage() {
        if (!_moreVisible.value) return
        _panelPage.value = NavPanelPage.More
        _reorderKey.value = null
        showPage()
    }

    fun openPrimaryPage() {
        _panelPage.value = NavPanelPage.Primary
        _reorderKey.value = null
        showPage()
    }

    fun enterReorder(key: String) {
        _reorderKey.value = key
        publishFocus(ChromeFocusTarget.Row(key))
    }

    fun exitReorder() {
        val key = _reorderKey.value ?: return
        _reorderKey.value = null
        if (_focusRequest.value?.target == ChromeFocusTarget.Row(key)) _focusRequest.value = null
    }

    fun select(dest: BrowseDest) {
        _selectedKey.value = dest.key
        _pendingCommit.value = true
        val onUnpinnedPage = dest.key in _layout.value.unpinnedIds
        if (onUnpinnedPage) {
            if (_panelPage.value != NavPanelPage.More) openMorePage()
        } else if (_panelPage.value != NavPanelPage.Primary) {
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
        pinnedDests = emptyList()
        unpinnedDests = emptyList()
        _layout.value = NavLayout()
        _panelDestinations.value = listOf(BrowseDest.Search, BrowseDest.Home)
        _allDestinations.value = listOf(BrowseDest.Search, BrowseDest.Home)
        _moreVisible.value = false
        _panelPage.value = NavPanelPage.Primary
        _reorderKey.value = null
        _selectedKey.value = BrowseDest.Home.key
        _pendingCommit.value = false
        _chromeFocused.value = false
        _focusRequest.value = null
        focusSeq = 0
        lastPublishedPage = NavPanelPage.Primary
        entryTarget = null
    }

    private fun resolveDestinations() {
        val layout = _layout.value
        pinnedDests = layout.pinnedIds.mapNotNull { customById[it] }
        unpinnedDests = layout.unpinnedIds.mapNotNull { customById[it] }
        val all = listOf(BrowseDest.Search, BrowseDest.Home) + pinnedDests + unpinnedDests
        _allDestinations.value = all
        _moreVisible.value = unpinnedDests.isNotEmpty()
        if (all.none { it.key == _selectedKey.value }) {
            _selectedKey.value = BrowseDest.Home.key
            _pendingCommit.value = true
            _panelPage.value = NavPanelPage.Primary
        }
        if (unpinnedDests.isEmpty()) _panelPage.value = NavPanelPage.Primary
        showPage()
    }

    private fun showPage() {
        val page = _panelPage.value
        _panelDestinations.value = when (page) {
            NavPanelPage.Primary -> listOf(BrowseDest.Search, BrowseDest.Home) + pinnedDests
            NavPanelPage.More -> unpinnedDests
        }
        if (page != lastPublishedPage) {
            lastPublishedPage = page
            publishFocus(pageFocusTarget(page, _moreVisible.value, _selectedKey.value))
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

@HiltViewModel
class NavRailViewModel @Inject constructor(
    val rail: NavRailState,
    private val authRepository: AuthRepository
) : ViewModel() {
    fun softLogout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun pin(dest: BrowseDest) {
        viewModelScope.launch {
            val neighbor = rail.neighborOf(dest.key)?.let { ChromeFocusTarget.Row(it) }
            rail.updateLayout(focusAfter = neighbor) { NavLayoutResolver.pin(it, dest.key) }
            if (rail.selectedKey.value == dest.key) rail.openPrimaryPage()
        }
    }

    fun unpin(dest: BrowseDest) {
        viewModelScope.launch {
            val neighbor = rail.neighborOf(dest.key)?.let { ChromeFocusTarget.Row(it) }
            rail.updateLayout(focusAfter = neighbor) { NavLayoutResolver.unpin(it, dest.key) }
            if (rail.selectedKey.value == dest.key) rail.openMorePage()
        }
    }

    fun moveReorder(delta: Int) {
        val key = rail.reorderKey.value ?: return
        viewModelScope.launch { rail.updateLayout { NavLayoutResolver.move(it, key, delta, rail::isShown) } }
    }
}
