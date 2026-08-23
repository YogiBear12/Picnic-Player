package app.picnic.player.data.playback

import app.picnic.player.data.settings.BurnInSubtitles
import app.picnic.player.data.settings.PlaybackSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleBurnTest {

    @Test
    fun offNeverBurns() {
        assertEquals(SubtitleBurn.NONE, subtitleBurn(settings(BurnInSubtitles.OFF), subtitleStreamIndex = 2))
    }

    @Test
    fun automaticBurnsOnlyWhenVideoIsConverted() {
        assertEquals(
            SubtitleBurn.WHEN_VIDEO_CONVERTED,
            subtitleBurn(settings(BurnInSubtitles.AUTOMATIC), subtitleStreamIndex = 2)
        )
    }

    @Test
    fun alwaysBurns() {
        assertEquals(SubtitleBurn.ALWAYS, subtitleBurn(settings(BurnInSubtitles.ALWAYS), subtitleStreamIndex = 2))
    }

    @Test
    fun noSelectedSubtitleNeverBurns() {
        assertEquals(SubtitleBurn.NONE, subtitleBurn(settings(BurnInSubtitles.ALWAYS), subtitleStreamIndex = null))
    }

    @Test
    fun forceDirectPlayWinsOverEveryMode() {
        assertEquals(
            SubtitleBurn.NONE,
            subtitleBurn(settings(BurnInSubtitles.ALWAYS, forceDirectPlay = true), subtitleStreamIndex = 2)
        )
    }

    private fun settings(mode: BurnInSubtitles, forceDirectPlay: Boolean = false) = PlaybackSettings(
        burnInSubtitles = mode,
        forceDirectPlay = forceDirectPlay
    )
}
