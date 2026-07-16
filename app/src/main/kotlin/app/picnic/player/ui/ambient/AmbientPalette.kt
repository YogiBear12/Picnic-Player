package app.picnic.player.ui.ambient

import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Four-corner colour palette extracted from a focused item's artwork, driving the
 * full-screen radial washes behind the immersive home. Quadrant histogram
 * local maxima + staged "lively" selection on **HSV** saturation/value.
 */
data class AmbientPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomRight: Color,
    val bottomLeft: Color
)

/**
 * Single vivid accent from the extracted palette — drives card focus glow and border
 * so the indicator colour matches the artwork.
 *
 * Prefer [extractFocusAccentColor] for cards: that samples the whole image once.
 * This helper remains for callers that already hold a 4-corner wash palette.
 */
fun AmbientPalette.focusAccent(): Color = listOf(topLeft, topRight, bottomRight, bottomLeft).maxByOrNull(::colorVividness) ?: topLeft

/**
 * Card focus accent: sample the **whole** image (not four corners), take histogram
 * local maxima, drop near-black/near-white, then pick the most vivid survivor.
 * Backdrop washes still use [extractAmbientPalette].
 */
fun extractFocusAccentColor(pixels: IntArray, width: Int, height: Int): Color? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    val cube = ColorCube()
    val full = Region(0, 0, width, height)
    var maxima = cube.extractFromRegion(pixels, width, full, avoidBlackWhite = true)
    if (maxima.isEmpty()) {
        maxima = cube.extractFromRegion(pixels, width, full, avoidBlackWhite = false)
    }
    if (maxima.isEmpty()) return null

    val candidates = maxima.map { Candidate(it.red, it.green, it.blue) }
    // Prefer a lively colour when one exists; otherwise the most vivid maximum.
    val lively = candidates.filter { it.saturation >= 15.0 && it.value >= 20.0 }
    val pool = lively.ifEmpty { candidates }
    val best = pool.maxByOrNull { colorVividness(it.toColor()) } ?: return null
    return best.toColor()
}

private fun colorVividness(color: Color): Float {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val saturation = if (max == 0f) 0f else (max - min) / max
    return saturation * max
}

private const val HISTOGRAM_SIZE = 30
private const val COLORS_PER_QUADRANT = 5
private const val BLACK_WHITE_DISTANCE = 0.25
private const val PAD_GREY = 221.0 / 255.0
private const val QUADRANT_FRACTION = 0.4
private const val PAIR_CONTRAST_THRESHOLD = 15.0
private const val PAIR_DARKEN_FACTOR = 0.45
private const val BRIGHT_DARKEN_FACTOR = 0.1
private const val REJECT_LIGHTNESS = 15.0

/**
 * Extracts the four-corner palette. [pixels] is ARGB_8888 row-major
 * (`Bitmap.getPixels`). Returns null when the artwork is essentially black or
 * yields no usable colours — the caller then shows the neutral base.
 */
fun extractAmbientPalette(pixels: IntArray, width: Int, height: Int): AmbientPalette? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null

    val candidates = extractQuadrantColors(pixels, width, height).toMutableList()
    if (candidates.isEmpty()) return null

    // Pad to four full quadrant slots with a neutral grey.
    while (candidates.size < 4 * COLORS_PER_QUADRANT) {
        candidates += Candidate(PAD_GREY, PAD_GREY, PAD_GREY)
    }

    val corners = (0 until 4).map { q ->
        pickLively(candidates.subList(q * COLORS_PER_QUADRANT, (q + 1) * COLORS_PER_QUADRANT))
    }
    val tl = corners[0]
    val tr = corners[1]
    val br = corners[2]
    val bl = corners[3]

    // Darken near-identical pairs so washes stay distinguishable.
    if (percentageDifferent(tl, tr) < PAIR_CONTRAST_THRESHOLD) tr.darken(PAIR_DARKEN_FACTOR)
    if (percentageDifferent(br, bl) < PAIR_CONTRAST_THRESHOLD) bl.darken(PAIR_DARKEN_FACTOR)
    for (c in corners) if (c.lightness > 50) c.darken(BRIGHT_DARKEN_FACTOR)

    // Reject palettes from essentially black artwork.
    val topPair = (tl.lightness + tr.lightness) / 2
    val bottomPair = (br.lightness + bl.lightness) / 2
    if (topPair <= REJECT_LIGHTNESS && bottomPair <= REJECT_LIGHTNESS) return null

    return AmbientPalette(tl.toColor(), tr.toColor(), br.toColor(), bl.toColor())
}

private data class Region(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int)

