package app.picnic.player.playback

data class TimedBars(val positionMs: Long, val bars: BlackBars)

/**
 * Bars across an item's runtime. Boundaries carry the timing of the thumbnails they came from,
 * which Jellyfin's keyframe-only trickplay leaves a few seconds loose.
 */
class BlackBarTrack(segments: List<Segment>) {

    data class Segment(val startMs: Long, val bars: BlackBars)

    private val segments = segments.ifEmpty { listOf(Segment(0L, BlackBars.None)) }.sortedBy { it.startMs }

    val varies: Boolean get() = segments.size > 1

    fun at(positionMs: Long): BlackBars {
        var low = 0
        var high = segments.lastIndex
        var found = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (segments[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return segments[found].bars
    }

    companion object {
        val None = BlackBarTrack(emptyList())

        fun constant(bars: BlackBars) = BlackBarTrack(listOf(Segment(0L, bars)))
    }
}

private const val FramingTolerance = 0.03f

/** Matches the sample floor [mergeBars] applies, so every run it keeps is one [mergeBars] answers. */
private const val MinRunFrames = 4

/**
 * Each run is reduced by [mergeBars], so a segment clears the picture across every frame it covers
 * and discards the same implausible measurements a whole-item answer would. A run must hold for
 * [MinRunFrames] to become a segment, so a lone dark frame cannot cut the timeline in two.
 */
fun blackBarSegments(frames: List<TimedBars>): List<BlackBarTrack.Segment> {
    if (frames.isEmpty()) return emptyList()
    val ordered = frames.sortedBy { it.positionMs }

    val runs = mutableListOf<MutableList<TimedBars>>()
    ordered.forEach { frame ->
        val current = runs.lastOrNull()
        if (current != null && frame.bars.matches(current.last().bars)) {
            current += frame
        } else {
            runs += mutableListOf(frame)
        }
    }

    val held = runs.filter { it.size >= MinRunFrames }.ifEmpty { listOf(ordered.toMutableList()) }
    val segments = mutableListOf<BlackBarTrack.Segment>()
    held.forEach { run ->
        val bars = mergeBars(run.map { it.bars })
        val previous = segments.lastOrNull()
        when {
            previous == null -> segments += BlackBarTrack.Segment(0L, bars)
            bars.matches(previous.bars) -> Unit
            else -> segments += BlackBarTrack.Segment(run.first().positionMs, bars)
        }
    }
    return segments
}

private fun BlackBars.matches(other: BlackBars): Boolean = kotlin.math.abs(top - other.top) <= FramingTolerance && kotlin.math.abs(bottom - other.bottom) <= FramingTolerance
