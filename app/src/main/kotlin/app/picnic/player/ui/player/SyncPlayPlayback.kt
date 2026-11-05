package app.picnic.player.ui.player

import android.util.Log
import androidx.media3.common.Player
import app.picnic.player.data.playback.msToTicks
import app.picnic.player.data.playback.ticksToMs
import app.picnic.player.data.syncplay.SyncCommand
import app.picnic.player.data.syncplay.SyncPlayRoomApi
import app.picnic.player.data.syncplay.SyncPlaySession
import app.picnic.player.di.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.PlayQueueUpdateReason
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayQueueItem

private const val TAG = "PicnicSyncPlay"
private const val STALL_REPORT_MS = 3_000L
private const val LANDING_TIMEOUT_MS = 2_500L
private const val LANDING_POLL_MS = 50L
private const val SEEK_SETTLE_MS = 400L
private const val IN_STEP_MS = 400L
private const val STILL_WAITING_MS = 10_000L

const val STILL_WAITING_NOTICE = "Still waiting for someone - press Play to start without them"

// The server repeats a command to correct a member, so a repeat only matters when the player disagrees with it.
internal fun isRedundant(command: SyncCommand, last: SyncCommand?, playWhenReady: Boolean, positionMs: Long): Boolean {
    if (command != last) return false
    return when (command.type) {
        SendCommandType.UNPAUSE -> playWhenReady
        SendCommandType.PAUSE, SendCommandType.STOP -> !playWhenReady
        SendCommandType.SEEK -> !playWhenReady && command.positionTicks?.let { abs(positionMs - it.ticksToMs()) <= IN_STEP_MS } == true
    }
}

