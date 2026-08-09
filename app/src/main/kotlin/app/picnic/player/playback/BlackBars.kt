package app.picnic.player.playback

data class BlackBars(val top: Float, val bottom: Float) {
    val pictureHeight: Float get() = (1f - top - bottom).coerceIn(0f, 1f)

    companion object {
        val None = BlackBars(0f, 0f)
    }
}

/** Limited-range black is 16, with headroom to 21 for compression. */
private const val BlackCeiling = 21

/** 2.76:1 in 16:9 gives 0.177, the widest letterbox there is to find. */
private const val SearchFraction = 0.25f

/** How far in from an edge a bar is looked for. A reading this deep never found the picture. */
internal fun barSearchRows(height: Int): Int = (height * SearchFraction).toInt()

fun barsInFrame(pixels: IntArray, width: Int, height: Int): BlackBars {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return BlackBars.None

    val range = barSearchRows(height)
    var top = 0
    while (top < range && rowIsBlack(pixels, top * width, width)) top++
    var bottom = 0
    while (bottom < range && rowIsBlack(pixels, (height - 1 - bottom) * width, width)) bottom++

    return BlackBars(top.toFloat() / height, bottom.toFloat() / height)
}

// Every column: a thumbnail is a few hundred pixels wide, and subsampling one steps over the
// handful of lit pixels that prove a row is picture.
private fun rowIsBlack(pixels: IntArray, offset: Int, width: Int): Boolean {
    var x = 0
    while (x < width) {
        val pixel = pixels[offset + x]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        if (((r * 77 + g * 151 + b * 28) shr 8) > BlackCeiling) return false
        x++
    }
    return true
}
