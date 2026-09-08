package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemKind

enum class CollectionSection(
    val title: String,
    val kinds: List<BaseItemKind>,
    val landscape: Boolean = false
) {
    MOVIES("Movies", listOf(BaseItemKind.MOVIE)),
    SHOWS("Shows", listOf(BaseItemKind.SERIES)),
    SEASONS("Seasons", listOf(BaseItemKind.SEASON)),
    EPISODES("Episodes", listOf(BaseItemKind.EPISODE), landscape = true),
    VIDEOS("Videos", listOf(BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO)),
    COLLECTIONS("Collections", listOf(BaseItemKind.BOX_SET)),
    PLAYLISTS("Playlists", listOf(BaseItemKind.PLAYLIST)),
    OTHER("Other Items", emptyList())
}

fun sectionFor(kind: BaseItemKind?): CollectionSection = CollectionSection.entries.firstOrNull { kind in it.kinds } ?: CollectionSection.OTHER
