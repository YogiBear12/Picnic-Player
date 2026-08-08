package app.picnic.player.playback

import app.picnic.player.data.settings.SubtitleArea

private const val MaxBottomPaddingFraction = 0.9f

private data class AnchorRect(val bottom: Float, val height: Float)

fun videoRectHeightFraction(videoWidth: Float, videoHeight: Float, boxWidth: Float, boxHeight: Float): Float {
    if (videoWidth <= 0f || videoHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f) return 1f
    val scale = minOf(boxWidth / videoWidth, boxHeight / videoHeight)
    return (videoHeight * scale / boxHeight).coerceIn(0f, 1f)
}

/** Where [area]'s bottom edge falls in a container-height fraction, plus the inset above it. */
fun subtitleBottomPaddingFraction(
    area: SubtitleArea,
    insetPercent: Int,
    videoRectHeightFraction: Float,
    bars: BlackBars
): Float {
    val rect = when (area) {
        SubtitleArea.SCREEN -> AnchorRect(bottom = 0f, height = 1f)
        SubtitleArea.IMAGE -> anchorRect(videoRectHeightFraction, BlackBars.None)
        SubtitleArea.AUTOMATIC -> anchorRect(videoRectHeightFraction, bars)
    }
    return (rect.bottom + rect.height * insetPercent / 100f).coerceIn(0f, MaxBottomPaddingFraction)
}

private fun anchorRect(videoRectHeightFraction: Float, bars: BlackBars): AnchorRect {
    val rect = videoRectHeightFraction.coerceIn(0f, 1f)
    return AnchorRect(
        bottom = (1f - rect) / 2f + bars.bottom * rect,
        height = rect * bars.pictureHeight
    )
}
