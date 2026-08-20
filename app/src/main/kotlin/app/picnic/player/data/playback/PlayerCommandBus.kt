package app.picnic.player.data.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface PlayerCommand {
    data object Stop : PlayerCommand

    data object Pause : PlayerCommand
    data object Unpause : PlayerCommand
    data object PlayPause : PlayerCommand

    data class Seek(val positionMs: Long) : PlayerCommand

    data object Rewind : PlayerCommand
    data object FastForward : PlayerCommand

    data object NextTrack : PlayerCommand

    data object PreviousTrack : PlayerCommand

    data class SetAudioIndex(val index: Int) : PlayerCommand

    data class SetSubtitleIndex(val index: Int?) : PlayerCommand
}

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
