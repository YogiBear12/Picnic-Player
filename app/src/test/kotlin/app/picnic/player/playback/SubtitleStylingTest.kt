package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleStylingTest {
    @Test
    fun scaleRgb_holdsBackEveryChannelAndKeepsAlpha() {
        assertEquals(0xFF999999.toInt(), 0xFFFFFFFF.toInt().scaleRgb(0.6f))
        assertEquals(0x80999999.toInt(), 0x80FFFFFF.toInt().scaleRgb(0.6f))
    }

    @Test
    fun scaleRgb_keepsHue() {
        assertEquals(0xFF999900.toInt(), 0xFFFFFF00.toInt().scaleRgb(0.6f))
        assertEquals(0xFF009999.toInt(), 0xFF00FFFF.toInt().scaleRgb(0.6f))
    }

    @Test
    fun scaleRgb_leavesBlackAndFullScaleAlone() {
        assertEquals(0xFF000000.toInt(), 0xFF000000.toInt().scaleRgb(0.6f))
        assertEquals(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt().scaleRgb(1f))
    }
}
