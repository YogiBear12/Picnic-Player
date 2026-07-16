package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * The capability profile sent to the server's PlaybackInfo.
 * Permissive direct play (ExoPlayer + from-source FFmpeg/libgav1 handle most
 * containers/codecs) with an HLS/H.264 transcode fallback; ASS/SSA/PGS handled
 * client-side by libass so they're requested embedded.
 */
object MediaProfiles {

    fun deviceProfile(): DeviceProfile = DeviceProfile(
        name = "Picnic Player",
        maxStreamingBitrate = 120_000_000,
        maxStaticBitrate = 100_000_000,
        directPlayProfiles = listOf(
            DirectPlayProfile(type = DlnaProfileType.VIDEO, container = VIDEO_CONTAINERS),
            DirectPlayProfile(type = DlnaProfileType.AUDIO, container = AUDIO_CONTAINERS)
        ),
        transcodingProfiles = listOf(
            TranscodingProfile(
                type = DlnaProfileType.VIDEO,
                container = "ts",
                videoCodec = "h264",
                audioCodec = "aac,ac3,eac3,mp3",
                protocol = MediaStreamProtocol.HLS,
                context = EncodingContext.STREAMING,
                conditions = emptyList<ProfileCondition>()
            )
        ),
        containerProfiles = emptyList(),
        codecProfiles = emptyList(),
        subtitleProfiles = listOf(
            SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EMBED),
            SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EMBED),
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EMBED),
            SubtitleProfile(format = "subrip", method = SubtitleDeliveryMethod.EMBED),
            SubtitleProfile(format = "pgssub", method = SubtitleDeliveryMethod.EMBED),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL)
        )
    )

    private const val VIDEO_CONTAINERS =
        "mp4,mkv,webm,ts,m2ts,mov,avi,flv,m4v,3gp,mpegts,mpg,mpeg,wmv,asf"
    private const val AUDIO_CONTAINERS =
        "mp3,flac,aac,m4a,m4b,ogg,oga,opus,wav,webma,wma"
}
