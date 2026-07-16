package app.picnic.player.ui.navigation

import androidx.navigation3.runtime.NavKey
import app.picnic.player.data.seerr.DetailTarget
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrMediaRequest
import app.picnic.player.data.seerr.seerrDetailTarget

/**
 * Resolves Discover / Search / Settings / Play-bridge destinations for a Seerr
 * title (#46). Shared so entry points cannot diverge on id-presence routing —
 * the single [seerrDetailTarget] gate decides Jellyfin vs Seerr Detail.
 *
 * [DetailKey.itemId] is the library id the gate chose (SD/HD preferred, else 4K
 * so a 4K-only copy still opens Jellyfin Detail). Without a valid id, Seerr
 * Detail stays the fallback.
 */
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

/**
 * Settings → Your requests. Uses the request media ref's Jellyfin id when present;
 * otherwise opens Seerr Detail.
 */
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
