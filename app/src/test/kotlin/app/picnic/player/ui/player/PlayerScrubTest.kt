package app.picnic.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScrubTest {

    @Test
    fun repeatsAccumulateFromTheTargetUntilCommit() {
        val scrub = PlayerScrub()
        assertFalse(scrub.active)

        scrub.scrubBy(10_000, fromMs = 20_000, durationMs = 100_000)
        assertTrue(scrub.active)
        assertEquals(30_000L, scrub.targetMs)
        scrub.scrubBy(10_000, fromMs = 21_000, durationMs = 100_000)

        assertEquals(40_000L, scrub.commit())
        assertFalse(scrub.active)
        scrub.scrubBy(-10_000, fromMs = 60_000, durationMs = 100_000)
        assertEquals(50_000L, scrub.targetMs)
    }

    @Test
    fun cancelEndsTheScrubAndTheNextPressStartsFromPlayback() {
        val scrub = PlayerScrub()
        scrub.scrubBy(10_000, fromMs = 20_000, durationMs = 100_000)

        scrub.cancel()

        assertFalse(scrub.active)
        scrub.scrubBy(-10_000, fromMs = 20_000, durationMs = 100_000)
        assertEquals(10_000L, scrub.targetMs)
        assertTrue(scrub.active)
    }

    @Test
    fun targetClampsToBothEndsOfTheDuration() {
        val scrub = PlayerScrub()

        scrub.scrubBy(-10_000, fromMs = 5_000, durationMs = 100_000)
        assertEquals(0L, scrub.targetMs)
        scrub.scrubBy(200_000, fromMs = 5_000, durationMs = 100_000)
        assertEquals(100_000L, scrub.targetMs)
    }
}
