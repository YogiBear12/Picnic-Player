package app.picnic.player.ui.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** The only writer of the player's play state and position; `PlayerTransportGuardTest` enforces it. */
class PlayerTransport(private val player: Player) {
    val playWhenReady: Boolean get() = player.playWhenReady

    fun play() = player.play()

    fun pause() = player.pause()

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun hold() = player.pause()

    fun endHold() = player.play()

    fun load(item: MediaItem, startMs: Long) {
        player.setMediaItem(item, startMs)
        player.prepare()
    }

    fun stop() = player.stop()

    fun restore(playing: Boolean) {
        player.playWhenReady = playing
    }
}
