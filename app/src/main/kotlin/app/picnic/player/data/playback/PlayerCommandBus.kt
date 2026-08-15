package app.picnic.player.data.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A remote-control command aimed at the **active** player.
 *
 * The websocket owner ([app.picnic.player.data.socket.WebSocketManager]) maps inbound
 * `PlaystateMessage` / audio-subtitle `GeneralCommand`s onto these and emits them here; the
 * currently-composed `PlayerViewModel` is the sole collector and applies them to its media3
 * player. When nothing is playing there is no collector, so commands are dropped safely —
 * exactly the "only actionable while our player is foregrounded" rule from the plan.
 *
 * Volume/mute is deliberately absent: TV audio is fixed-output, so those commands are neither
 * advertised nor modelled here (see [app.picnic.player.data.socket.SocketCapabilities]).
 */
sealed interface PlayerCommand {
    /** Stop playback and leave the player (returns to browse). */
    data object Stop : PlayerCommand

    data object Pause : PlayerCommand
    data object Unpause : PlayerCommand
    data object PlayPause : PlayerCommand

    /** Seek to an absolute position, already converted from ticks to milliseconds. */
    data class Seek(val positionMs: Long) : PlayerCommand

    /** Jog backward/forward by the user's configured skip step. */
    data object Rewind : PlayerCommand
    data object FastForward : PlayerCommand

    /** Advance to the next episode (next-up), if one is known. */
    data object NextTrack : PlayerCommand

    /** Go to the previous track — no history/queue model on a single-item player (ignored). */
    data object PreviousTrack : PlayerCommand

    /** Switch the active audio track by Jellyfin stream index. */
    data class SetAudioIndex(val index: Int) : PlayerCommand

    /** Switch the active subtitle track by Jellyfin stream index; null disables subtitles. */
    data class SetSubtitleIndex(val index: Int?) : PlayerCommand
}

/**
 * Singleton fan-in for [PlayerCommand]s, mirroring
 * [LibraryChangeBus][app.picnic.player.data.media.LibraryChangeBus]: a buffered
 * `MutableSharedFlow` with `tryEmit`, no replay (a late-arriving player must not replay a
 * stale pause/seek).
 */
@Singleton
class PlayerCommandBus @Inject constructor() {
    private val _commands = MutableSharedFlow<PlayerCommand>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands: SharedFlow<PlayerCommand> = _commands.asSharedFlow()

    fun emit(command: PlayerCommand) {
        _commands.tryEmit(command)
    }
}
