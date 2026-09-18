@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import app.picnic.player.data.playback.PlayMethodKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.TranscodingInfo

data class PlayingStream(
    val videoDirect: Boolean,
    val audioDirect: Boolean,
    val width: Int?,
    val height: Int?,
    val videoCodec: String?,
    val videoBitrate: Long?,
    val dynamicRange: VideoDynamicRange?,
    val audioCodec: String?,
    val audioChannels: Int?
)

fun playingStream(
    playMethod: PlayMethodKind?,
    transcodingInfo: TranscodingInfo?,
    mediaStreams: List<MediaStream>,
    selectedAudioIndex: Int?,
    videoFormat: Format?,
    audioFormat: Format?,
    videoDecoderMimeType: String?
): PlayingStream {
    val directPlay = playMethod == PlayMethodKind.DIRECT_PLAY
    val videoDirect = directPlay || transcodingInfo?.isVideoDirect == true
    val audioDirect = directPlay || transcodingInfo?.isAudioDirect == true

    val videoStream = mediaStreams.videoStream
    val audioStreams = mediaStreams.filter { it.type == MediaStreamType.AUDIO }
    val audioStream = audioStreams.firstOrNull { it.index == selectedAudioIndex } ?: audioStreams.singleOrNull()

    val sourceVideoCodec = listOfNotNull(videoStream?.codec?.uppercase(), videoStream?.profile)
        .joinToString(" ")
        .takeIf { it.isNotBlank() }

    return PlayingStream(
        videoDirect = videoDirect,
        audioDirect = audioDirect,
        width = videoFormat?.width?.takeIf { it > 0 },
        height = videoFormat?.height?.takeIf { it > 0 },
        videoCodec = if (videoDirect) {
            sourceVideoCodec ?: codecLabel(videoFormat?.sampleMimeType)
        } else {
            codecLabel(videoFormat?.sampleMimeType) ?: transcodingInfo?.videoCodec?.uppercase()
        },
        videoBitrate = if (videoDirect) {
            videoStream?.bitRate?.positiveLong() ?: videoFormat?.bitrate?.positiveLong()
        } else {
            videoFormat?.bitrate?.positiveLong() ?: transcodingInfo?.bitrate?.positiveLong()
        },
        dynamicRange = videoFormat?.let { videoDynamicRange(it, videoDecoderMimeType) },
        audioCodec = if (audioDirect) {
            audioStream?.codec?.uppercase() ?: codecLabel(audioFormat?.sampleMimeType)
        } else {
            codecLabel(audioFormat?.sampleMimeType) ?: transcodingInfo?.audioCodec?.uppercase()
        },
        audioChannels = if (audioDirect) {
            audioStream?.channels?.takeIf { it > 0 } ?: audioFormat?.channelCount?.takeIf { it > 0 }
        } else {
            transcodingInfo?.audioChannels?.takeIf { it > 0 } ?: audioFormat?.channelCount?.takeIf { it > 0 }
        }
    )
}

private fun Int.positiveLong(): Long? = toLong().takeIf { it > 0 }

private fun codecLabel(mimeType: String?): String? = when (mimeType) {
    null -> null
    MimeTypes.VIDEO_H264 -> "H264"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.VIDEO_MPEG2 -> "MPEG2"
    MimeTypes.VIDEO_DOLBY_VISION -> "DOVI"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3 -> "EAC3"
    MimeTypes.AUDIO_AC4 -> "AC4"
    MimeTypes.AUDIO_DTS -> "DTS"
    MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
    MimeTypes.AUDIO_TRUEHD -> "TRUEHD"
    MimeTypes.AUDIO_OPUS -> "OPUS"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_VORBIS -> "VORBIS"
    else -> mimeType.substringAfter('/').uppercase()
}
