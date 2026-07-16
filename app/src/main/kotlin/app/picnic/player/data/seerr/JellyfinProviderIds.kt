package app.picnic.player.data.seerr

/**
 * Extracts a positive TMDB id from Jellyfin provider ids.
 *
 * Jellyfin provider keys are user/server supplied strings and commonly arrive as
 * `Tmdb`, `tmdb`, or equivalent casing. Picnic only trusts an explicit TMDB id;
 * Hybrid Details does not fuzzy-match Jellyfin titles to Seerr.
 */
fun tmdbIdFromProviderIds(providerIds: Map<String, String?>?): Int? {
    val raw = providerIds
        .orEmpty()
        .entries
        .firstOrNull { it.key.equals("tmdb", ignoreCase = true) }
        ?.value
        ?.trim()
        ?: return null
    return raw.toIntOrNull()?.takeIf { it > 0 }
}
