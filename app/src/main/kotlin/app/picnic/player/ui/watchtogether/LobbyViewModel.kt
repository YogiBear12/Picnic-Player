package app.picnic.player.ui.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.picnic.player.data.auth.AuthRepository
import app.picnic.player.data.jellyfin.JellyfinImages
import app.picnic.player.data.media.MediaRepository
import app.picnic.player.data.socket.AppForegroundState
import app.picnic.player.data.syncplay.SyncGroup
import app.picnic.player.data.syncplay.SyncPlayRepository
import app.picnic.player.data.syncplay.SyncPlaySession
import app.picnic.player.data.syncplay.SyncQueue
import app.picnic.player.di.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
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
    authRepository: AuthRepository,
    private val foreground: AppForegroundState,
    @ApplicationScope private val appScope: CoroutineScope,
    @Assisted groupId: String
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(groupId: String): LobbyViewModel
    }

    data class Start(val itemId: UUID, val playlistItemId: UUID, val positionTicks: Long)

    private val id = UUID.fromString(groupId)

    private var leaving = false
    private var handedOff = false

    // The room reporting below belongs to whoever plays the room; it stops once the player takes over.
    private val roomWork = Job(viewModelScope.coroutineContext[Job])

    val group: StateFlow<SyncGroup?> = session.group
        .map { it?.takeIf { group -> group.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.group.value?.takeIf { it.id == id })

    private val _item = MutableStateFlow<BaseItemDto?>(null)
    val item = _item.asStateFlow()

    val brandingUrl: String? = authRepository.activeSessionFlow.value?.let { JellyfinImages.splashscreen(it) }

    private val pictures = MutableStateFlow<Map<String, String>>(emptyMap())

    val people: StateFlow<LobbyPeople?> = combine(group, pictures) { group, pictures ->
        group?.let { lobbyPeople(it.participants, authRepository.activeSessionFlow.value?.username.orEmpty(), pictures) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _closed = Channel<Unit>(Channel.CONFLATED)
    val closed = _closed.receiveAsFlow()

    private val _starts = Channel<Start>(Channel.CONFLATED)
    val starts: Flow<Start> = _starts.receiveAsFlow().onEach {
        handedOff = true
        roomWork.cancel()
    }

    init {
        viewModelScope.launch { runCatching { syncPlayRepository.memberPictures() }.onSuccess { pictures.value = it } }
        // Subscribed before our Ready goes out: in a playing room the server answers it with Unpause at once.
        viewModelScope.launch {
            session.commands
                .filter { it.type == SendCommandType.UNPAUSE }
                .mapNotNull { command -> group.value?.queue?.let { Start(it.itemId, it.playlistItemId, command.positionTicks ?: it.startTicks) } }
                .collect(_starts::send)
        }
        // After a Seek the server waits for every member's Ready, and a lobby member has nothing to load.
        viewModelScope.launch(roomWork) {
            session.commands.filter { it.type == SendCommandType.SEEK }.collect { command ->
                val queue = group.value?.queue ?: return@collect
                runCatching { syncPlayRepository.ready(command.positionTicks ?: queue.startTicks, false, queue.playlistItemId) }
            }
        }
        viewModelScope.launch {
            group.collect { if (it == null && !leaving) _closed.send(Unit) }
        }
        // The server drops a member once its socket closes in the background, so leave first and close.
        viewModelScope.launch(roomWork) {
            foreground.isVisible.first { !it }
            leave()
            _closed.send(Unit)
        }
        // A socket drop takes this member out of the room on the server, so join again and report Ready.
        viewModelScope.launch(roomWork) {
            session.reconnects.collect {
                try {
                    syncPlayRepository.join(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _closed.send(Unit)
                    return@collect
                }
                session.awaitQueue(id)?.let { reportReady(it) }
            }
        }
        viewModelScope.launch(roomWork) {
            group.mapNotNull { it?.queue }.distinctUntilChanged().collect { queue ->
                coroutineScope {
                    launch { loadItem(queue.itemId) }
                    reportReady(queue)
                }
            }
        }
    }

    private suspend fun loadItem(itemId: UUID) {
        if (_item.value?.id == itemId) return
        _item.value = try {
            mediaRepository.item(itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // A member counts as loading until it reports Ready, and the server holds the room for it.
    private suspend fun reportReady(queue: SyncQueue) {
        runCatching { syncPlayRepository.ready(queue.startTicks, false, queue.playlistItemId) }
    }

    fun play() {
        viewModelScope.launch { runCatching { syncPlayRepository.unpause() } }
    }

    /** A new queue at 0 starts by itself once every member reports Ready for it, which each lobby does. */
    fun playFromStart() {
        val queue = group.value?.queue ?: return
        viewModelScope.launch { runCatching { syncPlayRepository.setNewQueue(queue.entries.map { it.itemId }, 0L) } }
    }

    /** Runs on the app scope: the lobby is popped at once and its own scope goes with it. */
    fun leave() {
        if (leaving) return
        leaving = true
        appScope.launch(start = CoroutineStart.UNDISPATCHED) { runCatching { syncPlayRepository.leave() } }
    }

    /**
     * Any other way off the screen leaves too, including a remote command that covers it; only the hand-off to
     * the player stays. A covered lobby closes when it comes back, as the room is gone.
     */
    fun onScreenGone() {
        if (handedOff) return
        leave()
        roomWork.cancel()
        _closed.trySend(Unit)
    }
}

data class LobbyMember(val name: String, val pictureUrl: String?)

data class LobbyPeople(val members: List<LobbyMember>, val line: String)

internal fun lobbyPeople(participants: List<String>, you: String, pictures: Map<String, String>): LobbyPeople {
    val youIndex = participants.indexOfFirst { it.equals(you, ignoreCase = true) }
    val others = participants.filterIndexed { index, _ -> index != youIndex }
    val ordered = listOfNotNull(participants.getOrNull(youIndex)) + others
    val line = when (others.size) {
        0 -> "Just you"
        1 -> "You and ${others[0]}"
        2 -> "You, ${others[0]} and ${others[1]}"
        else -> "You and ${others.size} others"
    }
    return LobbyPeople(ordered.map { LobbyMember(it, pictures[it.lowercase()]) }, line)
}

private const val WEB_ROOM_SUFFIX = "'s group"

/** Rooms are named by whichever client made them; web's default "X's group" reads as ours. */
internal fun watchPartyName(roomName: String): String = if (roomName.endsWith(WEB_ROOM_SUFFIX)) roomName.removeSuffix(WEB_ROOM_SUFFIX) + WATCH_PARTY_SUFFIX else roomName

internal const val WATCH_PARTY_SUFFIX = "'s watch party"
