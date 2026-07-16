package app.picnic.player.ui.player.osd

import app.picnic.player.playback.AudioBoost
import app.picnic.player.playback.NightMode
import app.picnic.player.playback.SleepMode
import app.picnic.player.playback.SleepTimerState
import kotlin.math.abs

/** Shared OSD time formatting: `m:ss` or `h:mm:ss`. */
internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Signed decisecond subtitle-offset label, e.g. "+1.5s", "-0.5s", "0.0s". */
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

/** Playback speed label without trailing zeros, e.g. "1×", "1.5×", "0.75×". */
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

internal fun sleepModeLabel(mode: SleepMode): String = when (mode) {
    SleepMode.OFF -> "Off"
    SleepMode.MIN_15 -> "15 minutes"
    SleepMode.MIN_30 -> "30 minutes"
    SleepMode.MIN_45 -> "45 minutes"
    SleepMode.MIN_60 -> "60 minutes"
    SleepMode.END_OF_EPISODE -> "End of episode"
    SleepMode.END_OF_QUEUE -> "End of queue"
}

/** Row summary for the sleep timer: shows remaining time while a duration timer counts down. */
internal fun sleepSummary(state: SleepTimerState): String = when {
    state.mode == SleepMode.OFF -> "Off"
    state.mode.durationMinutes != null && state.remainingMs > 0 ->
        "${sleepModeLabel(state.mode)} · ${formatTime(state.remainingMs)} left"
    else -> sleepModeLabel(state.mode)
}
