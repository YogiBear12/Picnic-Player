package app.picnic.player.ui.player.osd

import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityRung
import org.junit.Assert.assertEquals
import org.junit.Test

class QualitySummaryTest {

    @Test
    fun directPlayReadsAsOriginal() {
        assertEquals("Original", qualitySummary(PlayMethodKind.DIRECT_PLAY, null))
    }

    @Test
    fun directStreamReadsAsOriginal() {
        assertEquals("Original", qualitySummary(PlayMethodKind.DIRECT_STREAM, QualityRung.P1080_8))
    }

    @Test
    fun transcodeNamesTheQualityInForce() {
        assertEquals("Transcoding · 1080p (Low)", qualitySummary(PlayMethodKind.TRANSCODE, QualityRung.P1080_6))
        assertEquals("Transcoding · 720p (High)", qualitySummary(PlayMethodKind.TRANSCODE, QualityRung.P720_4))
        assertEquals("Transcoding · 480p", qualitySummary(PlayMethodKind.TRANSCODE, QualityRung.P480_2))
    }

    @Test
    fun transcodeWaitsUntilTheQualityIsKnown() {
        assertEquals("Transcoding…", qualitySummary(PlayMethodKind.TRANSCODE, null))
    }

    @Test
    fun unknownMethodReadsAsOriginal() {
        assertEquals("Original", qualitySummary(null, null))
    }
}
