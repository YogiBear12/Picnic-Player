package app.picnic.player.playback

/**
 * Black bars an encoder baked into the frame — a 2.39:1 film carried in a 16:9 picture — as
 * fractions of its height. Bars a player adds to fit one aspect inside another are not these:
 * those follow from the video's display size and never need measuring.
 */
data class BlackBars(val top: Float, val bottom: Float) {

    val pictureHeight: Float get() = (1f - top - bottom).coerceIn(0f, 1f)

    companion object {
        val None = BlackBars(0f, 0f)
    }
}

private const val BarLumaCeiling = 22f

/** Below this a frame is unmeasurable: fades, title cards and a sheet's unwritten cells are all bar. */
private const val ContentLumaFloor = 48f

private const val MinBarFraction = 0.015f

/** 2.39:1 in 16:9 gives 0.127, 2.76:1 gives 0.177; past this, dark frames are likelier. */
private const val MaxBarFraction = 0.25f

private const val MinSamples = 4

/** Null when the frame carries no picture to measure a bar against. [rowLuma] top row first. */
fun barsInFrame(rowLuma: FloatArray): BlackBars? {
    if (rowLuma.isEmpty() || rowLuma.none { it >= ContentLumaFloor }) return null

    var top = 0
    while (top < rowLuma.size && rowLuma[top] < BarLumaCeiling) top++
    var bottom = 0
    while (bottom < rowLuma.size - top && rowLuma[rowLuma.size - 1 - bottom] < BarLumaCeiling) bottom++

    val height = rowLuma.size.toFloat()
    return BlackBars(top / height, bottom / height)
}

/**
 * One measurement for a whole item: the smallest bar seen on each side.
 *
 * A film that opens 2.39:1 and widens to full frame for some scenes reports no bars at all, so its
 * cues sit in the bar during the scope scenes rather than climbing over the picture during the
 * full-frame ones. Dark scenes only ever overstate a bar, so the minimum discards them for free.
 */
fun mergeBars(frames: List<BlackBars>): BlackBars {
    if (frames.size < MinSamples) return BlackBars.None
    val top = frames.minOf { it.top }.settle()
    val bottom = frames.minOf { it.bottom }.settle()
    return if (top <= 0f && bottom <= 0f) BlackBars.None else BlackBars(top, bottom)
}

private fun Float.settle(): Float = if (this < MinBarFraction || this > MaxBarFraction) 0f else this

fun rowLuma(pixels: IntArray, offset: Int, width: Int, columnStride: Int): Float {
    var total = 0L
    var count = 0
    var x = 0
    while (x < width) {
        val pixel = pixels[offset + x]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        total += (r * 77 + g * 151 + b * 28) shr 8
        count++
        x += columnStride
    }
    return if (count == 0) 0f else total.toFloat() / count
}
