package app.picnic.player.ui.ambient

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Resolves an artwork URL to an [AmbientPalette]: decode a small
 * server-resized copy via Coil, extract off the main thread, and cache successful
 * results. Extractions run **strictly one at a time** (a held-key scroll must not
 * fan out into concurrent decodes) and cache hits return synchronously, so this
 * is safe to call on every focus change behind the caller's debounce.
 *
 * Card focus accents reuse the same cache — populated from the card's own
 * [coil.compose.AsyncImage] decode so border colour matches the poster with no
 * extra network fetch.
 */
@Singleton
class AmbientPaletteLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = LruCache<String, AmbientPalette>(CACHE_ENTRIES)
    private val accentCache = LruCache<String, Color>(ACCENT_CACHE_ENTRIES)
    private val mutex = Mutex()

    /** Cached backdrop wash palette for [url], or null. */
    fun cached(url: String): AmbientPalette? = cache.get(url)

    /** Cached card focus accent colour for [url], or null. */
    fun focusAccentCached(url: String): Color? = accentCache.get(url)

    /** Full 4-corner ambient palette for the full-screen backdrop wash ([AMBIENT_SIZE] source). */
    suspend fun load(url: String): AmbientPalette? {
        cache.get(url)?.let { return it }
        return mutex.withLock {
            cache.get(url)?.let { return it }
            val palette = runCatching { fetchAndExtract(url) }.getOrNull()
            if (palette != null) cache.put(url, palette)
            palette
        }
    }

    /**
     * Single highlight colour for a card focus indicator. Uses a tiny [ACCENT_SIZE] source
     * and [extractFocusAccentColor] (full-image vivid pick — not the 4-corner wash palette).
     */
    suspend fun loadFocusAccent(url: String): Color? {
        accentCache.get(url)?.let { return it }
        val color = runCatching { fetchAccent(url) }.getOrNull()
        if (color != null) accentCache.put(url, color)
        return color
    }

    private suspend fun fetchAndExtract(url: String): AmbientPalette? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(AMBIENT_SIZE)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        if (result !is SuccessResult) return null
        return withContext(Dispatchers.Default) {
            extractFromBitmap(result.image.toBitmap())
        }
    }

    private suspend fun fetchAccent(url: String): Color? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(ACCENT_SIZE)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        if (result !is SuccessResult) return null
        return withContext(Dispatchers.Default) {
            extractAccentColor(result.image.toBitmap())
        }
    }

    private fun extractAccentColor(bitmap: Bitmap): Color? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val source = scaleAndToSoftware(bitmap, ACCENT_SIZE)
        val sw = source.width
        val sh = source.height
        val pixels = IntArray(sw * sh)
        source.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (source !== bitmap) source.recycle()
        return extractFocusAccentColor(pixels, sw, sh)
    }

    private fun extractFromBitmap(bitmap: Bitmap): AmbientPalette? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val source = scaleAndToSoftware(bitmap, AMBIENT_SIZE)
        val sw = source.width
        val sh = source.height
        val pixels = IntArray(sw * sh)
        source.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (source !== bitmap) source.recycle()
        return extractAmbientPalette(pixels, sw, sh)
    }

    private fun scaleAndToSoftware(bitmap: Bitmap, targetSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val sw = if (w > targetSize) targetSize else w
        val sh = if (w > targetSize) (h.toFloat() * targetSize / w).toInt().coerceAtLeast(1) else h

        if (sw == w && sh == h && bitmap.config != Bitmap.Config.HARDWARE) {
            return bitmap
        }

        val softwareBitmap = createBitmap(sw, sh)
        val canvas = android.graphics.Canvas(softwareBitmap)
        val paint = android.graphics.Paint().apply { isFilterBitmap = true }
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, sw, sh), paint)
        return softwareBitmap
    }

    private companion object {
        const val AMBIENT_SIZE = 240 // px — full-screen backdrop wash (keep detailed)
        const val ACCENT_SIZE = 48 // px — card focus highlight only (no detail needed)
        const val CACHE_ENTRIES = 32
        const val ACCENT_CACHE_ENTRIES = 512
    }
}
