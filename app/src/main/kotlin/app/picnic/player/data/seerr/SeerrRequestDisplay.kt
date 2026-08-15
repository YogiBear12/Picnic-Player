package app.picnic.player.data.seerr

/**
 * Settings → Your requests row model.
 * [title] is inline from the list payload when present, else hydrated via
 * `/movie/{tmdbId}` or `/tv/{tmdbId}` (cached in [SeerrRepository]).
 */
data class SeerrRequestDisplay(
    val request: SeerrMediaRequest,
    val title: String,
    val posterPath: String? = null,
    /**
     * Start year only (movie release or TV first air) from hydrated detail;
     * null until fetch completes. Request rows do not use series year ranges.
     */
    val yearLabel: String? = null
)

/** Title on the nested media ref when Seerr includes it (uncommon on list payloads). */
fun SeerrMediaRequest.mediaTitleOrNull(): String? {
    val media = media ?: return null
    return media.title?.takeIf { it.isNotBlank() }
        ?: media.name?.takeIf { it.isNotBlank() }
}

fun SeerrMediaRequest.mediaPosterOrNull(): String? = media?.posterPath?.takeIf { it.isNotBlank() }

fun SeerrMediaRequest.placeholderTitle(): String = "Request #$id"

fun SeerrMediaRequest.resolvedMediaType(): SeerrMediaType? {
    val raw = (mediaType ?: media?.mediaType)?.lowercase()
    return when (raw) {
        "movie" -> SeerrMediaType.MOVIE
        "tv" -> SeerrMediaType.TV
        else -> null
    }
}

internal data class SeerrTitleCacheKey(
    val mediaType: SeerrMediaType,
    val tmdbId: Int
)

internal data class SeerrCachedTitle(
    val title: String,
    val posterPath: String?,
    /** Movie release date or TV first air date (`YYYY-MM-DD`). */
    val releaseDate: String? = null,
    val lastAirDate: String? = null,
    val seriesStatus: String? = null
)

internal fun SeerrMediaRequest.titleCacheKeyOrNull(): SeerrTitleCacheKey? {
    val tmdbId = media?.tmdbId ?: return null
    val type = resolvedMediaType() ?: return null
    return SeerrTitleCacheKey(type, tmdbId)
}

/**
 * Build display rows from inline media fields and [cache] only — no network.
 * Missing titles use [placeholderTitle] until [SeerrRepository.hydrateRequestDisplays].
 */
internal fun requestDisplaysFromCache(
    requests: List<SeerrMediaRequest>,
    cache: Map<SeerrTitleCacheKey, SeerrCachedTitle>
): List<SeerrRequestDisplay> = requests.map { req ->
    val inlineTitle = req.mediaTitleOrNull()
    val inlinePoster = req.mediaPosterOrNull()
    val cached = req.titleCacheKeyOrNull()?.let { cache[it] }
    // Start year only — same shape for movie and TV; hero still uses ranges.
    val yearLabel = cached?.releaseDate?.take(4)?.takeIf { it.length == 4 }
    SeerrRequestDisplay(
        request = req,
        title = inlineTitle ?: cached?.title ?: req.placeholderTitle(),
        posterPath = inlinePoster ?: cached?.posterPath,
        yearLabel = yearLabel
    )
}
