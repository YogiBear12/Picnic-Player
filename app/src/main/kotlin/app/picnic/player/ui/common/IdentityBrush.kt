package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Stable identity colour for tiles without artwork: hash the display name to a
 * hue and shade it as a diagonal gradient in the app's dim ocean register.
 * Same name → same colour on every screen and launch.
 */
@Composable
fun rememberIdentityBrush(name: String): Brush = remember(name) {
    val hue = (abs(name.trim().lowercase().hashCode()) % 360).toFloat()
    Brush.linearGradient(
        colors = listOf(
            Color.hsl(hue, 0.48f, 0.40f),
            Color.hsl((hue + 40f) % 360f, 0.55f, 0.24f)
        )
    )
}
