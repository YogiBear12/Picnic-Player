package app.picnic.player.data.socket

import android.util.Log
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.LibraryChange
import app.picnic.player.data.media.LibraryChangeBus
import app.picnic.player.data.playback.PlayerCommand
import app.picnic.player.data.playback.PlayerCommandBus
import app.picnic.player.data.playback.ticksToMs
import app.picnic.player.di.IoDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.RestartRequiredMessage
import org.jellyfin.sdk.model.api.ServerRestartingMessage
import org.jellyfin.sdk.model.api.ServerShuttingDownMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage

/**
 * Session-scoped owner of the Jellyfin websocket.
 *
 * Opens one socket when a session becomes active and tears it down on logout / expiry /
 * session switch, driven by [ActiveSession]. On connect it registers this device as a
 * controllable session ([SessionSocket.registerCapabilities]) and, while the process is
 * RESUMED, fans inbound server pushes out to typed buses
 * that existing screens / the player already consume — so no screen changes are needed for
 * sync, and remote control reaches the player without the socket knowing about it.
 *
 * Stay-in-sync:
 *  - [LibraryChangedMessage] → [LibraryChange.LibraryContentChanged].
 *  - [UserDataChangedMessage], filtered to the current user → [LibraryChange.ItemUpdated].
 *
 * Remote control + lifecycle notices:
 *  - [PlaystateMessage] → [PlayerCommandBus] (pause/seek/next/…) applied to the active player.
 *  - Media-track [GeneralCommandMessage]s (`SetAudio/SubtitleStreamIndex`) → [PlayerCommandBus].
 *  - Display [GeneralCommandMessage]s (`DisplayMessage`/`SendString`) → [ServerMessageBus];
 *  `DisplayContent` and the navigation / D-pad-proxy subset → [RemoteControlBus].
 *  - [PlayMessage] (cast target) → [RemoteControlBus.Play] (PlayNow launches the player).
 *  - Server-lifecycle messages → [ServerMessageBus] transient notice.
 *
 * Volume/mute commands are never advertised and are dropped on receipt (fixed-output TV).
 * Keep-alive and reconnect-with-backoff are the SDK's job.
 */
