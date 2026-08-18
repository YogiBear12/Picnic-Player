package app.picnic.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.ui.player.osd.SubtitleDelayHud
import app.picnic.player.ui.player.osd.TrickplayCell
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay

@Composable
fun BoxScope.NextUpScrim(backdropUrl: String?, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(Modifier.fillMaxSize()) {
            backdropUrl?.let { url ->
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
    }
}

@Composable
fun BoxScope.PlayerLoadingState(state: PlayerUiState) {
    if (state.error != null) return
    if (state.isLoading) {
        state.backdropUrl?.let { url ->
            Image(
                painter = rememberAsyncImagePainter(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = PicnicColors.Accent)
    } else if (state.buffering && !state.isPlaying) {
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = PicnicColors.Accent)
    }
}

@Composable
fun BoxScope.PlayerNotice(notice: String?, onExpired: () -> Unit) {
    notice ?: return
    LaunchedEffect(notice) {
        delay(NOTICE_VISIBLE_MS)
        onExpired()
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(4f)
            .padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = notice,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun BoxScope.SubtitleDelayOverlay(
    adjust: SubtitleDelayAdjust,
    delayMs: Long,
    onExit: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .focusRequester(adjust.focusRequester)
            .focusable()
            .onKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                        adjust.release()
                        true
                    }
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.DirectionLeft -> {
                        adjust.holdEarlier()
                        true
                    }
                    event.key == Key.DirectionRight -> {
                        adjust.holdLater()
                        true
                    }
                    event.key == Key.Back || event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                        adjust.release()
                        onExit()
                        true
                    }
                    else -> false
                }
            }
    ) {
        SubtitleDelayHud(delayMs = delayMs, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun BoxScope.PlayPausePulse(pulse: PlayerPulse) {
    AnimatedVisibility(
        visible = pulse.visible,
        modifier = Modifier.align(Alignment.Center),
        enter = scaleIn(initialScale = 0.72f, animationSpec = tween(200)) +
            fadeIn(animationSpec = tween(200)),
        exit = scaleOut(targetScale = 0.88f, animationSpec = tween(240)) +
            fadeOut(animationSpec = tween(240))
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (pulse.playing) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
fun BoxScope.TrickplayPreviewOverlay(preview: TrickplayPreview, barBottomInset: Dp) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
    ) {
        val barWidth = maxWidth - OsdHorizontalPadding * 2
        val xOffset = OsdHorizontalPadding +
            (barWidth * preview.fraction - TrickplayPreviewWidth / 2f)
                .coerceIn(0.dp, barWidth - TrickplayPreviewWidth)
        TrickplayPreviewImage(
            frame = preview.frame,
            width = TrickplayPreviewWidth,
            height = TrickplayPreviewHeight,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset {
                    IntOffset(
                        x = xOffset.roundToPx(),
                        y = -(barBottomInset + TrickplayGapAboveScrubBar).roundToPx()
                    )
                }
        )
    }
}

@Composable
private fun TrickplayPreviewImage(
    frame: TrickplayFrame,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    TrickplayCell(
        frame = frame,
        modifier = modifier
            .width(width)
            .height(height)
    )
}
