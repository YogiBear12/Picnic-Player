package app.picnic.player.data.seerr

import app.picnic.player.data.media.NavImages

/**
 * TMDB / Seerr image URLs for Coil. When [cacheImages] is true, prefer Seerr's
 * image proxy; otherwise hit TMDB directly.
 */
object SeerrImages {
    private const val TMDB = "https://image.tmdb.org/t/p"

    fun poster(
        seerrBaseUrl: String?,
        path: String?,
        cacheImages: Boolean,
        size: String = "w500"
    ): String? = image(seerrBaseUrl, path, cacheImages, size)

    fun backdrop(
        seerrBaseUrl: String?,
        path: String?,
        cacheImages: Boolean,
        size: String = "w1280"
    ): String? = image(seerrBaseUrl, path, cacheImages, size)

    fun profile(
        seerrBaseUrl: String?,
        path: String?,
        cacheImages: Boolean,
        size: String = "w185"
    ): String? = image(seerrBaseUrl, path, cacheImages, size)

    fun ambient(
        seerrBaseUrl: String?,
        path: String?,
        cacheImages: Boolean
    ): String? = image(seerrBaseUrl, path, cacheImages, "w300")

    /**
     * Hero backdrop + ambient-wash pair handed through navigation on card click.
     * The wash prefers the backdrop and falls back to the poster.
     */
    fun navImages(
        seerrBaseUrl: String?,
        item: SeerrCatalogItem,
        cacheImages: Boolean
    ): NavImages = NavImages(
        bgUrl = backdrop(seerrBaseUrl, item.backdropPath, cacheImages),
        ambUrl = ambient(seerrBaseUrl, item.backdropPath ?: item.posterPath, cacheImages)
    )

    private fun image(
        seerrBaseUrl: String?,
        path: String?,
        cacheImages: Boolean,
        size: String
    ): String? {
        if (path.isNullOrBlank()) return null
        val normalized = if (path.startsWith("/")) path else "/$path"
        if (cacheImages && !seerrBaseUrl.isNullOrBlank()) {
            val base = seerrBaseUrl.trimEnd('/')
            return "$base/imageproxy/tmdb/t/p/$size$normalized"
        }
        return "$TMDB/$size$normalized"
    }
}
