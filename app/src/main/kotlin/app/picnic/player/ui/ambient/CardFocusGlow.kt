package app.picnic.player.ui.ambient

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos

private const val PulseAlphaMin = 0.4f

/**
 * Must stay strictly below 1. The glow is a [android.graphics.Paint.setShadowLayer] tint; fully
 * opaque (ARGB alpha 255) hits a different compositor path on some TV GPUs and flashes dark for a
 * frame at the pulse peak.
 */
private const val PulseAlphaMax = 0.92f

private const val PulseFullCycleMs = 2000

private const val ScaleFocusMs = 300
private const val ScaleUnfocusMs = 500
private const val ScalePressMs = 120
private const val ScaleReleaseMs = 300
private val ScaleEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/**
 * Draw-phase copy of tv-material 1.1.0 `SurfaceGlowNode`, `currentGlow` and `tvSurfaceScale`. The
 * card keeps its `glow` at the default and takes [CardFocusGlow.interactionSource] and
 * [CardFocusGlow.focusedScale], since the library draws its glow inside the card's scale layer.
 */
@Composable
fun rememberCardFocusGlow(
    baseColor: Color,
    shape: Shape,
    focusedScale: Float,
    enabled: Boolean = true
): CardFocusGlow {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState()
    val pressed = interactionSource.collectIsPressedAsState()
    return CardFocusGlow(
        interactionSource = interactionSource,
        focusedScale = focusedScale,
        baseColor = baseColor,
        shape = shape,
        enabled = enabled,
        focused = focused,
        pressed = pressed,
        scale = animatedCardScale(interactionSource, enabled && focused.value && !pressed.value, focusedScale),
        phase = if (LocalPulseFocusGlow.current && focused.value) pulsePhase() else null
    )
}

@Stable
class CardFocusGlow internal constructor(
    val interactionSource: MutableInteractionSource,
    val focusedScale: Float,
    internal val baseColor: Color,
    internal val shape: Shape,
    internal val enabled: Boolean,
    val focused: State<Boolean>,
    internal val pressed: State<Boolean>,
    internal val scale: State<Float>,
    internal val phase: State<Float>?
)

fun Modifier.cardFocusGlow(glow: CardFocusGlow): Modifier = drawWithCache {
    val outline = glow.shape.createOutline(size, layoutDirection, this)
    val radiusPx = CardFocusGlowElevation.toPx()
    val paint = Paint()
    onDrawBehind {
        if (glow.enabled && glow.focused.value && !glow.pressed.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val alpha = glow.phase?.let { pulseAlpha(it.value) } ?: CardFocusGlowAlpha
            val color = glow.baseColor.copy(alpha = alpha)
            paint.asFrameworkPaint().apply {
                this.color = color.copy(alpha = 0f).toArgb()
                setShadowLayer(radiusPx, 0f, 0f, color.toArgb())
            }
            scale(glow.scale.value, pivot = center) {
                drawIntoCanvas { canvas ->
                    when (outline) {
                        is Outline.Rectangle -> canvas.drawRect(outline.rect, paint)
                        is Outline.Rounded -> canvas.drawRoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            radiusX = outline.roundRect.topLeftCornerRadius.x,
                            radiusY = outline.roundRect.topLeftCornerRadius.y,
                            paint = paint
                        )
                        is Outline.Generic -> canvas.drawPath(outline.path, paint)
                    }
                }
            }
        }
    }
}

@Composable
private fun animatedCardScale(
    interactionSource: InteractionSource,
    zoomed: Boolean,
    focusedScale: Float
): State<Float> {
    val interaction by interactionSource.interactions.collectAsState(initial = FocusInteraction.Focus())
    return animateFloatAsState(
        targetValue = if (zoomed) focusedScale else 1f,
        animationSpec = tween(scaleDurationMs(interaction), easing = ScaleEasing),
        label = "cardFocusGlowScale"
    )
}

private fun scaleDurationMs(interaction: Interaction): Int = when (interaction) {
    is FocusInteraction.Focus -> ScaleFocusMs
    is FocusInteraction.Unfocus -> ScaleUnfocusMs
    is PressInteraction.Press -> ScalePressMs
    else -> ScaleReleaseMs
}

@Composable
private fun pulsePhase(): State<Float> = rememberInfiniteTransition(label = "cardFocusGlowPulse").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(PulseFullCycleMs, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    label = "cardFocusGlowPhase"
)

// (1 - cos(2πt)) / 2 ∈ [0, 1]; value and derivative match at t=0 and t=1.
private fun pulseAlpha(phase: Float): Float {
    val breathe = 0.5f * (1f - cos(phase * 2f * PI.toFloat()))
    return PulseAlphaMin + (PulseAlphaMax - PulseAlphaMin) * breathe
}
