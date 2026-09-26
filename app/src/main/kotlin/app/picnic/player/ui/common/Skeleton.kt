package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SkeletonFill = Color.White.copy(alpha = 0.14f)
internal val SkeletonTitleHeight = 12.dp
internal val SkeletonTextHeight = 10.dp
internal val SkeletonTextCorner = 3.dp
internal val SkeletonBlockCorner = 6.dp

/**
 * Phase comes from the shared frame clock, not from when this instance started, so every skeleton on
 * screen pulses in step however late it was composed. Read only inside a draw-phase lambda such as
 * `graphicsLayer { }`, so the pulse never recomposes.
 */
@Composable
internal fun rememberSkeletonPulse(): State<Float> {
    val alpha = remember { mutableFloatStateOf(PulseMinAlpha) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { alpha.floatValue = skeletonAlphaAt(it) }
    }
    return alpha
}

private fun skeletonAlphaAt(frameTimeMillis: Long): Float {
    val t = (frameTimeMillis % (PulseHalfCycleMs * 2)).toFloat() / PulseHalfCycleMs
    val rise = if (t <= 1f) t else 2f - t
    return PulseMinAlpha + (1f - PulseMinAlpha) * rise
}

private const val PulseHalfCycleMs = 900L
private const val PulseMinAlpha = 0.35f

internal fun Modifier.skeletonPulse(pulse: State<Float>): Modifier = graphicsLayer { alpha = pulse.value }

internal fun Modifier.skeletonFill(corner: Dp): Modifier = clip(RoundedCornerShape(corner)).background(SkeletonFill)

@Composable
internal fun SkeletonBar(width: Dp, height: Dp, corner: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(width = width, height = height).skeletonFill(corner))
}

@Composable
internal fun SkeletonTextBar(width: Dp, modifier: Modifier = Modifier) {
    SkeletonBar(width = width, height = SkeletonTextHeight, corner = SkeletonTextCorner, modifier = modifier)
}

@Composable
internal fun SkeletonCardRow(
    cardWidth: Dp,
    spacing: Dp,
    startInset: Dp = 0.dp,
    card: @Composable () -> Unit
) {
    val pulse = rememberSkeletonPulse()
    BoxWithConstraints(Modifier.clipToBounds()) {
        val slot = cardWidth + spacing
        val cards = if (slot > 0.dp) ((maxWidth - startInset) / slot).toInt() + 1 else 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = startInset)
                .skeletonPulse(pulse)
        ) {
            repeat(cards) { card() }
        }
    }
}