@Singleton
class WebSocketManager @Inject constructor(
    @ActiveSession private val activeSession: StateFlow<UserSession?>,
    private val sessionSocketFactory: SessionSocketFactory,
    private val foreground: AppForegroundState,
    private val libraryChangeBus: LibraryChangeBus,
    private val playerCommandBus: PlayerCommandBus,
    private val serverMessageBus: ServerMessageBus,
    private val remoteControlBus: RemoteControlBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val started = AtomicBoolean(false)

    /** Begins observing the active session. Idempotent; call once at app start (PicnicApp). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        // collectLatest cancels the prior session's socket when the active user changes
        // (switch or logout) before serving the next — a clean teardown/rebuild per session.
        scope.launch {
            activeSession.collectLatest { session ->
                if (session != null) serveSession(session)
            }
        }
    }

    // Teardown is deterministic via structured cancellation: on logout / session-switch
    // the collectLatest above cancels this whole subtree, so every socket subscription
    // stops. The SDK owns no explicit ApiClient/SocketApi close() — it disconnects the
    // underlying websocket once its subscriber count hits zero — so cancelling the scope
    // is the drop. (Verified by the teardown unit test.)
    private suspend fun serveSession(session: UserSession) {
        val socket = sessionSocketFactory.open(session)
        runCatching { socket.registerCapabilities() }
            .onFailure { Log.w(TAG, "postCapabilities failed for ${session.username}", it) }
        coroutineScope {
            launch { logConnectionState(socket) }
            // Only subscribe while the process is foregrounded; pausing here stops the
            // socket's inbound subscriptions without dropping the session-scoped client.
            foreground.isResumed.collectLatest { resumed ->
                if (!resumed) return@collectLatest
                coroutineScope {
                    // Stay-in-sync.
                    launch { fanOutLibraryChanges(socket) }
                    launch { fanOutUserDataChanges(socket, session.userId) }
                    // Remote control + lifecycle.
                    launch { fanOutPlaystate(socket) }
                    launch { fanOutPlay(socket) }
                    launch { fanOutGeneralCommands(socket) }
                    launch { fanOutServerLifecycle(socket) }
                }
            }
        }
    }

    private suspend fun fanOutLibraryChanges(socket: SessionSocket) {
        socket.messages(LibraryChangedMessage::class).collect { message ->
            val info = message.data ?: return@collect
            val hasChanges = info.itemsAdded.isNotEmpty() ||
                info.itemsRemoved.isNotEmpty() ||
                info.itemsUpdated.isNotEmpty()
            if (hasChanges) libraryChangeBus.emit(LibraryChange.LibraryContentChanged)
        }
    }

    private suspend fun fanOutUserDataChanges(socket: SessionSocket, userId: String) {
        socket.messages(UserDataChangedMessage::class).collect { message ->
            val info = message.data ?: return@collect
            // Ignore other users on a shared server — only our own cross-device changes.
            if (info.userId.toString() != userId) return@collect
            // UserItemDataDto carries no seriesId; emit the plain item update and let the
            // consumers' existing debounce refresh any series/season aggregates.
            info.userDataList.forEach { data ->
                libraryChangeBus.emit(LibraryChange.ItemUpdated(data.itemId.toString()))
            }
        }
    }

    // --- Remote control -------------------------------------------------------------

    /** PlayState remote control → the active player (dropped safely when nothing is playing). */
    private suspend fun fanOutPlaystate(socket: SessionSocket) {
        socket.messages(PlaystateMessage::class).collect { message ->
            val request = message.data ?: return@collect
            val command = when (request.command) {
                PlaystateCommand.STOP -> PlayerCommand.Stop
                PlaystateCommand.PAUSE -> PlayerCommand.Pause
                PlaystateCommand.UNPAUSE -> PlayerCommand.Unpause
                PlaystateCommand.PLAY_PAUSE -> PlayerCommand.PlayPause
                PlaystateCommand.NEXT_TRACK -> PlayerCommand.NextTrack
                PlaystateCommand.PREVIOUS_TRACK -> PlayerCommand.PreviousTrack
                PlaystateCommand.REWIND -> PlayerCommand.Rewind
                PlaystateCommand.FAST_FORWARD -> PlayerCommand.FastForward
                PlaystateCommand.SEEK ->
                    request.seekPositionTicks?.let { PlayerCommand.Seek(it.ticksToMs()) }
            }
            if (command != null) playerCommandBus.emit(command)
        }
    }

    /** Play cast command → launch playback on this TV (PlayNow); enqueue variants fall back to it. */
    private suspend fun fanOutPlay(socket: SessionSocket) {
        socket.messages(PlayMessage::class).collect { message ->
            val request = message.data ?: return@collect
            val itemId = request.itemIds?.firstOrNull()?.toString() ?: return@collect
            // No queue model yet: PlayNext/PlayLast/PlayInstantMix/PlayShuffle all fall back to
            // launching now on this idle TV, which is the useful cast behaviour for v1.
            when (request.playCommand) {
                PlayCommand.PLAY_NOW,
                PlayCommand.PLAY_NEXT,
                PlayCommand.PLAY_LAST,
                PlayCommand.PLAY_INSTANT_MIX,
                PlayCommand.PLAY_SHUFFLE ->
                    remoteControlBus.emit(
                        RemoteControlAction.Play(
                            itemId = itemId,
                            startPositionMs = request.startPositionTicks?.let { it.ticksToMs() }
                        )
                    )
            }
        }
    }

    /**
     * GeneralCommand subset → media-track control, on-screen text, navigation, and the D-pad
     * proxy. Volume/mute is dropped (never advertised). Commands with no sensible TV target are
     * ignored — see [SocketCapabilities] for the skipped list and rationale.
     */
    private suspend fun fanOutGeneralCommands(socket: SessionSocket) {
        socket.messages(GeneralCommandMessage::class).collect { message ->
            val command = message.data ?: return@collect
            val args = command.arguments.orEmpty()
            when (command.name) {
                // Media-track control → active player.
                GeneralCommandType.SET_AUDIO_STREAM_INDEX ->
                    args.intArg(ARG_INDEX)?.let { playerCommandBus.emit(PlayerCommand.SetAudioIndex(it)) }
                GeneralCommandType.SET_SUBTITLE_STREAM_INDEX -> {
                    // A missing or negative index means "no subtitles".
                    val index = args.intArg(ARG_INDEX)?.takeIf { it >= 0 }
                    playerCommandBus.emit(PlayerCommand.SetSubtitleIndex(index))
                }

                // Transient on-screen text.
                GeneralCommandType.DISPLAY_MESSAGE -> {
                    val text = args.arg(ARG_TEXT)
                    if (!text.isNullOrBlank()) {
                        serverMessageBus.emit(ServerNotice(text = text, header = args.arg(ARG_HEADER)))
                    }
                }
                GeneralCommandType.SEND_STRING ->
                    args.arg(ARG_STRING)?.takeIf { it.isNotBlank() }
                        ?.let { serverMessageBus.emit(ServerNotice(text = it)) }

                // Navigation → existing destinations.
                GeneralCommandType.DISPLAY_CONTENT ->
                    args.arg(ARG_ITEM_ID)?.let { remoteControlBus.emit(RemoteControlAction.ShowItem(it)) }
                GeneralCommandType.GO_HOME -> remoteControlBus.emit(RemoteControlAction.GoHome)
                GeneralCommandType.GO_TO_SETTINGS -> remoteControlBus.emit(RemoteControlAction.GoToSettings)

                // Phone-as-remote D-pad proxy.
                GeneralCommandType.MOVE_UP -> dispatchKey(RemoteKey.UP)
                GeneralCommandType.MOVE_DOWN -> dispatchKey(RemoteKey.DOWN)
                GeneralCommandType.MOVE_LEFT -> dispatchKey(RemoteKey.LEFT)
                GeneralCommandType.MOVE_RIGHT -> dispatchKey(RemoteKey.RIGHT)
                GeneralCommandType.SELECT -> dispatchKey(RemoteKey.SELECT)
                GeneralCommandType.BACK -> dispatchKey(RemoteKey.BACK)
                GeneralCommandType.PAGE_UP -> dispatchKey(RemoteKey.PAGE_UP)
                GeneralCommandType.PAGE_DOWN -> dispatchKey(RemoteKey.PAGE_DOWN)

                // Volume/mute is never advertised and explicitly dropped on receipt, plus any
                // other unhandled command (Live TV, screenshots, repeat/shuffle, …).
                else -> Log.d(TAG, "Ignoring unsupported GeneralCommand ${command.name}")
            }
        }
    }

    private fun dispatchKey(key: RemoteKey) = remoteControlBus.emit(RemoteControlAction.DispatchKey(key))

    // --- Server-lifecycle notices ---------------------------------------------------

    private suspend fun fanOutServerLifecycle(socket: SessionSocket) = coroutineScope {
        launch {
            socket.messages(ServerRestartingMessage::class).collect {
                serverMessageBus.emit(ServerNotice(text = "Server is restarting…"))
            }
        }
        launch {
            socket.messages(ServerShuttingDownMessage::class).collect {
                // Pause playback so we don't spin against a server that's going away.
                playerCommandBus.emit(PlayerCommand.Pause)
                serverMessageBus.emit(ServerNotice(text = "Server is shutting down"))
            }
        }
        launch {
            socket.messages(RestartRequiredMessage::class).collect {
                serverMessageBus.emit(ServerNotice(text = "Server restart required to apply changes"))
            }
        }
    }

    private suspend fun logConnectionState(socket: SessionSocket) {
        socket.state.collect { state ->
            when (state) {
                is SocketApiState.Connecting -> Log.d(TAG, "Socket connecting")
                is SocketApiState.Connected -> Log.i(TAG, "Socket connected")
                is SocketApiState.Disconnected -> Log.i(TAG, "Socket disconnected", state.error)
            }
        }
    }

    private companion object {
        const val TAG = "PicnicSocket"

        // GeneralCommand argument keys (Jellyfin server conventions). Looked up
        // case-insensitively via [arg] so casing drift between servers cannot break routing.
        const val ARG_INDEX = "Index"
        const val ARG_TEXT = "Text"
        const val ARG_HEADER = "Header"
        const val ARG_STRING = "String"
        const val ARG_ITEM_ID = "ItemId"
    }
}

/** Case-insensitive argument lookup (server argument-key casing is not guaranteed stable). */
private fun Map<String, String?>.arg(name: String): String? = this[name] ?: entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

private fun Map<String, String?>.intArg(name: String): Int? = arg(name)?.trim()?.toIntOrNull()
