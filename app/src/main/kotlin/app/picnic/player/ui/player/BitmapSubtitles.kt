@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import androidx.compose.ui.geometry.Size
import androidx.media3.common.text.Cue
import kotlin.math.abs

/**
 * Aspect-ratio band treated as a 4:3 subtitle plane. Broadcast subtitles ship a 4:3 plane over
 * widescreen video and are authored expecting to be stretched with the picture, so they follow the
 * video even though the shapes disagree.
 */
private const val SquarePlaneMinAspect = 1.22f
private const val SquarePlaneMaxAspect = 1.34f

/**
 * How far a plane's aspect ratio may drift from the video's, as a fraction of it, and still count
 * as the same shape. Cropped encodes round their height to a multiple of 8 or 16, so a plane cut to
 * match the video still misses it by a few rows. Wide enough to absorb that, well short of the ~4%
 * between neighbouring release ratios like 16:9 and 1.85:1.
 */
private const val AspectMatchTolerance = 0.02f

/**
 * The plane a bitmap cue was authored against, in pixels, or null if its geometry is unusable.
 *
 * Bitmap subtitles (PGS, DVB) are drawn onto a fixed presentation plane declared by the stream, and
 * media3 hands every cue over normalized to it: [widthFraction] is the bitmap's width as a fraction
 * of the plane, [heightFraction] its height. Dividing the bitmap's real pixel size by those
 * fractions recovers the plane, which the cue otherwise never states.
 */
fun subtitlePlaneSize(
    bitmapWidth: Int,
    bitmapHeight: Int,
    widthFraction: Float,
    heightFraction: Float
): Size? {
    if (widthFraction <= 0f || heightFraction <= 0f) return null
    val planeWidth = bitmapWidth / widthFraction
    val planeHeight = bitmapHeight / heightFraction
    if (!planeWidth.isFinite() || !planeHeight.isFinite()) return null
    if (planeWidth <= 0f || planeHeight <= 0f) return null
    return Size(planeWidth, planeHeight)
}

/**
 * Whether a subtitle plane of [planeAspect] should be mapped onto the video's display rect rather
 * than onto the screen.
 *
 * A plane shaped like the video *is* the video frame, so mapping it onto the picture puts every cue
 * exactly where the author placed it. A plane of a different shape means the video was re-encoded
 * with its black bars cropped away while the subtitles kept the original frame — squeezing that
 * plane into the picture would deform the cues and drag them up out of position, so those get
 * fitted to the screen instead and are free to sit outside the picture.
 */
fun bitmapSubtitlesFollowVideo(planeAspect: Float, videoAspect: Float): Boolean {
    if (planeAspect in SquarePlaneMinAspect..SquarePlaneMaxAspect) return true
    return abs(planeAspect - videoAspect) / videoAspect < AspectMatchTolerance
}

/**
 * The rectangle [cues] should be painted into, as a source size to inscribe in the video box with
 * `ContentScale.Fit`: either the video's own display size, or the subtitle plane fitted to the
 * screen. Returns [videoSizeDp] when no cue carries a bitmap, where the choice has nothing to draw.
 */
fun bitmapSubtitleFrame(cues: List<Cue>, videoSizeDp: Size?): Size? {
    val plane = cues.firstNotNullOfOrNull { cue ->
        cue.bitmap?.let { subtitlePlaneSize(it.width, it.height, cue.size, cue.bitmapHeight) }
    } ?: return videoSizeDp
    val videoAspect = videoSizeDp
        ?.takeIf { it.width > 0f && it.height > 0f }
        ?.let { it.width / it.height }
        ?: return plane
    return if (bitmapSubtitlesFollowVideo(plane.width / plane.height, videoAspect)) videoSizeDp else plane
}
