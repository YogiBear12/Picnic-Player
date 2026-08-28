package app.picnic.player.ui.ambient

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val PALETTE_FADE_MS = 1250
private const val FOCUS_DEBOUNCE_MS = 250L
private const val WASH_RADIUS = 0.85f

private const val WASH_BITMAP_W = 192
private const val WASH_BITMAP_H = 108

@Composable
fun rememberAmbientPalette(url: String?, loader: AmbientPaletteLoader): AmbientPalette? {
    var palette by remember { mutableStateOf<AmbientPalette?>(null) }
    LaunchedEffect(url) {
        if (url == null) {
            palette = null
            return@LaunchedEffect
        }
        loader.cached(url)?.let {
            palette = it
            return@LaunchedEffect
        }
        delay(FOCUS_DEBOUNCE_MS)
        if (!currentCoroutineContext().isActive) return@LaunchedEffect
        palette = loader.load(url)
    }
    return palette
}

@Stable
class AmbientWash internal constructor() {
    internal var current by mutableStateOf<ImageBitmap?>(null)
    internal var previous by mutableStateOf<ImageBitmap?>(null)
    internal val progress = Animatable(1f)

    val alpha: Float
        get() {
            val p = progress.value
            val leaving = if (previous != null) 1f - p else 0f
            val arriving = if (current != null) p else 0f
            return leaving + arriving
        }
}

@Composable
fun rememberAmbientWash(palette: AmbientPalette?): AmbientWash {
    val wash = remember { AmbientWash() }
    val washBitmap = remember(palette) { palette?.let(::renderWash) }
    LaunchedEffect(washBitmap) {
        wash.previous = wash.current
        wash.current = washBitmap
        wash.progress.snapTo(0f)
        wash.progress.animateTo(1f, tween(PALETTE_FADE_MS))
    }
    return wash
}

@Composable
fun AmbientBackground(
    wash: AmbientWash,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val p = wash.progress.value
        wash.previous?.let { drawBakedGradient(it, 1f - p) }
        wash.current?.let { drawBakedGradient(it, p) }
    }
}

private fun renderWash(palette: AmbientPalette): ImageBitmap = bakeGradient(WASH_BITMAP_W, WASH_BITMAP_H) {
    val radius = size.width * WASH_RADIUS
    corner(palette.topLeft, Offset(0f, 0f), radius)
    corner(palette.topRight, Offset(size.width, 0f), radius)
    corner(palette.bottomRight, Offset(size.width, size.height), radius)
    corner(palette.bottomLeft, Offset(0f, size.height), radius)
}

private fun DrawScope.corner(color: Color, center: Offset, radius: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        )
    )
}
