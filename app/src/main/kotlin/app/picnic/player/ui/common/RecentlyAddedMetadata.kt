package app.picnic.player.ui.common

import app.picnic.player.text.countLabel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

fun recentlyAddedDetail(
    item: BaseItemDto,
    seasonCount: Int?,
    runtime: String
): String = when (item.type) {
    BaseItemKind.SERIES -> {
        if (seasonCount != null && seasonCount > 0) {
            countLabel(seasonCount, "season")
        } else {
            val episodes = item.childCount
            when {
                episodes != null && episodes > 0 -> countLabel(episodes, "episode")
                else -> runtime
            }
        }
    }
    BaseItemKind.SEASON -> when {
        item.indexNumber != null && item.indexNumber != 0 -> "Season ${item.indexNumber}"
        !item.name.isNullOrBlank() -> item.name.orEmpty()
        else -> runtime
    }
    BaseItemKind.BOX_SET -> {
        val count = item.childCount ?: 0
        if (count > 0) countLabel(count, "item") else runtime
    }
    else -> runtime
}
