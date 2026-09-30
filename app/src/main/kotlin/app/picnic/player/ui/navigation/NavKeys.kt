package app.picnic.player.ui.navigation

import androidx.navigation3.runtime.NavKey
import app.picnic.player.data.media.FolderContext
import app.picnic.player.data.media.ItemQueue
import app.picnic.player.data.media.PersonalLibrary
import kotlinx.serialization.Serializable

@Serializable
data object StartupKey : NavKey

@Serializable
data object ServerEntryKey : NavKey

@Serializable
data object LoginKey : NavKey

@Serializable
data class ServerPickerKey(
    val unreachableServerId: String? = null,
    val errorText: String? = null
) : NavKey

@Serializable
data class ProfilePickerKey(val serverId: String) : NavKey

@Serializable
data object BrowseKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data class DetailKey(
    val itemId: String,
    val bgUrl: String? = null,
    val ambUrl: String? = null,
    val instanceId: String = java.util.UUID.randomUUID().toString()
) : NavKey

@Serializable
data class PlayerKey(
    val itemId: String,
    val startTicks: Long? = null,
    val mediaSourceId: String? = null,
    val queue: ItemQueue? = null
) : NavKey

@Serializable
data class EpisodesKey(
    val seriesId: String,
    val ambUrl: String? = null,
    val focusEpisodeId: String? = null,
    val seasonId: String? = null
) : NavKey

@Serializable
data class CollectionKey(val itemId: String) : NavKey

@Serializable
data class PlaylistKey(
    val playlistId: String,
    val name: String? = null
) : NavKey

@Serializable
data class GenreKey(
    val genreId: String,
    val name: String? = null,
    val libraryId: String? = null,
    val libraryName: String? = null
) : NavKey

@Serializable
data class SeerrDetailKey(
    val tmdbId: Int,
    val mediaType: String,
    val bgUrl: String? = null,
    val ambUrl: String? = null,
    val instanceId: String = java.util.UUID.randomUUID().toString()
) : NavKey

@Serializable
data class PersonKey(
    val jellyfinPersonId: String? = null,
    val tmdbId: Int? = null
) : NavKey

@Serializable
data class FilmographyKey(
    val tmdbId: Int,
    val personName: String,
    val knownForDepartment: String? = null
) : NavKey

@Serializable
data class FolderKey(
    val library: PersonalLibrary,
    val folderId: String,
    val folderName: String,
    val instanceId: String = java.util.UUID.randomUUID().toString()
) : NavKey

@Serializable
data class PhotoKey(
    val library: PersonalLibrary,
    val photoId: String,
    val photoName: String,
    val imageTag: String?,
    val folder: FolderContext? = null
) : NavKey
