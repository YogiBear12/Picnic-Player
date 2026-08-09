package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class BlackBarsTest {
    private val black = 0xFF101010.toInt()
    private val grey = 0xFF808080.toInt()

    private fun frame(
        width: Int = 20,
        height: Int = 100,
        topBar: Int,
        bottomBar: Int = topBar,
        picture: Int = grey
    ) = IntArray(width * height) { index ->
        val row = index / width
        if (row < topBar || row >= height - bottomBar) black else picture
    }

    @Test
    fun `a letterboxed frame reports both bars`() {
        val bars = barsInFrame(frame(topBar = 13), width = 20, height = 100)

        assertEquals(0.13f, bars.top, 0.001f)
        assertEquals(0.13f, bars.bottom, 0.001f)
        assertEquals(0.74f, bars.pictureHeight, 0.001f)
    }

    @Test
    fun `a full frame reports no bars`() {
        assertEquals(BlackBars.None, barsInFrame(frame(topBar = 0), width = 20, height = 100))
    }

    @Test
    fun `each edge is counted on its own`() {
        val bars = barsInFrame(frame(topBar = 16, bottomBar = 11), width = 20, height = 100)

        assertEquals(0.16f, bars.top, 0.001f)
        assertEquals(0.11f, bars.bottom, 0.001f)
    }

    @Test
    fun `a row carrying picture ends the bar`() {
        val pixels = frame(topBar = 13)
        pixels[12 * 20 + 10] = grey

        assertEquals(0.12f, barsInFrame(pixels, width = 20, height = 100).top, 0.001f)
    }

    @Test
    fun `a dark row that is not black is picture`() {
        val dim = 0xFF303030.toInt()

        assertEquals(0.13f, barsInFrame(frame(topBar = 13, picture = dim), width = 20, height = 100).top, 0.001f)
    }

    @Test
    fun `the search stops at the widest letterbox there is`() {
        val bars = barsInFrame(IntArray(2000) { black }, width = 20, height = 100)

        assertEquals(0.25f, bars.top, 0.001f)
        assertEquals(0.25f, bars.bottom, 0.001f)
    }

    @Test
    fun `an undersized buffer reports no bars`() {
        assertEquals(BlackBars.None, barsInFrame(IntArray(10), width = 20, height = 100))
        assertEquals(BlackBars.None, barsInFrame(IntArray(0), width = 0, height = 0))
    }
}
