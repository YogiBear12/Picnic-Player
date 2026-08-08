package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlackBarTrackTest {

    private val scope = BlackBars(0.128f, 0.128f)
    private val full = BlackBars.None

    private fun frames(bars: BlackBars, count: Int, fromMs: Long, stepMs: Long = 10_000L) = List(count) { TimedBars(fromMs + it * stepMs, bars) }

    @Test
    fun `a single framing yields one segment starting at zero`() {
        val segments = blackBarSegments(frames(scope, count = 10, fromMs = 0))

        assertEquals(1, segments.size)
        assertEquals(0L, segments[0].startMs)
        assertEquals(0.128f, segments[0].bars.bottom, 0.001f)
    }

    @Test
    fun `a change that holds becomes its own segment`() {
        val segments = blackBarSegments(
            frames(scope, count = 10, fromMs = 0) + frames(full, count = 10, fromMs = 100_000)
        )

        assertEquals(2, segments.size)
        assertEquals(0L, segments[0].startMs)
        assertEquals(100_000L, segments[1].startMs)
        assertEquals(BlackBars.None, segments[1].bars)
    }

    @Test
    fun `an implausibly wide bar is discarded rather than reported`() {
        val segments = blackBarSegments(frames(BlackBars(0.4f, 0.4f), count = 6, fromMs = 0))

        assertEquals(1, segments.size)
        assertEquals(BlackBars.None, segments[0].bars)
    }

    @Test
    fun `a stray frame does not cut the timeline`() {
        val segments = blackBarSegments(
            frames(scope, count = 10, fromMs = 0) +
                TimedBars(100_000, full) +
                frames(scope, count = 10, fromMs = 110_000)
        )

        assertEquals(1, segments.size)
    }

    @Test
    fun `returning to an earlier framing does not open a third segment`() {
        val segments = blackBarSegments(
            frames(scope, count = 5, fromMs = 0) +
                frames(full, count = 5, fromMs = 50_000) +
                frames(scope, count = 5, fromMs = 100_000)
        )

        assertEquals(3, segments.size)
        assertEquals(0.128f, segments[2].bars.bottom, 0.001f)
    }

    @Test
    fun `a segment reports the smallest bars it saw`() {
        val segments = blackBarSegments(
            listOf(
                TimedBars(0, BlackBars(0.14f, 0.14f)),
                TimedBars(10_000, BlackBars(0.128f, 0.128f)),
                TimedBars(20_000, BlackBars(0.13f, 0.13f)),
                TimedBars(30_000, BlackBars(0.135f, 0.135f))
            )
        )

        assertEquals(1, segments.size)
        assertEquals(0.128f, segments[0].bars.bottom, 0.001f)
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
        assertEquals(full, track.at(199_999))
        assertEquals(scope, track.at(200_000))
        assertEquals(scope, track.at(Long.MAX_VALUE))
    }

    @Test
    fun `a constant track answers the same bars everywhere`() {
        val track = BlackBarTrack.constant(scope)

        assertFalse(track.varies)
        assertEquals(scope, track.at(0))
        assertEquals(scope, track.at(5_000_000))
    }

    @Test
    fun `an empty track reports no bars`() {
        assertEquals(BlackBars.None, BlackBarTrack.None.at(0))
        assertFalse(BlackBarTrack.None.varies)
    }
}
