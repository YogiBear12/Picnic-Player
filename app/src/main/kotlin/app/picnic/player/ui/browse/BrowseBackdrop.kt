package app.picnic.player.ui.browse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import app.picnic.player.ui.ambient.AmbientBackground
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.ambient.LocalAmbientBackgrounds
import app.picnic.player.ui.ambient.bakeGradient
import app.picnic.player.ui.ambient.drawBakedGradient
import app.picnic.player.ui.ambient.rememberAmbientPalette
import app.picnic.player.ui.theme.TvBrowseMotion
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.first

private const val BACKDROP_WIDTH_FRACTION = 0.78f

@Composable
internal fun BrowseBackdrop(
    backdropUrl: String?,
    ambientUrl: String?,
    ambientLoader: AmbientPaletteLoader,
    alphaScale: State<Float>,
    modifier: Modifier = Modifier
) {
    var displayedUrl by remember { mutableStateOf<String?>(null) }
    var imageLoaded by remember { mutableStateOf(false) }
    val layerAlpha = remember { Animatable(0f) }

    LaunchedEffect(backdropUrl) {
        if (backdropUrl == displayedUrl) return@LaunchedEffect
        if (displayedUrl != null) {
            layerAlpha.animateTo(0f, tween(TvBrowseMotion.BACKDROP_FADE_OUT_MS, easing = EaseOut))
        }
        imageLoaded = false
        layerAlpha.snapTo(0f)
        displayedUrl = backdropUrl
        if (backdropUrl == null) return@LaunchedEffect
        snapshotFlow { imageLoaded }.first { it }
        layerAlpha.animateTo(1f, tween(TvBrowseMotion.ARTWORK_FADE_IN_MS, easing = EaseOut))
    }

    val scrimAlpha = remember { Animatable(0f) }

    val ambientOn = LocalAmbientBackgrounds.current
    val palette = if (ambientOn) rememberAmbientPalette(ambientUrl, ambientLoader) else null
    val hasImage = displayedUrl != null

    val scrimReady = !ambientOn || palette != null
    LaunchedEffect(scrimReady) {
        if (scrimReady) {
            scrimAlpha.animateTo(1f, tween(SCRIM_FADE_IN_MS, easing = EaseOut))
        }
    }

    Box(modifier) {
        if (ambientOn) {
            AmbientBackground(palette, Modifier.fillMaxSize(), base = Color.Transparent)
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = layerAlpha.value * alphaScale.value
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
            AsyncImage(
                model = displayedUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                onState = { st ->
                    if (st is AsyncImagePainter.State.Success &&
                        st.result.request.data == displayedUrl
                    ) {
                        imageLoaded = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(BACKDROP_WIDTH_FRACTION)
                    .aspectRatio(16f / 9f)
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = scrimAlpha.value
                    drawBakedGradient(sideScrim, s)
                    drawBakedGradient(bottomScrim, s)
                }
        )
    }
}

private const val SCRIM_FADE_IN_MS = 1250

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
