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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Resolves an artwork URL to an [AmbientPalette]: decode a small
 * server-resized copy via Coil, extract off the main thread, and cache successful
 * results. Extractions run **strictly one at a time** (a held-key scroll must not
 * fan out into concurrent decodes) and cache hits return synchronously, so this
 * is safe to call on every focus change behind the caller's debounce.
 *
 * Card focus accents share the cache but not the concurrency limit — every visible card
 * prefetches its accent so the focus chrome is already coloured when focus lands, never white
 * first. That prefetch is kept cheap by three things: callers pass an accent-sized artwork URL
 * (a fraction of the poster's bytes), identical URLs share one in-flight fetch, and the fan-out
 * is capped so a fast scroll cannot starve the posters themselves.
 *
 * Every request here carries its own [diskCacheKey]. Coil keys the disk cache on the URL alone,
 * and an entry read while another request is still writing it decodes to garbage that Coil then
 * keeps — poisoning that artwork until the cache is cleared. A private key makes that collision
 * impossible even when a caller passes a URL a card is also displaying.
 */
@Singleton
class AmbientPaletteLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = LruCache<String, AmbientPalette>(CACHE_ENTRIES)
    private val accentCache = LruCache<String, Color>(ACCENT_CACHE_ENTRIES)
    private val failedAccents = LruCache<String, Unit>(ACCENT_CACHE_ENTRIES)
    private val mutex = Mutex()

    /** Owns accent work so one caller's cancellation can't kill a fetch others are awaiting. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accentGate = Semaphore(MAX_CONCURRENT_ACCENTS)
    private val inFlightAccents = mutableMapOf<String, Deferred<Color?>>()
    private val inFlightLock = Mutex()

    /** Cached backdrop wash palette for [url], or null. */
    fun cached(url: String): AmbientPalette? = cache.get(url)

    /** Cached card focus accent colour for [url], or null. */
    fun focusAccentCached(url: String): Color? = accentCache.get(url)

    /**
     * Focus accent straight from a server-supplied BlurHash — no network, no image decode, so it
     * resolves during composition and the chrome is already coloured before focus can land.
     */
    fun focusAccentFromBlurHash(hash: String): Color? {
        accentCache.get(hash)?.let { return it }
        val pixels = BlurHash.decode(hash, BLURHASH_SIZE, BLURHASH_SIZE) ?: return null
        // Blurred source: rank by chroma rather than frequency, or the pick lands on the blend.
        val accent = extractVividAccentColor(pixels, BLURHASH_SIZE, BLURHASH_SIZE) ?: return null
        val color = accent.forFocusChromeOver(averageColor(pixels, BLURHASH_SIZE, BLURHASH_SIZE))
        accentCache.put(hash, color)
        return color
    }

    /**
     * Final chrome colour for an accent drawn over [artwork]: boosted for punch, then held apart
     * from the artwork's own average so the indicator can't blend into what it sits on.
     */
    private fun Color.forFocusChromeOver(artwork: Color): Color = asFocusChrome().ensureContrastAgainst(artwork)

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
     *
     * Callers prefetch this for every visible card, so repeat calls for a URL already being
     * fetched join that fetch instead of starting another, and a URL that has already failed
     * returns null without retrying — otherwise every recomposition during a scroll re-queues
     * work that is known to be pointless.
     */
    suspend fun loadFocusAccent(url: String): Color? {
        accentCache.get(url)?.let { return it }
        if (failedAccents.get(url) != null) return null

        val deferred = inFlightLock.withLock {
            inFlightAccents[url] ?: scope.async {
                val color = runCatching { accentGate.withPermit { fetchAccent(url) } }.getOrNull()
                if (color != null) accentCache.put(url, color) else failedAccents.put(url, Unit)
                inFlightLock.withLock { inFlightAccents.remove(url) }
                color
            }.also { inFlightAccents[url] = it }
        }
        // Awaited, not runCatching'd: the fetch itself can't throw, so the only exception here is
        // the caller's own cancellation, which must keep propagating.
        return deferred.await()
    }

    private suspend fun fetchAndExtract(url: String): AmbientPalette? {
        val result = context.imageLoader.execute(paletteRequest(url, AMBIENT_SIZE))
        if (result !is SuccessResult) return null
        return withContext(Dispatchers.Default) {
            extractFromBitmap(result.image.toBitmap())
        }
    }

    private suspend fun fetchAccent(url: String): Color? {
        val result = context.imageLoader.execute(paletteRequest(url, ACCENT_SIZE))
        if (result !is SuccessResult) return null
        return withContext(Dispatchers.Default) {
            extractAccentColor(result.image.toBitmap())
        }
    }

    /** Software decode at [size], on a disk cache entry no displayed image can share. */
    private fun paletteRequest(url: String, size: Int) = ImageRequest.Builder(context)
        .data(url)
        .size(size)
        .allowHardware(false)
        .diskCacheKey("$url#palette$size")
        .build()

    private fun extractAccentColor(bitmap: Bitmap): Color? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val source = scaleAndToSoftware(bitmap, ACCENT_SIZE)
        val sw = source.width
        val sh = source.height
        val pixels = IntArray(sw * sh)
        source.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (source !== bitmap) source.recycle()
        // Real artwork keeps the histogram pick — its vivid areas are large enough to be the mode.
        val accent = extractFocusAccentColor(pixels, sw, sh) ?: return null
        return accent.forFocusChromeOver(averageColor(pixels, sw, sh))
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

        /** Accent prefetches run in parallel, but few enough to leave the posters bandwidth. */
        const val MAX_CONCURRENT_ACCENTS = 4

        /** A BlurHash holds no detail past a few components; this is already generous. */
        const val BLURHASH_SIZE = 16
    }
}
