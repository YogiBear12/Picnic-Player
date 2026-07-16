package app.picnic.player.ui.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import app.picnic.player.playback.DisplayModeCandidate
import app.picnic.player.playback.VideoDisplayHints
import app.picnic.player.playback.coalesceVideoDisplayParams
import app.picnic.player.playback.findBestDisplayMode
import kotlin.math.roundToInt

private const val MIN_DELAY_MS = 2000L

/**
 * Adjusts the TV HDMI display mode from Jellyfin video stream metadata when
 * Match refresh rate / Match resolution are On. ExoPlayer [Format] fills gaps only.
 */
@Composable
fun DisplayModeManager(
    player: Player,
    matchRefreshRate: Boolean,
    matchResolution: Boolean,
    jellyfinHints: VideoDisplayHints? = null
) {
    val activity = LocalActivity.current ?: return
    val displayManager = remember(activity) { activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    DisposableEffect(player, matchRefreshRate, matchResolution, jellyfinHints) {
        if (!matchRefreshRate && !matchResolution) {
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 }
            return@DisposableEffect onDispose {}
        }

        fun applyDisplayMode(format: Format?) {
            val params = coalesceVideoDisplayParams(
                jellyfin = jellyfinHints,
                formatWidth = format?.width ?: 0,
                formatHeight = format?.height ?: 0,
                formatFrameRate = format?.frameRate ?: 0f
            ) ?: return

            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val currentMode = display.mode
            val supportedModes = display.supportedModes
                ?.map { it.toCandidate() }
                .orEmpty()

            val targetMode = findBestDisplayMode(
                supportedModes = supportedModes,
                streamWidth = params.width,
                streamHeight = params.height,
                targetFrameRate = params.frameRate,
                matchRefreshRate = matchRefreshRate,
                matchResolution = matchResolution
            ) ?: return

            if (targetMode.modeId == currentMode.modeId) return

            val isSeamless = isSeamlessSwitch(currentMode, targetMode)

            val displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == display.displayId && !isSeamless) {
                        if (player.isPlaying) {
                            player.pause()
                            Handler(Looper.getMainLooper()).postDelayed({
                                player.play()
                            }, MIN_DELAY_MS)
                        }
                    }
                }
            }

            displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

            activity.window.attributes = activity.window.attributes.apply {
                preferredDisplayModeId = targetMode.modeId
            }

            Handler(Looper.getMainLooper()).postDelayed({
                displayManager.unregisterDisplayListener(displayListener)
            }, 5000)
        }

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                applyDisplayMode(selectedVideoFormat(tracks))
            }
        }

        player.addListener(listener)
        // Apply as soon as Jellyfin hints are known; tracks listener covers Format fallback.
        applyDisplayMode(selectedVideoFormat(player.currentTracks))

        onDispose {
            player.removeListener(listener)
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 0 }
        }
    }
}

private fun selectedVideoFormat(tracks: Tracks): Format? {
    for (group in tracks.groups) {
        if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) {
                    return group.getTrackFormat(i)
                }
            }
        }
    }
    return null
}

private fun Display.Mode.toCandidate(): DisplayModeCandidate = DisplayModeCandidate(
    modeId = modeId,
    width = physicalWidth,
    height = physicalHeight,
    refreshRate = refreshRate
)

private fun isSeamlessSwitch(currentMode: Display.Mode, targetMode: DisplayModeCandidate): Boolean {
    val currentRate = (currentMode.refreshRate * 1000).roundToInt()
    val targetRate = (targetMode.refreshRate * 1000).roundToInt()

    if (targetRate == currentRate) return true

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alts = currentMode.alternativeRefreshRates.map { (it * 1000).roundToInt() }
        if (alts.any { it % targetRate == 0 }) {
            return true
        }
    }
    return false
}
