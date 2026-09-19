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
    private fun range(format: Format, decoderMimeType: String): VideoDynamicRange {
        val decoder = VideoDecoder(name = "test", mimeType = decoderMimeType, hardwareAccelerated = true)
        return videoDynamicRange(VideoOutput(format, decoder))
    }

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
        assertEquals(
            VideoDynamicRange.HDR10,
            range(format(colorTransfer = C.COLOR_TRANSFER_ST2084), MimeTypes.VIDEO_H265)
        )
        assertEquals(
            VideoDynamicRange.HLG,
            range(format(colorTransfer = C.COLOR_TRANSFER_HLG), MimeTypes.VIDEO_H265)
        )
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
        assertEquals(
            VideoDynamicRange.SDR,
            range(format(colorTransfer = C.COLOR_TRANSFER_SDR), MimeTypes.VIDEO_H265)
        )
    }

    @Test
    fun dolbyVisionMime_isDoviWithoutColorInfo() {
        assertEquals(
            VideoDynamicRange.DOVI,
            range(format(mimeType = MimeTypes.VIDEO_DOLBY_VISION), MimeTypes.VIDEO_DOLBY_VISION)
        )
    }

    @Test
    fun dolbyVisionCodec_isDoviBehindAnHevcMime() {
        assertEquals(
            VideoDynamicRange.DOVI,
            range(format(codecs = "dvhe.08.06"), MimeTypes.VIDEO_DOLBY_VISION)
        )
        assertEquals(
            VideoDynamicRange.DOVI,
            range(format(codecs = "dvh1.05.06"), MimeTypes.VIDEO_DOLBY_VISION)
        )
    }

    @Test
    fun dolbyVisionInputDecodedAsHevc_reportsItsHdrBaseLayer() {
        val format = format(
            mimeType = MimeTypes.VIDEO_DOLBY_VISION,
            codecs = "dvhe.07.06",
            colorTransfer = C.COLOR_TRANSFER_ST2084
        )

        assertEquals(VideoDynamicRange.HDR10, range(format, MimeTypes.VIDEO_H265))
    }

    @Test
    fun dolbyVisionInputDecodedAsDolbyVision_staysDovi() {
        val format = format(
            mimeType = MimeTypes.VIDEO_DOLBY_VISION,
            codecs = "dvhe.08.06",
            colorTransfer = C.COLOR_TRANSFER_ST2084
        )

        assertEquals(VideoDynamicRange.DOVI, range(format, MimeTypes.VIDEO_DOLBY_VISION))
    }

    @Test
    fun sdrCodecs_areSdr() {
        assertEquals(
            VideoDynamicRange.SDR,
            range(format(codecs = "hvc1.1.6.L120.90"), MimeTypes.VIDEO_H265)
        )
        assertEquals(
            VideoDynamicRange.SDR,
            range(format(codecs = "avc1.640028"), MimeTypes.VIDEO_H265)
        )
    }
}
