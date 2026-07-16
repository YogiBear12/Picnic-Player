package app.picnic.player.data.playback.profile

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Size

class MediaCodecQuery(
    private val mediaCodecList: MediaCodecList,
    private val softwareCodecsEnabled: Boolean = false
) {
    private val MediaCodecInfo.isSoftwareCodec: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isSoftwareOnly

    private fun decoderInfos(): Sequence<MediaCodecInfo> = mediaCodecList.codecInfos.asSequence()
        .filter { !it.isEncoder }
        .filter { softwareCodecsEnabled || !it.isSoftwareCodec }

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

    fun getMaxResolution(mime: String): Size {
        val resolutions = decoderInfos()
            .mapNotNull { info -> getCapabilitiesOrNull(info, mime)?.videoCapabilities }
            .mapNotNull { vc ->
                val w = vc.supportedWidths?.upper ?: return@mapNotNull null
                val h = vc.supportedHeights?.upper ?: return@mapNotNull null
                w to h
            }

        val maxWidth = resolutions.maxOfOrNull { it.first } ?: 0
        val maxHeight = resolutions.maxOfOrNull { it.second } ?: 0
        return Size(maxWidth, maxHeight)
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

    private fun getCapabilitiesOrNull(info: MediaCodecInfo, mime: String): MediaCodecInfo.CodecCapabilities? = try {
        info.getCapabilitiesForType(mime)
    } catch (e: IllegalArgumentException) {
        null
    }
}