class SyncPlayPlayback @AssistedInject constructor(
    @Assisted private val groupId: UUID,
    @Assisted private val player: Player,
    @Assisted private val transport: PlayerTransport,
    @Assisted parentScope: CoroutineScope,
    @Assisted private val onNotice: (String) -> Unit,
    @Assisted private val onLeft: () -> Unit,
    @Assisted private val onItemChanged: (SyncPlayQueueItem) -> Unit,
    @Assisted("queueReset") private val onQueueReset: () -> Unit,
    private val session: SyncPlaySession,
    private val server: SyncPlayRoomApi,
    @ApplicationScope private val appScope: CoroutineScope
) : TransportIntents {
    @AssistedFactory
    interface Factory {
        fun create(
            groupId: UUID,
            player: Player,
            transport: PlayerTransport,
            parentScope: CoroutineScope,
            onNotice: (String) -> Unit,
            onLeft: () -> Unit,
            onItemChanged: (SyncPlayQueueItem) -> Unit,
            @Assisted("queueReset") onQueueReset: () -> Unit
        ): SyncPlayPlayback
    }

    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)

    // One sender keeps requests in the order they were made; the server's state machine depends on it.
    private val requests = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private var lastCommand: SyncCommand? = null
    private var reportedBuffering = false
    private var stall: Job? = null
    private var pendingSeekTicks: Long? = null
    private var seekSettle: Job? = null
    private var stillWaiting: Job? = null
    private var holding = false
    private val playing: UUID? = session.group.value?.queue?.playlistItemId
    private var resetting = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                // A reload negotiates its stream while idle, so that counts toward the stall too.
                Player.STATE_BUFFERING, Player.STATE_IDLE -> if (stall?.isActive != true) {
                    stall = scope.launch {
                        delay(STALL_REPORT_MS)
                        transport.applyPause()
                        reportBuffering()
                    }
                }
                Player.STATE_READY -> {
                    stall?.cancel()
                    if (reportedBuffering) reportReady()
                }
            }
        }
    }

    fun start() {
        transport.enterRoom(this)
        player.addListener(listener)
        scope.launch { for (request in requests) runCatching { request() } }
        scope.launch { server.keepClockInSync() }
        scope.launch {
            // Repeats are dropped before collectLatest, or a repeat would cancel the landing it duplicates.
            session.commands
                .filter { !isRedundant(it, lastCommand, player.playWhenReady, player.currentPosition) }
                .onEach { lastCommand = it }
                .collectLatest {
                    server.awaitClockSynced()
                    apply(it)
                }
        }
        scope.launch { session.reconnects.collect { rejoin() } }
        scope.launch {
            session.group.first { it?.id != groupId }
            detach()
        }
        scope.launch { followQueue() }
        // The room is already playing when the player opens; Buffering holds it until this player has loaded.
        scope.launch {
            server.awaitClockSynced()
            reportBuffering()
            if (player.playbackState == Player.STATE_READY) reportReady()
        }
    }

    val nextItemId: UUID? get() = session.group.value?.queue?.next?.itemId

    // Names the entry this player opened on: the server ignores one it has already left, so two Next Ups can't skip twice.
    fun requestNext() {
        val entry = playing ?: return
        requests.trySend { server.nextItem(entry) }
    }

    /** Puts the room back on its first file, paused, which is what sends every member to the lobby. */
    fun finishQueue(onFailed: () -> Unit) {
        val itemIds = session.group.value?.queue?.entries?.map { it.itemId } ?: return onQueueReset()
        resetting = true
        // The lobby's Ready waits for the Pause, or the room could count every member Ready and start file 1 again.
        appScope.launch {
            val reset = runCatching { server.setNewQueue(itemIds, 0L) }.isSuccess
            // A failed reset leaves the room on this file, where the lobby's Ready would only reopen the player.
            if (!reset && session.group.value?.queue?.playlistItemId == playing) return@launch onFailed()
            runCatching { server.pause() }
            onQueueReset()
        }
    }

    // A re-join empties the queue before the server restores the same entries, so only a different entry counts.
    private suspend fun followQueue() {
        val queue = session.group.first { it?.id == groupId && it.queue != null && it.queue.playlistItemId != playing }?.queue ?: return
        player.removeListener(listener)
        when {
            resetting -> Unit
            queue.reason == PlayQueueUpdateReason.NEW_PLAYLIST -> onQueueReset()
            else -> onItemChanged(queue.entries[queue.index])
        }
        scope.cancel()
    }

    override fun play() {
        flushSeek()
        requests.trySend { server.unpause() }
    }

    override fun pause() {
        flushSeek()
        requests.trySend { server.pause() }
    }

    // Seeks land locally at once so a quick-skip burst stacks from its last target; the room gets the final one.
    override fun seekTo(positionMs: Long) {
        transport.applySeekTo(positionMs)
        pendingSeekTicks = positionMs.msToTicks()
        seekSettle?.cancel()
        seekSettle = scope.launch {
            delay(SEEK_SETTLE_MS)
            flushSeek()
        }
    }

    override fun hold() {
        holding = true
        transport.applyPause()
        reportBuffering()
    }

    override fun endHold() {
        holding = false
        if (reportedBuffering && player.playbackState == Player.STATE_READY) reportReady()
    }

    // The room owns play state, so a stream reload keeps whatever the room last applied.
    override fun restore(playing: Boolean) = Unit

    private fun flushSeek() {
        val ticks = pendingSeekTicks ?: return
        pendingSeekTicks = null
        requests.trySend { server.seek(ticks) }
    }

    private suspend fun apply(command: SyncCommand) {
        val atMs = server.toLocalMs(command.whenServerMs)
        delay(atMs - System.currentTimeMillis())
        val targetMs = command.positionTicks?.ticksToMs()
        Log.d(TAG, "apply ${command.type} target=$targetMs at=${player.currentPosition} late=${System.currentTimeMillis() - atMs}")
        when (command.type) {
            SendCommandType.UNPAUSE -> {
                if (targetMs != null) seekIfOff(targetMs + (System.currentTimeMillis() - atMs).coerceAtLeast(0))
                transport.applyPlay()
            }
            SendCommandType.PAUSE -> {
                transport.applyPause()
                targetMs?.let(::seekIfOff)
            }
            SendCommandType.SEEK -> {
                transport.applyPause()
                targetMs?.let(transport::applySeekTo)
                // The server keeps re-sending Seek while a member's Ready is off the target, so wait for the landing.
                withTimeoutOrNull(LANDING_TIMEOUT_MS) {
                    while (player.playbackState != Player.STATE_READY) delay(LANDING_POLL_MS)
                }
                reportReady()
            }
            SendCommandType.STOP -> transport.applyPause()
        }
    }

    private fun seekIfOff(targetMs: Long) {
        if (abs(player.currentPosition - targetMs) > IN_STEP_MS) transport.applySeekTo(targetMs)
    }

    private fun reportBuffering() {
        if (reportedBuffering) return
        val entry = session.group.value?.queue?.playlistItemId ?: return
        reportedBuffering = true
        val ticks = player.currentPosition.msToTicks()
        Log.d(TAG, "buffering at=${player.currentPosition}")
        requests.trySend { server.buffering(ticks, false, entry) }
    }

    // While the display switches, only endHold may release the room; a Seek landing or a READY waits for it.
    private fun reportReady() {
        if (holding) return
        reportedBuffering = false
        val entry = session.group.value?.queue?.playlistItemId ?: return
        val ticks = player.currentPosition.msToTicks()
        val playing = player.isPlaying
        Log.d(TAG, "ready at=${player.currentPosition} playing=$playing")
        requests.trySend { server.ready(ticks, playing, entry) }
        stillWaiting?.cancel()
        // The server answers a Ready with "pause when ready" while others load, so only the room state shows it is stuck.
        stillWaiting = scope.launch {
            delay(STILL_WAITING_MS)
            if (!reportedBuffering && session.group.value?.state == GroupStateType.WAITING) onNotice(STILL_WAITING_NOTICE)
        }
    }

    private suspend fun rejoin() {
        if (runCatching { server.join(groupId) }.isFailure) return
        session.awaitQueue(groupId) ?: return
        transport.applyPause()
        reportReady()
    }

    /** Runs on the app scope: the player is closing and its own scope goes with it. */
    fun leave() {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) { runCatching { server.leave() } }
    }

    private fun detach() {
        transport.leaveRoom()
        player.removeListener(listener)
        scope.cancel()
        onLeft()
    }
}
