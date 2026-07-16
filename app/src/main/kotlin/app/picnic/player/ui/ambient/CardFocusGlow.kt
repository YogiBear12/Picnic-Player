package app.picnic.player.ui.ambient

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import kotlin.math.PI
import kotlin.math.cos

private const val PulseAlphaMin = 0.4f

/**
 * Must stay strictly below 1. TV Material glow is drawn via [android.graphics.Paint.setShadowLayer]
 * with [Glow.elevationColor] as the shadow tint; fully opaque (ARGB alpha 255) hits a different
 * compositor path on some TV GPUs and flashes dark for a frame at the pulse peak.
 */
private const val PulseAlphaMax = 0.92f

/** Full breathe cycle (dim → bright → dim). */
private const val PulseFullCycleMs = 2000

/**
 * Focused-card [Glow]: static baseline, or an alpha-only pulse when
 * [LocalPulseFocusGlow] is on and [focused] is true. Elevation stays fixed —
 * only brightness breathes. The infinite transition only composes while pulsing.
 *
 * Pulse uses a linear phase mapped through a cosine for a gentle ~2s breathe.
 * Peak alpha is capped below 1 so the underlying [Paint.setShadowLayer] tint never
 * becomes fully opaque (device QA: dark flash at the bright peak, not at the dim
 * turnaround).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun rememberCardFocusGlow(baseColor: Color, focused: Boolean): Glow {
    val pulseEnabled = LocalPulseFocusGlow.current
    if (!pulseEnabled || !focused) {
        return Glow(
            elevationColor = baseColor.copy(alpha = CardFocusGlowAlpha),
            elevation = CardFocusGlowElevation
        )
    }

    val transition = rememberInfiniteTransition(label = "cardFocusGlowPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseFullCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cardFocusGlowPhase"
    )
    // (1 - cos(2πt)) / 2 ∈ [0, 1]; value and derivative match at t=0 and t=1.
    val breathe = 0.5f * (1f - cos(phase * 2f * PI.toFloat()))
    val alpha = PulseAlphaMin + (PulseAlphaMax - PulseAlphaMin) * breathe
    return Glow(
        elevationColor = baseColor.copy(alpha = alpha),
        elevation = CardFocusGlowElevation
    )
}
