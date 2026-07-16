package app.picnic.player.data.playback.profile

import android.content.Context
import android.media.MediaCodecList
import app.picnic.player.data.settings.PlaybackSettings
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile

object DynamicProfileBuilder {
    fun build(androidContext: Context, settings: PlaybackSettings): DeviceProfile {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val query = MediaCodecQuery(mediaCodecList)
        val capabilities = DeviceCapabilities(query)

        val baseAudioCodecs = arrayOf(
            "aac", "ac3", "eac3", "dts", "flac", "mp3", "opus", "vorbis", "truehd"
        )
        val allowedAudioCodecs = if (settings.downmixStereo) {
            arrayOf("aac", "mp3", "opus", "vorbis")
        } else {
            baseAudioCodecs
        }

        val allowedVideoCodecs = listOfNotNull(
            "h264",
            if (capabilities.supportsHevc()) "hevc" else null,
            if (capabilities.supportsAV1()) "av1" else null,
            "vp8",
            "vp9",
            "mpeg",
            "mpeg2video"
        ).toTypedArray()

        return buildDeviceProfile {
            name = "Picnic Player"
            maxStreamingBitrate = 120_000_000
            maxStaticBitrate = 100_000_000

            directPlayProfile {
                type = DlnaProfileType.VIDEO
                container(
                    "mp4", "mkv", "webm", "ts", "m2ts", "mov", "avi", "flv", "m4v", "asf", "wmv"
                )
                videoCodec(*allowedVideoCodecs)
                audioCodec(*allowedAudioCodecs)
            }

            directPlayProfile {
                type = DlnaProfileType.AUDIO
                container(
                    "mp3", "flac", "aac", "m4a", "m4b", "ogg", "oga", "opus", "wav", "wma"
                )
                audioCodec(*allowedAudioCodecs)
            }

            transcodingProfile {
                type = DlnaProfileType.VIDEO
                context = EncodingContext.STREAMING
                container = "ts"
                protocol = MediaStreamProtocol.HLS
                videoCodec(*allowedVideoCodecs)
                val transcodeAudioCodecs = if (settings.downmixStereo) {
                    arrayOf("aac", "mp3")
                } else {
                    arrayOf("aac", "ac3", "eac3", "mp3")
                }
                audioCodec(*transcodeAudioCodecs)
            }

            // Codec Profiles
            codecProfile {
                type = CodecType.VIDEO
                codec = "hevc"
                conditions {
                    if (!capabilities.supportsHevc()) {
                        ProfileConditionValue.VIDEO_PROFILE equals "none"
                    } else {
                        ProfileConditionValue.VIDEO_PROFILE notEquals "none"
                    }
                }

                applyConditions {
                    if (!settings.forceDoviProfile7 && !capabilities.supportsHevcDolbyVisionEL()) {
                        ProfileConditionValue.VIDEO_RANGE_TYPE inCollection listOf("DOVIWithEL", "DOVIWithELHDR10Plus")
                    }
                }
            }

            codecProfile {
                type = CodecType.VIDEO
                codec = "av1"
                conditions {
                    if (!capabilities.supportsAV1()) {
                        ProfileConditionValue.VIDEO_PROFILE equals "none"
                    } else {
                        ProfileConditionValue.VIDEO_PROFILE notEquals "none"
                    }
                }
            }

            codecProfile {
                type = CodecType.VIDEO_AUDIO
                conditions {
                    if (settings.downmixStereo) {
                        ProfileConditionValue.AUDIO_CHANNELS lowerThanOrEquals 2
                    } else {
                        ProfileConditionValue.AUDIO_CHANNELS lowerThanOrEquals 8
                    }
                }
            }

            subtitleProfile("ass", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("ssa", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("srt", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("subrip", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("pgssub", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL)
        }
    }
}
