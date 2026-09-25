package app.picnic.player.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.ServerConnection
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.HomeContentLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Onboarding step 2 — sign in to the [OnboardingSession.pendingServer]
 * via a split-pane chooser: Quick Connect (default) or username + password.
 *
 * Quick Connect initiates automatically and polls in the background, so its code
 * is live the moment the pane is shown. Errors are tracked **per method** so a
 * Quick Connect failure never bleeds onto the password panel (onboarding QC-2).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val homeLoader: HomeContentLoader,
    private val onboarding: OnboardingSession
) : ViewModel() {

    enum class Method { QuickConnect, Password }

    data class UiState(
        val server: ServerConnection? = null,
        val method: Method = Method.QuickConnect,
        val username: String = "",
        val password: String = "",
        val quickConnectCode: String? = null,
        val quickConnectError: String? = null,
        val passwordError: String? = null,
        val loading: Boolean = false,
        val loggedIn: Boolean = false
    ) {
        /** Base URL the QR encodes (the Quick Connect code is appended as ?code= at render). */
        val quickConnectUrl: String?
            get() = server?.baseUrl?.trimEnd('/')?.let { "$it/web/#/quickconnect" }

        /** Plain server URL shown as text under the QR — no path, no code. */
        val serverDisplayUrl: String?
            get() = server?.baseUrl?.trimEnd('/')
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var quickConnectJob: Job? = null

    init {
        val server = onboarding.pendingServer
        val prefill = onboarding.prefillUsername
        _state.update {
            it.copy(
                server = server,
                username = prefill.orEmpty(),
                // A user picked from the Profile Picker lands straight on password.
                method = if (prefill != null) Method.Password else Method.QuickConnect
            )
        }
        if (server != null && prefill == null) startQuickConnect()
    }

    fun selectMethod(method: Method) {
        _state.update { it.copy(method = method) }
        // Lazily start Quick Connect the first time its pane is shown.
        if (method == Method.QuickConnect && quickConnectJob?.isActive != true) startQuickConnect()
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value, passwordError = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, passwordError = null) }

    fun login() {
        val s = _state.value
        val server = s.server ?: return
        _state.update { it.copy(loading = true, passwordError = null) }
        viewModelScope.launch {
            runCatching { authRepository.loginWithPassword(server, s.username.trim(), s.password) }
                .onSuccess(::complete)
                .onFailure { e ->
                    val msg = e.message?.takeIf(String::isNotBlank) ?: "Sign-in failed"
                    _state.update { it.copy(loading = false, passwordError = msg) }
                }
        }
    }

    private fun startQuickConnect() {
        val server = _state.value.server ?: return
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            val request = runCatching { authRepository.startQuickConnect(server) }.getOrElse {
                _state.update { it.copy(quickConnectError = "Quick Connect unavailable") }
                return@launch
            }
            _state.update { it.copy(quickConnectCode = request.code, quickConnectError = null) }
            while (isActive) {
                delay(POLL_MS)
                val approved = runCatching {
                    authRepository.isQuickConnectApproved(server, request.secret)
                }.getOrDefault(false)
                if (approved) {
                    runCatching { authRepository.loginWithQuickConnect(server, request.secret) }
                        .onSuccess(::complete)
                        .onFailure {
                            _state.update {
                                it.copy(quickConnectCode = null, quickConnectError = "Sign-in failed")
                            }
                        }
                    return@launch
                }
            }
        }
    }

    private fun complete(session: UserSession) {
        homeLoader.enter(session)
        quickConnectJob?.cancel()
        onboarding.clear()
        _state.update { it.copy(loading = false, loggedIn = true) }
    }

    override fun onCleared() {
        quickConnectJob?.cancel()
    }

    private companion object {
        const val POLL_MS = 5000L
    }
}
