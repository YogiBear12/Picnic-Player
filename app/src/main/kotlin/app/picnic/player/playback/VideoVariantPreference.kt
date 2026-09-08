@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import org.jellyfin.sdk.model.api.MediaStream

fun TrackSelectionParameters.withPreferredVideoMimeTypes(mediaStreams: List<MediaStream>): TrackSelectionParameters = buildUpon()
    .setPreferredVideoMimeTypes(*preferredVideoMimeTypes(mediaStreams).toTypedArray())
    .build()

internal fun preferredVideoMimeTypes(mediaStreams: List<MediaStream>): List<String> = when (mediaStreams.videoStream?.codec?.lowercase()) {
    "hevc", "h265" -> listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265)
    "av1" -> listOf(MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_AV1)
    "vp9" -> listOf(MimeTypes.VIDEO_VP9)
    "h264", "avc" -> listOf(MimeTypes.VIDEO_H264)
    else -> emptyList()
}
