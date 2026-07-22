package app.picnic.player.ui.startup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.SessionCheck
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.ui.navigation.BrowseKey
import app.picnic.player.ui.navigation.ProfilePickerKey
import app.picnic.player.ui.navigation.ServerEntryKey
import app.picnic.player.ui.navigation.ServerPickerKey
import app.picnic.player.ui.theme.PicnicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val seerrRepository: SeerrRepository,
    private val homeLoader: app.picnic.player.data.media.HomeContentLoader,
    private val settingsStore: app.picnic.player.data.settings.SettingsStore,
    serverDiscovery: app.picnic.player.data.media.ServerDiscovery
) : ViewModel() {
    private val _destination = MutableStateFlow<NavKey?>(null)
    val destination = _destination.asStateFlow()

    init {
        // Prefetch LAN discovery during the splash so the server screen has results
        // ready instead of searching in front of the user.
        serverDiscovery.ensureStarted()
        viewModelScope.launch {
            authRepository.warmLocalCache()
            _destination.value = resolveDestination()
        }
    }

    /**
     * Bootstrap phase → start destination. An active session goes straight
     * Home. Otherwise onboarded servers route to the return-user pickers —
     * Profile Picker for a single server, Server Picker for several — and a clean
     * install starts the onboarding wizard at server entry.
     */
    private suspend fun resolveDestination(): NavKey {
        // Active session → Home when the token still works AND auto-login is enabled. With
        // auto-login off, land on the profile picker (Select User). A REJECTED token expires and
        // falls through to the picker; an UNREACHABLE server routes to Select Server with an error
        // on its card and, crucially, does NOT expire the token — the server's just down.
        authRepository.activeSession()?.let { session ->
            return when (val check = authRepository.validateSession(session)) {
                SessionCheck.Valid -> {
                    val autoLogin = settingsStore.settings.first().autoLoginLastUser
                    if (autoLogin) {
                        seerrRepository.attach(session)
                        // Start the home fetch NOW so it overlaps the rest of the splash — Home
                        // re-uses this in-flight work and usually lands on fresh rows immediately.
                        // Fire-and-forget; the loader runs in the app scope.
                        homeLoader.prefetch(session)
                        BrowseKey
                    } else {
                        ProfilePickerKey(session.server.id)
                    }
                }
                SessionCheck.AuthInvalid -> {
                    authRepository.expireStoredSession(session.server.id, session.userId)
                    ProfilePickerKey(session.server.id)
                }
                is SessionCheck.Unreachable ->
                    ServerPickerKey(session.server.id, check.message)
            }
        }
        val servers = authRepository.onboardedServers()
        if (servers.isEmpty()) return ServerEntryKey
        val activeServer = authRepository.activeServerId()
        return if (activeServer != null && servers.any { it.id == activeServer }) {
            ProfilePickerKey(activeServer)
        } else {
            ServerPickerKey()
        }
    }
}

/** Decides the start destination from the stored session, then routes once. */
@Composable
fun StartupScreen(
    onResolved: (NavKey) -> Unit,
    viewModel: StartupViewModel = hiltViewModel()
) {
    // Startup shows the plain ocean wash — drop any backdrop left by a media screen.
    app.picnic.player.ui.ambient.PublishBackdrop(null)
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) { destination?.let(onResolved) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PicnicColors.Accent)
    }
}
