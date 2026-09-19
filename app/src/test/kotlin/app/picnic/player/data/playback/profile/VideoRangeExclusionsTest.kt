package app.picnic.player.data.playback.profile

import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRangeExclusionsTest {
    @Test
    fun nativeProfile7Decoder_allowsBothEnhancementLayerRanges() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = true,
            supportsHevcDolbyVisionProfile7 = true,
            supportsHevcDolbyVisionProfile8 = false,
            supportsHevcHDR10 = false,
            supportsHevcHDR10Plus = false,
            hevcDoviHdr10PlusBug = false
        )

        assertFalse(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
    }

    @Test
    fun profile8DecoderAlone_allowsBothEnhancementLayerRanges() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = true,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcDolbyVisionProfile8 = true,
            supportsHevcHDR10 = false,
            supportsHevcHDR10Plus = false,
            hevcDoviHdr10PlusBug = false
        )

        assertFalse(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
    }

    @Test
    fun hdr10OnlyDecoder_allowsProfile7ElButStillExcludesSingleLayerDovi() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = false,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcDolbyVisionProfile8 = false,
            supportsHevcHDR10 = true,
            supportsHevcHDR10Plus = false,
            hevcDoviHdr10PlusBug = false
        )

        assertFalse(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
        assertTrue(VideoRangeType.DOVI.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_HDR10.serialName in ranges)
    }

    @Test
    fun hdr10PlusOnlyDecoder_separatesTheTwoEnhancementLayerRanges() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = false,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcDolbyVisionProfile8 = false,
            supportsHevcHDR10 = false,
            supportsHevcHDR10Plus = true,
            hevcDoviHdr10PlusBug = false
        )

        assertTrue(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
    }

    @Test
    fun noHdrDecoderAtAll_excludesEveryDolbyVisionRange() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = false,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcDolbyVisionProfile8 = false,
            supportsHevcHDR10 = false,
            supportsHevcHDR10Plus = false,
            hevcDoviHdr10PlusBug = false
        )

        assertTrue(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
        assertTrue(VideoRangeType.DOVI.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_HDR10.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName in ranges)
        assertTrue(VideoRangeType.HDR10.serialName in ranges)
    }

    @Test
    fun hdr10PlusDefect_excludesHdr10PlusDoviRangesEvenWithADecoder() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = true,
            supportsHevcDolbyVisionProfile7 = true,
            supportsHevcDolbyVisionProfile8 = true,
            supportsHevcHDR10 = true,
            supportsHevcHDR10Plus = true,
            hevcDoviHdr10PlusBug = true
        )

        assertTrue(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
    }
}
