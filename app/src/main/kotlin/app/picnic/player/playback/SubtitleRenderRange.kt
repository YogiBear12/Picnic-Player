@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

/** Dynamic range of the picture a subtitle cue is drawn over. */
enum class SubtitleRenderRange { SDR, HDR }

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
fun subtitleRenderRange(format: Format?): SubtitleRenderRange {
    if (format == null) return SubtitleRenderRange.SDR
    val transfer = format.colorInfo?.colorTransfer
    val hdr = transfer == C.COLOR_TRANSFER_ST2084 ||
        transfer == C.COLOR_TRANSFER_HLG ||
        MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType, ignoreCase = true) ||
        format.codecs?.lowercase()?.let { codecs -> DolbyVisionCodecPrefixes.any(codecs::startsWith) } == true
    return if (hdr) SubtitleRenderRange.HDR else SubtitleRenderRange.SDR
}
