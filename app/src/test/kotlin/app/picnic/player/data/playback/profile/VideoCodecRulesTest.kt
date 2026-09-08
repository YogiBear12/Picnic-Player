package app.picnic.player.data.playback.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodecRulesTest {
    private fun support(
        avc: Boolean = true,
        avcHigh10: Boolean = false,
        avcLevel: Int = 51,
        avcHigh10Level: Int = 0,
        hevc: Boolean = true,
        hevcMain10: Boolean = true,
        hevcLevel: Int = 153,
        hevcMain10Level: Int = 153,
        av1: Boolean = false,
        av1TenBit: Boolean = false,
        vp9: Boolean = false,
        vp9TenBit: Boolean = false,
        vp8: Boolean = false,
        mpeg2: Boolean = false
    ) = VideoDecoderSupport(
        avc = avc,
        avcHigh10 = avcHigh10,
        avcLevel = avcLevel,
        avcHigh10Level = avcHigh10Level,
        hevc = hevc,
        hevcMain10 = hevcMain10,
        hevcLevel = hevcLevel,
        hevcMain10Level = hevcMain10Level,
        av1 = av1,
        av1TenBit = av1TenBit,
        vp9 = vp9,
        vp9TenBit = vp9TenBit,
        vp8 = vp8,
        mpeg2 = mpeg2
    )

    private fun ruleFor(codec: String, support: VideoDecoderSupport) = videoCodecRules(support).single { it.codec == codec }

    @Test
    fun eightBitOnlyAvcDecoder_leavesHigh10OutOfPlayableProfiles() {
        val rule = ruleFor("h264", support(avcHigh10 = false))

        assertFalse("high 10" in rule.playableProfiles)
        assertTrue("high" in rule.playableProfiles)
        assertTrue("constrained baseline" in rule.playableProfiles)
    }

    @Test
    fun avcHigh10Decoder_addsHigh10WithItsOwnLevelLimit() {
        val rule = ruleFor("h264", support(avcHigh10 = true, avcLevel = 51, avcHigh10Level = 42))

        assertTrue("high 10" in rule.playableProfiles)
        assertEquals(AVC_TRANSCODE_TARGET_PROFILE, rule.playableProfiles.first())
        assertEquals(AVC_TRANSCODE_TARGET_PROFILE, rule.levelLimits.first().profiles.first())
        assertEquals(
            listOf(
                ProfileLevelLimit(listOf(AVC_TRANSCODE_TARGET_PROFILE, "main", "baseline", "constrained baseline"), 51),
                ProfileLevelLimit(listOf("high 10"), 42)
            ),
            rule.levelLimits
        )
    }

    @Test
    fun unknownDecoderLevel_producesNoLevelLimit() {
        val rule = ruleFor("h264", support(avcLevel = UNKNOWN_STREAM_LEVEL))

        assertTrue(rule.levelLimits.isEmpty())
    }

    @Test
    fun hevcWithoutMain10_rejectsTenBitProfile() {
        val rule = ruleFor("hevc", support(hevcMain10 = false, hevcLevel = 120))

        assertEquals(listOf("main"), rule.playableProfiles)
        assertEquals(listOf(ProfileLevelLimit(listOf("main"), 120)), rule.levelLimits)
    }

    @Test
    fun av1AndVp9WithoutTenBitDecoder_capBitDepth() {
        val rules = videoCodecRules(support(av1 = true, av1TenBit = false, vp9 = true, vp9TenBit = true))

        assertEquals(8, rules.single { it.codec == "av1" }.maxBitDepth)
        assertNull(rules.single { it.codec == "vp9" }.maxBitDepth)
    }

    @Test
    fun missingDecoders_dropCodecsFromTheProfileEntirely() {
        val codecs = videoCodecRules(support(hevc = false, av1 = false, vp9 = false, vp8 = false, mpeg2 = false))
            .map { it.codec }

        assertEquals(listOf("h264"), codecs)
    }

    @Test
    fun mpeg2Decoder_coversBothMpegCodecNames() {
        val codecs = videoCodecRules(support(mpeg2 = true)).map { it.codec }

        assertTrue("mpeg2video" in codecs)
        assertTrue("mpeg" in codecs)
    }
}
