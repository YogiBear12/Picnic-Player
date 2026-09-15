package app.picnic.player.ui.player.osd

import app.picnic.player.data.playback.PlayMethodKind
import app.picnic.player.data.playback.quality.QualityRung
import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import app.picnic.player.ui.common.formatClock
import java.util.Locale
import kotlin.math.abs

internal fun formatDelay(ms: Long): String {
    val sign = if (ms > 0) {
        "+"
    } else if (ms < 0) {
        "-"
    } else {
        ""
    }
    return "$sign%.1fs".format(abs(ms / 1000.0))
}

internal fun formatSpeed(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$text×"
}

internal fun audioBoostLabel(level: AudioBoost): String = when (level) {
    AudioBoost.OFF -> "Off"
    AudioBoost.LOW -> "Low"
    AudioBoost.MED -> "Medium"
    AudioBoost.HIGH -> "High"
}

internal fun nightModeLabel(level: NightMode): String = when (level) {
    NightMode.OFF -> "Off"
    NightMode.LIGHT -> "Light"
    NightMode.STRONG -> "Strong"
}

internal fun sleepModeLabel(mode: SleepMode, isEpisode: Boolean): String = when (mode) {
    SleepMode.OFF -> "Off"
    SleepMode.MIN_15 -> "15 minutes"
    SleepMode.MIN_30 -> "30 minutes"
    SleepMode.MIN_45 -> "45 minutes"
    SleepMode.MIN_60 -> "60 minutes"
    SleepMode.MIN_90 -> "90 minutes"
    SleepMode.MIN_120 -> "120 minutes"
    SleepMode.END_OF_EPISODE -> if (isEpisode) "End of episode" else "End of movie"
}

internal fun sleepSummary(state: SleepTimerState, isEpisode: Boolean): String = when {
    state.mode == SleepMode.OFF -> "Off"
    state.mode.durationSeconds != null && state.remainingMs > 0 ->
        "${sleepModeLabel(state.mode, isEpisode)} · ${formatClock(state.remainingMs)} left"
    else -> sleepModeLabel(state.mode, isEpisode)
}

internal data class ServerTranscode(
    val resolution: String?,
    val bitrate: String?
) {
    val details: List<String> get() = listOfNotNull(resolution, bitrate)
}

internal fun serverTranscode(
    playMethod: PlayMethodKind?,
    rung: QualityRung?,
    height: Int?,
    bitrate: Int?
): ServerTranscode? {
    if (playMethod != PlayMethodKind.TRANSCODE || rung != null) return null
    return ServerTranscode(
        resolution = height?.takeIf { it > 0 }?.let { ladderResolution(it) },
        bitrate = bitrate?.takeIf { it > 0 }?.let { formatBitrate(it.toLong()) }
    )
}

private val CamelCaseBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")

internal fun humanizeReason(raw: String): String {
    if (raw.contains(' ')) return raw
    return raw.replace(CamelCaseBoundary, " ").lowercase().replaceFirstChar { it.uppercase() }
}

internal fun qualitySummary(playMethod: PlayMethodKind?, rung: QualityRung?, serverTranscode: ServerTranscode?): String {
    val details = serverTranscode?.details.orEmpty()
    return when {
        playMethod != PlayMethodKind.TRANSCODE -> "Original"
        rung != null -> rung.label
        details.isEmpty() -> "Transcoding…"
        else -> details.joinToString(" · ")
    }
}

private fun ladderResolution(height: Int): String = QualityRung.entries.sortedBy { it.height }.firstOrNull { height <= it.height }?.frameLabel ?: "${height}p"

internal fun formatBitrate(bitrate: Long): String {
    val kbps = bitrate / 1000.0
    val mbps = kbps / 1000.0
    return when {
        mbps >= 1.0 -> String.format(Locale.getDefault(), "%.2f Mbps", mbps)
        else -> String.format(Locale.getDefault(), "%.0f Kbps", kbps)
    }
}
