@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.ui.player

import androidx.media3.common.text.Cue
import app.picnic.player.data.settings.SubtitleArea
import app.picnic.player.playback.BlackBars
import app.picnic.player.playback.SubtitleLineAnchor
import app.picnic.player.playback.subtitleAnchoredLine

fun anchorTextCues(
    cues: List<Cue>,
    area: SubtitleArea,
    insetPercent: Int,
    videoRectHeightFraction: Float,
    bars: BlackBars
): List<Cue> {
    if (cues.none { it.isLinePositioned }) return cues
    return cues.map { cue ->
        if (!cue.isLinePositioned) {
            cue
        } else {
            cue.buildUpon()
                .setLine(
                    subtitleAnchoredLine(
                        anchor = cue.lineAnchor(),
                        area = area,
                        insetPercent = insetPercent,
                        videoRectHeightFraction = videoRectHeightFraction,
                        bars = bars
                    ),
                    Cue.LINE_TYPE_FRACTION
                )
                .build()
        }
    }
}

private val Cue.isLinePositioned: Boolean
    get() = line != Cue.DIMEN_UNSET && lineType == Cue.LINE_TYPE_FRACTION

private fun Cue.lineAnchor(): SubtitleLineAnchor = when (lineAnchor) {
    Cue.ANCHOR_TYPE_END -> SubtitleLineAnchor.Bottom(line)
    Cue.ANCHOR_TYPE_MIDDLE -> SubtitleLineAnchor.Middle(line)
    else -> SubtitleLineAnchor.Top(line)
}
