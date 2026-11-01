package app.picnic.player.ui.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.syncplay.SyncGroup
import app.picnic.player.data.syncplay.SyncPlayRepository
import app.picnic.player.data.syncplay.SyncPlaySession
import app.picnic.player.di.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.SendCommandType

@HiltViewModel(assistedFactory = LobbyViewModel.Factory::class)
class LobbyViewModel @AssistedInject constructor(
    private val session: SyncPlaySession,
    private val syncPlayRepository: SyncPlayRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @Assisted groupId: String
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(groupId: String): LobbyViewModel
    }

    data class Start(val itemId: UUID, val positionTicks: Long)

    private val id = UUID.fromString(groupId)

    private var leaving = false

    val group: StateFlow<SyncGroup?> = session.group
        .map { it?.takeIf { group -> group.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.group.value?.takeIf { it.id == id })

    private val _item = MutableStateFlow<BaseItemDto?>(null)
    val item = _item.asStateFlow()

    private val _closed = Channel<Unit>(Channel.CONFLATED)
    val closed = _closed.receiveAsFlow()

    val starts: Flow<Start> = session.commands
        .filter { it.type == SendCommandType.UNPAUSE }
        .mapNotNull { command -> group.value?.queue?.let { Start(it.itemId, command.positionTicks ?: it.startTicks) } }

    init {
        viewModelScope.launch {
            group.collect { if (it == null && !leaving) _closed.send(Unit) }
        }
        viewModelScope.launch {
            group.mapNotNull { it?.queue }.distinctUntilChanged().collect { queue ->
                // A member counts as loading until it reports Ready, and the server holds the room for it.
                runCatching {
                    syncPlayRepository.ready(queue.startTicks, false, queue.playlistItemId)
                    syncPlayRepository.groups().firstOrNull { it.groupId == id }?.let(session::onGroupInfo)
                }
                if (_item.value?.id != queue.itemId) {
                    _item.value = try {
                        mediaRepository.item(queue.itemId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }

    fun play() {
        viewModelScope.launch { runCatching { syncPlayRepository.unpause() } }
    }

    /** A new queue at 0 starts by itself once every member reports Ready for it, which each lobby does. */
    fun playFromStart() {
        val queue = group.value?.queue ?: return
        viewModelScope.launch { runCatching { syncPlayRepository.setNewQueue(queue.itemId, 0L) } }
    }

    /** Runs on the app scope: the lobby is popped at once and its own scope goes with it. */
    fun leave() {
        leaving = true
        appScope.launch { runCatching { syncPlayRepository.leave() } }
    }
}
