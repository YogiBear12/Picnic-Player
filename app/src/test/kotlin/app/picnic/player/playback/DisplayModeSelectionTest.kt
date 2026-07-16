package app.picnic.player.playback

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDisplayHintsTest {

    @Test
    fun videoDisplayHints_prefersRealFrameRate() {
        val stream = videoStream(
            width = 1920,
            height = 1080,
            realFrameRate = 23.976f,
            averageFrameRate = 24f
        )
        val hints = listOf(stream).videoDisplayHints()
        assertEquals(1920, hints?.width)
        assertEquals(1080, hints?.height)
        assertEquals(23.976f, hints?.frameRate)
    }

    @Test
    fun videoDisplayHints_fallsBackToAverageFrameRate() {
        val stream = videoStream(
            width = 3840,
            height = 2160,
            averageFrameRate = 59.94f
        )
        val hints = listOf(stream).videoDisplayHints()
        assertEquals(3840, hints?.width)
        assertEquals(2160, hints?.height)
        assertEquals(59.94f, hints?.frameRate)
    }

    @Test
    fun videoDisplayHints_ignoresNonVideoStreams() {
        val audio = MediaStream(
            type = MediaStreamType.AUDIO,
            index = 1,
            isInterlaced = false,
            isDefault = false,
            isForced = false,
            isHearingImpaired = false,
            isExternal = false,
            isTextSubtitleStream = false,
            supportsExternalStream = false
        )
        assertNull(listOf(audio).videoDisplayHints())
    }

    @Test
    fun coalesce_prefersJellyfinOverFormat() {
        val jellyfin = VideoDisplayHints(width = 1920, height = 1080, frameRate = 23.976f)
        val resolved = coalesceVideoDisplayParams(
            jellyfin = jellyfin,
            formatWidth = 1280,
            formatHeight = 720,
            formatFrameRate = 30f
        )
        assertEquals(ResolvedVideoDisplayParams(1920, 1080, 23.976f), resolved)
    }

    @Test
    fun coalesce_fillsMissingFrameRateFromFormat() {
        val jellyfin = VideoDisplayHints(width = 1920, height = 1080, frameRate = null)
        val resolved = coalesceVideoDisplayParams(
            jellyfin = jellyfin,
            formatWidth = 0,
            formatHeight = 0,
            formatFrameRate = 24f
        )
        assertEquals(ResolvedVideoDisplayParams(1920, 1080, 24f), resolved)
    }

    @Test
    fun coalesce_returnsNullWhenFrameRateStillMissing() {
        assertNull(
            coalesceVideoDisplayParams(
                jellyfin = VideoDisplayHints(width = 1920, height = 1080),
                formatWidth = 0,
                formatHeight = 0,
                formatFrameRate = 0f
            )
        )
    }

    @Test
    fun hasAny_falseWhenEmpty() {
        assertFalse(VideoDisplayHints().hasAny)
        assertTrue(VideoDisplayHints(frameRate = 24f).hasAny)
    }
}

class DisplayModeSelectionTest {

    private val modes = listOf(
        DisplayModeCandidate(1, 1920, 1080, 60f),
        DisplayModeCandidate(2, 1920, 1080, 24f),
        DisplayModeCandidate(3, 3840, 2160, 24f),
        DisplayModeCandidate(4, 3840, 2160, 60f)
    )

    @Test
    fun findBest_matchRefreshRateOnly_picksHighestResAtExactRate() {
        val best = findBestDisplayMode(
            supportedModes = modes,
            streamWidth = 1920,
            streamHeight = 1080,
            targetFrameRate = 24f,
            matchRefreshRate = true,
            matchResolution = false
        )
        assertNotNull(best)
        assertEquals(3, best!!.modeId)
        assertEquals(24f, best.refreshRate)
    }

    @Test
    fun findBest_matchResolution_prefersExactMode() {
        val best = findBestDisplayMode(
            supportedModes = modes,
            streamWidth = 1920,
            streamHeight = 1080,
            targetFrameRate = 24f,
            matchRefreshRate = true,
            matchResolution = true
        )
        assertEquals(2, best?.modeId)
    }

    @Test
    fun findBest_bothOff_returnsNull() {
        assertNull(
            findBestDisplayMode(
                supportedModes = modes,
                streamWidth = 1920,
                streamHeight = 1080,
                targetFrameRate = 24f,
                matchRefreshRate = false,
                matchResolution = false
            )
        )
    }

    @Test
    fun frameRateMatches_allows120For24() {
        assertTrue(frameRateMatches(120_000, 24_000))
        assertTrue(frameRateMatches(60_000, 24_000)) // 2.5x pulldown
        assertFalse(frameRateMatches(50_000, 24_000))
    }
}

private fun videoStream(
    width: Int? = null,
    height: Int? = null,
    realFrameRate: Float? = null,
    averageFrameRate: Float? = null
): MediaStream = MediaStream(
    type = MediaStreamType.VIDEO,
    index = 0,
    width = width,
    height = height,
    realFrameRate = realFrameRate,
    averageFrameRate = averageFrameRate,
    isInterlaced = false,
    isDefault = true,
    isForced = false,
    isHearingImpaired = false,
    isExternal = false,
    isTextSubtitleStream = false,
    supportsExternalStream = false
)
