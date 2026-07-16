package app.picnic.player.data.playback

import app.picnic.player.data.settings.SegmentAction
import app.picnic.player.playback.SleepMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTickTest {

    private val allAsk = PlaybackTickSettings(
        introAction = SegmentAction.ASK_TO_SKIP,
        recapAction = SegmentAction.ASK_TO_SKIP,
        outroAction = SegmentAction.ASK_TO_SKIP,
        previewAction = SegmentAction.ASK_TO_SKIP,
        commercialAction = SegmentAction.ASK_TO_SKIP,
        displayNextUpDuringOutro = true
    )

    private fun input(
        positionMs: Long = 1_000,
        durationMs: Long = 100_000,
        phase: PlaybackPhase = PlaybackPhase.READY,
        segments: List<MediaSegment> = emptyList(),
        settings: PlaybackTickSettings = allAsk,
        sleepMode: SleepMode = SleepMode.OFF,
        hasNextUp: Boolean = false,
        hasPresentedFirstFrame: Boolean = true,
        autoSkippedIds: Set<String> = emptySet(),
        outroNextUpShown: Boolean = false,
        prevEndedAwaitingNext: Boolean = false,
        prevVideoStillPlaying: Boolean = false,
        hasError: Boolean = false
    ) = PlaybackTickInput(
        positionMs = positionMs,
        durationMs = durationMs,
        bufferedMs = positionMs,
        isPlaying = phase == PlaybackPhase.READY,
        phase = phase,
        segments = segments,
        settings = settings,
        sleepMode = sleepMode,
        hasNextUp = hasNextUp,
        hasPresentedFirstFrame = hasPresentedFirstFrame,
        autoSkippedIds = autoSkippedIds,
        outroNextUpShown = outroNextUpShown,
        prevEndedAwaitingNext = prevEndedAwaitingNext,
        prevVideoStillPlaying = prevVideoStillPlaying,
        hasError = hasError
    )

    private fun seg(kind: SegmentKind, start: Long, end: Long, id: String = kind.name) = MediaSegment(id = id, kind = kind, startMs = start, endMs = end)

    // --- segment auto-skip -----------------------------------------------------

    @Test
    fun autoSkip_seeksToEnd_andMarksOnce() {
        val intro = seg(SegmentKind.INTRO, 0, 5_000)
        val d = playbackTick(
            input(
                positionMs = 1_000,
                segments = listOf(intro),
                settings = allAsk.copy(introAction = SegmentAction.SKIP_AUTOMATICALLY)
            )
        )
        assertEquals(5_000L, d.autoSkipToMs)
        assertEquals("INTRO", d.markSkippedId)
        assertNull(d.currentSegment) // auto-skip does not show the button
    }

    @Test
    fun autoSkip_notRepeated_whenAlreadySkipped() {
        val intro = seg(SegmentKind.INTRO, 0, 5_000)
        val d = playbackTick(
            input(
                positionMs = 1_000,
                segments = listOf(intro),
                settings = allAsk.copy(introAction = SegmentAction.SKIP_AUTOMATICALLY),
                autoSkippedIds = setOf("INTRO")
            )
        )
        assertNull(d.autoSkipToMs)
        assertNull(d.markSkippedId)
    }

    @Test
    fun askToSkip_showsButton() {
        val intro = seg(SegmentKind.INTRO, 0, 5_000)
        val d = playbackTick(input(positionMs = 1_000, segments = listOf(intro)))
        assertEquals(intro, d.currentSegment)
        assertNull(d.autoSkipToMs)
    }

    @Test
    fun askToSkip_hiddenAfterSkippedAway() {
        val intro = seg(SegmentKind.INTRO, 0, 5_000)
        val d = playbackTick(
            input(positionMs = 1_000, segments = listOf(intro), autoSkippedIds = setOf("INTRO"))
        )
        assertNull(d.currentSegment)
    }

    @Test
    fun outsideSegment_noButton() {
        val intro = seg(SegmentKind.INTRO, 0, 5_000)
        val d = playbackTick(input(positionMs = 9_000, segments = listOf(intro)))
        assertNull(d.currentSegment)
    }

    // --- outro next-up ---------------------------------------------------------

    @Test
    fun outroRunsToEnd_triggersNextUp() {
        val outro = seg(SegmentKind.OUTRO, 95_000, 100_000)
        val d = playbackTick(
            input(positionMs = 96_000, durationMs = 100_000, segments = listOf(outro), hasNextUp = true)
        )
        assertTrue(d.setOutroNextUpShown)
        assertTrue(d.endedAwaitingNext)
        assertTrue(d.videoStillPlaying)
    }

    @Test
    fun outroWithAfterCredits_doesNotTrigger() {
        // endMs well short of duration => post-outro content => no trigger.
        val outro = seg(SegmentKind.OUTRO, 80_000, 85_000)
        val d = playbackTick(
            input(positionMs = 81_000, durationMs = 100_000, segments = listOf(outro), hasNextUp = true)
        )
        assertFalse(d.setOutroNextUpShown)
        assertFalse(d.endedAwaitingNext)
    }

    @Test
    fun outro_notTriggered_whenAlreadyShown() {
        val outro = seg(SegmentKind.OUTRO, 95_000, 100_000)
        val d = playbackTick(
            input(
                positionMs = 96_000,
                segments = listOf(outro),
                hasNextUp = true,
                outroNextUpShown = true
            )
        )
        assertFalse(d.setOutroNextUpShown)
    }

    @Test
    fun outro_notTriggered_whenNoNextUp() {
        val outro = seg(SegmentKind.OUTRO, 95_000, 100_000)
        val d = playbackTick(input(positionMs = 96_000, segments = listOf(outro), hasNextUp = false))
        assertFalse(d.setOutroNextUpShown)
    }

    @Test
    fun outro_notTriggered_whenAutoSkip() {
        val outro = seg(SegmentKind.OUTRO, 95_000, 100_000)
        val d = playbackTick(
            input(
                positionMs = 96_000,
                segments = listOf(outro),
                settings = allAsk.copy(outroAction = SegmentAction.SKIP_AUTOMATICALLY),
                hasNextUp = true
            )
        )
        assertFalse(d.setOutroNextUpShown)
        assertEquals(100_000L, d.autoSkipToMs)
    }

    // --- ended / sleep ---------------------------------------------------------

    @Test
    fun endedDefault_latchesEndedAwaitingNext() {
        val d = playbackTick(input(phase = PlaybackPhase.ENDED))
        assertTrue(d.endedAwaitingNext)
        assertFalse(d.pauseForSleep)
    }

    @Test
    fun sleepEndOfEpisode_pausesAndSuppressesNext() {
        val d = playbackTick(input(phase = PlaybackPhase.ENDED, sleepMode = SleepMode.END_OF_EPISODE))
        assertTrue(d.pauseForSleep)
        assertFalse(d.endedAwaitingNext) // sleep suppresses the queue advance
    }

    @Test
    fun sleepEndOfQueue_pausesOnlyWhenNoNextUp() {
        val withNext = playbackTick(
            input(phase = PlaybackPhase.ENDED, sleepMode = SleepMode.END_OF_QUEUE, hasNextUp = true)
        )
        assertFalse(withNext.pauseForSleep)
        assertTrue(withNext.endedAwaitingNext)

        val noNext = playbackTick(
            input(phase = PlaybackPhase.ENDED, sleepMode = SleepMode.END_OF_QUEUE, hasNextUp = false)
        )
        assertTrue(noNext.pauseForSleep)
        assertFalse(noNext.endedAwaitingNext)
    }

    @Test
    fun videoStill_clearsOnEndWhenPreviouslyPlaying() {
        val d = playbackTick(input(phase = PlaybackPhase.ENDED, prevVideoStillPlaying = true))
        assertFalse(d.videoStillPlaying)
    }

    // --- mirror fields ---------------------------------------------------------

    @Test
    fun firstFrame_presentedOnReady() {
        val d = playbackTick(input(phase = PlaybackPhase.READY, hasPresentedFirstFrame = false))
        assertTrue(d.presentFirstFrame)
        assertFalse(d.isLoading) // presented this tick => not loading
    }

    @Test
    fun loading_whenNotPresentedAndNoError() {
        val d = playbackTick(input(phase = PlaybackPhase.BUFFERING, hasPresentedFirstFrame = false))
        assertFalse(d.presentFirstFrame)
        assertTrue(d.isLoading)
        assertTrue(d.buffering)
    }

    @Test
    fun negativePosition_coercedToZero() {
        val d = playbackTick(input(positionMs = -1))
        assertEquals(0L, d.positionMs)
    }
}
