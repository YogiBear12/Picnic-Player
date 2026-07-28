package app.picnic.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapSubtitlesTest {

    // --- recovering the plane ---------------------------------------------------

    @Test
    fun planeIsRecoveredFromTheCueFractions() {
        // A 1600x120 bitmap covering 1600/1920 of the width and 120/1080 of the height.
        val plane = subtitlePlaneSize(1600, 120, 1600f / 1920f, 120f / 1080f)
        assertEquals(1920f, plane!!.width, 0.5f)
        assertEquals(1080f, plane.height, 0.5f)
    }

    @Test
    fun croppedPlaneIsRecoveredFromTheCueFractions() {
        val plane = subtitlePlaneSize(1600, 120, 1600f / 1920f, 120f / 804f)
        assertEquals(1920f, plane!!.width, 0.5f)
        assertEquals(804f, plane.height, 0.5f)
    }

    @Test
    fun unsetFractionsHaveNoPlane() {
        assertNull(subtitlePlaneSize(1600, 120, -Float.MAX_VALUE, 120f / 1080f))
        assertNull(subtitlePlaneSize(1600, 120, 1600f / 1920f, 0f))
    }

    // --- choosing what to follow ------------------------------------------------

    @Test
    fun planeMatchingTheVideoFollowsIt() {
        assertTrue(bitmapSubtitlesFollowVideo(planeAspect = 16f / 9f, videoAspect = 16f / 9f))
        // A cropped 2.39:1 plane over the same cropped video.
        assertTrue(bitmapSubtitlesFollowVideo(planeAspect = 1920f / 804f, videoAspect = 1920f / 804f))
    }

    @Test
    fun planeCutToADifferentRoundingStillCountsAsAMatch() {
        // The same 2.39:1 crop, plane rounded to mod-4 and the video to mod-16.
        assertTrue(bitmapSubtitlesFollowVideo(planeAspect = 1920f / 804f, videoAspect = 1920f / 800f))
        assertTrue(bitmapSubtitlesFollowVideo(planeAspect = 1920f / 800f, videoAspect = 1920f / 808f))
    }

    @Test
    fun uncroppedPlaneOverCroppedVideoFollowsTheScreen() {
        assertFalse(bitmapSubtitlesFollowVideo(planeAspect = 16f / 9f, videoAspect = 1920f / 804f))
    }

    @Test
    fun neighbouringReleaseRatiosAreNotConfused() {
        // A 16:9 plane over a 1.85:1 crop is a real mismatch, not rounding.
        assertFalse(bitmapSubtitlesFollowVideo(planeAspect = 16f / 9f, videoAspect = 1920f / 1038f))
    }

    @Test
    fun squarePlaneAlwaysFollowsTheVideo() {
        assertTrue(bitmapSubtitlesFollowVideo(planeAspect = 4f / 3f, videoAspect = 16f / 9f))
        assertTrue(bitmapSubtitlesFollowVideo(planeAspect = 720f / 576f, videoAspect = 16f / 9f))
    }
}
