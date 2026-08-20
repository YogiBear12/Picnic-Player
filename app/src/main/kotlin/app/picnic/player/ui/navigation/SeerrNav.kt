package app.picnic.player.ui.navigation

import androidx.navigation3.runtime.NavKey
import app.picnic.player.data.seerr.DetailTarget
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.seerrDetailTarget

fun resolveSeerrNavKey(
    tmdbId: Int,
    mediaType: String,
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null,
    bgUrl: String? = null,
    ambUrl: String? = null
): NavKey = when (val target = seerrDetailTarget(jellyfinMediaId, jellyfinMediaId4k)) {
    is DetailTarget.Jellyfin -> DetailKey(itemId = target.itemId, bgUrl = bgUrl, ambUrl = ambUrl)
    DetailTarget.Seerr -> SeerrDetailKey(
        tmdbId = tmdbId,
        mediaType = mediaType.lowercase(),
        bgUrl = bgUrl,
        ambUrl = ambUrl
    )
}

fun resolveSeerrNavKey(
    item: SeerrCatalogItem,
    bgUrl: String? = null,
    ambUrl: String? = null
): NavKey = resolveSeerrNavKey(
    tmdbId = item.tmdbId,
    mediaType = item.mediaType.name.lowercase(),
    jellyfinMediaId = item.jellyfinMediaId,
    jellyfinMediaId4k = item.jellyfinMediaId4k,
    bgUrl = bgUrl,
    ambUrl = ambUrl
)

fun resolveSeerrNavKey(request: SeerrMediaRequest): NavKey? {
    val media = request.media ?: return null
    val tmdbId = media.tmdbId ?: return null
    val mediaType = request.mediaType ?: media.mediaType ?: "movie"
    return resolveSeerrNavKey(
        tmdbId = tmdbId,
        mediaType = mediaType,
        jellyfinMediaId = media.jellyfinMediaId,
        jellyfinMediaId4k = media.jellyfinMediaId4k
    )
}
