@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

enum class VideoDynamicRange(val label: String) {
    SDR("SDR"),
    HDR10("HDR10"),
    HLG("HLG"),
    DOVI("Dolby Vision");

    val isHdr: Boolean get() = this != SDR
}

/** Codec prefixes that identify a Dolby Vision bitstream in [Format.codecs]. */
private val DolbyVisionCodecPrefixes = listOf("dvhe", "dvh1", "dvav", "dva1", "dav1")

/**
 * The range cues drawn over [format] have to hold up against.
 *
 * Taken from the format the decoder is fed rather than the library's metadata, so a source that
 * arrives tonemapped — a server-side transcode of HDR down to SDR — is read as the SDR it now is.
 *
 * Dolby Vision is matched on its own: the range lives in the bitstream's codec signalling, and
 * colour info commonly arrives unset, so a transfer-function test alone misses it.
 */
fun videoDynamicRange(format: Format?): VideoDynamicRange {
    if (format == null) return VideoDynamicRange.SDR
    val codecs = format.codecs?.lowercase()
    val dolbyVision = MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType, ignoreCase = true) ||
        DolbyVisionCodecPrefixes.any { codecs?.startsWith(it) == true }
    val transfer = format.colorInfo?.colorTransfer
    return when {
        dolbyVision -> VideoDynamicRange.DOVI
        transfer == C.COLOR_TRANSFER_ST2084 -> VideoDynamicRange.HDR10
        transfer == C.COLOR_TRANSFER_HLG -> VideoDynamicRange.HLG
        else -> VideoDynamicRange.SDR
    }
}