private fun extractQuadrantColors(pixels: IntArray, w: Int, h: Int): List<Candidate> {
    val regionW = floor(w * QUADRANT_FRACTION).toInt()
    val regionH = floor(h * QUADRANT_FRACTION).toInt()
    val regions = listOf(
        Region(0, 0, regionW, regionH), // top-left
        Region(w - regionW, 0, w, regionH), // top-right
        Region(w - regionW, h - regionH, w, h), // bottom-right
        Region(0, h - regionH, regionW, h) // bottom-left
    )
    val cube = ColorCube()
    val out = ArrayList<Candidate>()
    for (region in regions) {
        var colors = cube.extractFromRegion(pixels, w, region, avoidBlackWhite = true)
        if (colors.isEmpty()) colors = cube.extractFromRegion(pixels, w, region, avoidBlackWhite = false)
        if (colors.isEmpty()) continue
        for (i in 0 until COLORS_PER_QUADRANT) {
            val m = if (i < colors.size) colors[i] else colors[0]
            out += Candidate(m.red, m.green, m.blue)
        }
    }
    return out
}

private class Maxima(val red: Double, val green: Double, val blue: Double, val hitCount: Int)

/** RGB histogram with local-maxima colour detection over corner regions. */
private class ColorCube {
    private val cells = HISTOGRAM_SIZE * HISTOGRAM_SIZE * HISTOGRAM_SIZE
    private val hits = IntArray(cells)
    private val redAcc = DoubleArray(cells)
    private val greenAcc = DoubleArray(cells)
    private val blueAcc = DoubleArray(cells)

    private fun cellIndex(r: Int, g: Int, b: Int) = r + g * HISTOGRAM_SIZE + b * HISTOGRAM_SIZE * HISTOGRAM_SIZE

    fun extractFromRegion(
        pixels: IntArray,
        width: Int,
        region: Region,
        avoidBlackWhite: Boolean
    ): List<Maxima> {
        var maxima = findLocalMaxima(pixels, width, region)
        if (avoidBlackWhite) {
            maxima = filterAwayFrom(maxima, 0.0, 0.0, 0.0)
            maxima = filterAwayFrom(maxima, 1.0, 1.0, 1.0)
        }
        return adaptiveDistinctFilter(maxima, COLORS_PER_QUADRANT)
    }

    private fun filterAwayFrom(maxima: List<Maxima>, r: Double, g: Double, b: Double): List<Maxima> = maxima.filter { distance(it.red - r, it.green - g, it.blue - b) >= BLACK_WHITE_DISTANCE }

    private fun adaptiveDistinctFilter(maxima: List<Maxima>, target: Int): List<Maxima> {
        var result = maxima
        if (result.size > target) {
            var threshold = 0.1
            for (i in 0 until 10) {
                val filtered = filterDistinct(result, threshold)
                if (filtered.size <= target) break
                result = filtered
                threshold += 0.05
            }
            result = result.subList(0, target.coerceAtMost(result.size))
        }
        return result
    }

    private fun filterDistinct(maxima: List<Maxima>, threshold: Double): List<Maxima> {
        val kept = ArrayList<Maxima>()
        for (m in maxima) {
            val tooClose = kept.any {
                distance(m.red - it.red, m.green - it.green, m.blue - it.blue) < threshold
            }
            if (!tooClose) kept += m
        }
        return kept
    }

    private fun findLocalMaxima(pixels: IntArray, width: Int, region: Region): List<Maxima> {
        hits.fill(0)
        redAcc.fill(0.0)
        greenAcc.fill(0.0)
        blueAcc.fill(0.0)

        for (y in region.minY until region.maxY) {
            val row = y * width
            for (x in region.minX until region.maxX) {
                val p = pixels[row + x]
                val r = ((p shr 16) and 0xFF) / 255.0
                val g = ((p shr 8) and 0xFF) / 255.0
                val b = (p and 0xFF) / 255.0
                val cell = cellIndex(
                    (r * (HISTOGRAM_SIZE - 1)).toInt(),
                    (g * (HISTOGRAM_SIZE - 1)).toInt(),
                    (b * (HISTOGRAM_SIZE - 1)).toInt()
                )
                hits[cell]++
                redAcc[cell] += r
                greenAcc[cell] += g
                blueAcc[cell] += b
            }
        }

        val maxima = ArrayList<Maxima>()
        for (r in 0 until HISTOGRAM_SIZE) {
            for (g in 0 until HISTOGRAM_SIZE) {
                for (b in 0 until HISTOGRAM_SIZE) {
                    val cell = cellIndex(r, g, b)
                    val c = hits[cell]
                    if (c == 0) continue
                    if (!isLocalMaximum(r, g, b, c)) continue
                    maxima += Maxima(redAcc[cell] / c, greenAcc[cell] / c, blueAcc[cell] / c, c)
                }
            }
        }
        maxima.sortByDescending { it.hitCount }
        return maxima
    }

    private fun isLocalMaximum(r: Int, g: Int, b: Int, hitCount: Int): Boolean {
        for (dr in -1..1) {
            for (dg in -1..1) {
                for (db in -1..1) {
                    if (dr == 0 && dg == 0 && db == 0) continue
                    val nr = r + dr
                    val ng = g + dg
                    val nb = b + db
                    if (nr < 0 || ng < 0 || nb < 0) continue
                    if (nr >= HISTOGRAM_SIZE || ng >= HISTOGRAM_SIZE || nb >= HISTOGRAM_SIZE) continue
                    if (hits[cellIndex(nr, ng, nb)] > hitCount) return false
                }
            }
        }
        return true
    }
}

