package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

internal val BaseItemDto.seedId: UUID get() = seriesId ?: id

internal val BaseItemDto.seedName: String? get() = seriesName ?: name

internal fun watchHistoryKinds(kinds: List<BaseItemKind>): List<BaseItemKind> = kinds.map {
    if (it == BaseItemKind.SERIES) BaseItemKind.EPISODE else it
}

internal fun chooseWatchSeeds(pool: List<BaseItemDto>): List<BaseItemDto> = pool
    .shuffled()
    .distinctBy { it.seedId }
