package app.picnic.player.data.playback.quality

import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

enum class QualityRung(
    val videoBitrate: Int,
    val width: Int,
    val height: Int,
    val qualifier: String? = null
) {
    P1080_12(12_000_000, 1920, 1080, "High"),
    P1080_8(8_000_000, 1920, 1080, "Medium"),
    P1080_6(6_000_000, 1920, 1080, "Low"),
    P720_4(4_000_000, 1280, 720, "High"),
    P720_3(3_000_000, 1280, 720, "Medium"),
    P480_2(2_000_000, 854, 480);

    val label: String get() = if (qualifier == null) "${height}p" else "${height}p ($qualifier)"

    val bitrateLabel: String get() = formatMbps(videoBitrate)

    val sizeHint: String get() = "about ${formatHourlySize(videoBitrate)}/hr"

    companion object {
        fun named(name: String?): QualityRung? = entries.firstOrNull { it.name == name }
    }
}

sealed interface QualityOption {
    data object Original : QualityOption
    data class Transcode(val rung: QualityRung) : QualityOption
}

data class SourceQuality(
    val videoBitrate: Int?,
    val totalBitrate: Int?,
    val videoCodec: String?
) {
    private val comparisonBitrate: Int?
        get() {
            val bitrate = videoBitrate?.takeIf { it > 0 } ?: return null
            val efficient = videoCodec?.lowercase() in EFFICIENT_CODECS
            return if (efficient) (bitrate * EFFICIENT_CODEC_FACTOR).roundToInt() else bitrate
        }

    internal fun savesBandwidth(rung: QualityRung): Boolean = totalBitrate == null || totalBitrate <= 0 || rung.videoBitrate < totalBitrate

    internal fun worthConverting(rung: QualityRung): Boolean {
        val comparison = comparisonBitrate
        val doesNotExceedSource = comparison == null || rung.videoBitrate <= comparison
        return savesBandwidth(rung) && doesNotExceedSource
    }

    companion object {
        // Servers up to 10.11 report the container total as the video stream's bitrate when the
        // probe omits a per-stream value, so a value equal to the total is derived from instead.
        fun of(totalBitrate: Int?, streams: List<MediaStream>): SourceQuality {
            val total = totalBitrate?.takeIf { it > 0 }
            val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
            val reported = video?.bitRate?.takeIf { it > 0 && it != total }
            val audio = streams.filter { it.type == MediaStreamType.AUDIO }
            val videoBitrate = when {
                reported != null -> reported
                total == null -> null
                audio.isNotEmpty() && audio.all { (it.bitRate ?: 0) > 0 } ->
                    (total - audio.sumOf { it.bitRate ?: 0 }).takeIf { it > 0 } ?: total
                else -> total
            }
            return SourceQuality(videoBitrate, total, video?.codec)
        }
    }
}

// h264 output needs ~1.5x the bitrate of an HEVC/AV1/VP9 source for the same picture.
private const val EFFICIENT_CODEC_FACTOR = 1.5

private val EFFICIENT_CODECS = setOf("hevc", "h265", "av1", "vp9")

fun rungsFor(source: SourceQuality): List<QualityRung> = QualityRung.entries.filter { source.worthConverting(it) }

fun qualityOptions(source: SourceQuality): List<QualityOption> = buildList {
    add(QualityOption.Original)
    rungsFor(source).forEach { add(QualityOption.Transcode(it)) }
}

fun automaticRung(source: SourceQuality): QualityRung = QualityRung.entries.firstOrNull { source.savesBandwidth(it) } ?: QualityRung.entries.last()

fun defaultQualityLabel(rung: QualityRung?): String = if (rung == null) "Original" else "${rung.label} · ${rung.bitrateLabel}"

private fun formatMbps(bitrate: Int): String {
    val mbps = bitrate / 1_000_000.0
    val text = if (mbps % 1.0 == 0.0) mbps.roundToInt().toString() else mbps.toString()
    return "$text Mbps"
}

private fun formatHourlySize(bitrate: Int): String {
    val megabytes = bitrate / 1_000_000.0 * 450.0
    if (megabytes < 1_000.0) {
        val rounded = (megabytes / 50.0).roundToInt() * 50
        return "$rounded MB"
    }
    val gigabytes = (megabytes / 1_000.0 * 2.0).roundToInt() / 2.0
    val text = if (gigabytes % 1.0 == 0.0) gigabytes.roundToInt().toString() else gigabytes.toString()
    return "$text GB"
}
