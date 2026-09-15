package app.picnic.player.util

import java.util.Locale

/** The stream fields every audio label reads. They always arrive together, off one `MediaStream`. */
data class AudioFormat(
    val codec: String? = null,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val spatialFormat: String? = null,
    val profile: String? = null,
    val displayTitle: String? = null
)

private val AudioCodecNames = mapOf(
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

private val ChannelLayout = Regex("^\\d\\.\\d$")

fun AudioFormat.codecLabel(): String? {
    val key = codec?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    AudioCodecNames[key]?.let { return it }
    val prefix = AudioCodecNames.keys.firstOrNull { key.startsWith(it) }
    return prefix?.let { AudioCodecNames[it] } ?: key.uppercase(Locale.ROOT)
}

/**
 * Atmos and DTS:X are object-based, so a channel count alongside them only names the bed they fold
 * down to. The server reports the format inconsistently — a spatial field on newer versions, the
 * codec profile or the composed title on older ones.
 */
fun AudioFormat.spatialLabel(): String? {
    val haystack = listOfNotNull(spatialFormat, profile, displayTitle).joinToString(" ")
    return when {
        haystack.contains("atmos", ignoreCase = true) -> "Atmos"
        haystack.contains("dts:x", ignoreCase = true) -> "DTS:X"
        haystack.contains("dtsx", ignoreCase = true) -> "DTS:X"
        else -> null
    }
}

fun AudioFormat.channelLabel(): String? {
    channelLayout?.trim()?.takeIf { ChannelLayout.matches(it) }?.let { return it }
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
fun AudioFormat.label(): String? = listOfNotNull(codecLabel(), spatialLabel() ?: channelLabel())
    .joinToString(" ")
    .takeIf { it.isNotEmpty() }
