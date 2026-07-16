package app.picnic.player.ui.browse

import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped state for the persistent navigation rail. The rail outlives any one
 * nav destination — it renders at the nav-host level over Browse AND pushed item screens
 * (Detail/Collection/Genre) — so its selection, destinations, and focus targets live here
 * rather than in [BrowseShellHost].
 *
 * Producers: [app.picnic.player.ui.home.HomeViewModel] publishes the session + video
 * libraries when the home rows load; the nav host clears everything on sign-out/user swap.
 * Consumer: the nav host renders the drawer from this state; the browse shell reads
 * [selectedKey]/[destinations] to switch panes and consumes [pendingCommit] to seed focus.
 */
@Singleton
class NavRailState @Inject constructor() {

    private val _session = MutableStateFlow<UserSession?>(null)
    val session = _session.asStateFlow()

    private val _destinations = MutableStateFlow<List<BrowseDest>>(
        listOf(BrowseDest.Search, BrowseDest.Home)
    )
    val destinations = _destinations.asStateFlow()

    private var lastLibraries: List<BrowseDest.Library> = emptyList()
    private var discoverEnabled: Boolean = false

    private val _selectedKey = MutableStateFlow(BrowseDest.Home.key)
    val selectedKey = _selectedKey.asStateFlow()

    /** True while a rail activation awaits the target pane taking focus (Commit). */
    private val _pendingCommit = MutableStateFlow(false)
    val pendingCommit = _pendingCommit.asStateFlow()

    /** Whether any rail item currently holds focus — the shell's back-ladder reads this. */
    private val _chromeFocused = MutableStateFlow(false)
    val chromeFocused = _chromeFocused.asStateFlow()

    // One permanent requester per destination key: shared by the drawer items (attach) and
    // the shell's back-ladder (request). getOrPut so late-arriving library keys just work.
    private val requesters = mutableMapOf<String, FocusRequester>()
    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun publish(
        session: UserSession,
        libraries: List<BrowseDest.Library>,
        showDiscover: Boolean = discoverEnabled
    ) {
        _session.value = session
        lastLibraries = libraries
        discoverEnabled = showDiscover
        _destinations.value = buildDestinations(libraries, showDiscover)
        // If Discover was selected and is now hidden, fall back to Home.
        if (!showDiscover && _selectedKey.value == BrowseDest.Discover.key) {
            _selectedKey.value = BrowseDest.Home.key
        }
    }

    /** Update Discover visibility without reloading libraries (Settings toggle / Seerr link). */
    fun setDiscoverVisible(visible: Boolean) {
        val session = _session.value ?: return
        publish(session, lastLibraries, showDiscover = visible)
    }

    private fun buildDestinations(
        libraries: List<BrowseDest.Library>,
        showDiscover: Boolean
    ): List<BrowseDest> = buildList {
        add(BrowseDest.Search)
        add(BrowseDest.Home)
        if (showDiscover) add(BrowseDest.Discover)
        addAll(libraries)
    }

    /** Rail item activated: switch destination and ask the pane to take focus when ready. */
    fun select(dest: BrowseDest) {
        _selectedKey.value = dest.key
        _pendingCommit.value = true
    }

    /** Selection change without the focus hand-off (the shell's Back-ladder Home jump). */
    fun setSelected(key: String) {
        _selectedKey.value = key
    }

    fun consumeCommit(): Boolean = _pendingCommit.value.also { _pendingCommit.value = false }

    fun setChromeFocused(focused: Boolean) {
        _chromeFocused.value = focused
    }

    /** Sign-out / user swap: the next user gets a fresh rail. */
    fun clear() {
        _session.value = null
        lastLibraries = emptyList()
        discoverEnabled = false
        _destinations.value = listOf(BrowseDest.Search, BrowseDest.Home)
        _selectedKey.value = BrowseDest.Home.key
        _pendingCommit.value = false
        _chromeFocused.value = false
    }
}

/** Thin handle so the nav host (a plain composable) can reach the singleton via Hilt. */
@HiltViewModel
class NavRailViewModel @Inject constructor(
    val rail: NavRailState,
    private val authRepository: app.picnic.player.data.auth.AuthRepository
) : ViewModel() {
    /** Drawer's switch-user action: drop the session; the caller navigates to the picker. */
    fun softLogout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
