package app.picnic.player.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Panel { NONE, AUDIO, SUBTITLE, CHAPTERS, SETTINGS }

enum class BackOutcome { Handled, ClosedOsd, NextUp, ExitPlayer }

@Stable
class PlayerChrome {

    var osdVisible by mutableStateOf(false)
        private set

    var panel by mutableStateOf(Panel.NONE)
        private set

    var lastPanel by mutableStateOf(Panel.NONE)
        private set

    var revealTick by mutableIntStateOf(0)
        private set

    var skipPillDismissed by mutableStateOf(false)
        private set

    var subtitleAdjust by mutableStateOf(false)
        private set

    var quickSkipMs by mutableStateOf(0L)
        private set

    var quickSkipVisible by mutableStateOf(false)
        private set

    var quickSkipTick by mutableIntStateOf(0)
        private set

    private var nextUpVisible by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)

    val videoHasFocus: Boolean
        get() = !osdVisible && panel == Panel.NONE && !nextUpVisible && !subtitleAdjust

    fun skipPillShowing(segmentActive: Boolean): Boolean = segmentActive && !skipPillDismissed && !osdVisible && panel == Panel.NONE && !nextUpVisible

    fun skipInOsd(segmentActive: Boolean): Boolean = segmentActive && (skipPillDismissed || osdVisible || panel != Panel.NONE)

    fun reveal() {
        if (inPictureInPicture) return
        osdVisible = true
        revealTick++
        quickSkipVisible = false
        quickSkipMs = 0L
        skipPillDismissed = true
    }

    fun keepAlive() {
        revealTick++
    }

    fun hideOsd() {
        osdVisible = false
    }

    fun openPanel(panel: Panel) {
        this.panel = panel
        lastPanel = panel
        skipPillDismissed = true
    }

    fun closePanel() {
        panel = Panel.NONE
        reveal()
    }

    fun consumeLastPanel() {
        lastPanel = Panel.NONE
    }

    fun enterSubtitleAdjust() {
        subtitleAdjust = true
        osdVisible = false
        panel = Panel.NONE
    }

    var returningFromSubtitleAdjust by mutableStateOf(false)
        private set

    fun exitSubtitleAdjust() {
        subtitleAdjust = false
        panel = Panel.SETTINGS
        returningFromSubtitleAdjust = true
    }

    fun consumeSubtitleAdjustReturn() {
        returningFromSubtitleAdjust = false
    }

    fun onSegmentChanged(segmentActive: Boolean, enteredAtStart: Boolean) {
        skipPillDismissed = when {
            !segmentActive -> false
            osdVisible || panel != Panel.NONE -> true
            else -> !enteredAtStart
        }
    }

    fun dismissSkipPill() {
        skipPillDismissed = true
    }

    fun onQuickSkip(deltaMs: Long) {
        quickSkipMs += deltaMs
        quickSkipVisible = true
        quickSkipTick++
    }

    fun endQuickSkip() {
        quickSkipVisible = false
        quickSkipMs = 0L
    }

    fun onNextUpVisibleChanged(visible: Boolean) {
        nextUpVisible = visible
        if (visible) clearOverlays()
    }

    fun onPipModeChanged(inPip: Boolean) {
        inPictureInPicture = inPip
        if (inPip) clearOverlays()
    }

    fun onBack(segmentActive: Boolean): BackOutcome = when {
        subtitleAdjust -> {
            exitSubtitleAdjust()
            BackOutcome.Handled
        }
        nextUpVisible -> BackOutcome.NextUp
        panel != Panel.NONE -> {
            closePanel()
            BackOutcome.Handled
        }
        osdVisible -> {
            osdVisible = false
            BackOutcome.ClosedOsd
        }
        skipPillShowing(segmentActive) -> {
            skipPillDismissed = true
            BackOutcome.Handled
        }
        else -> BackOutcome.ExitPlayer
    }

    private fun clearOverlays() {
        osdVisible = false
        panel = Panel.NONE
    }
}
