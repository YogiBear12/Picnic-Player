package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val PulseVisibleMs = 750L

@Stable
class PlayerPulse {
    var visible by mutableStateOf(false)
        private set

    var playing by mutableStateOf(true)
        private set

    internal var tick by mutableIntStateOf(0)
        private set

    fun show(playing: Boolean) {
        this.playing = playing
        tick++
    }

    internal suspend fun flash() {
        if (tick == 0) return
        visible = true
        delay(PulseVisibleMs)
        visible = false
    }
}

@Composable
fun rememberPlayerPulse(): PlayerPulse {
    val pulse = remember { PlayerPulse() }
    LaunchedEffect(pulse.tick) { pulse.flash() }
    return pulse
}
