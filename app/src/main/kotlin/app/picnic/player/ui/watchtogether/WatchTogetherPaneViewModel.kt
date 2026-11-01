package app.picnic.player.ui.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.syncplay.SyncPlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.GroupInfoDto

@HiltViewModel
class WatchTogetherPaneViewModel @Inject constructor(
    private val syncPlayRepository: SyncPlayRepository
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val rooms: List<GroupInfoDto> = emptyList(),
        val joining: UUID? = null,
        val failedJoin: GroupInfoDto? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var work: Job? = null

    fun load() {
        work?.cancel()
        _state.update { it.copy(loading = true, joining = null) }
        work = viewModelScope.launch { refresh() }
    }

    fun retry() {
        _state.value.failedJoin?.let(::join) ?: load()
    }

    fun join(room: GroupInfoDto) {
        if (_state.value.joining != null) return
        work?.cancel()
        _state.update { it.copy(joining = room.groupId, failedJoin = null) }
        work = viewModelScope.launch {
            try {
                syncPlayRepository.join(room.groupId)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update {
                    it.copy(loading = false, joining = null, failedJoin = room, error = "Couldn't join ${room.groupName.orEmpty()}")
                }
            }
        }
    }

    private suspend fun refresh() {
        try {
            val rooms = syncPlayRepository.groups()
            _state.update { UiState(loading = false, rooms = rooms) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { UiState(loading = false, error = "Couldn't load rooms") }
        }
    }
}
