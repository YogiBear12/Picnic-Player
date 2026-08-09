package app.picnic.player.playback

import kotlin.math.abs

data class TimedBars(val positionMs: Long, val bars: BlackBars)

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

/** Depths within a row and a half of each other are one framing measured either side of a pixel. */
private const val FramingMergeRows = 1.5f

/** A framing has to account for this much of the item; below it a depth is a misread. */
private const val MinFramingShare = 0.02f

fun blackBarSegments(frames: List<TimedBars>, frameHeight: Int): List<BlackBarTrack.Segment> {
    if (frames.isEmpty() || frameHeight <= 0) return emptyList()
    val ordered = frames.sortedBy { it.positionMs }
    val depths = ordered.map { minOf(it.bars.top, it.bars.bottom) }
    val framings = framingsOf(depths, frameHeight)

    val segments = mutableListOf<BlackBarTrack.Segment>()
    ordered.forEachIndexed { index, frame ->
        val depth = framings.minBy { abs(it - depths[index]) }
        val bars = if (depth <= 0f) BlackBars.None else BlackBars(depth, depth)
        val previous = segments.lastOrNull()
        when {
            previous == null -> segments += BlackBarTrack.Segment(0L, bars)
            previous.bars != bars -> segments += BlackBarTrack.Segment(frame.positionMs, bars)
        }
    }
    return segments
}

private fun framingsOf(depths: List<Float>, frameHeight: Int): List<Float> {
    val merge = FramingMergeRows / frameHeight
    val capped = barSearchRows(frameHeight).toFloat() / frameHeight
    val kept = mutableListOf<Pair<Float, Int>>()
    depths.filter { it < capped }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .forEach { (depth, count) ->
            val index = kept.indexOfFirst { abs(it.first - depth) <= merge }
            if (index < 0) {
                kept += depth to count
            } else {
                val (center, weight) = kept[index]
                kept[index] = (center * weight + depth * count) / (weight + count) to weight + count
            }
        }
    return kept.filter { it.second >= MinFramingShare * depths.size }
        .map { it.first }
        .sorted()
        .ifEmpty { listOf(0f) }
}
