package app.picnic.player.data.playback.profile

import android.content.Context
import android.media.MediaCodecList
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.data.settings.PlaybackSettings
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.deviceprofile.DeviceProfileBuilder
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile

object DynamicProfileBuilder {
    fun build(
        androidContext: Context,
        settings: PlaybackSettings,
        rung: QualityRung? = null
    ): DeviceProfile {
        if (settings.forceDirectPlay) return forceDirectPlayProfile()

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

        val transcodeVideoCodecs = listOfNotNull(
            if (capabilities.supportsHevc()) "hevc" else null,
            "h264"
        ).toTypedArray()

        val allowedVideoCodecs = listOfNotNull(
            "h264",
            if (capabilities.supportsHevc()) "hevc" else null,
            if (capabilities.supportsAV1()) "av1" else null,
            "vp8",
            "vp9",
            "mpeg",
            "mpeg2video"
        ).toTypedArray()

        val unsupportedHevcRanges = unsupportedHevcRangeTypes(
            supportsHevcDolbyVision = capabilities.supportsHevcDolbyVision(),
            supportsHevcDolbyVisionEL = capabilities.supportsHevcDolbyVisionEL(),
            supportsHevcHDR10 = capabilities.supportsHevcHDR10(),
            supportsHevcHDR10Plus = capabilities.supportsHevcHDR10Plus(),
            forceDoviProfile7 = settings.forceDoviProfile7
        )
        val unsupportedAv1Ranges = unsupportedAv1RangeTypes(
            supportsAV1DolbyVision = capabilities.supportsAV1DolbyVision(),
            supportsAV1HDR10 = capabilities.supportsAV1HDR10(),
            supportsAV1HDR10Plus = capabilities.supportsAV1HDR10Plus()
        )

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
                // One container only: offered a choice, the server picks fMP4, whose segments
                // fail to parse on this device.
                container = "ts"
                protocol = MediaStreamProtocol.HLS
                videoCodec(*transcodeVideoCodecs)
                // Encode-only codecs: a passed-through bitstream restarts the audio track
                // continuously here, halving playback speed.
                audioCodec("aac", "mp3")
                copyTimestamps = false
                enableSubtitlesInManifest = true
            }

            rung?.let { target ->
                transcodeVideoCodecs.forEach { codecName ->
                    codecProfile {
                        type = CodecType.VIDEO
                        codec = codecName
                        conditions {
                            ProfileConditionValue.WIDTH lowerThanOrEquals target.width
                            ProfileConditionValue.HEIGHT lowerThanOrEquals target.height
                        }
                    }
                }
            }

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

            excludeUnsupportedVideoRanges("hevc", unsupportedHevcRanges)
            excludeUnsupportedVideoRanges("av1", unsupportedAv1Ranges)

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

            subtitleProfile("vtt", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("vtt", SubtitleDeliveryMethod.HLS)
            subtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("webvtt", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("webvtt", SubtitleDeliveryMethod.HLS)
            subtitleProfile("webvtt", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("srt", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("subrip", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("subrip", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("ass", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("ass", SubtitleDeliveryMethod.ENCODE)
            subtitleProfile("ssa", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("ssa", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("ssa", SubtitleDeliveryMethod.ENCODE)
            subtitleProfile("pgssub", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("pgssub", SubtitleDeliveryMethod.ENCODE)
            subtitleProfile("dvdsub", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("dvdsub", SubtitleDeliveryMethod.ENCODE)
            subtitleProfile("dvbsub", SubtitleDeliveryMethod.EMBED)
            subtitleProfile("dvbsub", SubtitleDeliveryMethod.ENCODE)
        }
    }

    /**
     * Expert override ("Force direct play"): announce full compatibility so the server always
     * returns a direct-play source. No transcoding profile, no codec conditions, bitrate cap
     * lifted; downmix / DoVi settings are intentionally ignored. Genuinely unsupported media may
     * fail to play — that's the accepted trade-off of the Advanced toggle.
     */
    private fun forceDirectPlayProfile(): DeviceProfile = buildDeviceProfile {
        name = "Picnic Player (Direct)"
        maxStreamingBitrate = 1_000_000_000
        maxStaticBitrate = 1_000_000_000

        directPlayProfile {
            type = DlnaProfileType.VIDEO
            container(
                "mp4", "mkv", "webm", "ts", "m2ts", "mov", "avi", "flv", "m4v",
                "asf", "wmv", "3gp", "ogv", "mpg", "mpeg", "vob"
            )
        }
        directPlayProfile {
            type = DlnaProfileType.AUDIO
            container(
                "mp3", "flac", "aac", "m4a", "m4b", "ogg", "oga", "opus", "wav", "wma", "ac3", "eac3", "dts"
            )
        }

        subtitleProfile("ass", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("ssa", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("srt", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("subrip", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("pgssub", SubtitleDeliveryMethod.EMBED)
        subtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL)
    }
}

/**
 * Ask the server to remux/transcode when [codec] media uses an unsupported [VideoRangeType].
 *
 * Jellyfin only applies a codec profile when [applyConditions] match. The failing
 * `VIDEO_RANGE_TYPE notEquals …` condition then drives StreamBuilder away from Direct Play.
 * A plain "not equals" without apply-conditions would never attach to the right titles.
 */
private fun DeviceProfileBuilder.excludeUnsupportedVideoRanges(
    codec: String,
    unsupportedRangeTypes: Set<String>
) {
    if (unsupportedRangeTypes.isEmpty()) return
    val joined = unsupportedRangeTypes.joinToString("|")
    codecProfile {
        type = CodecType.VIDEO
        this.codec = codec
        conditions {
            ProfileConditionValue.VIDEO_RANGE_TYPE notEquals joined
        }
        applyConditions {
            ProfileConditionValue.VIDEO_RANGE_TYPE inCollection unsupportedRangeTypes.toList()
        }
    }
}
