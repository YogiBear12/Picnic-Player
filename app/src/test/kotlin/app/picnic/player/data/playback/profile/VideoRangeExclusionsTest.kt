package app.picnic.player.data.playback.profile

import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRangeExclusionsTest {
    @Test
    fun dvWithoutEnhancementLayer_excludesEl_butAllowsDoviWithHdr10() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = true,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcHDR10 = true,
            supportsHevcHDR10Plus = false,
            forceDoviProfile7 = false,
            hevcDoviHdr10PlusBug = false
        )

        assertTrue(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_INVALID.serialName in ranges)
        assertTrue(VideoRangeType.HDR10_PLUS.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_HDR10.serialName in ranges)
        assertFalse(VideoRangeType.DOVI.serialName in ranges)
        assertFalse(VideoRangeType.HDR10.serialName in ranges)
    }

    @Test
    fun forceDoviProfile7_keepsElRangesPlayable() {
        val ranges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = true,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcHDR10 = true,
            supportsHevcHDR10Plus = false,
            forceDoviProfile7 = true,
            hevcDoviHdr10PlusBug = false
        )

        assertFalse(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
    }

    @Test
    fun noDolbyVisionDecoder_excludesSingleLayerDoviUnlessHdr10FallbackExists() {
        val withHdr10 = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = false,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcHDR10 = true,
            supportsHevcHDR10Plus = false,
            forceDoviProfile7 = false,
            hevcDoviHdr10PlusBug = false
        )
        assertTrue(VideoRangeType.DOVI.serialName in withHdr10)
        assertFalse(VideoRangeType.DOVI_WITH_HDR10.serialName in withHdr10)

        val withoutHdr10 = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = false,
            supportsHevcDolbyVisionProfile7 = false,
            supportsHevcHDR10 = false,
            supportsHevcHDR10Plus = false,
            forceDoviProfile7 = false,
            hevcDoviHdr10PlusBug = false
        )
        assertTrue(VideoRangeType.DOVI_WITH_HDR10.serialName in withoutHdr10)
        assertTrue(VideoRangeType.HDR10.serialName in withoutHdr10)
    }
}
