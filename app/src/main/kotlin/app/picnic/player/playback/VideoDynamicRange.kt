@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.MimeTypes

enum class VideoDynamicRange {
    SDR,
    HDR10,
    HLG,
    DOVI;

    val isHdr: Boolean get() = this != SDR
}

internal val DolbyVisionCodecPrefixes = listOf("dvhe", "dvh1", "dvav", "dva1", "dav1")

fun videoDynamicRange(output: VideoOutput): VideoDynamicRange {
    val format = output.format
    val codecs = format.codecs?.lowercase()
    val dolbyVision = MimeTypes.VIDEO_DOLBY_VISION.equals(output.decoder.mimeType, ignoreCase = true) &&
        (
            MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType, ignoreCase = true) ||
                DolbyVisionCodecPrefixes.any { codecs?.startsWith(it) == true }
            )
    val transfer = format.colorInfo?.colorTransfer
    return when {
        dolbyVision -> VideoDynamicRange.DOVI
        transfer == C.COLOR_TRANSFER_ST2084 -> VideoDynamicRange.HDR10
        transfer == C.COLOR_TRANSFER_HLG -> VideoDynamicRange.HLG
        else -> VideoDynamicRange.SDR
    }
}
