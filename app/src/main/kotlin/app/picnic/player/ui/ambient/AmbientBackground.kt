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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

val AmbientBase = Color(0xFF0E0E10)

private const val PALETTE_FADE_MS = 1250
private const val FOCUS_DEBOUNCE_MS = 250L
private const val WASH_RADIUS = 0.85f

private const val WASH_BITMAP_W = 192
private const val WASH_BITMAP_H = 108

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

@Composable
fun AmbientBackground(
    palette: AmbientPalette?,
    modifier: Modifier = Modifier,
    base: Color = AmbientBase
) {
    val washBitmap = remember(palette) { palette?.let(::renderWash) }

    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(washBitmap) {
        previous = current
        current = washBitmap
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PALETTE_FADE_MS))
    }

    Canvas(modifier) {
        drawRect(base)
        val p = progress.value
        previous?.let { drawBakedGradient(it, 1f - p) }
        current?.let { drawBakedGradient(it, p) }
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
