package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemKind

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
    BaseItemKind.USER_VIEW,
    BaseItemKind.COLLECTION_FOLDER
)

val PLAYABLE_KINDS: List<BaseItemKind> = listOf(
    BaseItemKind.MOVIE,
    BaseItemKind.EPISODE,
    BaseItemKind.VIDEO,
    BaseItemKind.MUSIC_VIDEO
)
