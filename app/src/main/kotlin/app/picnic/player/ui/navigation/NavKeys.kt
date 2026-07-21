package app.picnic.player.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination keys. Keys are plain serializable data —
 * no string routes, no URL encoding. The back stack itself lives in [PicnicNavHost] as
 * snapshot state; screens receive typed keys.
 *
 * Onboarding is a wizard — server entry → login → browse — plus the return-user pickers.
 * The pending server for login is held in
 * [app.picnic.player.ui.onboarding.OnboardingSession], not in the key.
 */
@Serializable
data object StartupKey : NavKey

@Serializable
data object ServerEntryKey : NavKey

@Serializable
data object LoginKey : NavKey

@Serializable
data object ServerPickerKey : NavKey

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
    val mediaSourceId: String? = null
) : NavKey

@Serializable
data class EpisodesKey(
    val seriesId: String,
    val ambUrl: String? = null,
    val focusEpisodeId: String? = null,
    val seasonId: String? = null
) : NavKey

/** One collection (box set): a grid of its children. */
@Serializable
data class CollectionKey(
    val itemId: String,
    val name: String? = null
) : NavKey

/** One playlist: its ordered items on the playlist detail screen. */
@Serializable
data class PlaylistKey(
    val playlistId: String,
    val name: String? = null
) : NavKey

/**
 * Genre grid — across all video libraries by default (search tab), or scoped to one
 * library when opened from that library's Genres tab.
 */
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

/**
 * Hybrid Person (#62). [tmdbId] joins Seerr person APIs; [jellyfinPersonId] is
 * optional when opened from Jellyfin Cast. Legacy library-only Person uses UUID
 * without TMDB (no Missing row). Seerr Cast entry uses TMDB only.
 */
@Serializable
data class PersonKey(
    val jellyfinPersonId: String? = null,
    val tmdbId: Int? = null
) : NavKey
