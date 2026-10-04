package app.picnic.player.ui.player.osd

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubStepTest {

    @Test
    fun aTapAndTheFirstSixtyStepsOfAHoldStepTenSeconds() {
        assertEquals(10_000L, scrubStepMs(0))
        assertEquals(10_000L, scrubStepMs(59))
    }

    @Test
    fun theNextSixtyStepsStepTwentySeconds() {
        assertEquals(20_000L, scrubStepMs(60))
        assertEquals(20_000L, scrubStepMs(119))
    }

    @Test
    fun theHoldTopsOutAtThirtySeconds() {
        assertEquals(30_000L, scrubStepMs(120))
        assertEquals(30_000L, scrubStepMs(1000))
    }
}
