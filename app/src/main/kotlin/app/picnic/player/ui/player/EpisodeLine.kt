package app.picnic.player.ui.player

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** `S{n} E{n} • {episode title}` for TV episodes on the player OSD. */
internal fun episodeOsdLine(item: BaseItemDto): String {
    if (item.type != BaseItemKind.EPISODE) {
        return if (item.seriesName != null) item.name.orEmpty() else ""
    }
    val parts = mutableListOf<String>()
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    if (season != null && episode != null) parts += "S$season E$episode"
    item.name?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
    return parts.joinToString(" • ")
}
