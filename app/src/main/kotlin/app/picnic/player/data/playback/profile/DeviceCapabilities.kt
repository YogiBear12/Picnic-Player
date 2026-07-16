package app.picnic.player.data.playback.profile

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.os.Build

class DeviceCapabilities(
    private val query: MediaCodecQuery
) {
    fun supportsHevc(): Boolean = query.hasCodecForMime(MediaFormat.MIMETYPE_VIDEO_HEVC)

    fun supportsHevcMain10(): Boolean = query.hasDecoder(
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        CodecProfileLevel.HEVCProfileMain10,
        CodecProfileLevel.HEVCMainTierLevel1
    )

    fun supportsHevcHDR10(): Boolean = query.hasDecoder(
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        CodecProfileLevel.HEVCProfileMain10HDR10,
        CodecProfileLevel.HEVCMainTierLevel1
    )

    fun supportsHevcHDR10Plus(): Boolean = query.hasDecoder(
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        CodecProfileLevel.HEVCProfileMain10HDR10Plus,
        CodecProfileLevel.HEVCMainTierLevel1
    )

    fun supportsHevcDolbyVision(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        query.hasCodecForMime(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)

    fun supportsHevcDolbyVisionEL(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        query.hasDecoder(
            MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
            CodecProfileLevel.DolbyVisionProfileDvheDtb, // Profile 7
            CodecProfileLevel.DolbyVisionLevelHd24
        )

    fun supportsAV1(): Boolean = query.hasCodecForMime("video/av01") // MimeTypes.VIDEO_AV1

    fun supportsAV1Main10(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        query.hasDecoder(
            "video/av01",
            CodecProfileLevel.AV1ProfileMain10,
            CodecProfileLevel.AV1Level5
        )

    fun supportsAV1HDR10(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        query.hasDecoder(
            "video/av01",
            CodecProfileLevel.AV1ProfileMain10HDR10,
            CodecProfileLevel.AV1Level5
        )

    fun supportsAV1HDR10Plus(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        query.hasDecoder(
            "video/av01",
            CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            CodecProfileLevel.AV1Level5
        )

    fun supportsAV1DolbyVision(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        query.hasDecoder(
            MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
            CodecProfileLevel.DolbyVisionProfileDvav110, // Profile 10
            CodecProfileLevel.DolbyVisionLevelHd24
        )

    fun getMaxAvcLevel(): Int = query.getDecoderLevel(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        CodecProfileLevel.AVCProfileHigh
    )

    fun getMaxHevcLevel(): Int = query.getDecoderLevel(
        MediaFormat.MIMETYPE_VIDEO_HEVC,
        CodecProfileLevel.HEVCProfileMain
    )
}
