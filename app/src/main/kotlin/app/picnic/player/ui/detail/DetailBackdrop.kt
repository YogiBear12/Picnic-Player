package app.picnic.player.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.picnic.player.ui.ambient.AmbientBackground
import app.picnic.player.ui.ambient.BackdropFrame
import app.picnic.player.ui.ambient.bakeGradient
import app.picnic.player.ui.ambient.drawBakedGradient
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.asPainter

@Composable
internal fun DetailBackdrop(
    frame: BackdropFrame,
    modifier: Modifier = Modifier
) {
    val (art, artAlpha, wash, _, dim) = frame
    val context = LocalContext.current
    val painter = remember(art) { art?.image?.asPainter(context) }
    val artOpaque = { painter != null && artAlpha.value * dim.value >= 1f }
    Box(
        modifier.drawWithContent {
            if (!artOpaque()) drawRect(PicnicColors.Background)
            drawContent()
            drawBakedGradient(detailMask)
        }
    ) {
        AmbientBackground(wash, Modifier.fillMaxSize().drawWithContent { if (!artOpaque()) drawContent() })
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = artAlpha.value * dim.value }
            )
        }
    }
}

private const val MASK_BITMAP_W = 480
private const val MASK_BITMAP_H = 270

private val MaskSide = Color(0xFF04080C)

// Stops are the approved 80% shielding, already scaled; layers composite in this order.
private val detailMask: ImageBitmap by lazy {
    bakeGradient(MASK_BITMAP_W, MASK_BITMAP_H) {
        drawRect(
            Brush.horizontalGradient(
                0f to MaskSide.copy(alpha = 0.704f),
                0.23f to MaskSide.copy(alpha = 0.632f),
                0.40f to MaskSide.copy(alpha = 0.448f),
                0.58f to MaskSide.copy(alpha = 0.104f),
                0.74f to MaskSide.copy(alpha = 0f)
            )
        )
        drawRect(
            Brush.verticalGradient(
                0.43f to PicnicColors.Background.copy(alpha = 0f),
                0.68f to PicnicColors.Background.copy(alpha = 0.288f),
                0.86f to PicnicColors.Background.copy(alpha = 0.624f),
                1f to PicnicColors.Background.copy(alpha = 0.768f)
            )
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                0.16f to Color.Black.copy(alpha = 0f)
            )
        )
    }
}
