package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleStylingTest {

    @Test
    fun matchingAspect_noScaling() {
        assertEquals(1f, subtitleTextSizeScale(16f / 9f, 16f / 9f), 0.001f)
    }

    @Test
    fun letterboxed_scalesUpByTheLostHeight() {
        // 2.39:1 in a 16:9 container fills 74.4% of the height; text scales back to full size.
        assertEquals(1.344f, subtitleTextSizeScale(2.39f, 16f / 9f), 0.001f)
    }

    @Test
    fun pillarboxed_noScaling() {
        // 4:3 already fills the container height.
        assertEquals(1f, subtitleTextSizeScale(4f / 3f, 16f / 9f), 0.001f)
    }

    @Test
    fun unknownVideoSize_noScaling() {
        assertEquals(1f, subtitleTextSizeScale(null, 16f / 9f), 0.001f)
        assertEquals(1f, subtitleTextSizeScale(2.39f, 0f), 0.001f)
    }
}
