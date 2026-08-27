package app.picnic.player.ui.grid

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.picnic.player.ui.ambient.bakeGradient
import app.picnic.player.ui.ambient.drawBakedGradient
import app.picnic.player.ui.theme.PicnicColors

private const val OCEAN_BITMAP_W = 480
private const val OCEAN_BITMAP_H = 270

private val oceanBitmap: ImageBitmap by lazy(::renderOcean)

@Composable
fun OceanAmbientBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawBakedGradient(oceanBitmap) }
}

private fun renderOcean(): ImageBitmap = bakeGradient(OCEAN_BITMAP_W, OCEAN_BITMAP_H) {
    val w = size.width
    val h = size.height
    val wash = w * 0.85f

    drawRect(
        brush = Brush.verticalGradient(
            0f to PicnicColors.OceanSurface,
            0.22f to PicnicColors.OceanDeep,
            0.62f to PicnicColors.OceanAbyss,
            1f to PicnicColors.Background
        )
    )

    radialWash(PicnicColors.OceanTwilight.copy(alpha = 0.68f), Offset(0f, 0f), wash * 1.05f)
    radialWash(PicnicColors.Cyan.copy(alpha = 0.42f), Offset(w, 0f), wash * 0.70f)
    radialWash(PicnicColors.OceanMidwater.copy(alpha = 0.58f), Offset(w, h), wash * 0.86f)
    radialWash(PicnicColors.OceanKelp.copy(alpha = 0.38f), Offset(0f, h), wash * 0.64f)

    radialWash(PicnicColors.CyanDim.copy(alpha = 0.24f), Offset(w * 0.52f, h * 0.06f), w * 0.58f)
    radialWash(PicnicColors.Cyan.copy(alpha = 0.16f), Offset(w * 0.18f, h * 0.74f), w * 0.36f)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
            center = Offset(w * 0.5f, h * 0.42f),
            radius = w * 0.92f
        )
    )
}

private fun DrawScope.radialWash(color: Color, center: Offset, radius: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        )
    )
}
