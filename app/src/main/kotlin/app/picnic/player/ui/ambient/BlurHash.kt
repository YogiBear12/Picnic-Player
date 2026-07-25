package app.picnic.player.ui.ambient

import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

/**
 * Decoder for the BlurHash strings the server ships alongside each item's image tags.
 *
 * A hash is a handful of DCT coefficients, so it expands to a low-frequency preview with no
 * network call and no image decode. Far too coarse to display, but it carries the artwork's
 * colour distribution — enough to pick a focus accent from data the item list already holds.
 */
object BlurHash {
    private const val ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    /** ARGB pixels for [hash] at [width] x [height], or null if the hash is malformed. */
    fun decode(hash: String, width: Int, height: Int): IntArray? {
        if (hash.length < 6 || width <= 0 || height <= 0) return null
        val sizeFlag = decode83(hash, 0, 1) ?: return null
        val componentsX = sizeFlag % 9 + 1
        val componentsY = sizeFlag / 9 + 1
        if (hash.length != 4 + 2 * componentsX * componentsY + 2) return null

        val quantisedMax = decode83(hash, 1, 2) ?: return null
        val maxValue = (quantisedMax + 1) / 166f

        val components = Array(componentsX * componentsY) { FloatArray(3) }
        components[0] = decodeDc(decode83(hash, 2, 6) ?: return null)
        for (i in 1 until components.size) {
            val value = decode83(hash, 4 + i * 2, 6 + i * 2) ?: return null
            components[i] = decodeAc(value, maxValue)
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until componentsY) {
                    for (i in 0 until componentsX) {
                        val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                        val component = components[i + j * componentsX]
                        r += component[0] * basis
                        g += component[1] * basis
                        b += component[2] * basis
                    }
                }
                pixels[y * width + x] =
                    (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return pixels
    }

    private fun decode83(value: String, from: Int, to: Int): Int? {
        var result = 0
        for (index in from until to) {
            val digit = ALPHABET.indexOf(value[index])
            if (digit < 0) return null
            result = result * 83 + digit
        }
        return result
    }

    private fun decodeDc(value: Int) = floatArrayOf(
        srgbToLinear(value shr 16),
        srgbToLinear(value shr 8 and 255),
        srgbToLinear(value and 255)
    )

    private fun decodeAc(value: Int, maxValue: Float) = floatArrayOf(
        signedPow((value / (19 * 19) - 9) / 9f) * maxValue,
        signedPow((value / 19 % 19 - 9) / 9f) * maxValue,
        signedPow((value % 19 - 9) / 9f) * maxValue
    )

    private fun signedPow(value: Float) = value.absoluteValue.pow(2f) * value.sign

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
}
