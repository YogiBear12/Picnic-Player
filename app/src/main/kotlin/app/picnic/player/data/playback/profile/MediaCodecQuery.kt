package app.picnic.player.data.playback.profile

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

class MediaCodecQuery(
    private val mediaCodecList: MediaCodecList,
    private val softwareCodecsEnabled: Boolean = false
) {
    private val MediaCodecInfo.isSoftwareCodec: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isSoftwareOnly

    private val decoders: List<MediaCodecInfo> by lazy {
        mediaCodecList.codecInfos
            .filter { !it.isEncoder }
            .filter { softwareCodecsEnabled || !it.isSoftwareCodec }
    }

    private fun decoderInfos(): List<MediaCodecInfo> = decoders

    fun hasCodecForMime(mime: String): Boolean = decoderInfos().any { info ->
        info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }

    fun hasDecoder(mime: String, profile: Int, level: Int): Boolean = decoderInfos().any { info -> supportsProfileLevel(info, mime, profile, level) }

    fun getDecoderLevel(mime: String, profile: Int): Int {
        var maxLevel = 0
        for (info in decoderInfos()) {
            val capabilities = getCapabilitiesOrNull(info, mime) ?: continue
            for (profileLevel in capabilities.profileLevels) {
                if (profileLevel.profile == profile) {
                    maxLevel = maxOf(maxLevel, profileLevel.level)
                }
            }
        }
        return maxLevel
    }

    private fun supportsProfileLevel(info: MediaCodecInfo, mime: String, profile: Int, level: Int): Boolean {
        val capabilities = getCapabilitiesOrNull(info, mime) ?: return false
        return capabilities.profileLevels.any { profileLevel ->
            profileLevel.profile == profile && meetsLevelRequirement(mime, profileLevel.level, level)
        }
    }

    private fun meetsLevelRequirement(mime: String, decoderLevel: Int, requiredLevel: Int): Boolean {
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_H263, ignoreCase = true)) {
            if (decoderLevel != requiredLevel &&
                decoderLevel == android.media.MediaCodecInfo.CodecProfileLevel.H263Level45 &&
                requiredLevel > android.media.MediaCodecInfo.CodecProfileLevel.H263Level10
            ) {
                return false
            }
        }
        return decoderLevel >= requiredLevel
    }

    private fun getCapabilitiesOrNull(info: MediaCodecInfo, mime: String): MediaCodecInfo.CodecCapabilities? {
        if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) return null
        return try {
            info.getCapabilitiesForType(mime)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
