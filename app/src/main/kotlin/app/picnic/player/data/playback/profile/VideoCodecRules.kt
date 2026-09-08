package app.picnic.player.data.playback.profile

internal data class VideoDecoderSupport(
    val avc: Boolean,
    val avcHigh10: Boolean,
    val avcLevel: Int,
    val avcHigh10Level: Int,
    val hevc: Boolean,
    val hevcMain10: Boolean,
    val hevcLevel: Int,
    val hevcMain10Level: Int,
    val av1: Boolean,
    val av1TenBit: Boolean,
    val vp9: Boolean,
    val vp9TenBit: Boolean,
    val vp8: Boolean,
    val mpeg2: Boolean
)

internal data class ProfileLevelLimit(
    val profiles: List<String>,
    val maxLevel: Int
)

internal data class VideoCodecRule(
    val codec: String,
    val playableProfiles: List<String> = emptyList(),
    val levelLimits: List<ProfileLevelLimit> = emptyList(),
    val maxBitDepth: Int? = null
)

private const val EIGHT_BIT = 8

internal const val AVC_TRANSCODE_TARGET_PROFILE = "high"
internal const val HEVC_TRANSCODE_TARGET_PROFILE = "main"

private val avcEightBitProfiles = listOf(AVC_TRANSCODE_TARGET_PROFILE, "main", "baseline", "constrained baseline")
private const val AVC_TEN_BIT_PROFILE = "high 10"
private val hevcEightBitProfiles = listOf(HEVC_TRANSCODE_TARGET_PROFILE)
private const val HEVC_TEN_BIT_PROFILE = "main 10"

internal fun videoCodecRules(support: VideoDecoderSupport): List<VideoCodecRule> = buildList {
    if (support.avc) add(avcRule(support))
    if (support.hevc) add(hevcRule(support))
    if (support.av1) add(bitDepthRule("av1", support.av1TenBit))
    if (support.vp9) add(bitDepthRule("vp9", support.vp9TenBit))
    if (support.vp8) add(VideoCodecRule("vp8"))
    if (support.mpeg2) {
        add(VideoCodecRule("mpeg2video"))
        add(VideoCodecRule("mpeg"))
    }
}

private fun avcRule(support: VideoDecoderSupport): VideoCodecRule = VideoCodecRule(
    codec = "h264",
    playableProfiles = if (support.avcHigh10) avcEightBitProfiles + AVC_TEN_BIT_PROFILE else avcEightBitProfiles,
    levelLimits = buildList {
        levelLimit(avcEightBitProfiles, support.avcLevel)?.let(::add)
        if (support.avcHigh10) levelLimit(listOf(AVC_TEN_BIT_PROFILE), support.avcHigh10Level)?.let(::add)
    }
)

private fun hevcRule(support: VideoDecoderSupport): VideoCodecRule = VideoCodecRule(
    codec = "hevc",
    playableProfiles = if (support.hevcMain10) hevcEightBitProfiles + HEVC_TEN_BIT_PROFILE else hevcEightBitProfiles,
    levelLimits = buildList {
        levelLimit(hevcEightBitProfiles, support.hevcLevel)?.let(::add)
        if (support.hevcMain10) levelLimit(listOf(HEVC_TEN_BIT_PROFILE), support.hevcMain10Level)?.let(::add)
    }
)

private fun bitDepthRule(codec: String, tenBit: Boolean): VideoCodecRule = VideoCodecRule(
    codec = codec,
    maxBitDepth = if (tenBit) null else EIGHT_BIT
)

private fun levelLimit(profiles: List<String>, level: Int): ProfileLevelLimit? = if (level > UNKNOWN_STREAM_LEVEL) {
    ProfileLevelLimit(profiles, level)
} else {
    null
}
