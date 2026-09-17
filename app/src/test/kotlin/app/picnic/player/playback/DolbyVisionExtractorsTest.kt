@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DolbyVisionExtractorsTest {
    @Test
    fun convertedProfile7_isAdvertisedAsProfile8() {
        assertEquals("dvhe.08.06", rewrite(DoviStrategy.CONVERT_TO_P8, codecs = "dvhe.07.06")?.codecs)
        assertEquals("dvh1.08.06", rewrite(DoviStrategy.CONVERT_TO_P8, codecs = "dvh1.07.06")?.codecs)
    }

    @Test
    fun otherProfiles_areUnchanged() {
        val profile8 = dolbyVisionFormat(codecs = "dvhe.08.06")
        val profile5 = dolbyVisionFormat(codecs = "dvhe.05.06")

        assertSame(profile8, rewrite(DoviStrategy.CONVERT_TO_P8, profile8))
        assertSame(profile5, rewrite(DoviStrategy.CONVERT_TO_P8, profile5))
    }

    @Test
    fun nonDolbyVisionTrack_isUnchanged() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setCodecs("dvhe.07.06")
            .build()

        assertSame(format, rewrite(DoviStrategy.CONVERT_TO_P8, format))
    }

    @Test
    fun nonVideoTrack_isNotWrapped() {
        val track = CapturingTrackOutput()
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setCodecs("dvhe.07.06")
            .build()

        DolbyVisionFormatExtractorOutput(CapturingExtractorOutput(track), DoviStrategy.CONVERT_TO_P8)
            .track(0, C.TRACK_TYPE_AUDIO)
            .format(format)

        assertSame(format, track.format)
    }

    private fun dolbyVisionFormat(codecs: String?): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
        .setCodecs(codecs)
        .build()

    private fun rewrite(strategy: DoviStrategy, codecs: String?): Format? = rewrite(strategy, dolbyVisionFormat(codecs))

    private fun rewrite(strategy: DoviStrategy, format: Format): Format? {
        val track = CapturingTrackOutput()
        DolbyVisionFormatExtractorOutput(CapturingExtractorOutput(track), strategy)
            .track(0, C.TRACK_TYPE_VIDEO)
            .format(format)
        return track.format
    }
}

private class CapturingExtractorOutput(
    private val trackOutput: TrackOutput
) : ExtractorOutput {
    override fun track(id: Int, type: Int): TrackOutput = trackOutput

    override fun endTracks() = Unit

    override fun seekMap(seekMap: SeekMap) = Unit
}

private class CapturingTrackOutput : ForwardingTrackOutput(DiscardingTrackOutput()) {
    var format: Format? = null
        private set

    override fun format(format: Format) {
        this.format = format
    }
}
