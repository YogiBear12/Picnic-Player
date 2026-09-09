package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

fun BaseItemDto.withSeriesFallback(
    seriesItems: Map<UUID, BaseItemDto>,
    seasonLeads: Map<UUID, BaseItemDto>
): BaseItemDto = withSeriesFallback(seriesId?.let { seriesItems[it] }, seasonLeads[id])

fun BaseItemDto.withSeriesFallback(series: BaseItemDto?, leadEpisode: BaseItemDto? = null): BaseItemDto {
    if (type != BaseItemKind.SEASON || series == null) return this
    val ownYear = productionYear ?: leadEpisode?.premiereDate?.year ?: leadEpisode?.productionYear
    val dated = ownYear != null
    return copy(
        overview = overview?.takeIf { it.isNotBlank() } ?: series.overview,
        genres = genres?.takeIf { it.isNotEmpty() } ?: series.genres,
        communityRating = communityRating ?: series.communityRating,
        criticRating = criticRating ?: series.criticRating,
        officialRating = officialRating?.takeIf { it.isNotBlank() } ?: series.officialRating,
        productionYear = if (dated) ownYear else series.productionYear,
        premiereDate = if (dated) premiereDate else series.premiereDate,
        status = if (dated) status else series.status,
        endDate = if (dated) endDate else series.endDate
    )
}
