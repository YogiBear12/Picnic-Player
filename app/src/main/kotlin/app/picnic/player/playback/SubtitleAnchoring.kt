package app.picnic.player.playback

import app.picnic.player.data.settings.SubtitleArea

private const val MaxBottomPaddingFraction = 0.9f
private const val ParserEdgeLine = 0.08f

private data class AnchorRect(val bottom: Float, val height: Float) {
    val top: Float get() = 1f - bottom - height
}

sealed interface SubtitleLineAnchor {
    val line: Float

    data class Top(override val line: Float) : SubtitleLineAnchor

    data class Bottom(override val line: Float) : SubtitleLineAnchor

    data class Middle(override val line: Float) : SubtitleLineAnchor
}

fun videoRectHeightFraction(videoWidth: Float, videoHeight: Float, boxWidth: Float, boxHeight: Float): Float {
    if (videoWidth <= 0f || videoHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f) return 1f
    val scale = minOf(boxWidth / videoWidth, boxHeight / videoHeight)
    return (videoHeight * scale / boxHeight).coerceIn(0f, 1f)
}

fun subtitleAnchoredLine(
    anchor: SubtitleLineAnchor,
    area: SubtitleArea,
    insetPercent: Int,
    videoRectHeightFraction: Float,
    bars: BlackBars
): Float {
    val rect = anchorRectFor(area, videoRectHeightFraction, bars)
    val inset = rect.height * insetPercent / 100f
    val topLimit = rect.top + inset
    val bottomLimit = rect.top + rect.height - inset
    val anchored = when {
        anchor is SubtitleLineAnchor.Top && anchor.line <= ParserEdgeLine -> topLimit
        anchor is SubtitleLineAnchor.Bottom && anchor.line >= 1f - ParserEdgeLine -> bottomLimit
        else -> (rect.top + anchor.line.coerceIn(0f, 1f) * rect.height)
            .coerceIn(minOf(topLimit, bottomLimit), maxOf(topLimit, bottomLimit))
    }
    return anchored.coerceIn(0f, 1f)
}

fun subtitleBottomPaddingFraction(
    area: SubtitleArea,
    insetPercent: Int,
    videoRectHeightFraction: Float,
    bars: BlackBars
): Float {
    val line = subtitleAnchoredLine(
        SubtitleLineAnchor.Bottom(1f),
        area,
        insetPercent,
        videoRectHeightFraction,
        bars
    )
    return (1f - line).coerceIn(0f, MaxBottomPaddingFraction)
}

private fun anchorRectFor(
    area: SubtitleArea,
    videoRectHeightFraction: Float,
    bars: BlackBars
): AnchorRect = when (area) {
    SubtitleArea.SCREEN -> AnchorRect(bottom = 0f, height = 1f)
    SubtitleArea.IMAGE -> anchorRect(videoRectHeightFraction, BlackBars.None)
    SubtitleArea.AUTOMATIC -> anchorRect(videoRectHeightFraction, bars)
}

private fun anchorRect(videoRectHeightFraction: Float, bars: BlackBars): AnchorRect {
    val rect = videoRectHeightFraction.coerceIn(0f, 1f)
    return AnchorRect(
        bottom = (1f - rect) / 2f + bars.bottom * rect,
        height = rect * bars.pictureHeight
    )
}
