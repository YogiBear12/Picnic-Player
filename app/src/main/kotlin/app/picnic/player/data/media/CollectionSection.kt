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
    OTHER("Other Items", emptyList())
}

fun sectionFor(kind: BaseItemKind?): CollectionSection = CollectionSection.entries.firstOrNull { kind in it.kinds } ?: CollectionSection.OTHER

val NON_VIDEO_KINDS: List<BaseItemKind> = listOf(
    BaseItemKind.AUDIO,
    BaseItemKind.AUDIO_BOOK,
    BaseItemKind.MUSIC_ALBUM,
    BaseItemKind.MUSIC_ARTIST,
    BaseItemKind.MUSIC_GENRE,
    BaseItemKind.BOOK,
    BaseItemKind.PHOTO,
    BaseItemKind.PHOTO_ALBUM,
    BaseItemKind.GENRE,
    BaseItemKind.STUDIO,
    BaseItemKind.PERSON,
    BaseItemKind.PLAYLIST,
    BaseItemKind.USER_VIEW,
    BaseItemKind.COLLECTION_FOLDER
)

val PLAYABLE_KINDS: List<BaseItemKind> = listOf(
    BaseItemKind.MOVIE,
    BaseItemKind.EPISODE,
    BaseItemKind.VIDEO,
    BaseItemKind.MUSIC_VIDEO
)
