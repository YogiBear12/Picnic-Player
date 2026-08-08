package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlackBarsTest {

    /** A frame of [rows] rows, [bar] of them black at each edge and the rest mid-grey. */
    private fun frame(rows: Int, bar: Int, content: Float = 128f) = FloatArray(rows) {
        if (it < bar || it >= rows - bar) 2f else content
    }

    @Test
    fun letterboxedFrame_reportsBothBars() {
        val bars = barsInFrame(frame(rows = 100, bar = 13))
        assertEquals(0.13f, bars!!.top, 0.001f)
        assertEquals(0.13f, bars.bottom, 0.001f)
        assertEquals(0.74f, bars.pictureHeight, 0.001f)
    }

    @Test
    fun fullFrame_reportsNoBars() {
        val bars = barsInFrame(frame(rows = 100, bar = 0))
        assertEquals(0f, bars!!.top, 0.001f)
        assertEquals(0f, bars.bottom, 0.001f)
    }

    @Test
    fun frameWithNoPicture_isRejected() {
        assertNull(barsInFrame(FloatArray(100) { 2f }))
        assertNull(barsInFrame(FloatArray(0)))
    }

    @Test
    fun nearlyBlackFrame_isRejectedRatherThanReadAsOneHugeBar() {
        // A fade caught mid-way: every row is dark, none of it is picture.
        assertNull(barsInFrame(FloatArray(100) { 20f }))
    }

    @Test
    fun onlyTheBottomBar_isMeasuredIndependently() {
        val rows = FloatArray(100) { if (it >= 90) 2f else 128f }
        val bars = barsInFrame(rows)
        assertEquals(0f, bars!!.top, 0.001f)
        assertEquals(0.10f, bars.bottom, 0.001f)
    }

    @Test
    fun mergeTakesTheSmallestBarSeen() {
        val bars = mergeBars(
            listOf(
                BlackBars(0.13f, 0.13f),
                BlackBars(0.20f, 0.20f),
                BlackBars(0.13f, 0.13f),
                BlackBars(0.13f, 0.13f)
            )
        )
        assertEquals(0.13f, bars.top, 0.001f)
        assertEquals(0.13f, bars.bottom, 0.001f)
    }

    @Test
    fun oneFullFrameScene_disablesTheBarsForTheWholeItem() {
        // The IMAX case: a film that ever fills the frame reports no bars at all.
        val bars = mergeBars(
            listOf(
                BlackBars(0.13f, 0.13f),
                BlackBars(0.13f, 0.13f),
                BlackBars(0f, 0f),
                BlackBars(0.13f, 0.13f)
            )
        )
        assertEquals(BlackBars.None, bars)
    }

    @Test
    fun tooFewSamples_reportNothing() {
        assertEquals(BlackBars.None, mergeBars(listOf(BlackBars(0.13f, 0.13f))))
        assertEquals(BlackBars.None, mergeBars(emptyList()))
    }

    @Test
    fun implausiblyWideBars_areDiscarded() {
        val bars = List(4) { BlackBars(0.4f, 0.4f) }
        assertEquals(BlackBars.None, mergeBars(bars))
    }

    @Test
    fun barsTooSmallToBeLetterboxing_readAsNone() {
        val bars = List(4) { BlackBars(0.005f, 0.005f) }
        assertEquals(BlackBars.None, mergeBars(bars))
    }

    @Test
    fun rowLuma_weightsChannelsAndHonoursTheStride() {
        val white = IntArray(8) { 0xFFFFFFFF.toInt() }
        assertEquals(255f, rowLuma(white, 0, 8, 1), 1f)

        val black = IntArray(8) { 0xFF000000.toInt() }
        assertEquals(0f, rowLuma(black, 0, 8, 1), 0.001f)

        // Stride 2 reads only the even columns, which here are the black ones.
        val striped = IntArray(8) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertEquals(0f, rowLuma(striped, 0, 8, 2), 0.001f)
    }
}
