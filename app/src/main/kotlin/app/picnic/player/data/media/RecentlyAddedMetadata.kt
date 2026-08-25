package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Metadata "detail" slot for a "Recently added" item — the value that replaces
 * runtime.
 */
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
    BaseItemKind.SEASON -> item.indexNumber?.let { "Season $it" } ?: runtime
    else -> runtime
}
