package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import app.picnic.player.data.playback.MediaSegment
import kotlinx.coroutines.delay

enum class Panel { NONE, AUDIO, SUBTITLE, CHAPTERS, SETTINGS }

enum class BackOutcome { Handled, NextUp, ExitPlayer }

enum class PlayerModal { SUBTITLE_ADJUST, REPORT_ISSUE }

enum class PlayerOverlay { NEXT_UP, STILL_WATCHING }

private const val MinOsdHideSeconds = 2L
private const val QuickSkipBurstMs = 1_000L
private const val SkipPillVisibleMs = 10_000L

@Stable
class PlayerChrome(private val scrub: PlayerScrub) {
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

    var modal by mutableStateOf<PlayerModal?>(null)
        private set

    var quickSkipMs by mutableStateOf(0L)
        private set

    var quickSkipVisible by mutableStateOf(false)
        private set

    var quickSkipTick by mutableIntStateOf(0)
        private set

    var skipPillElapsed by mutableFloatStateOf(0f)
        private set

    private var overlay by mutableStateOf<PlayerOverlay?>(null)
    private var segmentActive by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)

    val videoKeysActive: Boolean
        get() = !osdVisible && panel == Panel.NONE && overlay == null && modal == null

    val videoHasFocus: Boolean
        get() = videoKeysActive && !skipPillShowing

    val skipPillShowing: Boolean
        get() = segmentActive &&
            !skipPillDismissed &&
            !osdVisible &&
            panel == Panel.NONE &&
            overlay == null &&
            modal == null

    val skipInOsd: Boolean
        get() = segmentActive && (skipPillDismissed || osdVisible || panel != Panel.NONE || modal != null)

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
        scrub.cancel()
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

    var returningFromSubtitleAdjust by mutableStateOf(false)
        private set

    fun enterModal(target: PlayerModal) {
        modal = target
        hideOsd()
        panel = Panel.NONE
    }

    fun exitModalToSettings() {
        val closed = modal ?: return
        modal = null
        panel = Panel.SETTINGS
        if (closed == PlayerModal.SUBTITLE_ADJUST) returningFromSubtitleAdjust = true
    }

    fun closeModal() {
        modal = null
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
        if (deltaMs == 0L && !quickSkipVisible) return
        quickSkipMs = if (quickSkipVisible) quickSkipMs + deltaMs else deltaMs
        quickSkipVisible = true
        quickSkipTick++
    }

    fun endQuickSkip() {
        quickSkipVisible = false
    }

    fun onOverlayChanged(next: PlayerOverlay?) {
        overlay = next
        // Still Watching has just paused playback itself; ending the scrub normally would resume behind it.
        if (next == PlayerOverlay.STILL_WATCHING) scrub.abandon()
        if (next != null) clearOverlays()
    }

    fun onPipModeChanged(inPip: Boolean) {
        inPictureInPicture = inPip
        if (inPip) clearOverlays()
    }

    fun onBack(): BackOutcome = when {
        modal != null -> {
            exitModalToSettings()
            BackOutcome.Handled
        }
        overlay == PlayerOverlay.STILL_WATCHING -> BackOutcome.ExitPlayer
        overlay == PlayerOverlay.NEXT_UP -> BackOutcome.NextUp
        panel != Panel.NONE -> {
            closePanel()
            BackOutcome.Handled
        }
        osdVisible -> {
            hideOsd()
            BackOutcome.Handled
        }
        skipPillShowing -> {
            skipPillDismissed = true
            BackOutcome.Handled
        }
        else -> BackOutcome.ExitPlayer
    }

    private fun clearOverlays() {
        hideOsd()
        panel = Panel.NONE
    }

    internal suspend fun awaitOsdTimeout(osdHideSeconds: Int) {
        if (!osdVisible || panel != Panel.NONE || scrub.active) return
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
        skipPillElapsed = 0f
        val start = withFrameMillis { it }
        do {
            val elapsedMs = withFrameMillis { it } - start
            skipPillElapsed = (elapsedMs.toFloat() / SkipPillVisibleMs).coerceAtMost(1f)
        } while (elapsedMs < SkipPillVisibleMs)
        dismissSkipPill()
    }
}

@Composable
fun rememberPlayerChrome(
    osdHideSeconds: Int,
    scrub: PlayerScrub,
    isPlaying: Boolean,
    segment: MediaSegment?
): PlayerChrome {
    val chrome = remember { PlayerChrome(scrub) }
    LaunchedEffect(chrome.revealTick, chrome.osdVisible, chrome.panel, isPlaying, scrub.active) {
        chrome.awaitOsdTimeout(osdHideSeconds)
    }
    LaunchedEffect(chrome.quickSkipTick) {
        chrome.awaitQuickSkipEnd()
    }
    LaunchedEffect(segment, chrome.skipPillShowing) {
        chrome.awaitSkipPillTimeout()
    }
    return chrome
}
