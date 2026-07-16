package app.picnic.player.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.auth.ServerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Return-user Server Picker. Lists onboarded servers in saved order;
 * supports edit-mode reorder/forget. Forgetting the last server returns to the
 * onboarding wizard (Welcome / server entry).
 */
@HiltViewModel
class ServerPickerViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val servers: List<ServerConnection> = emptyList(),
        val loading: Boolean = true,
        val goAddServer: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val servers = authRepository.onboardedServers()
        val ordered = applyPickerOrder(servers, authRepository.serverOrder()) { it.id }
        _state.update { it.copy(servers = ordered, loading = false) }
    }

    fun reorder(serverIds: List<String>) {
        val byId = _state.value.servers.associateBy { it.id }
        _state.update { it.copy(servers = serverIds.mapNotNull { id -> byId[id] }) }
        viewModelScope.launch { authRepository.setServerOrder(serverIds) }
    }

    fun forget(serverId: String) {
        viewModelScope.launch {
            authRepository.forgetServer(serverId)
            load()
            if (_state.value.servers.isEmpty()) _state.update { it.copy(goAddServer = true) }
        }
    }

    fun consumeNav() = _state.update { it.copy(goAddServer = false) }
}
