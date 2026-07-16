package app.picnic.player.ui.ambient

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

val CardFocusBorderWidth = 1.5.dp

/** TV Material [androidx.tv.material3.Glow] — tune here until it feels right. */
val CardFocusGlowElevation = 8.dp
const val CardFocusGlowAlpha = 0.6f

private const val AccentFadeMs = 380

data class CardFocusAccent(
    val borderColor: Color,
    val glowColor: Color
)

/**
 * Focus accent (border + glow colour) from the artwork palette via a small, cached, software
 * fetch ([AmbientPaletteLoader.load]) — so the *displayed* poster stays a hardware bitmap (fast,
 * no allocation-limit blanks). Pass [loadAccent] = the card's focus state in big scrolling grids
 * so only the focused card (the only one that shows the glow) does palette work. White when
 * [LocalColouredFocus] is off.
 */
@Composable
fun rememberCardFocusAccent(imageUrl: String?, loadAccent: Boolean = true): CardFocusAccent {
    val coloured = LocalColouredFocus.current
    val loader = LocalAmbientPaletteLoader.current
    var accent by remember(imageUrl) {
        mutableStateOf(if (coloured) imageUrl?.let { loader.focusAccentCached(it) } else null)
    }

    LaunchedEffect(imageUrl, coloured, loadAccent) {
        if (coloured && loadAccent && imageUrl != null && accent == null) {
            accent = loader.loadFocusAccent(imageUrl)
        }
    }

    val target = if (coloured) accent?.boostForFocusChrome() else null
    val borderColor by animateColorAsState(
        targetValue = target ?: Color.White,
        animationSpec = tween(AccentFadeMs),
        label = "cardFocusBorder"
    )

    return CardFocusAccent(
        borderColor = borderColor,
        glowColor = target ?: Color.White
    )
}

private fun Color.boostForFocusChrome(): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.15f).coerceIn(0.4f, 1f)
    hsv[2] = (hsv[2] * 1.05f).coerceIn(0.55f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}
