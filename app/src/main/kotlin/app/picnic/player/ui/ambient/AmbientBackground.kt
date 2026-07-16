package app.picnic.player.ui.ambient

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Base behind the washes — matches the immersive home's neutral background. */
val AmbientBase = Color(0xFF0E0E10)

private const val PALETTE_FADE_MS = 1250 // colour lerp (longer than image)
private const val FOCUS_DEBOUNCE_MS = 250L // uncached extraction waits out fast scrolling
private const val WASH_RADIUS = 0.85f // corner gradient radius as a fraction of width

// Wash is a smooth low-frequency gradient — render it tiny and upscale (16:9). Keeps the
// per-palette shading to a few thousand pixels instead of the full 1080p field.
private const val WASH_BITMAP_W = 192
private const val WASH_BITMAP_H = 108

/**
 * Resolves [url] to a palette for the focused item. Cache hits apply
 * instantly; uncached extraction is debounced behind focus changes and the
 * previous palette stays until the new one settles. Stale URLs are discarded
 * automatically when [url] changes (the effect cancels).
 */
@Composable
fun rememberAmbientPalette(url: String?, loader: AmbientPaletteLoader): AmbientPalette? {
    var palette by remember { mutableStateOf<AmbientPalette?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        loader.cached(url)?.let {
            palette = it
            return@LaunchedEffect
        }
        delay(FOCUS_DEBOUNCE_MS)
        if (!currentCoroutineContext().isActive) return@LaunchedEffect
        val loaded = loader.load(url)
        if (loaded != null) palette = loaded
    }
    return palette
}

/**
 * Full-screen ambient washes: four corner radial gradients over a dark base.
 *
 * Each palette's wash is rasterised **once** into a small [ImageBitmap] (radial gradients are
 * low-frequency, so a downscaled bitmap upscales imperceptibly), and a palette change
 * cross-fades the previous wash into the new one via layer *alpha*. That replaces the old
 * per-frame `animateColorAsState` colour morph, which re-created four full-screen radial
 * shaders on every one of the ~1.25 s fade's frames — ~5× full-screen overdraw per frame that
 * pegged the GPU and dropped ~34 frames on every card move. Now the per-frame cost is two
 * cheap textured blits.
 */
@Composable
fun AmbientBackground(
    palette: AmbientPalette?,
    modifier: Modifier = Modifier,
    base: Color = AmbientBase
) {
    // Corner-only wash for this palette (null = neutral: the base shows through). Rebuilt only
    // when the palette actually changes.
    val washBitmap = remember(palette) { palette?.let(::renderWash) }

    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember { Animatable(1f) }

    // Cross-fade previous → current whenever the wash changes. Alpha only — the shaders were
    // already baked into the bitmaps, so no gradient is re-shaded during the fade.
    LaunchedEffect(washBitmap) {
        previous = current
        current = washBitmap
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PALETTE_FADE_MS))
    }

    Canvas(modifier) {
        drawRect(base)
        val p = progress.value
        val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
        previous?.let {
            drawImage(it, dstSize = dst, alpha = 1f - p, filterQuality = FilterQuality.Medium)
        }
        current?.let {
            drawImage(it, dstSize = dst, alpha = p, filterQuality = FilterQuality.Medium)
        }
    }
}

/**
 * Rasterises the four corner radials (over a transparent field, so the caller's base shows
 * through and washes cross-fade cleanly) into a small bitmap. [WASH_BITMAP_W]×[WASH_BITMAP_H]
 * is deliberately tiny — a smooth gradient carries no high-frequency detail, so upscaling to
 * the screen is free of visible artefacts while the shading work is a few thousand pixels once.
 */
private fun renderWash(palette: AmbientPalette): ImageBitmap {
    val bitmap = ImageBitmap(WASH_BITMAP_W, WASH_BITMAP_H)
    val size = Size(WASH_BITMAP_W.toFloat(), WASH_BITMAP_H.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GraphicsCanvas(bitmap), size) {
        val radius = this.size.width * WASH_RADIUS
        corner(palette.topLeft, Offset(0f, 0f), radius)
        corner(palette.topRight, Offset(this.size.width, 0f), radius)
        corner(palette.bottomRight, Offset(this.size.width, this.size.height), radius)
        corner(palette.bottomLeft, Offset(0f, this.size.height), radius)
    }
    return bitmap
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.corner(
    color: Color,
    center: Offset,
    radius: Float
) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        )
    )
}
