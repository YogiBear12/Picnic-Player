package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemDto

internal val EpisodeOrder = compareBy(nullsLast<Int>()) { item: BaseItemDto -> item.parentIndexNumber }
    .thenBy(nullsLast()) { item: BaseItemDto -> item.indexNumber }

internal fun BaseItemDto.isRegularEpisode(): Boolean = (parentIndexNumber ?: 1) > 0

fun preferResumeEpisode(nextUp: BaseItemDto?, resumable: List<BaseItemDto>): BaseItemDto? = resumable
    .filter { (it.userData?.playbackPositionTicks ?: 0L) > 0L }
    .minWithOrNull(EpisodeOrder)
    ?: nextUp

internal fun firstEpisodeToPlay(episodes: List<BaseItemDto>): BaseItemDto? = episodes.filter { it.isRegularEpisode() }.minWithOrNull(EpisodeOrder)
    ?: episodes.minWithOrNull(EpisodeOrder)
