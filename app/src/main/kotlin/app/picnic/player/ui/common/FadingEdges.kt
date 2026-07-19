package app.picnic.player.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top and/or bottom edge of a scrollable [Column] to transparent, signalling that more
 * content lies off-screen. Standard `drawWithContent` + [BlendMode.DstIn] fade — background-
 * agnostic (it lowers the content's alpha rather than painting a colour), so it works over any
 * sheet. Drive [topFade]/[bottomFade] from `scrollState.canScrollBackward`/`canScrollForward`.
 */
fun Modifier.verticalFadingEdges(
    topFade: Boolean,
    bottomFade: Boolean,
    length: Dp = 24.dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val lenPx = length.toPx()
        if (topFade && lenPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = lenPx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomFade && lenPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = size.height - lenPx,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
