@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.Dimension
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackgroundFill
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
    SubtitleSize.SMALL -> 22f
    SubtitleSize.STANDARD -> 24f
    SubtitleSize.LARGE -> 26f
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
 * The same black fill goes to a different Media3 slot depending on style:
 * BOXED uses windowColor (one rectangle behind the whole cue block), WRAPPED uses
 * backgroundColor (fill hugs each line of text). The black outline is kept even
 * with the fill on — it is invisible against black and keeps edge handling in one
 * state.
 */
fun SubtitleAppearance.toCaptionStyle(): CaptionStyleCompat {
    val fill = if (background) {
        val alpha = when (backgroundFill) {
            SubtitleBackgroundFill.TRANSLUCENT -> 128
            SubtitleBackgroundFill.SOLID -> 255
        }
        (alpha shl 24) or (Color.BLACK and 0x00FFFFFF)
    } else {
        Color.TRANSPARENT
    }
    val boxed = backgroundStyle == SubtitleBackgroundStyle.BOXED
    return CaptionStyleCompat(
        /* foregroundColor= */ colour.toArgb(),
        /* backgroundColor= */ if (boxed) Color.TRANSPARENT else fill,
        /* windowColor= */ if (boxed) fill else Color.TRANSPARENT,
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
