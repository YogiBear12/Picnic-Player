@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.playback.subtitleBottomPaddingFraction
import app.picnic.player.playback.videoRectHeightFraction

@Composable
fun BoxScope.PlayerStage(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    subtitleAppearance: SubtitleAppearance,
    showNextUpOverlay: Boolean,
    pipModifier: Modifier
) {
    val playerScale by animateFloatAsState(
        targetValue = if (showNextUpOverlay) NextUpPlayerScale else 1f,
        animationSpec = tween(400),
        label = "playerScale"
    )
    val insetFraction = ((1f - playerScale) / (1f - NextUpPlayerScale)).coerceIn(0f, 1f)
    val playerInset = NextUpPlayerInset * insetFraction

    val (bitmapCues, textCues) = state.subtitleCues.partition { it.bitmap != null }
    val presentationState = rememberPresentationState(viewModel.player)

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = playerInset, top = playerInset)
            .fillMaxSize(playerScale)
            .clip(RoundedCornerShape(12.dp * insetFraction))
            .background(Color.Black)
            .then(pipModifier)
            .zIndex(if (showNextUpOverlay) 2f else 0f),
        contentAlignment = Alignment.Center
    ) {
        PlayerSurface(
            player = viewModel.player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(
                ContentScale.Fit,
                presentationState.videoSizeDp
            )
        )
        AndroidView(
            factory = { context -> viewModel.assOverlayView(context) },
            modifier = Modifier.resizeWithContentScale(
                ContentScale.Fit,
                presentationState.videoSizeDp
            )
        )
        AndroidView(
            factory = { context -> SubtitleView(context) },
            update = { it.setCues(bitmapCues) },
            onReset = { it.setCues(emptyList()) },
            modifier = Modifier.resizeWithContentScale(
                ContentScale.Fit,
                bitmapSubtitleFrame(bitmapCues, presentationState.videoSizeDp)
            )
        )
        val videoDynamicRange by viewModel.videoDynamicRange.collectAsStateWithLifecycle()
        val blackBars by viewModel.blackBars.collectAsStateWithLifecycle()
        val videoRectHeight = presentationState.videoSizeDp?.let { video ->
            videoRectHeightFraction(video.width, video.height, maxWidth.value, maxHeight.value)
        } ?: 1f
        val bottomPadding = subtitleBottomPaddingFraction(
            subtitleAppearance.area,
            subtitleAppearance.insetPercent,
            videoRectHeight,
            blackBars
        )
        AndroidView(
            factory = { context -> SubtitleView(context) },
            update = { subtitleView ->
                viewModel.attachSubtitleView(subtitleView, bottomPadding, videoDynamicRange)
                subtitleView.setCues(textCues)
            },
            onReset = { it.setCues(emptyList()) },
            modifier = Modifier.fillMaxSize()
        )
    }
}
