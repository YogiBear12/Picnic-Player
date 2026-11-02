package app.picnic.player.ui.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.syncplay.SyncPlayRepository
import app.picnic.player.data.syncplay.SyncPlaySession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.GroupInfoDto

@HiltViewModel
class WatchTogetherPaneViewModel @Inject constructor(
    private val syncPlayRepository: SyncPlayRepository,
    private val syncPlaySession: SyncPlaySession
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val rooms: List<GroupInfoDto> = emptyList(),
        val joining: UUID? = null,
        val failedJoin: GroupInfoDto? = null
    ) {
        /** A join in flight or a failed join's Retry owns the screen; a poll must not replace either. */
        val pollable: Boolean get() = joining == null && failedJoin == null

        fun withRooms(rooms: List<GroupInfoDto>): UiState = if (pollable) copy(loading = false, error = null, rooms = rooms) else this

        /** Only a fetch the user is waiting on shows the error; a failed background poll keeps what is on screen. */
        fun withLoadFailure(): UiState = if (loading) copy(loading = false, error = LoadFailedMessage) else this

        internal val content: PaneContent
            get() = when {
                loading && rooms.isEmpty() -> PaneContent.Loading
                error != null -> PaneContent.Error
                rooms.isEmpty() -> PaneContent.Empty
                else -> PaneContent.Rooms
            }

        /** Removing a focused node drops focus to an arbitrary fallback, often the nav panel, so such a swap parks focus first. */
        internal fun dropsFocus(next: UiState, focusedRoom: UUID?): Boolean = content != next.content || content == PaneContent.Rooms && next.rooms.none { it.groupId == focusedRoom }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var work: Job? = null

    private val _joined = Channel<UUID>(Channel.BUFFERED)
    val joined = _joined.receiveAsFlow()

    fun load() {
        work?.cancel()
        _state.update { it.copy(loading = true, joining = null) }
        work = viewModelScope.launch { refresh() }
    }

    /** The server pushes no list changes to a user outside a group, so the list is polled. */
    suspend fun poll() {
        while (true) {
            if (_state.value.pollable) refresh()
            delay(PollInterval)
        }
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
                check(syncPlaySession.awaitJoined(room.groupId))
                _state.update { it.copy(joining = null) }
                _joined.send(room.groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The server may have made us a member before the failure; leaving outside a room is harmless.
                runCatching { syncPlayRepository.leave() }
                _state.update {
                    it.copy(loading = false, joining = null, failedJoin = room, error = "Couldn't join ${watchPartyName(room.groupName.orEmpty())}")
                }
            }
        }
    }

    private suspend fun refresh() {
        try {
            val rooms = syncPlayRepository.groups()
            _state.update { it.withRooms(rooms) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.withLoadFailure() }
        }
    }
}

internal enum class PaneContent { Loading, Error, Empty, Rooms }

private val PollInterval = 10.seconds
private const val LoadFailedMessage = "Couldn't load watch parties"
