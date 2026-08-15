@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackground
import app.picnic.player.data.settings.SubtitleBackgroundFill
import app.picnic.player.data.settings.SubtitleColour
import app.picnic.player.data.settings.SubtitleSize
import kotlin.math.roundToInt

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

/**
 * Holds cues back from the floodlight-white the compositor gives an SDR overlay in HDR output.
 * Gamma-encoded, so 0.60 of the code value is ≈0.31 of the light — near the 203-nit reference white
 * of BT.2408 on a panel mapping SDR white around 500 nits. An estimate: no compositor states its
 * mapping.
 */
private const val HdrLuminanceScale = 0.60f

internal fun Int.scaleRgb(scale: Float): Int {
    fun channel(shift: Int): Int {
        val value = (this shr shift) and 0xFF
        return ((value * scale).roundToInt().coerceIn(0, 255)) shl shift
    }
    return (this and 0xFF.shl(24)) or channel(16) or channel(8) or channel(0)
}

fun SubtitleAppearance.toCaptionStyle(range: VideoDynamicRange): CaptionStyleCompat {
    val fill = if (background != SubtitleBackground.OFF) {
        val alpha = when (backgroundFill) {
            SubtitleBackgroundFill.TRANSLUCENT -> 128
            SubtitleBackgroundFill.SOLID -> 255
        }
        (alpha shl 24) or (Color.BLACK and 0x00FFFFFF)
    } else {
        Color.TRANSPARENT
    }
    val boxed = background == SubtitleBackground.BOXED
    val foreground = if (range.isHdr) colour.toArgb().scaleRgb(HdrLuminanceScale) else colour.toArgb()
    return CaptionStyleCompat(
        /* foregroundColor= */ foreground,
        /* backgroundColor= */ if (boxed) Color.TRANSPARENT else fill,
        /* windowColor= */ if (boxed) fill else Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        /* edgeColor= */ Color.BLACK,
        Typeface.DEFAULT
    )
}

fun SubtitleAppearance.applyTo(
    view: SubtitleView,
    bottomPaddingFraction: Float,
    range: VideoDynamicRange = VideoDynamicRange.SDR
) {
    view.setApplyEmbeddedStyles(false)
    view.setStyle(toCaptionStyle(range))
    view.setFractionalTextSize(size.toHeightFraction())
    view.setBottomPaddingFraction(bottomPaddingFraction)
}
