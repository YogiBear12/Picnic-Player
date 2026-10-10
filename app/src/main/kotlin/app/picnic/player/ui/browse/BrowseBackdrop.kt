package app.picnic.player.ui.browse

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.picnic.player.ui.ambient.AmbientBackground
import app.picnic.player.ui.ambient.BackdropFrame
import app.picnic.player.ui.ambient.bakeGradient
import app.picnic.player.ui.ambient.drawBakedGradient
import coil3.compose.asPainter

private const val BACKDROP_WIDTH_FRACTION = 0.78f

@Composable
internal fun BrowseBackdrop(
    frame: BackdropFrame,
    modifier: Modifier = Modifier
) {
    val (art, artAlpha, wash, ambientOn, dim) = frame
    val context = LocalContext.current
    val painter = remember(art) { art?.image?.asPainter(context) }
    Box(modifier) {
        if (ambientOn) {
            AmbientBackground(wash, Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = artAlpha.value * dim.value
                }
                .drawWithCache {
                    val imgW = size.width * BACKDROP_WIDTH_FRACTION
                    val imgH = imgW * 9f / 16f
                    val imgLeft = size.width - imgW
                    val horizontal = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        ((imgLeft + imgW * 0.04f) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                        ((imgLeft + imgW * 0.55f) / size.width).coerceIn(0f, 1f) to Color.White
                    )
                    val vertical = Brush.verticalGradient(
                        0f to Color.White,
                        (imgH * 0.5f / size.height) to Color.White,
                        (imgH * 0.95f / size.height) to Color.Transparent
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(horizontal, blendMode = BlendMode.DstIn)
                        drawRect(vertical, blendMode = BlendMode.DstIn)
                    }
                }
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(BACKDROP_WIDTH_FRACTION)
                        .aspectRatio(16f / 9f)
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = if (ambientOn) wash.alpha else 1f
                    drawBakedGradient(sideScrim, s)
                    drawBakedGradient(bottomScrim, s)
                }
        )
    }
}

private const val SCRIM_BITMAP_W = 480
private const val SCRIM_BITMAP_H = 270

private val sideScrim: ImageBitmap by lazy {
    bakeGradient(SCRIM_BITMAP_W, SCRIM_BITMAP_H) {
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.35f to Color.Black.copy(alpha = 0.18f),
                0.65f to Color.Transparent
            )
        )
    }
}

private val bottomScrim: ImageBitmap by lazy {
    bakeGradient(SCRIM_BITMAP_W, SCRIM_BITMAP_H) {
        drawRect(
            Brush.verticalGradient(
                0.65f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.55f)
            )
        )
    }
}
