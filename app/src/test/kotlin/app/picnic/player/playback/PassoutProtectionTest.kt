package app.picnic.player.playback

import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassoutProtectionTest {

    private val time = TestTimeSource()
    private val controller = PlaybackSessionController(CoroutineScope(Job()), time)

    private var protectionEnabled = true

    private fun episode(minutes: Int): Boolean {
        time += minutes.minutes
        controller.onItemAutoplayed()
        return controller.shouldAskStillWatching(protectionEnabled)
    }

    @Test
    fun neitherThresholdMetDoesNotGate() {
        assertFalse(episode(20))
        assertFalse(episode(20))
    }

    @Test
    fun episodeCountWithoutTheClockDoesNotGate() {
        assertFalse(episode(20))
        assertFalse(episode(20))
        assertFalse(episode(20))
        assertFalse(episode(20))
    }

    @Test
    fun theClockWithoutTheEpisodeCountDoesNotGate() {
        assertFalse(episode(100))
        assertFalse(episode(10))
    }

    @Test
    fun bothThresholdsMetGates() {
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
    }

    @Test
    fun interactionRestartsTheRun() {
        assertFalse(episode(45))
        assertFalse(episode(45))
        controller.onInteraction()
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
    }

    @Test
    fun theSelfStartedEpisodeCountsTowardTheThree() {
        controller.playerStarted()
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
    }

    @Test
    fun autoAdvanceIsNotAnInteraction() {
        controller.playerStarted()
        assertFalse(episode(45))
        controller.handOffToNextItem()
        controller.playerStarted()
        assertFalse(episode(45))
        controller.handOffToNextItem()
        controller.playerStarted()
        assertTrue(episode(45))
    }

    @Test
    fun answeringThePromptResetsBothCounters() {
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
        controller.onInteraction()
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
    }

    @Test
    fun disabledNeverGates() {
        protectionEnabled = false
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertFalse(episode(45))
    }

    @Test
    fun teardownDuringAHandoffStillStartsTheNextSessionClean() {
        assertFalse(episode(45))
        assertFalse(episode(45))
        controller.handOffToNextItem()
        controller.reset()
        controller.playerStarted()
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
    }

    @Test
    fun sessionTeardownZeroesTheCounters() {
        assertFalse(episode(45))
        assertFalse(episode(45))
        controller.reset()
        assertFalse(episode(45))
        assertFalse(episode(45))
        assertTrue(episode(45))
    }
}
