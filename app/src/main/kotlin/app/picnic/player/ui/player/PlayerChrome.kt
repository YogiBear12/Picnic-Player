package app.picnic.player.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which panel is open over the OSD. Only one can be. */
enum class Panel { NONE, AUDIO, SUBTITLE, CHAPTERS, SETTINGS }

/** What Back did, so the screen can perform the parts only it can (navigation, scrub cancel). */
enum class BackOutcome {
    /** Consumed inside the chrome; nothing else to do. */
    Handled,

    /** The OSD was closed — the screen must also cancel any scrub in progress. */
    ClosedOsd,

    /** The next-up overlay is showing and owns Back. */
    NextUp,

    /** Nothing was open: leave the player. */
    ExitPlayer
}

/**
 * Everything the player draws over the video: the OSD, its panels, the skip pill, the quick-skip
 * indicator and subtitle-delay mode — and, above all, which of them is on screen at once.
 *
 * They are mutually exclusive in ways that are easy to get wrong when each is its own flag: the
 * skip pill only auto-appears while the OSD is down, the OSD and panels must vanish under the
 * next-up overlay, nothing shows in Picture-in-Picture, and Back has to reach them in a fixed
 * order. Callers get named transitions and ask [videoHasFocus] / [skipPillShowing] rather than
 * re-deriving those rules at each call site.
 *
 * Timers, animations and focus stay with the screen: this holds what is showing, not for how long.
 */
@Stable
class PlayerChrome {

    var osdVisible by mutableStateOf(false)
        private set

    var panel by mutableStateOf(Panel.NONE)
        private set

    /** The panel Back just closed, so the screen can restore focus to the button that opened it. */
    var lastPanel by mutableStateOf(Panel.NONE)
        private set

    /** Bumped on every reveal or interaction; the screen keys its inactivity timer on this. */
    var revealTick by mutableIntStateOf(0)
        private set

    /** True once the pill for the current segment has been dismissed, timed out, or never auto-showed. */
    var skipPillDismissed by mutableStateOf(false)
        private set

    var subtitleAdjust by mutableStateOf(false)
        private set

    /** Signed total of the current D-pad seek burst, and whether its indicator is up. */
    var quickSkipMs by mutableStateOf(0L)
        private set

    var quickSkipVisible by mutableStateOf(false)
        private set

    /** Bumped per seek press so the screen can restart the burst timeout. */
    var quickSkipTick by mutableIntStateOf(0)
        private set

    private var nextUpVisible by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)

    /** True when nothing is over the video, so the surface takes focus and D-pad keys drive playback. */
    val videoHasFocus: Boolean
        get() = !osdVisible && panel == Panel.NONE && !nextUpVisible && !subtitleAdjust

    /** The floating skip pill: only while a segment is live and nothing else is over the video. */
    fun skipPillShowing(segmentActive: Boolean): Boolean = segmentActive && !skipPillDismissed && !osdVisible && panel == Panel.NONE && !nextUpVisible

    /** Skip lives inside the OSD whenever the pill is not carrying it. */
    fun skipInOsd(segmentActive: Boolean): Boolean = segmentActive && (skipPillDismissed || osdVisible || panel != Panel.NONE)

    fun reveal() {
        if (inPictureInPicture) return
        osdVisible = true
        revealTick++
        // Opening the OSD ends any quick-skip burst, and takes the skip button over from the pill
        // for the rest of this segment — the pill must not pop back when the OSD times out.
        quickSkipVisible = false
        quickSkipMs = 0L
        skipPillDismissed = true
    }

    /** An interaction inside the OSD: restart the inactivity timer without re-opening anything. */
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

    /** The screen has restored focus for [lastPanel]; don't seed it again on the next reveal. */
    fun consumeLastPanel() {
        lastPanel = Panel.NONE
    }

    fun enterSubtitleAdjust() {
        subtitleAdjust = true
        osdVisible = false
        panel = Panel.NONE
    }

    fun exitSubtitleAdjust() {
        subtitleAdjust = false
        panel = Panel.SETTINGS
    }

    /**
     * A media segment became active (or ended). The pill auto-appears only when playback crossed
     * into the segment naturally with the video clear — entering mid-segment after a seek, or with
     * the OSD already up, leaves skip to the OSD.
     */
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

    /** The next-up overlay owns the screen while it is up; nothing may sit under it. */
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
        // The pill is up with nothing over it: Back dismisses it for this segment rather than
        // leaving the player. Skip still lives in the OSD.
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
