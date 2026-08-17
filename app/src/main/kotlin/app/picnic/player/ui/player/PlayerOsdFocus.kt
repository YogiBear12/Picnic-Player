package app.picnic.player.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester
import app.picnic.player.ui.common.requestFocusWhenAttached

@Stable
class PlayerOsdFocus {
    val video = FocusRequester()
    val audio = FocusRequester()
    val subtitle = FocusRequester()
    val settings = FocusRequester()
    val scrubber = FocusRequester()
    val skipPill = FocusRequester()
    val osdSkip = FocusRequester()

    fun requestVideo() {
        runCatching { video.requestFocus() }
    }

    suspend fun seedFor(lastPanel: Panel) {
        val target = when (lastPanel) {
            Panel.AUDIO -> audio
            Panel.SUBTITLE -> subtitle
            Panel.SETTINGS -> settings
            Panel.CHAPTERS, Panel.NONE -> scrubber
        }
        target.requestFocusWhenAttached()
    }
}
