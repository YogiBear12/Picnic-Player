package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.picnic.player.data.playback.MediaSegment
import kotlinx.coroutines.delay

enum class Panel { NONE, AUDIO, SUBTITLE, CHAPTERS, SETTINGS }

enum class BackOutcome { Handled, ClosedOsd, NextUp, ExitPlayer }

private const val MinOsdHideSeconds = 2L
private const val QuickSkipBurstMs = 1_000L
private const val SkipPillVisibleMs = 10_000L

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
    private var segmentActive by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)

    val videoKeysActive: Boolean
        get() = !osdVisible && panel == Panel.NONE && !nextUpVisible && !subtitleAdjust

    val videoHasFocus: Boolean
        get() = videoKeysActive && !skipPillShowing

    val skipPillShowing: Boolean
        get() = segmentActive && !skipPillDismissed && !osdVisible && panel == Panel.NONE && !nextUpVisible

    val skipInOsd: Boolean
        get() = segmentActive && (skipPillDismissed || osdVisible || panel != Panel.NONE)

    fun reveal() {
        if (inPictureInPicture) return
        osdVisible = true
        revealTick++
        quickSkipVisible = false
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
        this.segmentActive = segmentActive
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
        quickSkipMs = if (quickSkipVisible) quickSkipMs + deltaMs else deltaMs
        quickSkipVisible = true
        quickSkipTick++
    }

    fun endQuickSkip() {
        quickSkipVisible = false
    }

    fun onNextUpVisibleChanged(visible: Boolean) {
        nextUpVisible = visible
        if (visible) clearOverlays()
    }

    fun onPipModeChanged(inPip: Boolean) {
        inPictureInPicture = inPip
        if (inPip) clearOverlays()
    }

    fun onBack(): BackOutcome = when {
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
        skipPillShowing -> {
            skipPillDismissed = true
            BackOutcome.Handled
        }
        else -> BackOutcome.ExitPlayer
    }

    private fun clearOverlays() {
        osdVisible = false
        panel = Panel.NONE
    }

    internal suspend fun awaitOsdTimeout(osdHideSeconds: Int, scrubbing: Boolean) {
        if (!osdVisible || panel != Panel.NONE || scrubbing) return
        delay(osdHideSeconds.toLong().coerceAtLeast(MinOsdHideSeconds) * 1000)
        hideOsd()
    }

    internal suspend fun awaitQuickSkipEnd() {
        if (quickSkipTick == 0) return
        delay(QuickSkipBurstMs)
        endQuickSkip()
    }

    internal suspend fun awaitSkipPillTimeout() {
        if (!skipPillShowing) return
        delay(SkipPillVisibleMs)
        dismissSkipPill()
    }
}

@Composable
fun rememberPlayerChrome(
    osdHideSeconds: Int,
    scrubbing: Boolean,
    isPlaying: Boolean,
    segment: MediaSegment?
): PlayerChrome {
    val chrome = remember { PlayerChrome() }
    LaunchedEffect(chrome.revealTick, chrome.osdVisible, chrome.panel, isPlaying, scrubbing) {
        chrome.awaitOsdTimeout(osdHideSeconds, scrubbing)
    }
    LaunchedEffect(chrome.quickSkipTick) {
        chrome.awaitQuickSkipEnd()
    }
    LaunchedEffect(segment, chrome.skipPillShowing) {
        chrome.awaitSkipPillTimeout()
    }
    return chrome
}
