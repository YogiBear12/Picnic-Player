package app.picnic.player.ui.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekByTargetTest {

    private val duration = 600_000L

    @Test
    fun midFileAppliesTheFullStepBothWays() {
        assertEquals(330_000L, seekByTarget(300_000, 30_000, duration))
        assertEquals(270_000L, seekByTarget(300_000, -30_000, duration))
    }

    @Test
    fun forwardStopsOnTheLastFrame() {
        assertEquals(599_800L, seekByTarget(590_000, 30_000, duration))
    }

    @Test
    fun backwardStopsAtTheStart() {
        assertEquals(0L, seekByTarget(10_000, -30_000, duration))
    }

    @Test
    fun forwardFromPastTheCeilingNeverSeeksBack() {
        assertEquals(599_900L, seekByTarget(599_900, 30_000, duration))
        assertEquals(duration, seekByTarget(duration, 30_000, duration))
    }

    @Test
    fun unknownDurationKeepsOnlyTheFloor() {
        assertEquals(330_000L, seekByTarget(300_000, 30_000, C.TIME_UNSET))
        assertEquals(0L, seekByTarget(10_000, -30_000, C.TIME_UNSET))
    }
}
