package app.picnic.player.playback

import kotlin.math.roundToInt

/** Platform-agnostic display mode candidate for HDMI mode selection. */
data class DisplayModeCandidate(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float
)

/**
 * Picks the best supported HDMI mode for the playing video stream.
 * [matchRefreshRate] / [matchResolution] gate which dimensions matter; both off → null.
 */
fun findBestDisplayMode(
    supportedModes: List<DisplayModeCandidate>,
    streamWidth: Int,
    streamHeight: Int,
    targetFrameRate: Float,
    matchRefreshRate: Boolean,
    matchResolution: Boolean
): DisplayModeCandidate? {
    if (!matchRefreshRate && !matchResolution) return null
    if (streamWidth <= 0 || streamHeight <= 0 || targetFrameRate <= 0f) return null

    val streamRateRounded = (targetFrameRate * 1000).roundToInt()

    val candidates = supportedModes
        .filterNot { it.height < streamHeight || it.width < streamWidth }
        .sortedWith(
            compareByDescending<DisplayModeCandidate> { it.width * it.height }
                .thenBy { (it.refreshRate * 1000).roundToInt() }
        )

    val validRefreshRates = if (matchRefreshRate) {
        candidates.filter { frameRateMatches((it.refreshRate * 1000).roundToInt(), streamRateRounded) }
    } else {
        candidates
    }

    if (!matchResolution) {
        return validRefreshRates
            .filter { (it.refreshRate * 1000).roundToInt() == streamRateRounded }
            .maxByOrNull { it.width * it.height }
            ?: validRefreshRates.maxByOrNull { it.width * it.height }
    }

    return validRefreshRates.firstOrNull {
        it.width == streamWidth &&
            it.height == streamHeight &&
            (it.refreshRate * 1000).roundToInt() == streamRateRounded
    }
        ?: validRefreshRates.lastOrNull {
            it.width >= streamWidth &&
                it.height >= streamHeight &&
                (it.refreshRate * 1000).roundToInt() == streamRateRounded
        }
        ?: validRefreshRates.lastOrNull {
            it.width == streamWidth &&
                it.height == streamHeight &&
                frameRateMatches((it.refreshRate * 1000).roundToInt(), streamRateRounded)
        }
        ?: validRefreshRates.lastOrNull {
            it.width >= streamWidth &&
                it.height >= streamHeight &&
                frameRateMatches((it.refreshRate * 1000).roundToInt(), streamRateRounded)
        }
}

internal fun frameRateMatches(displayRate: Int, streamRate: Int): Boolean {
    if (streamRate == 0) return false
    return displayRate % streamRate == 0 ||
        // Exact multiple (e.g. 24fps in 120hz)
        displayRate == (streamRate * 2.5).roundToInt() // e.g. 24fps in 60hz (3:2 pulldown)
}
