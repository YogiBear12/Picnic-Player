package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlackBarTrackTest {
    private val scope = BlackBars(0.122f, 0.122f)
    private val imax = BlackBars(0.022f, 0.022f)
    private val full = BlackBars.None

    private fun frames(bars: BlackBars, count: Int, fromMs: Long, stepMs: Long = 10_000L) = List(count) { TimedBars(fromMs + it * stepMs, bars) }

    private fun segments(frames: List<TimedBars>) = blackBarSegments(frames, frameHeight = 90)

    @Test
    fun `a single framing yields one segment starting at zero`() {
        val result = segments(frames(scope, count = 100, fromMs = 0))

        assertEquals(1, result.size)
        assertEquals(0L, result[0].startMs)
        assertEquals(0.122f, result[0].bars.bottom, 0.001f)
    }

    @Test
    fun `a change opens a segment at the frame that measured it`() {
        val result = segments(
            frames(scope, count = 50, fromMs = 0) + frames(full, count = 50, fromMs = 500_000)
        )

        assertEquals(2, result.size)
        assertEquals(500_000L, result[1].startMs)
        assertEquals(BlackBars.None, result[1].bars)
    }

    @Test
    fun `a lone frame is a segment of its own`() {
        val result = segments(
            frames(scope, count = 50, fromMs = 0) +
                TimedBars(500_000, full) +
                frames(scope, count = 20, fromMs = 510_000) +
                frames(full, count = 40, fromMs = 710_000)
        )

        assertEquals(500_000L, result[1].startMs)
        assertEquals(BlackBars.None, result[1].bars)
        assertEquals(510_000L, result[2].startMs)
    }

    @Test
    fun `an edge that overruns into dark picture takes the file's letterbox`() {
        val result = segments(
            frames(scope, count = 99, fromMs = 0) + listOf(TimedBars(990_000, BlackBars(0.122f, 0.178f)))
        )

        assertEquals(1, result.size)
        assertEquals(0.122f, result[0].bars.bottom, 0.001f)
    }

    @Test
    fun `both edges overrunning still takes the file's letterbox`() {
        val result = segments(
            frames(scope, count = 99, fromMs = 0) + listOf(TimedBars(990_000, BlackBars(0.222f, 0.244f)))
        )

        assertEquals(1, result.size)
        assertEquals(0.122f, result[0].bars.top, 0.001f)
    }

    @Test
    fun `a reading that never found the picture cannot become a framing`() {
        val capped = BlackBars(22f / 90f, 22f / 90f)
        val result = segments(
            frames(scope, count = 80, fromMs = 0) + frames(capped, count = 20, fromMs = 800_000)
        )

        assertEquals(1, result.size)
        assertEquals(0.122f, result[0].bars.top, 0.001f)
    }

    @Test
    fun `a file with two letterboxes keeps both`() {
        val result = segments(
            frames(scope, count = 60, fromMs = 0) +
                frames(imax, count = 20, fromMs = 600_000) +
                frames(scope, count = 20, fromMs = 800_000)
        )

        assertEquals(3, result.size)
        assertEquals(0.122f, result[0].bars.top, 0.001f)
        assertEquals(0.022f, result[1].bars.top, 0.001f)
        assertEquals(600_000L, result[1].startMs)
        assertEquals(0.122f, result[2].bars.top, 0.001f)
    }

    @Test
    fun `a depth too rare to be a framing lands on the nearest one`() {
        val result = segments(
            frames(scope, count = 99, fromMs = 0) + listOf(TimedBars(990_000, BlackBars(0.100f, 0.100f)))
        )

        assertEquals(1, result.size)
        assertEquals(0.122f, result[0].bars.top, 0.001f)
    }

    @Test
    fun `an item with no letterbox anywhere reports none throughout`() {
        val result = segments(frames(full, count = 100, fromMs = 0))

        assertEquals(1, result.size)
        assertEquals(BlackBars.None, result[0].bars)
    }

    @Test
    fun `frames out of order are placed by position`() {
        val result = segments(
            frames(full, count = 50, fromMs = 500_000) + frames(scope, count = 50, fromMs = 0)
        )

        assertEquals(2, result.size)
        assertEquals(0.122f, result[0].bars.top, 0.001f)
        assertEquals(500_000L, result[1].startMs)
    }

    @Test
    fun `lookup finds the framing in force at a position`() {
        val track = BlackBarTrack(
            listOf(
                BlackBarTrack.Segment(0L, scope),
                BlackBarTrack.Segment(100_000L, full),
                BlackBarTrack.Segment(200_000L, scope)
            )
        )

        assertEquals(scope, track.at(0))
        assertEquals(scope, track.at(99_999))
        assertEquals(full, track.at(100_000))
        assertEquals(scope, track.at(200_000))
        assertEquals(scope, track.at(Long.MAX_VALUE))
    }

    @Test
    fun `a constant track answers the same bars everywhere`() {
        val track = BlackBarTrack.constant(scope)

        assertFalse(track.varies)
        assertEquals(scope, track.at(5_000_000))
    }

    @Test
    fun `an empty track reports no bars`() {
        assertEquals(BlackBars.None, BlackBarTrack.None.at(0))
        assertFalse(BlackBarTrack.None.varies)
    }
}
