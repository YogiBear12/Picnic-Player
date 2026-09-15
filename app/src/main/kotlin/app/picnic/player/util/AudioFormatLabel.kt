package app.picnic.player.util

import java.util.Locale

private val CodecNames = mapOf(
    "ac3" to "DD",
    "ac-3" to "DD",
    "eac3" to "DD+",
    "e-ac-3" to "DD+",
    "ec-3" to "DD+",
    "truehd" to "TrueHD",
    "mlp" to "TrueHD",
    "dts" to "DTS",
    "dca" to "DTS",
    "dtshd" to "DTS-HD",
    "dts-hd" to "DTS-HD",
    "dtshd_ma" to "DTS-HD",
    "aac" to "AAC",
    "mp3" to "MP3",
    "pcm" to "PCM",
    "pcm_s16le" to "PCM",
    "pcm_s24le" to "PCM"
)

fun audioCodecLabel(codec: String?): String? {
    val key = codec?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    CodecNames[key]?.let { return it }
    val prefix = CodecNames.keys.firstOrNull { key.startsWith(it) }
    return prefix?.let { CodecNames[it] } ?: key.uppercase(Locale.ROOT)
}

/**
 * Atmos and DTS:X are object-based, so a channel count alongside them only names the bed they fold
 * down to. The server reports the format inconsistently — a spatial field on newer versions, the
 * codec profile or the composed title on older ones.
 */
fun audioSpatialLabel(spatialFormat: String?, profile: String?, displayTitle: String?): String? {
    val haystack = listOfNotNull(spatialFormat, profile, displayTitle).joinToString(" ")
    return when {
        haystack.contains("atmos", ignoreCase = true) -> "Atmos"
        haystack.contains("dts:x", ignoreCase = true) -> "DTS:X"
        haystack.contains("dtsx", ignoreCase = true) -> "DTS:X"
        else -> null
    }
}

fun audioChannelLabel(channels: Int?, channelLayout: String?): String? {
    channelLayout?.trim()?.takeIf { Regex("^\\d\\.\\d$").matches(it) }?.let { return it }
    return when (channels) {
        null, 0 -> null
        1 -> "Mono"
        2 -> "Stereo"
        3 -> "2.1"
        6 -> "5.1"
        7 -> "6.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }
}

/** One chip: the codec, then whichever of spatial format or channel count says more. */
fun audioFormatLabel(
    codec: String?,
    channels: Int?,
    channelLayout: String?,
    spatialFormat: String?,
    profile: String?,
    displayTitle: String?
): String? {
    val parts = listOfNotNull(
        audioCodecLabel(codec),
        audioSpatialLabel(spatialFormat, profile, displayTitle) ?: audioChannelLabel(channels, channelLayout)
    )
    return parts.joinToString(" ").takeIf { it.isNotEmpty() }
}
