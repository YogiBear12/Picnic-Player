package app.picnic.player.ui.player.osd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.ui.common.formatClock
import app.picnic.player.ui.player.PlayerUiState
import app.picnic.player.ui.player.TrickplayPreview

/**
 * Minimalist, timeline-centric OSD. Trickplay previews are
 * rendered by [app.picnic.player.ui.player.PlayerScreen] so this column never
 * changes height while scrubbing.
 */
@Composable
fun ModernOsd(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSettings: () -> Unit,
    onChapters: () -> Unit,
    skipForwardSeconds: Int,
    skipBackwardSeconds: Int,
    onDismiss: () -> Unit,
    onInteract: () -> Unit,
    onScrubPreviewChange: (TrickplayPreview?) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onScrubBarBottomInset: (Dp) -> Unit,
    trickplayFor: (Long) -> TrickplayFrame?,
    audioFocusRequester: FocusRequester,
    subtitleFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    scrubberFocusRequester: FocusRequester,
    osdSkipFocusRequester: FocusRequester,
    showSkipInOsd: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true
) {
    val scrubberFocus = scrubberFocusRequester
    var scrubFocused by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    // Focus seeding is owned by PlayerScreen (it decides scrubber vs the button that opened
    // a just-closed panel), so the OSD no longer self-grabs the scrubber on enable.

    val duration = state.durationMs.coerceAtLeast(0)
    val shown = if (scrubbing) scrubTarget else state.positionMs
    val fraction = if (duration > 0) (shown.toFloat() / duration) else 0f

    // Publish scrub state so PlayerScreen can pause/resume playback and hold the OSD open
    // while an active scrub is in progress.
    LaunchedEffect(scrubbing) { onScrubbingChange(scrubbing) }

    LaunchedEffect(scrubbing, scrubTarget, duration) {
        if (!scrubbing || duration <= 0) {
            onScrubPreviewChange(null)
            return@LaunchedEffect
        }
        val frame = trickplayFor(scrubTarget) ?: run {
            onScrubPreviewChange(null)
            return@LaunchedEffect
        }
        onScrubPreviewChange(TrickplayPreview(frame, fraction))
    }

    fun scrubBy(direction: Int) {
        if (duration <= 0) return
        if (!scrubbing) {
            scrubbing = true
            scrubTarget = state.positionMs
        }
        // Honour the user's configured skip seconds (asymmetric fwd/back).
        val magnitude = (if (direction > 0) skipForwardSeconds else skipBackwardSeconds) * 1000L
        scrubTarget = (scrubTarget + direction * magnitude).coerceIn(0, duration)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .padding(horizontal = 56.dp, vertical = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1
                )
                if (state.subtitle.isNotEmpty()) {
                    Text(
                        state.subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            AnimatedVisibility(visible = showSkipInOsd && state.currentSegment != null) {
                Row {
                    val label = when (state.currentSegment?.kind) {
                        app.picnic.player.data.playback.SegmentKind.INTRO -> "Skip Intro"
                        app.picnic.player.data.playback.SegmentKind.RECAP -> "Skip Recap"
                        app.picnic.player.data.playback.SegmentKind.OUTRO -> "Skip Outro"
                        app.picnic.player.data.playback.SegmentKind.PREVIEW -> "Skip Preview"
                        app.picnic.player.data.playback.SegmentKind.COMMERCIAL -> "Skip Ad"
                        else -> "Skip"
                    }
                    OsdTextButton(
                        text = label,
                        focusRequester = osdSkipFocusRequester,
                        focusEnabled = focusEnabled,
                        onClick = onSkip,
                        onDown = { scrubberFocus.requestFocus() },
                        onBack = onDismiss,
                        onInteract = onInteract
                    )
                    Spacer(Modifier.size(12.dp))
                }
            }
            OsdIconButton(
                icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Audio") },
                focusRequester = audioFocusRequester,
                focusEnabled = focusEnabled,
                onClick = onAudio,
                onDown = { scrubberFocus.requestFocus() },
                onBack = onDismiss,
                onInteract = onInteract
            )
            Spacer(Modifier.size(12.dp))
            OsdIconButton(
                icon = { Icon(Icons.Filled.ClosedCaption, contentDescription = "Subtitles") },
                focusRequester = subtitleFocusRequester,
                focusEnabled = focusEnabled,
                onClick = onSubtitles,
                onDown = { scrubberFocus.requestFocus() },
                onBack = onDismiss,
                onInteract = onInteract
            )
            Spacer(Modifier.size(12.dp))
            OsdIconButton(
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                focusRequester = settingsFocusRequester,
                focusEnabled = focusEnabled,
                onClick = onSettings,
                onDown = { scrubberFocus.requestFocus() },
                onBack = onDismiss,
                onInteract = onInteract
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val rootHeight = coords.findRootCoordinates().size.height
                    val top = coords.boundsInRoot().top
                    onScrubBarBottomInset(with(density) { (rootHeight - top).toDp() })
                }
        ) {
            ScrubberBar(
                fraction = fraction,
                buffered = if (duration > 0) (state.bufferedMs.toFloat() / duration) else 0f,
                focused = scrubFocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(scrubberFocus)
                    .onFocusChanged { scrubFocused = it.isFocused }
                    .focusProperties { canFocus = focusEnabled }
                    .focusable(focusEnabled)
                    .onKeyEvent { event ->
                        if (!focusEnabled) return@onKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        onInteract()
                        when (event.key) {
                            Key.DirectionLeft -> {
                                scrubBy(-1)
                                true
                            }
                            Key.DirectionRight -> {
                                scrubBy(1)
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                if (scrubbing) {
                                    onSeek(scrubTarget)
                                    scrubbing = false
                                    onScrubPreviewChange(null)
                                } else {
                                    onPlayPause()
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                // Locked while actively scrubbing: can't escape to the OSD buttons
                                // (which would strand the trickplay preview on screen). Commit or
                                // cancel first.
                                if (!scrubbing) audioFocusRequester.requestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                if (!scrubbing) onChapters()
                                true
                            }
                            Key.Back -> {
                                // Dead branch in practice: Back is routed to the screen's
                                // BackHandler (enableOnBackInvokedCallback bypasses focused nodes),
                                // which owns scrub-cancel + play-state restore. Kept for parity.
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(shown), color = Color.White, style = MaterialTheme.typography.labelLarge)
            // Total runtime (not time remaining, no "ends at" clock).
            Text(
                formatClock(duration),
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ScrubberBar(
    fraction: Float,
    buffered: Float,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val height = if (focused) 6.dp else 4.dp
    val thumbSize = 14.dp
    val color = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier.height(18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height))
                .background(Color.White.copy(alpha = 0.24f))
        )
        Box(
            Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(height)).background(Color.White.copy(alpha = 0.38f))
        )
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(height)).background(color)
        )
        if (focused) {
            val thumbCenter = (maxWidth * fraction.coerceIn(0f, 1f))
                .coerceIn(thumbSize / 2, maxWidth - thumbSize / 2)
            Box(
                Modifier
                    .offset(x = thumbCenter - thumbSize / 2)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun OsdIconButton(
    icon: @Composable () -> Unit,
    focusRequester: FocusRequester,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    onDown: () -> Unit,
    onBack: () -> Unit,
    onInteract: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.16f))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = focusEnabled }
            .focusable(focusEnabled)
            .onKeyEvent { event ->
                if (!focusEnabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onInteract()
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onClick()
                        true
                    }
                    Key.DirectionDown -> {
                        onDown()
                        true
                    }
                    Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (focused) Color.Black else Color.White
        ) { icon() }
    }
}

@Composable
private fun OsdTextButton(
    text: String,
    focusRequester: FocusRequester,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    onDown: () -> Unit,
    onBack: () -> Unit,
    onInteract: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.16f))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = focusEnabled }
            .focusable(focusEnabled)
            .onKeyEvent { event ->
                if (!focusEnabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onInteract()
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onClick()
                        true
                    }
                    Key.DirectionDown -> {
                        onDown()
                        true
                    }
                    Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Color.Black else Color.White
        )
    }
}
