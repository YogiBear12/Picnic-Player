package app.picnic.player.data.playback

/**
 * Jellyfin expresses every position and duration in 100-nanosecond ticks, while media3 works in
 * milliseconds. Convert at the edge with [ticksToMs] / [msToTicks] rather than open-coding the
 * factor: which way the division goes is the easy thing to get backwards.
 */
const val TICKS_PER_MS = 10_000L

fun Long.ticksToMs(): Long = this / TICKS_PER_MS

fun Long.msToTicks(): Long = this * TICKS_PER_MS
