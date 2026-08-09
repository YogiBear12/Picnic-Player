package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CueLatchedBarsTest {
    private val scope = BlackBars(0.128f, 0.128f)
    private val full = BlackBars.None

    private val track = BlackBarTrack(
        listOf(
            BlackBarTrack.Segment(0L, scope),
            BlackBarTrack.Segment(100_000L, full)
        )
    )

    @Test
    fun `bars start at no bars until a track arrives`() {
        assertEquals(BlackBars.None, CueLatchedBars().bars.value)
    }

    @Test
    fun `a new track applies at once`() {
        val latched = CueLatchedBars()

        latched.setTrack(track, positionMs = 0)

        assertEquals(scope, latched.bars.value)
    }

    @Test
    fun `the next cue takes the framing in force`() {
        val latched = CueLatchedBars()
        latched.setTrack(track, positionMs = 0)

        latched.onCueBoundary(positionMs = 120_000)

        assertEquals(full, latched.bars.value)
    }

    @Test
    fun `a cue boundary before the change keeps the old framing`() {
        val latched = CueLatchedBars()
        latched.setTrack(track, positionMs = 0)

        latched.onCueBoundary(positionMs = 99_999)

        assertEquals(scope, latched.bars.value)
    }

    @Test
    fun `switching to a track without bars applies mid-line`() {
        val latched = CueLatchedBars()
        latched.setTrack(track, positionMs = 0)

        latched.setTrack(BlackBarTrack.None, positionMs = 10_000)

        assertEquals(BlackBars.None, latched.bars.value)
    }
}
