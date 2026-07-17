@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.Dimension
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackgroundStyle
import app.picnic.player.data.settings.SubtitleColour
import app.picnic.player.data.settings.SubtitleSize

/**
 * Maps [SubtitleAppearance] onto a Media3 [SubtitleView] for text-based cues
 * (SRT/VTT/…). ASS/SSA cues render through libass and keep their authored styling.
 *
 * Both the playback engine and the appearance page's live preview apply style
 * through [applyTo], so the preview is rendered by the exact code path used
 * during playback.
 */

/** Cue text size in sp. STANDARD matches the pre-#54 fixed default. */
fun SubtitleSize.toSp(): Float = when (this) {
    SubtitleSize.SMALLER -> 18f
    SubtitleSize.STANDARD -> 24f
    SubtitleSize.LARGER -> 30f
}

fun SubtitleColour.toArgb(): Int = when (this) {
    SubtitleColour.WHITE -> Color.WHITE
    SubtitleColour.YELLOW -> Color.YELLOW
    SubtitleColour.CYAN -> Color.CYAN
    SubtitleColour.GREEN -> Color.GREEN
}

/** Bottom margin as a fraction of the view height (8%). */
const val SubtitleBottomPaddingFraction = 0.08f

/**
 * The background box is the caption window (one rectangle behind the whole cue
 * block), not a per-line wrap. The black outline is kept even with the box on —
 * it is invisible against black and keeps edge handling in one state.
 */
fun SubtitleAppearance.toCaptionStyle(): CaptionStyleCompat {
    val window = if (background) {
        val alpha = when (backgroundStyle) {
            SubtitleBackgroundStyle.TRANSLUCENT -> 128
            SubtitleBackgroundStyle.SOLID -> 255
        }
        (alpha shl 24) or (Color.BLACK and 0x00FFFFFF)
    } else {
        Color.TRANSPARENT
    }
    return CaptionStyleCompat(
        /* foregroundColor= */ colour.toArgb(),
        /* backgroundColor= */ Color.TRANSPARENT,
        /* windowColor= */ window,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        /* edgeColor= */ Color.BLACK,
        Typeface.DEFAULT
    )
}

fun SubtitleAppearance.applyTo(view: SubtitleView) {
    view.setApplyEmbeddedStyles(false)
    view.setStyle(toCaptionStyle())
    view.setFixedTextSize(Dimension.SP, size.toSp())
    view.setBottomPaddingFraction(SubtitleBottomPaddingFraction)
}
