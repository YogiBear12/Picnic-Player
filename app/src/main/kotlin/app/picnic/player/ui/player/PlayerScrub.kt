package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Stable
class PlayerScrub {
    var active by mutableStateOf(false)
        private set

    var preview by mutableStateOf<TrickplayPreview?>(null)
        private set

    var barBottomInset by mutableStateOf(0.dp)
        private set

    private var resumeWhenDone = false

    fun onActiveChange(scrubbing: Boolean) {
        active = scrubbing
    }

    fun onPreviewChange(next: TrickplayPreview?) {
        preview = next
    }

    fun onBarBottomInset(inset: Dp) {
        barBottomInset = inset
    }

    fun cancel() {
        active = false
        preview = null
    }

    fun clearPreview() {
        preview = null
    }

    internal fun syncPlayback(player: Player) {
        if (active) {
            resumeWhenDone = player.playWhenReady
            player.pause()
        } else if (resumeWhenDone) {
            player.play()
        }
    }
}

@Composable
fun rememberPlayerScrub(player: Player): PlayerScrub {
    val scrub = remember { PlayerScrub() }
    LaunchedEffect(scrub.active) { scrub.syncPlayback(player) }
    return scrub
}
