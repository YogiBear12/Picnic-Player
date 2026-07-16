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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.picnic.player.ui.ambient.AmbientBackground
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.ambient.LocalAmbientBackgrounds
import app.picnic.player.ui.ambient.rememberAmbientPalette
import app.picnic.player.ui.grid.OceanAmbientBackground
import app.picnic.player.ui.theme.TvBrowseMotion
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.first

/** Backdrop image width as a fraction of the screen. */
private const val BACKDROP_WIDTH_FRACTION = 0.78f

/**
 * Backdrop band: black base → ambient washes → focused item
 * backdrop (top-right, 78% screen width, 16:9) with DstIn edge dissolve.
 */
@Composable
internal fun BrowseBackdrop(
    backdropUrl: String?,
    ambientUrl: String?,
    ambientLoader: AmbientPaletteLoader,
    modifier: Modifier = Modifier
) {
    var displayedUrl by remember { mutableStateOf<String?>(null) }
    // Set by the AsyncImage when the *currently committed* backdrop finishes loading.
    var imageLoaded by remember { mutableStateOf(false) }
    val layerAlpha = remember { Animatable(0f) }

    // Single owner of [layerAlpha]: fade the outgoing image out, commit the newest URL, wait for
    // it to load, then fade it in. Keyed on backdropUrl so the latest navigation always wins.
    //
    // The fade-in MUST live here, not in a competing coroutine. Previously onState launched its
    // own layerAlpha.animateTo(1f); when the *outgoing* image loaded late, that mutation cancelled
    // this effect's fade-out via the Animatable mutation mutex, so the effect never reached
    // `displayedUrl = backdropUrl` and the backdrop stayed stuck on the previous item while the
    // palette/logo/details had already advanced (the reported desync).
    LaunchedEffect(backdropUrl) {
        if (backdropUrl == displayedUrl) return@LaunchedEffect
        if (displayedUrl != null) {
            layerAlpha.animateTo(0f, tween(TvBrowseMotion.BACKDROP_FADE_OUT_MS, easing = EaseOut))
        }
        imageLoaded = false
        layerAlpha.snapTo(0f)
        displayedUrl = backdropUrl
        if (backdropUrl == null) return@LaunchedEffect
        // Wait for the committed image, then fade it in. snapshotFlow reads the current value
        // first, so a cache hit that already flipped the flag fades in immediately.
        snapshotFlow { imageLoaded }.first { it }
        layerAlpha.animateTo(1f, tween(TvBrowseMotion.ARTWORK_FADE_IN_MS, easing = EaseOut))
    }

    // One-time scrim fade-in, started together with the first ambient-colour fade (when the
    // palette first loads) and using the same duration, so the colour isn't delayed by it.
    val scrimAlpha = remember { Animatable(0f) }

    // OFF → static ocean wash; no colour extraction (skips the decode entirely). The focused
    // backdrop image, its DstIn dissolve, and the scrim are unchanged — only the wash swaps.
    val ambientOn = LocalAmbientBackgrounds.current
    val palette = if (ambientOn) rememberAmbientPalette(ambientUrl, ambientLoader) else null
    val hasImage = displayedUrl != null

    // Fade the scrim in alongside the first ambient colour (same trigger + duration). With the
    // ocean wash there is no colour load to wait on, so the scrim fades in from first composition.
    val scrimReady = !ambientOn || palette != null
    LaunchedEffect(scrimReady) {
        if (scrimReady) {
            scrimAlpha.animateTo(1f, tween(SCRIM_FADE_IN_MS, easing = EaseOut))
        }
    }

    Box(modifier) {
        if (ambientOn) {
            AmbientBackground(palette, Modifier.fillMaxSize(), base = Color.Transparent)
        } else {
            OceanAmbientBackground(Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .fillMaxSize()
                // The offscreen layer exists for the DstIn edge dissolve. Applying the fade
                // ALPHA on this same layer (not on the inner image) is the key perf move: the
                // image + dissolve rasterise into the offscreen once, then the fade only
                // varies the layer's composite alpha — so an animating backdrop no longer
                // re-renders the full-screen offscreen every frame (the old per-move GPU spike).
                // The offscreen layer exists for the DstIn edge dissolve. Applying the fade
                // ALPHA on this same layer (not on the inner image) keeps the fade composite-
                // only — the image + dissolve rasterise once, then only the layer alpha varies.
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = layerAlpha.value
                }
                // drawWithCache builds the two dissolve gradients once per size instead of
                // allocating fresh shaders on every draw.
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
                    // Only flag the load whose URL matches the committed backdrop; the swap
                    // effect owns the fade-in. Guarding on the URL stops a late-arriving
                    // success for the outgoing image from fading the wrong artwork in.
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

        // Scrim baked into a single drawBehind so the fade reads alpha in the draw phase
        // (no recomposition, no full-screen offscreen alpha layer competing with the colour).
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val s = scrimAlpha.value
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.55f * s),
                            0.35f to Color.Black.copy(alpha = 0.18f * s),
                            0.65f to Color.Transparent
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            0.65f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f * s)
                        )
                    )
                }
        )
    }
}

/** Matches AmbientBackground's PALETTE_FADE_MS so scrim + colour finish together. */
private const val SCRIM_FADE_IN_MS = 1250