private fun distance(dr: Double, dg: Double, db: Double) = sqrt(dr * dr + dg * dg + db * db)

private fun percentageDifferent(a: Candidate, b: Candidate): Double = (kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)) / 3 * 100

/**
 * Picks the first lively colour from a quadrant's five candidates via staged
 * HSV saturation/value bands, loosening until something survives.
 */
private fun pickLively(five: List<Candidate>): Candidate {
    fun inRange(c: Candidate, satMin: Double, satMax: Double, lumMin: Double, lumMax: Double): Boolean {
        val sat = c.saturation
        val lum = c.value
        return sat in satMin..satMax && lum in lumMin..lumMax && (sat + lum) <= 190
    }
    fun drop(list: List<Candidate>, satMin: Double, satMax: Double, lumMin: Double, lumMax: Double) = list.filter { inRange(it, satMin, satMax, lumMin, lumMax) }

    var picked = drop(listOf(five[0]), 25.0, 100.0, 25.0, 100.0)
    if (picked.isEmpty()) picked = drop(listOf(five[1]), 15.0, 100.0, 25.0, 100.0)
    if (picked.isEmpty()) picked = drop(listOf(five[0]), 2.0, 40.0, 30.0, 95.0)
    if (picked.isEmpty()) picked = drop(listOf(five[1]), 2.0, 40.0, 60.0, 95.0)
    if (picked.isEmpty()) {
        val rest = five.subList(1, five.size)
        picked = drop(rest, 30.0, 100.0, 25.0, 100.0)
        if (picked.isEmpty()) picked = drop(rest, 15.0, 100.0, 25.0, 100.0)
    }
    if (picked.isEmpty()) picked = drop(five, 5.0, 90.0, 5.0, 90.0)
    if (picked.isEmpty()) picked = listOf(five[0])
    return picked.first()
}

/** Mutable working colour with HSV metrics + HSL darken (channels in 0..1). */
private class Candidate(var red: Double, var green: Double, var blue: Double) {
    private var hsl: DoubleArray? = null

    /** HSV saturation 0–100. */
    val saturation: Double
        get() {
            val maxC = max3(red, green, blue)
            val minC = min3(red, green, blue)
            if (maxC <= 0) return 0.0
            return (maxC - minC) / maxC * 100
        }

    /** HSV value (max channel) 0–100. */
    val value: Double get() = max3(red, green, blue) * 100

    /** HSL lightness 0–100. */
    val lightness: Double
        get() {
            val h = hsl ?: hslFromRgb().also { hsl = it }
            return h[2]
        }

    fun darken(factor: Double) {
        val h = hsl ?: hslFromRgb().also { hsl = it }
        h[2] *= 1 - factor
        rgbFromHsl()
    }

    fun toColor(): Color = Color(
        red = red.coerceIn(0.0, 1.0).toFloat(),
        green = green.coerceIn(0.0, 1.0).toFloat(),
        blue = blue.coerceIn(0.0, 1.0).toFloat()
    )

    private fun hslFromRgb(): DoubleArray {
        val maxC = max3(red, green, blue)
        val minC = min3(red, green, blue)
        val sum = maxC + minC
        val l = sum / 2
        var h = 0.0
        var s = 0.0
        if (maxC != minC) {
            val c = maxC - minC
            s = c / if (l > 0.5) 2 - sum else sum
            h = when {
                red >= maxC -> ((green - blue) / c).let { if (green < blue) it + 6 else it }
                green >= maxC -> 2 + (blue - red) / c
                else -> 4 + (red - green) / c
            }
            h /= 6
        }
        return doubleArrayOf(360 * h, 100 * s, 100 * l)
    }

    private fun rgbFromHsl() {
        val h = hsl!!
        val hue = h[0] / 360
        val s = h[1] / 100
        val l = h[2] / 100
        if (s <= 0) {
            red = l
            green = l
            blue = l
            return
        }
        if (l <= 0) {
            red = 0.0
            green = 0.0
            blue = 0.0
            return
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val channels = doubleArrayOf(hue + 1.0 / 3, hue, hue - 1.0 / 3)
        val out = DoubleArray(3)
        for (i in 0 until 3) {
            var t = channels[i]
            if (t < 0) t += 1
            if (t > 1) t -= 1
            out[i] = when {
                t < 1.0 / 6 -> p + 6 * (q - p) * t
                t < 0.5 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        red = out[0]
        green = out[1]
        blue = out[2]
    }
}

private fun max3(a: Double, b: Double, c: Double) = if (a > b) (if (a > c) a else c) else (if (b > c) b else c)
private fun min3(a: Double, b: Double, c: Double) = if (a < b) (if (a < c) a else c) else (if (b < c) b else c)
