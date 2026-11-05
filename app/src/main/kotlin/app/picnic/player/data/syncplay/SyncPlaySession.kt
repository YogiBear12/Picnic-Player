package app.picnic.player.data.syncplay

import app.picnic.player.data.socket.Notice
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.GroupUpdate
import org.jellyfin.sdk.model.api.PlayQueueUpdateReason
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayGroupDoesNotExistUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupLeftUpdate
import org.jellyfin.sdk.model.api.SyncPlayLibraryAccessDeniedUpdate
import org.jellyfin.sdk.model.api.SyncPlayNotInGroupUpdate
import org.jellyfin.sdk.model.api.SyncPlayPlayQueueUpdate
import org.jellyfin.sdk.model.api.SyncPlayQueueItem
import org.jellyfin.sdk.model.api.SyncPlayStateUpdate
import org.jellyfin.sdk.model.api.SyncPlayUserJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayUserLeftUpdate

/** The room's queue; [index] is always inside [entries]. */
data class SyncQueue(val entries: List<SyncPlayQueueItem>, val index: Int, val startTicks: Long, val reason: PlayQueueUpdateReason) {
    val current: SyncPlayQueueItem get() = entries[index]
    val itemId: UUID get() = current.itemId
    val playlistItemId: UUID get() = current.playlistItemId
    val next: SyncPlayQueueItem? get() = entries.getOrNull(index + 1)
}

data class SyncGroup(
    val id: UUID,
    val name: String,
    val state: GroupStateType,
    val participants: List<String>,
    val queue: SyncQueue? = null
)

data class SyncCommand(val type: SendCommandType, val whenServerMs: Long, val positionTicks: Long?)

private val NO_ID = UUID(0, 0)

fun reduceGroup(group: SyncGroup?, update: GroupUpdate): SyncGroup? {
    if (update is SyncPlayGroupJoinedUpdate) {
        val info = update.data
        return SyncGroup(info.groupId, info.groupName, info.state, info.participants)
    }
    // These answer our own requests and arrive with an empty group id, so they are not matched by id.
    if (update is SyncPlayNotInGroupUpdate || update is SyncPlayGroupDoesNotExistUpdate || update is SyncPlayLibraryAccessDeniedUpdate) return null
    if (group == null || update.groupId != group.id) return group
    return when (update) {
        is SyncPlayGroupLeftUpdate -> null
        is SyncPlayStateUpdate -> group.copy(state = update.data.state)
        // shortcut: the server lists each user once, so a user's second session leaving drops their name too. Count sessions if that shows up.
        is SyncPlayUserJoinedUpdate -> if (update.data in group.participants) group else group.copy(participants = group.participants + update.data)
        is SyncPlayUserLeftUpdate -> group.copy(participants = group.participants - update.data)
        is SyncPlayPlayQueueUpdate -> {
            val queue = update.data
            val valid = queue.playingItemIndex in queue.playlist.indices
            group.copy(queue = if (valid) SyncQueue(queue.playlist, queue.playingItemIndex, queue.startPositionTicks, queue.reason) else null)
        }
        else -> group
    }
}

fun groupNotice(group: SyncGroup?, update: GroupUpdate): Notice? {
    if (group == null || update.groupId != group.id) return null
    return when (update) {
        is SyncPlayUserJoinedUpdate -> Notice(text = "${update.data} has joined")
        is SyncPlayUserLeftUpdate -> Notice(text = "${update.data} has left")
        else -> null
    }
}

// The server also sends commands with a zero playlist item id (the creator gets a Stop on create) and
// commands for a queue entry this client has moved past; neither applies to what is playing.
fun acceptCommand(group: SyncGroup?, command: SendCommand, nowMs: Long): SyncCommand? {
    val queue = group?.queue ?: return null
    if (command.groupId != group.id || command.playlistItemId == NO_ID || command.playlistItemId != queue.playlistItemId) return null
    return SyncCommand(command.command, command.`when`.epochMsNear(nowMs), command.positionTicks)
}

@Singleton
class SyncPlaySession @Inject constructor() {
    private val _group = MutableStateFlow<SyncGroup?>(null)
    val group: StateFlow<SyncGroup?> = _group.asStateFlow()

    private val _commands = MutableSharedFlow<SyncCommand>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands: SharedFlow<SyncCommand> = _commands.asSharedFlow()

    private val _reconnects = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The socket came back after a drop; the server removed us from the room when it closed. */
    val reconnects: SharedFlow<Unit> = _reconnects.asSharedFlow()

    fun onReconnected() {
        _reconnects.tryEmit(Unit)
    }

    /** A join or create request can return before the socket's GroupJoined, and the lobby needs the group. */
    suspend fun awaitJoined(groupId: UUID): Boolean = withTimeoutOrNull(SERVER_CONFIRM_TIMEOUT_MS) { group.first { it?.id == groupId } } != null

    /** After a re-join the server clears the queue and then restores it, so wait for the restored one. */
    suspend fun awaitQueue(groupId: UUID): SyncQueue? = withTimeoutOrNull(SERVER_CONFIRM_TIMEOUT_MS) { group.first { it?.id == groupId && it.queue != null } }?.queue

    fun onLeft() {
        _group.value = null
    }

    fun onGroupUpdate(update: GroupUpdate) = _group.update { reduceGroup(it, update) }

    fun onCommand(command: SendCommand) {
        acceptCommand(_group.value, command, System.currentTimeMillis())?.let(_commands::tryEmit)
    }

    private companion object {
        const val SERVER_CONFIRM_TIMEOUT_MS = 10_000L
    }
}
