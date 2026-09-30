@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.photo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.ActionButton
import app.picnic.player.ui.common.ArtworkImage
import app.picnic.player.ui.common.ArtworkPlaceholder
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.PosterPlaceholderLabel
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.navigation.PhotoKey

@Composable
fun PhotoScreen(
    photoKey: PhotoKey,
    onBack: () -> Unit,
    onSessionExpired: (String) -> Unit,
    viewModel: PhotoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val current = state.current

    LaunchedEffect(photoKey) { viewModel.bind(photoKey) }
    LaunchedEffect(state.sessionExpiredServerId) {
        state.sessionExpiredServerId?.let {
            viewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
    }
    BackHandler { if (state.overlay) viewModel.toggleOverlay() else onBack() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val url = current?.let {
            LocalImageUrls.current.containedPrimary(it.id.toString(), it.imageTag, constraints.maxWidth, constraints.maxHeight)
        }
        var attempt by remember(current?.id) { mutableIntStateOf(0) }
        var imageFailed by remember(current?.id, attempt) { mutableStateOf(false) }
        val showRetry = state.listFailed || imageFailed
        LaunchedEffect(showRetry) {
            if (showRetry) retryFocus.requestFocusWhenAttached() else focus.requestFocusWhenAttached()
        }
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            viewModel.previous()
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.next()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (showRetry) return@onPreviewKeyEvent false
                            if (event.nativeKeyEvent.repeatCount == 0) viewModel.toggleOverlay()
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
        ) {
            if (url != null && !imageFailed) {
                key(url, attempt) {
                    ArtworkImage(
                        url = url,
                        contentDescription = current.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onSettled = { failed -> imageFailed = failed }
                    )
                }
            } else if (url == null) {
                ArtworkPlaceholder()
                PosterPlaceholderLabel(current?.name)
            }
            if (showRetry) {
                ActionButton(
                    label = "Retry",
                    onActivate = {
                        attempt++
                        viewModel.retry()
                    },
                    focusRequester = retryFocus,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (state.overlay && state.index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterStart).padding(24.dp).size(36.dp)
                )
            }
            if (state.overlay && state.index < state.photos.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(24.dp).size(36.dp)
                )
            }
            if (state.overlay && current != null) {
                val count = if (state.listLoaded) "${state.index + 1} / ${state.photos.size}" else ""
                Text(
                    text = listOf(current.name, count).filter { it.isNotBlank() }.joinToString("  "),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
