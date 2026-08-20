package app.picnic.player.ui.detail

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

internal data class DetailPlayTarget(
    val itemId: String,
    val resumeTicks: Long?,
    val title: String
)

internal fun detailPlayTarget(item: BaseItemDto, nextUpEpisode: BaseItemDto?): DetailPlayTarget {
    val episode = nextUpEpisode?.takeIf { item.type == BaseItemKind.SERIES }
    val source = episode ?: item
    val resumeTicks = source.userData?.playbackPositionTicks?.takeIf { it > 0 }
    val verb = if (resumeTicks != null) "Resume" else "Play"
    return DetailPlayTarget(
        itemId = source.id.toString(),
        resumeTicks = resumeTicks,
        title = if (episode == null) verb else "$verb ${episodeLabel(episode)}"
    )
}

private fun episodeLabel(episode: BaseItemDto): String {
    val season = episode.parentIndexNumber
    return if (season == 0) "S0 E${episode.indexNumber}" else "S$season E${episode.indexNumber}"
}
