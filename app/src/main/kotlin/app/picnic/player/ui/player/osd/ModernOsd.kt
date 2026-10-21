package app.picnic.player.ui.player.osd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import app.picnic.player.data.playback.MediaSegment
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.ui.common.flatSurfaceBorder
import app.picnic.player.ui.common.formatClock
import app.picnic.player.ui.player.ChapterMark
import app.picnic.player.ui.player.PlayerUiState
import app.picnic.player.ui.player.TrickplayPreview

private const val SCRUB_STEP_MS = 10_000L
private const val SCRUB_STEPS_PER_SPEED = 60
private const val SCRUB_SPEEDS = 3
private const val CHAPTER_GAP_MAX_SHARE = 4

internal val OsdHorizontalPadding = 56.dp
private val OsdTitleInset = 40.dp
private val OsdScrimEdge = Color(0xCC000000)
private val ScrubberFocusedHeight = 6.dp
private val ScrubberRestingHeight = 4.dp
private val ScrubberThumbSize = 14.dp
private val ScrubberTouchHeight = 18.dp
private val ChapterGap = 3.dp

internal fun chapterBoundaries(chapters: List<ChapterMark>, durationMs: Long): List<Float> {
    if (durationMs <= 0) return listOf(0f, 1f)
    val starts = chapters.map { it.startMs }
        .filter { it > 0 && it < durationMs }
        .map { it.toFloat() / durationMs }
    return (listOf(0f, 1f) + starts).distinct().sorted()
}

internal fun previewTitle(chapters: List<ChapterMark>, segments: List<MediaSegment>, positionMs: Long): String? {
    if (chapters.isNotEmpty()) return chapters[activeChapterIndex(chapters, positionMs)].title
    return segments.firstOrNull { positionMs in it.startMs until it.endMs }?.let { segmentName(it.kind) }
}

internal fun scrubStepMs(repeatCount: Int): Long = SCRUB_STEP_MS * (repeatCount / SCRUB_STEPS_PER_SPEED + 1).coerceAtMost(SCRUB_SPEEDS)

@Composable
internal fun OsdTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(OsdScrimEdge, Color.Transparent)))
            .padding(horizontal = OsdHorizontalPadding, vertical = OsdTitleInset)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun ModernOsd(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSettings: () -> Unit,
    onChapters: () -> Unit,
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

    val duration = state.durationMs.coerceAtLeast(0)
    val shown = if (scrubbing) scrubTarget else state.positionMs
    val fraction = if (duration > 0) (shown.toFloat() / duration) else 0f
    val boundaries = remember(state.chapters, duration) { chapterBoundaries(state.chapters, duration) }

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
        onScrubPreviewChange(
            TrickplayPreview(
                frame = frame,
                fraction = fraction,
                title = previewTitle(state.chapters, state.segments, scrubTarget),
                positionMs = scrubTarget
            )
        )
    }

    fun scrubBy(direction: Int, repeatCount: Int) {
        if (duration <= 0) return
        if (!scrubbing) {
            scrubbing = true
            scrubTarget = state.positionMs
        }
        scrubTarget = (scrubTarget + direction * scrubStepMs(repeatCount)).coerceIn(0, duration)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, OsdScrimEdge)))
            .padding(horizontal = OsdHorizontalPadding, vertical = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = showSkipInOsd && state.currentSegment != null) {
                state.currentSegment?.let { segment ->
                    Row {
                        OsdTextButton(
                            text = skipLabel(segment.kind),
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
                boundaries = boundaries,
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
                        val repeatCount = event.nativeKeyEvent.repeatCount
                        when (event.key) {
                            Key.DirectionLeft -> {
                                scrubBy(-1, repeatCount)
                                true
                            }
                            Key.DirectionRight -> {
                                scrubBy(1, repeatCount)
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                if (repeatCount == 0) {
                                    if (scrubbing) {
                                        onSeek(scrubTarget)
                                        scrubbing = false
                                        onScrubPreviewChange(null)
                                    } else {
                                        onPlayPause()
                                    }
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (!scrubbing) audioFocusRequester.requestFocus()
                                true
                            }
                            Key.DirectionDown -> {
                                if (!scrubbing) onChapters()
                                true
                            }
                            Key.Back -> {
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
    boundaries: List<Float>,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val height = if (focused) ScrubberFocusedHeight else ScrubberRestingHeight
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(ScrubberTouchHeight)) {
        val barHeight = height.toPx()
        val top = (size.height - barHeight) / 2
        val halfGap = ChapterGap.toPx() / 2
        for (index in 0 until boundaries.lastIndex) {
            val start = size.width * boundaries[index]
            val end = size.width * boundaries[index + 1]
            val inset = minOf(halfGap, (end - start) / CHAPTER_GAP_MAX_SHARE)
            val left = start + if (index == 0) 0f else inset
            val right = end - if (index == boundaries.lastIndex - 1) 0f else inset
            drawScrubberFill(Color.White.copy(alpha = 0.24f), left, right, top, barHeight)
            drawScrubberFill(Color.White.copy(alpha = 0.38f), left, minOf(right, size.width * buffered.coerceIn(0f, 1f)), top, barHeight)
            drawScrubberFill(color, left, minOf(right, size.width * fraction.coerceIn(0f, 1f)), top, barHeight)
        }
        if (focused) {
            val radius = minOf(ScrubberThumbSize.toPx() / 2, size.width / 2)
            val center = (size.width * fraction.coerceIn(0f, 1f)).coerceIn(radius, size.width - radius)
            drawCircle(color, radius, Offset(center, size.height / 2))
        }
    }
}

private fun DrawScope.drawScrubberFill(color: Color, left: Float, right: Float, top: Float, height: Float) {
    if (right <= left) return
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, height),
        cornerRadius = CornerRadius(height / 2)
    )
}

@Composable
private fun OsdButton(
    modifier: Modifier,
    focusRequester: FocusRequester,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    onDown: () -> Unit,
    onBack: () -> Unit,
    onInteract: () -> Unit,
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {
            onInteract()
            onClick()
        },
        enabled = focusEnabled,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = focusEnabled }
            .onKeyEvent { event ->
                if (!focusEnabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                onInteract()
                when (event.key) {
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
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.16f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.16f)
        ),
        border = flatSurfaceBorder()
    ) {
        content(focused)
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
    OsdButton(
        modifier = Modifier.size(40.dp),
        focusRequester = focusRequester,
        focusEnabled = focusEnabled,
        onClick = onClick,
        onDown = onDown,
        onBack = onBack,
        onInteract = onInteract
    ) { focused ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    if (focused) Color.Black else Color.White
            ) { icon() }
        }
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
    OsdButton(
        modifier = Modifier.height(40.dp),
        focusRequester = focusRequester,
        focusEnabled = focusEnabled,
        onClick = onClick,
        onDown = onDown,
        onBack = onBack,
        onInteract = onInteract
    ) { focused ->
        Box(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Color.Black else Color.White
            )
        }
    }
}
