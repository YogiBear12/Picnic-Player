package app.picnic.player.data.seerr

data class SeerrRequestDisplay(
    val request: SeerrMediaRequest,
    val title: String,
    val posterPath: String? = null,
    val yearLabel: String? = null
)

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
    val releaseDate: String? = null,
    val lastAirDate: String? = null,
    val seriesStatus: String? = null
)

internal fun SeerrMediaRequest.titleCacheKeyOrNull(): SeerrTitleCacheKey? {
    val tmdbId = media?.tmdbId ?: return null
    val type = resolvedMediaType() ?: return null
    return SeerrTitleCacheKey(type, tmdbId)
}

internal fun requestDisplaysFromCache(
    requests: List<SeerrMediaRequest>,
    cache: Map<SeerrTitleCacheKey, SeerrCachedTitle>
): List<SeerrRequestDisplay> = requests.map { req ->
    val inlineTitle = req.mediaTitleOrNull()
    val inlinePoster = req.mediaPosterOrNull()
    val cached = req.titleCacheKeyOrNull()?.let { cache[it] }
    val yearLabel = cached?.releaseDate?.take(4)?.takeIf { it.length == 4 }
    SeerrRequestDisplay(
        request = req,
        title = inlineTitle ?: cached?.title ?: req.placeholderTitle(),
        posterPath = inlinePoster ?: cached?.posterPath,
        yearLabel = yearLabel
    )
}
