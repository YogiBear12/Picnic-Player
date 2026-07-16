package app.picnic.player.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.ServerConnection
import app.picnic.player.data.media.ServerDiscovery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Step 1 of onboarding — resolve a [ServerConnection]. LAN discovery
 * runs automatically on entry (no separate button); the user may also type an
 * address. A resolved server is parked in [OnboardingSession] and the screen is
 * told to advance to Login — nothing is persisted until login succeeds.
 */
@HiltViewModel
class ServerEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboarding: OnboardingSession,
    private val serverDiscovery: ServerDiscovery
) : ViewModel() {

    data class UiState(
        val serverUrl: String = "",
        val discovering: Boolean = true,
        val discovered: List<ServerConnection> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val resolved: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Discovery was already kicked off during the splash; observe shared results
        // (and ensure it ran, for direct entry to this screen).
        serverDiscovery.ensureStarted()
        viewModelScope.launch {
            serverDiscovery.servers.collect { servers ->
                _state.update { it.copy(discovered = servers) }
            }
        }
        viewModelScope.launch {
            serverDiscovery.discovering.collect { discovering ->
                _state.update { it.copy(discovering = discovering) }
            }
        }
    }

    fun setServerUrl(value: String) = _state.update { it.copy(serverUrl = value, error = null) }

    fun connectManual() {
        val url = _state.value.serverUrl.trim()
        if (url.isEmpty()) return
        resolve(url)
    }

    fun selectDiscovered(server: ServerConnection) = resolve(server.baseUrl)

    /** Clears the one-shot navigate flag after the UI has pushed Login. */
    fun consumeResolved() {
        if (!_state.value.resolved) return
        _state.update { it.copy(resolved = false) }
    }

    private fun resolve(input: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { authRepository.resolveServer(input) }
                .onSuccess { server ->
                    onboarding.pendingServer = server
                    onboarding.prefillUsername = null
                    _state.update { it.copy(loading = false, resolved = true) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message?.takeIf(String::isNotBlank)
                                ?: "Couldn't reach \"$input\""
                        )
                    }
                }
        }
    }
}
