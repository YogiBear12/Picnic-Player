package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemDto

data class MediaGridPage(
    val items: List<BaseItemDto>,
    val totalCount: Int
)
