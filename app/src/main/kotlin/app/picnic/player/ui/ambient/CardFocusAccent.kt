package app.picnic.player.ui.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
fun rememberCardFocusAccent(
    imageUrl: String?,
    blurHash: String? = null,
    loadAccent: Boolean = true
): CardFocusAccent {
    val coloured = LocalColouredFocus.current
    val loader = LocalAmbientPaletteLoader.current
    // A BlurHash resolves here, synchronously, from data the item list already carries — the
    // accent is coloured on the card's first frame, so focus can never outrun it. Callers with
    // no hash (non-Jellyfin artwork) fall back to the prefetching fetch below.
    var accent by remember(imageUrl, blurHash) {
        mutableStateOf(
            when {
                !coloured -> null
                blurHash != null -> loader.focusAccentFromBlurHash(blurHash)
                else -> imageUrl?.let { loader.focusAccentCached(it) }
            }
        )
    }

    LaunchedEffect(imageUrl, coloured, loadAccent) {
        if (coloured && loadAccent && imageUrl != null && accent == null) {
            accent = loader.loadFocusAccent(imageUrl)
        }
    }

    // Already boosted and contrast-corrected by the loader; null means the artwork is greyscale
    // enough that no honest hue exists, so the chrome stays white.
    val target = if (coloured) accent else null
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
