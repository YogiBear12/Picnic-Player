package app.picnic.player.ui.player

import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityOption
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.playback.SleepMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun aTranscodeIsRunningAtTheNegotiatedRung() {
        val state = PlayerUiState(playMethod = PlayMethodKind.TRANSCODE, streamRung = QualityRung.P1080_6)
        assertEquals(QualityOption.Transcode(QualityRung.P1080_6), state.activeQuality)
    }

    @Test
    fun aServerImposedTranscodeMatchesNoQualityOption() {
        val state = PlayerUiState(playMethod = PlayMethodKind.TRANSCODE, streamRung = null)
        assertNull(state.activeQuality)
    }

    @Test
    fun aRemuxReadsAsOriginal() {
        val state = PlayerUiState(playMethod = PlayMethodKind.DIRECT_STREAM, streamRung = QualityRung.P1080_6)
        assertEquals(QualityOption.Original, state.activeQuality)
    }

    @Test
    fun directPlayReadsAsOriginal() {
        val state = PlayerUiState(playMethod = PlayMethodKind.DIRECT_PLAY, streamRung = QualityRung.P1080_6)
        assertEquals(QualityOption.Original, state.activeQuality)
    }

    @Test
    fun anUnknownPlayMethodReadsAsOriginal() {
        assertEquals(QualityOption.Original, PlayerUiState(streamRung = QualityRung.P1080_6).activeQuality)
    }

    private val next = NextUpItem("next", "Next", "S1:E2", "", "", null, null)
    private val outroNextUp = PlayerUiState(nextUp = next, endedAwaitingNext = true, videoStillPlaying = true)

    @Test
    fun countdownExpiryDuringOutro_autoplays() {
        assertEquals(NextUpExpiry.AUTOPLAY, outroNextUp.nextUpExpiry(SleepMode.OFF))
        SleepMode.entries.filter { it.durationSeconds != null }.forEach { mode ->
            assertEquals(mode.name, NextUpExpiry.AUTOPLAY, outroNextUp.nextUpExpiry(mode))
        }
    }

    @Test
    fun countdownExpiryWithEndOfEpisodeSleep_pausesForSleep() {
        assertEquals(NextUpExpiry.PAUSE_FOR_SLEEP, outroNextUp.nextUpExpiry(SleepMode.END_OF_EPISODE))
    }

    @Test
    fun countdownExpiryAfterThePromptOrADismiss_isIgnored() {
        listOf(
            outroNextUp.copy(stillWatching = true),
            outroNextUp.copy(endedAwaitingNext = false, videoStillPlaying = false),
            outroNextUp.copy(nextUp = null)
        ).forEach { assertEquals(it.toString(), NextUpExpiry.IGNORE, it.nextUpExpiry(SleepMode.END_OF_EPISODE)) }
    }

    @Test
    fun keepWatchingAfterCountdownExpiryMidOutro_playsTheNextEpisode() {
        val prompted = outroNextUp.completedByNextUpExpiry(askStillWatching = true)
        assertTrue(prompted.stillWatching)
        assertSame(next, prompted.queuedNextUp)
        assertSame(next, outroNextUp.completedByNextUpExpiry(askStillWatching = false).queuedNextUp)
    }

    @Test
    fun keepWatchingBeforeCountdownExpiryMidOutro_resumesTheOutro() {
        assertNull(outroNextUp.copy(stillWatching = true).queuedNextUp)
    }

    @Test
    fun keepWatchingWithoutPendingNextUp_resumesTheCurrentItem() {
        assertNull(PlayerUiState(nextUp = next).queuedNextUp)
    }
}
