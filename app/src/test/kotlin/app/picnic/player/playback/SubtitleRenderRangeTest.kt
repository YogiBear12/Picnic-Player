@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleRenderRangeTest {

    private fun format(
        mimeType: String = MimeTypes.VIDEO_H265,
        codecs: String? = null,
        colorTransfer: Int? = null
    ): Format = Format.Builder()
        .setSampleMimeType(mimeType)
        .setCodecs(codecs)
        .setColorInfo(
            colorTransfer?.let {
                ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(it)
                    .build()
            }
        )
        .build()

    @Test
    fun pqAndHlgTransfers_areHdr() {
        assertEquals(SubtitleRenderRange.HDR, subtitleRenderRange(format(colorTransfer = C.COLOR_TRANSFER_ST2084)))
        assertEquals(SubtitleRenderRange.HDR, subtitleRenderRange(format(colorTransfer = C.COLOR_TRANSFER_HLG)))
    }

    @Test
    fun sdrTransfer_isSdr() {
        assertEquals(SubtitleRenderRange.SDR, subtitleRenderRange(format(colorTransfer = C.COLOR_TRANSFER_SDR)))
    }

    @Test
    fun dolbyVisionMime_isHdrWithoutColorInfo() {
        assertEquals(
            SubtitleRenderRange.HDR,
            subtitleRenderRange(format(mimeType = MimeTypes.VIDEO_DOLBY_VISION))
        )
    }

    @Test
    fun dolbyVisionCodec_isHdrBehindAnHevcMime() {
        // Profile 8 in Matroska arrives as HEVC with the range only stated in the codec string.
        assertEquals(SubtitleRenderRange.HDR, subtitleRenderRange(format(codecs = "dvhe.08.06")))
        assertEquals(SubtitleRenderRange.HDR, subtitleRenderRange(format(codecs = "dvh1.05.06")))
    }

    @Test
    fun sdrCodecsAndMissingFormat_areSdr() {
        assertEquals(SubtitleRenderRange.SDR, subtitleRenderRange(format(codecs = "hvc1.1.6.L120.90")))
        assertEquals(SubtitleRenderRange.SDR, subtitleRenderRange(format(codecs = "avc1.640028")))
        assertEquals(SubtitleRenderRange.SDR, subtitleRenderRange(null))
    }
}
