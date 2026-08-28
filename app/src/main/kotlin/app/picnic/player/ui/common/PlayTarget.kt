package app.picnic.player.ui.common

import app.picnic.player.data.playback.ticksToMs
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

const val PlayFromStartLabel = "Play from start"

fun resumeLabel(resumeTicks: Long): String = "Resume from ${formatClock(resumeTicks.ticksToMs())}"

data class PlayTarget(
    val itemId: String,
    val resumeTicks: Long?,
    val title: String
)

fun playTarget(item: BaseItemDto, nextUpEpisode: BaseItemDto? = null): PlayTarget {
    val episode = nextUpEpisode?.takeIf { item.type == BaseItemKind.SERIES }
    val source = episode ?: item
    val resumeTicks = source.resumeTicks()
    val verb = if (resumeTicks != null) "Resume" else "Play"
    return PlayTarget(
        itemId = source.id.toString(),
        resumeTicks = resumeTicks,
        title = when {
            episode != null -> "$verb ${episodeLabel(episode)}"
            resumeTicks != null -> resumeLabel(resumeTicks)
            else -> verb
        }
    )
}

private fun episodeLabel(episode: BaseItemDto): String {
    val season = episode.parentIndexNumber
    return if (season == 0) "S0 E${episode.indexNumber}" else "S$season E${episode.indexNumber}"
}
