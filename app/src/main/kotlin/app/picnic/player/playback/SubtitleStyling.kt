@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackgroundFill
import app.picnic.player.data.settings.SubtitleBackgroundStyle
import app.picnic.player.data.settings.SubtitleColour
import app.picnic.player.data.settings.SubtitleSize
import kotlin.math.roundToInt

/**
 * Maps [SubtitleAppearance] onto a Media3 [SubtitleView] for text-based cues
 * (SRT/VTT/…). ASS/SSA cues render through libass and keep their authored styling.
 *
 * Both the playback engine and the appearance page's live preview apply style
 * through [applyTo], so the preview is rendered by the exact code path used
 * during playback.
 */

/** Cue height as a fraction of the picture height. */
fun SubtitleSize.toHeightFraction(): Float = when (this) {
    SubtitleSize.SMALLER -> 0.0258f
    SubtitleSize.SMALL -> 0.0331f
    SubtitleSize.STANDARD -> 0.0405f
    SubtitleSize.LARGE -> 0.0478f
    SubtitleSize.LARGER -> 0.0552f
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
 * Fraction of each colour channel kept when cues sit over HDR video.
 *
 * Cues are drawn into an SDR overlay that the compositor lifts into the HDR output, and it puts
 * SDR white far above the picture's own diffuse white — so a cue that reads as white over SDR
 * reads as a floodlight over HDR. Scaling the channels holds it back down.
 *
 * The channels are gamma-encoded, so 0.60 of the code value is 0.60^2.2 ≈ 0.31 of the light.
 * Against a panel mapping SDR white near 500 nits that lands the cue just under the 203-nit
 * reference white of BT.2408 — the level graphics are authored to sit at. Only ever an estimate:
 * the compositor never states what it maps SDR white to, and each panel picks its own.
 */
private const val HdrLuminanceScale = 0.60f

/**
 * Scales the colour channels by [scale], leaving alpha alone.
 *
 * All three channels move together, so the hue and saturation survive untouched and only the
 * brightness drops — a yellow cue stays exactly as yellow, just dimmer.
 */
internal fun Int.scaleRgb(scale: Float): Int {
    fun channel(shift: Int): Int {
        val value = (this shr shift) and 0xFF
        return ((value * scale).roundToInt().coerceIn(0, 255)) shl shift
    }
    return (this and 0xFF.shl(24)) or channel(16) or channel(8) or channel(0)
}

/**
 * Only the text colour answers to [range] — the outline and the fill are black either way, which
 * emits no light to hold back and keeps the cue's edge contrast intact as the text dims.
 *
 * The same black fill goes to a different Media3 slot depending on style:
 * BOXED uses windowColor (one rectangle behind the whole cue block), WRAPPED uses
 * backgroundColor (fill hugs each line of text). The black outline is kept even
 * with the fill on — it is invisible against black and keeps edge handling in one
 * state.
 */
fun SubtitleAppearance.toCaptionStyle(range: SubtitleRenderRange): CaptionStyleCompat {
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
    val foreground = when (range) {
        SubtitleRenderRange.SDR -> colour.toArgb()
        SubtitleRenderRange.HDR -> colour.toArgb().scaleRgb(HdrLuminanceScale)
    }
    return CaptionStyleCompat(
        /* foregroundColor= */ foreground,
        /* backgroundColor= */ if (boxed) Color.TRANSPARENT else fill,
        /* windowColor= */ if (boxed) fill else Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        /* edgeColor= */ Color.BLACK,
        Typeface.DEFAULT
    )
}

/**
 * Cancels letterboxing, so a size step renders the same height whatever the content's aspect.
 * 1 when the video fills the container's height (pillarboxed or matching).
 */
fun subtitleTextSizeScale(videoAspect: Float?, containerAspect: Float): Float {
    if (videoAspect == null || videoAspect <= 0f || containerAspect <= 0f) return 1f
    return (videoAspect / containerAspect).coerceAtLeast(1f)
}

fun SubtitleAppearance.applyTo(
    view: SubtitleView,
    textSizeScale: Float = 1f,
    range: SubtitleRenderRange = SubtitleRenderRange.SDR
) {
    view.setApplyEmbeddedStyles(false)
    view.setStyle(toCaptionStyle(range))
    view.setFractionalTextSize(size.toHeightFraction() * textSizeScale)
    view.setBottomPaddingFraction(SubtitleBottomPaddingFraction)
}
