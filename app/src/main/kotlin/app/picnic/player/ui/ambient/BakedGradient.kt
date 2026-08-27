package app.picnic.player.ui.ambient

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

internal fun bakeGradient(width: Int, height: Int, draw: DrawScope.() -> Unit): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    val bounds = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GraphicsCanvas(bitmap), bounds, draw)
    return bitmap
}

internal fun DrawScope.drawBakedGradient(bitmap: ImageBitmap, alpha: Float = 1f) {
    drawImage(
        bitmap,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha
    )
}
