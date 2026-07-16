package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Combined library grid target. */
enum class MediaGridKind(val itemType: BaseItemKind, val title: String) {
    MOVIES(BaseItemKind.MOVIE, "Movies"),
    SHOWS(BaseItemKind.SERIES, "Shows")
}

data class MediaGridPage(
    val items: List<BaseItemDto>,
    val totalCount: Int
)
