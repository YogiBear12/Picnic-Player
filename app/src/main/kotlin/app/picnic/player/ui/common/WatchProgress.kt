package app.picnic.player.ui.common

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

val PlayableKinds = setOf(
    BaseItemKind.MOVIE,
    BaseItemKind.EPISODE,
    BaseItemKind.VIDEO,
    BaseItemKind.MUSIC_VIDEO
)

/** Resume position as 0..1. Containers aggregate episodes watched into `playedPercentage`,
 *  which is not a resume position, so they report none. */
fun BaseItemDto.watchProgress(): Float = if (type in PlayableKinds) {
    ((userData?.playedPercentage ?: 0.0) / 100.0).toFloat()
} else {
    0f
}
