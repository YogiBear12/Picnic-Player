package app.picnic.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.seerr.SeerrIssue
import app.picnic.player.data.seerr.SeerrIssueDisplay
import app.picnic.player.data.seerr.SeerrRepository
import app.picnic.player.data.socket.ServerMessageBus
import app.picnic.player.data.socket.ServerNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class IssuesViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val serverMessageBus: ServerMessageBus
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val issues: List<SeerrIssueDisplay> = emptyList(),
        val error: String? = null,
        val busyIssueId: Int? = null,
        val selected: SeerrIssueDisplay? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.issues.isEmpty(), error = null) }
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            runCatching { seerrRepository.hydrateIssueDisplays(seerrRepository.refreshIssues()) }
                .onSuccess { issues -> _state.update { it.copy(loading = false, issues = issues, error = null) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Could not load issues") } }
        }
    }

    fun select(row: SeerrIssueDisplay?) {
        _state.update { it.copy(selected = row) }
        val id = row?.issue?.id ?: return
        viewModelScope.launch {
            val full = runCatching { seerrRepository.issue(id) }.getOrNull() ?: return@launch
            _state.update { state ->
                if (state.selected?.issue?.id != id) {
                    state
                } else {
                    state.copy(selected = state.selected.copy(issue = full))
                }
            }
        }
    }

    fun addComment(issueId: Int, message: String) {
        val text = message.trim()
        if (text.isEmpty() || _state.value.busyIssueId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyIssueId = issueId) }
            val updated = runCatching { seerrRepository.addIssueComment(issueId, text) }.getOrNull()
            _state.update { state ->
                state.copy(
                    busyIssueId = null,
                    selected = if (updated != null && state.selected?.issue?.id == issueId) {
                        state.selected.copy(issue = updated)
                    } else {
                        state.selected
                    }
                )
            }
            if (updated == null) {
                serverMessageBus.emit(ServerNotice(text = "Could not post comment"))
            } else {
                refresh()
            }
        }
    }

    fun setResolved(issueId: Int, resolved: Boolean) {
        mutate(issueId) { seerrRepository.setIssueResolved(issueId, resolved) }
    }

    fun delete(issueId: Int) {
        mutate(issueId) {
            seerrRepository.deleteIssue(issueId)
            null
        }
    }

    private fun mutate(issueId: Int, block: suspend () -> SeerrIssue?) {
        if (_state.value.busyIssueId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyIssueId = issueId) }
            val result = runCatching { block() }
            val updated = result.getOrNull()
            _state.update {
                it.copy(
                    busyIssueId = null,
                    selected = when {
                        result.isFailure -> it.selected
                        updated == null -> null
                        it.selected?.issue?.id == issueId -> it.selected.copy(issue = updated)
                        else -> it.selected
                    }
                )
            }
            if (result.isFailure) {
                serverMessageBus.emit(ServerNotice(text = "Could not update issue"))
            } else {
                refresh()
            }
        }
    }
}
