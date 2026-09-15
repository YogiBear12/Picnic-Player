package app.picnic.player.ui.player.osd

import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityRung
import org.junit.Assert.assertEquals
import org.junit.Test

class QualitySummaryTest {
    private fun imposed(height: Int?, bitrate: Int?) = serverTranscode(PlayMethodKind.TRANSCODE, null, height, bitrate)

    @Test
    fun serverReasonCodesReadAsWords() {
        assertEquals("Audio codec not supported", humanizeReason("AudioCodecNotSupported"))
        assertEquals("Container bitrate exceeds limit", humanizeReason("ContainerBitrateExceedsLimit"))
        assertEquals("Already spaced text", humanizeReason("Already spaced text"))
    }

    @Test
    fun directPlayReadsAsOriginal() {
        assertEquals("Original", qualitySummary(PlayMethodKind.DIRECT_PLAY, null, null))
    }

    @Test
    fun directStreamReadsAsOriginal() {
        assertEquals("Original", qualitySummary(PlayMethodKind.DIRECT_STREAM, QualityRung.P1080_8, null))
    }

    @Test
    fun transcodeNamesTheQualityInForce() {
        assertEquals("1080p (Low)", qualitySummary(PlayMethodKind.TRANSCODE, QualityRung.P1080_6, null))
        assertEquals("720p (High)", qualitySummary(PlayMethodKind.TRANSCODE, QualityRung.P720_4, null))
        assertEquals("480p", qualitySummary(PlayMethodKind.TRANSCODE, QualityRung.P480_2, null))
    }

    @Test
    fun serverImposedTranscodeReportsWhatTheServerSendsWithoutALadderQualifier() {
        assertEquals("1080p · 4.63 Mbps", qualitySummary(PlayMethodKind.TRANSCODE, null, imposed(1080, 4_634_321)))
    }

    @Test
    fun serverImposedTranscodeNamesFourKTheSameWayThePickerDoes() {
        assertEquals("4K · 18.00 Mbps", qualitySummary(PlayMethodKind.TRANSCODE, null, imposed(2160, 18_000_000)))
        assertEquals("4K", qualitySummary(PlayMethodKind.TRANSCODE, null, imposed(2160, 0)))
    }

    @Test
    fun serverImposedTranscodeReportsWhicheverDetailIsKnown() {
        assertEquals("720p", qualitySummary(PlayMethodKind.TRANSCODE, null, imposed(720, 0)))
        assertEquals("4.63 Mbps", qualitySummary(PlayMethodKind.TRANSCODE, null, imposed(null, 4_634_321)))
    }

    @Test
    fun transcodeWaitsUntilTheServerReportsWhatItIsSending() {
        assertEquals("Transcoding…", qualitySummary(PlayMethodKind.TRANSCODE, null, imposed(null, null)))
    }

    @Test
    fun unknownMethodReadsAsOriginal() {
        assertEquals("Original", qualitySummary(null, null, null))
    }
}
