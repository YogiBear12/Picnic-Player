package app.picnic.player.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.ProfilePickerLocal
import app.picnic.player.data.auth.ServerConnection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Return-user Profile Picker for one server. Lists the server's public
 * users unioned with locally stored sessions: a profile with a cached token
 * switches session without re-login, anything else routes to Login with the
 * username prefilled. "Add user" goes to Login with a clean slate.
 */
@HiltViewModel(assistedFactory = ProfilePickerViewModel.Factory::class)
class ProfilePickerViewModel @AssistedInject constructor(
    private val authRepository: AuthRepository,
    private val onboarding: OnboardingSession,
    @Assisted private val serverId: String
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(serverId: String): ProfilePickerViewModel
    }

    data class Profile(
        val userId: String,
        val name: String,
        val imageUrl: String?,
        val hasStoredToken: Boolean,
        val authError: String? = null
    )

    data class UiState(
        val serverName: String = "",
        val profiles: List<Profile> = emptyList(),
        /** False once local (disk) picker data has been applied — not network. */
        val loading: Boolean = true,
        val goReady: Boolean = false,
        val goLogin: Boolean = false
    )

    private var server: ServerConnection? = null

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (_state.value.loading) {
                authRepository.warmLocalCache()
                authRepository.peekProfilePickerLocal(serverId)?.let { local ->
                    server = local.server
                    publishProfiles(local)
                }
            }
            refreshPublicUsers()
        }
    }

    private fun buildInitialState(): UiState {
        val local = authRepository.peekProfilePickerLocal(serverId) ?: return UiState()
        server = local.server
        return UiState(
            serverName = local.server.name,
            profiles = mergeProfiles(local, authRepository::peekHasStoredToken),
            loading = false
        )
    }

    /** Refreshes the public-user list from the server; stored sessions stay visible. */
    private suspend fun refreshPublicUsers() {
        val server = server
            ?: authRepository.onboardedServers().firstOrNull { it.id == serverId }
            ?: run {
                _state.update { it.copy(loading = false) }
                return
            }
        this.server = server
        authRepository.setActiveServer(serverId)

        if (_state.value.profiles.isEmpty()) {
            val local = authRepository.peekProfilePickerLocal(serverId)
                ?: ProfilePickerLocal(
                    server = server,
                    stored = authRepository.storedUsers(serverId),
                    cachedPublic = authRepository.cachedPublicUsers(serverId),
                    profileOrder = authRepository.profileOrder(serverId),
                    authErrors = authRepository.sessionAuthErrors(serverId)
                )
            publishProfiles(local)
        }

        val stored = authRepository.storedUsers(serverId)
        val order = authRepository.profileOrder(serverId)
        val authErrors = authRepository.sessionAuthErrors(serverId)
        val public = runCatching { authRepository.publicUsers(server) }
            .getOrDefault(authRepository.cachedPublicUsers(serverId))
        publishProfiles(
            ProfilePickerLocal(
                server = server,
                stored = stored,
                cachedPublic = public,
                profileOrder = order,
                authErrors = authErrors
            )
        )
    }

    private fun publishProfiles(local: ProfilePickerLocal) {
        _state.update {
            it.copy(
                serverName = local.server.name,
                profiles = mergeProfiles(local, authRepository::peekHasStoredToken),
                loading = false
            )
        }
    }

    private fun mergeProfiles(
        local: ProfilePickerLocal,
        hasToken: (serverId: String, userId: String) -> Boolean
    ): List<Profile> {
        val server = local.server
        val stored = local.stored
        val public = local.cachedPublic
        val authErrors = local.authErrors
        val publicById = public.associateBy { it.id }

        val fromStored = stored.map { session ->
            val pub = publicById[session.userId]
            Profile(
                userId = session.userId,
                name = session.username.ifBlank { pub?.name.orEmpty() },
                imageUrl = userImageUrl(server, session.userId, pub?.primaryImageTag),
                hasStoredToken = hasToken(server.id, session.userId),
                authError = authErrors[session.userId]
            )
        }
        val fromPublicOnly = public
            .filter { user -> stored.none { it.userId == user.id } }
            .map { user ->
                Profile(
                    userId = user.id,
                    name = user.name,
                    imageUrl = userImageUrl(server, user.id, user.primaryImageTag),
                    hasStoredToken = hasToken(server.id, user.id),
                    authError = authErrors[user.id]
                )
            }
        return applyPickerOrder(fromStored + fromPublicOnly, local.profileOrder) { it.userId }
    }

    /** Clears the one-shot navigation flags once the screen has acted on them. */
    fun consumeNav() = _state.update { it.copy(goReady = false, goLogin = false) }

    /** "Change server" soft-logs-out the server so cold start shows Server Picker. */
    fun changeServer() {
        viewModelScope.launch { authRepository.clearActiveServer() }
    }

    fun activate(userId: String) {
        _state.value.profiles.firstOrNull { it.userId == userId }?.let { select(it) }
    }

    fun reorder(userIds: List<String>) {
        val byId = _state.value.profiles.associateBy { it.userId }
        val reordered = userIds.mapNotNull { byId[it] }
        _state.update { it.copy(profiles = reordered) }
        viewModelScope.launch { authRepository.setProfileOrder(serverId, userIds) }
    }

    fun forget(userId: String) {
        viewModelScope.launch {
            authRepository.forgetUser(serverId, userId)
            refreshPublicUsers()
        }
    }

    fun select(profile: Profile) {
        val server = server ?: return
        if (profile.hasStoredToken && profile.authError == null) {
            viewModelScope.launch {
                val session = authRepository.useStoredSession(serverId, profile.userId)
                if (session != null) {
                    _state.update { it.copy(goReady = true) }
                } else {
                    routeToLogin(server, profile.name)
                }
            }
        } else {
            routeToLogin(server, profile.name)
        }
    }

    fun addUser() {
        val server = server ?: return
        routeToLogin(server, null)
    }

    private fun routeToLogin(server: ServerConnection, prefillUsername: String?) {
        onboarding.pendingServer = server
        onboarding.prefillUsername = prefillUsername
        _state.update { it.copy(goLogin = true) }
    }

    private fun userImageUrl(server: ServerConnection, userId: String, tag: String?): String {
        val base = "${server.baseUrl.trimEnd('/')}/Users/$userId/Images/Primary?fillWidth=256&quality=90"
        return if (tag != null) "$base&tag=$tag" else base
    }
}
