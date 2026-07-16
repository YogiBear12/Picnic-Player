package app.picnic.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Frosted card background — matches TrackPanel / OSD frosted-glass tokens. */
private val CardBackground = Color(0xC0181E24)

/**
 * Next-up card shown bottom-right at the end of an episode (or during the outro in opt-in mode).
 * The full-screen next-up backdrop and the shrunk top-left video are drawn by PlayerScreen beneath
 * this layer, so this composable draws only the card.
 *
 * Stateless: callers supply all data and callbacks. [countdownSeconds] is the number of seconds to
 * count down before [onPlayNext] is called automatically. The [LaunchedEffect] keyed on [item]
 * means the countdown resets correctly if the item ever changes; in practice it does not.
 *
 * Focus lands on the thumbnail on entry. Selecting it calls [onPlayNext] immediately. Back calls
 * [onBack]. D-pad directions are trapped so focus cannot leak out of the overlay.
 */
@Composable
fun NextUpOverlay(
    item: NextUpItem,
    countdownSeconds: Int,
    onPlayNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var secondsLeft by remember(item) { mutableIntStateOf(countdownSeconds) }
    val thumbFocus = remember { FocusRequester() }

    // Countdown: structured concurrency cancels this coroutine when the composable leaves
    // (Back pressed / navigation) — no manual cleanup needed. Per side-effects.md.
    LaunchedEffect(item) {
        for (remaining in countdownSeconds downTo 1) {
            secondsLeft = remaining
            delay(1_000)
        }
        secondsLeft = 0
        onPlayNext()
    }

    // Request focus once on entry. The parent AnimatedVisibility handles the overall fade-in.
    LaunchedEffect(Unit) {
        runCatching { thumbFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Intercept Back here, at the overlay root, before the focus system can consume the
            // first press to move focus off the card. onPreviewKeyEvent dispatches ancestor→focused,
            // so this fires ahead of the thumbnail's own handling and Back works in a single press.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            }
    ) {
        // Card only — anchored bottom-right over the full-screen backdrop; the shrunk video sits
        // top-left. The backdrop and video are drawn by PlayerScreen beneath this layer.
        NextUpCard(
            item = item,
            secondsLeft = secondsLeft,
            thumbFocusRequester = thumbFocus,
            onPlayNext = onPlayNext,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 56.dp, bottom = 56.dp)
        )
    }
}

@Composable
private fun NextUpCard(
    item: NextUpItem,
    secondsLeft: Int,
    thumbFocusRequester: FocusRequester,
    onPlayNext: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(max = 520.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Countdown label above the card.
        if (secondsLeft > 0) {
            Text(
                text = "Next episode playing in $secondsLeft second${if (secondsLeft == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // Frosted card: text info (left) + focusable thumbnail (right).
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CardBackground)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text column.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaLine = listOf(item.seasonEpisode, item.meta)
                    .filter { it.isNotEmpty() }
                    .joinToString(" • ")
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.overview.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Focusable thumbnail with play glyph.
            NextUpThumbnail(
                thumbUrl = item.thumbUrl,
                onPlayNext = onPlayNext,
                onBack = onBack,
                focusRequester = thumbFocusRequester,
                modifier = Modifier.size(width = 160.dp, height = 90.dp)
            )
        }
    }
}

@Composable
private fun NextUpThumbnail(
    thumbUrl: String?,
    onPlayNext: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111820))
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onPlayNext()
                        true
                    }
                    // Back is handled by the overlay root's onPreviewKeyEvent (single-press).
                    // Trap all D-pad directions so focus stays in the overlay.
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            }
            .then(
                if (focused) {
                    Modifier.background(Color.White.copy(alpha = 0.15f))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!thumbUrl.isNullOrEmpty()) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Circular play glyph overlay.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play next episode",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
