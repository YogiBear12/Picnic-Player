@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDynamicRangeTest {
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
    fun pqAndHlgTransfers_nameTheirOwnRange() {
        assertEquals(VideoDynamicRange.HDR10, videoDynamicRange(format(colorTransfer = C.COLOR_TRANSFER_ST2084), decoderMimeType = null))
        assertEquals(VideoDynamicRange.HLG, videoDynamicRange(format(colorTransfer = C.COLOR_TRANSFER_HLG), decoderMimeType = null))
    }

    @Test
    fun everyRangeAboveSdrReadsAsHdr() {
        assertTrue(VideoDynamicRange.HDR10.isHdr)
        assertTrue(VideoDynamicRange.HLG.isHdr)
        assertTrue(VideoDynamicRange.DOVI.isHdr)
        assertFalse(VideoDynamicRange.SDR.isHdr)
    }

    @Test
    fun sdrTransfer_isSdr() {
        assertEquals(VideoDynamicRange.SDR, videoDynamicRange(format(colorTransfer = C.COLOR_TRANSFER_SDR), decoderMimeType = null))
    }

    @Test
    fun dolbyVisionMime_isDoviWithoutColorInfo() {
        assertEquals(
            VideoDynamicRange.DOVI,
            videoDynamicRange(format(mimeType = MimeTypes.VIDEO_DOLBY_VISION), decoderMimeType = null)
        )
    }

    @Test
    fun dolbyVisionCodec_isDoviBehindAnHevcMime() {
        assertEquals(VideoDynamicRange.DOVI, videoDynamicRange(format(codecs = "dvhe.08.06"), decoderMimeType = null))
        assertEquals(VideoDynamicRange.DOVI, videoDynamicRange(format(codecs = "dvh1.05.06"), decoderMimeType = null))
    }

    @Test
    fun dolbyVisionInputDecodedAsHevc_reportsItsHdrBaseLayer() {
        val format = format(
            mimeType = MimeTypes.VIDEO_DOLBY_VISION,
            codecs = "dvhe.07.06",
            colorTransfer = C.COLOR_TRANSFER_ST2084
        )

        assertEquals(VideoDynamicRange.HDR10, videoDynamicRange(format, MimeTypes.VIDEO_H265))
    }

    @Test
    fun dolbyVisionInputDecodedAsDolbyVision_staysDovi() {
        val format = format(
            mimeType = MimeTypes.VIDEO_DOLBY_VISION,
            codecs = "dvhe.08.06",
            colorTransfer = C.COLOR_TRANSFER_ST2084
        )

        assertEquals(VideoDynamicRange.DOVI, videoDynamicRange(format, MimeTypes.VIDEO_DOLBY_VISION))
    }

    @Test
    fun sdrCodecsAndMissingFormat_areSdr() {
        assertEquals(VideoDynamicRange.SDR, videoDynamicRange(format(codecs = "hvc1.1.6.L120.90"), decoderMimeType = null))
        assertEquals(VideoDynamicRange.SDR, videoDynamicRange(format(codecs = "avc1.640028"), decoderMimeType = null))
        assertEquals(VideoDynamicRange.SDR, videoDynamicRange(null, decoderMimeType = null))
    }
}
