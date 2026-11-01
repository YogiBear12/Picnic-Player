package app.picnic.player.ui.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

interface TransportIntents {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun hold()
    fun endHold()
    fun restore(playing: Boolean)
}

/** The only writer of the player's play state and position; `PlayerTransportGuardTest` enforces it. */
class PlayerTransport(private val player: Player) {
    private val local = object : TransportIntents {
        override fun play() = player.play()
        override fun pause() = player.pause()
        override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
        private var held = false

        override fun hold() {
            if (!player.isPlaying) return
            held = true
            player.pause()
        }

        override fun endHold() {
            if (!held) return
            held = false
            player.play()
        }

        override fun restore(playing: Boolean) {
            player.playWhenReady = playing
        }
    }

    /** In a room, intents go to the room, which drives the player back through the `apply` methods. */
    private var intents: TransportIntents = local

    val playWhenReady: Boolean get() = player.playWhenReady

    fun enterRoom(room: TransportIntents) {
        intents = room
    }

    fun leaveRoom() {
        intents = local
    }

    fun play() = intents.play()

    fun pause() = intents.pause()

    fun togglePlayPause() {
        if (player.playWhenReady) pause() else play()
    }

    fun seekTo(positionMs: Long) = intents.seekTo(positionMs)

    fun hold() = intents.hold()

    fun endHold() = intents.endHold()

    fun applyPlay() = player.play()

    fun applyPause() = player.pause()

    fun applySeekTo(positionMs: Long) = player.seekTo(positionMs)

    fun load(item: MediaItem, startMs: Long) {
        player.setMediaItem(item, startMs)
        player.prepare()
    }

    fun stop() = player.stop()

    fun restore(playing: Boolean) = intents.restore(playing)
}
