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
    P2160_40(40_000_000, 3840, 2160, "High"),
    P2160_20(20_000_000, 3840, 2160, "Medium"),
    P1080_12(12_000_000, 1920, 1080, "High"),
    P1080_8(8_000_000, 1920, 1080, "Medium"),
    P1080_6(6_000_000, 1920, 1080, "Low"),
    P720_4(4_000_000, 1280, 720, "High"),
    P720_3(3_000_000, 1280, 720, "Medium"),
    P480_2(2_000_000, 854, 480);

    val frameLabel: String get() = if (height >= 2160) "4K" else "${height}p"

    val label: String get() = if (qualifier == null) frameLabel else "$frameLabel ($qualifier)"

    val bitrateLabel: String get() = formatMbps(videoBitrate)

    val sizeHint: String get() = "about ${formatHourlySize(videoBitrate)}/hr"

    internal fun fitsWithin(frame: QualityRung): Boolean = width <= frame.width && height <= frame.height

    companion object {
        fun named(name: String?): QualityRung? = entries.firstOrNull { it.name == name }

        fun conversionCeiling(allowFourK: Boolean): QualityRung = if (allowFourK) entries.first() else entries.first { it.height < 2160 }
    }
}

sealed interface QualityOption {
    data object Original : QualityOption
    data class Transcode(val rung: QualityRung) : QualityOption
}

data class SourceQuality(
    val videoBitrate: Int?,
    val totalBitrate: Int?,
    val videoCodec: String?,
    val width: Int? = null,
    val height: Int? = null
) {
    private val comparisonBitrate: Int?
        get() {
            val bitrate = videoBitrate?.takeIf { it > 0 } ?: return null
            val efficient = videoCodec?.lowercase() in EFFICIENT_CODECS
            return if (efficient) (bitrate * EFFICIENT_CODEC_FACTOR).roundToInt() else bitrate
        }

    internal val frame: QualityRung?
        get() {
            val sourceWidth = width?.takeIf { it > 0 } ?: return null
            val sourceHeight = height?.takeIf { it > 0 } ?: return null
            return FRAMES.firstOrNull { sourceWidth >= it.width || sourceHeight >= it.height } ?: FRAMES.last()
        }

    internal fun savesBandwidth(rung: QualityRung): Boolean = totalBitrate == null || totalBitrate <= 0 || rung.videoBitrate < totalBitrate

    internal fun withinSourceFrame(rung: QualityRung): Boolean {
        val sourceFrame = frame ?: return true
        return rung.fitsWithin(sourceFrame)
    }

    internal fun worthConverting(rung: QualityRung): Boolean {
        val comparison = comparisonBitrate
        val doesNotExceedSource = comparison == null || rung.videoBitrate <= comparison
        return savesBandwidth(rung) && doesNotExceedSource && withinSourceFrame(rung)
    }

    companion object {
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
            return SourceQuality(videoBitrate, total, video?.codec, video?.width, video?.height)
        }
    }
}

private const val EFFICIENT_CODEC_FACTOR = 1.5

private val EFFICIENT_CODECS = setOf("hevc", "h265", "av1", "vp9")

private val FRAMES = QualityRung.entries.distinctBy { it.width to it.height }

fun rungsFor(source: SourceQuality, ceiling: QualityRung): List<QualityRung> = QualityRung.entries.filter { it.fitsWithin(ceiling) && source.worthConverting(it) }

fun qualityOptions(source: SourceQuality, ceiling: QualityRung): List<QualityOption> = buildList {
    add(QualityOption.Original)
    rungsFor(source, ceiling).forEach { add(QualityOption.Transcode(it)) }
}

fun automaticRung(source: SourceQuality, ceiling: QualityRung): QualityRung = QualityRung.entries.firstOrNull { it.fitsWithin(ceiling) && source.savesBandwidth(it) }
    ?: QualityRung.entries.last()

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

data class NegotiatedSource(
    val supportsDirectPlay: Boolean?,
    val transcodingUrl: String?,
    val quality: SourceQuality
) {
    val serverIsConverting: Boolean get() = supportsDirectPlay != true && transcodingUrl != null
}

sealed interface ConversionPlan {
    data object AsNegotiated : ConversionPlan

    data class Renegotiate(val rung: QualityRung) : ConversionPlan
}

fun conversionPlan(
    source: NegotiatedSource,
    requested: QualityRung?,
    ceiling: QualityRung
): ConversionPlan {
    if (!source.serverIsConverting) return ConversionPlan.AsNegotiated
    if (requested != null && requested.fitsWithin(ceiling)) return ConversionPlan.AsNegotiated
    return ConversionPlan.Renegotiate(automaticRung(source.quality, ceiling))
}
