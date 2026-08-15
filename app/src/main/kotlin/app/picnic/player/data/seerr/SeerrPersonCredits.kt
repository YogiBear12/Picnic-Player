package app.picnic.player.data.seerr

import java.time.LocalDate

/**
 * Hybrid Person credit helpers — mix/dedupe, library-link filter, release-date sort.
 */

/** True when Seerr links the credit to any Jellyfin library id (SD/HD or 4K). */
fun SeerrMediaInfo?.hasLibraryLink(): Boolean {
    if (this == null) return false
    return !jellyfinMediaId.isNullOrBlank() || !jellyfinMediaId4k.isNullOrBlank()
}

/** Cast + crew mixed; first wins on `(mediaType, tmdbId)`. */
fun dedupePersonCredits(credits: List<SeerrPersonCredit>): List<SeerrPersonCredit> {
    val seen = LinkedHashSet<Pair<SeerrMediaType, Int>>()
    val out = ArrayList<SeerrPersonCredit>(credits.size)
    for (credit in credits) {
        if (seen.add(credit.mediaType to credit.tmdbId)) out += credit
    }
    return out
}

/**
 * Mix cast then crew and dedupe. Skips credits without a resolvable media type.
 */
fun mixPersonCredits(
    cast: List<SeerrPersonCreditCast>,
    crew: List<SeerrPersonCreditCrew>
): List<SeerrPersonCredit> {
    val mixed = ArrayList<SeerrPersonCredit>(cast.size + crew.size)
    cast.mapNotNullTo(mixed) { it.toPersonCredit() }
    crew.mapNotNullTo(mixed) { it.toPersonCredit() }
    return dedupePersonCredits(mixed)
}

/** Seerr-only library row: credits with a Jellyfin media id (SD/HD or 4K). */
fun libraryLinkedPersonCredits(credits: List<SeerrPersonCredit>): List<SeerrPersonCredit> = credits.filter { it.mediaInfo.hasLibraryLink() }

/** Role line for Known-for metaline (cast character or crew job). */
fun personCreditRoleLine(creditRole: String?): String? = creditRole?.takeIf { it.isNotBlank() }

/** Year (movie) or episode count (TV) for Known-for metaline second line. */
fun personCreditDetailLine(
    mediaType: SeerrMediaType,
    releaseDate: String?,
    episodeCount: Int?
): String? = when (mediaType) {
    SeerrMediaType.MOVIE -> releaseDate?.take(4)?.takeIf { it.length == 4 }
    SeerrMediaType.TV -> episodeCount?.takeIf { it > 0 }?.let { count ->
        if (count == 1) "1 episode" else "$count episodes"
    }
}

/**
 * Parses a Seerr/TMDB date string (`yyyy-MM-dd` or longer). Null/blank/unparseable → null.
 */
fun parseReleaseDate(raw: String?): LocalDate? {
    val slice = raw?.take(10)?.takeIf { it.length == 10 } ?: return null
    return runCatching { LocalDate.parse(slice) }.getOrNull()
}

/** Newest → oldest; null/unparseable dates last. */
fun <T> releaseDateDescComparator(dateOf: (T) -> String?): Comparator<T> = Comparator { a, b ->
    val da = parseReleaseDate(dateOf(a))
    val db = parseReleaseDate(dateOf(b))
    when {
        da == null && db == null -> 0
        da == null -> 1
        db == null -> -1
        else -> db.compareTo(da)
    }
}

/** Newest → oldest by [dateOf]; null/unparseable dates last. */
fun <T> List<T>.sortedByReleaseDateDesc(dateOf: (T) -> String?): List<T> = sortedWith(releaseDateDescComparator(dateOf))

/** Coalesces SD/HD then 4K Jellyfin ids for Hybrid nav; not for 4K request UI. */
fun SeerrPersonCredit.toCatalogItem(): SeerrCatalogItem = SeerrCatalogItem(
    tmdbId = tmdbId,
    mediaType = mediaType,
    title = title,
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    mediaStatus = mediaInfo?.status,
    // Carry both ids; the Hybrid Detail gate ([seerrDetailTarget]) prefers HD,
    // else 4K. (Previously coalesced here — the gate owns that fallback now.)
    jellyfinMediaId = mediaInfo?.jellyfinMediaId?.takeIf { it.isNotBlank() },
    jellyfinMediaId4k = mediaInfo?.jellyfinMediaId4k?.takeIf { it.isNotBlank() },
    creditRole = creditRole,
    episodeCount = episodeCount
)

private fun SeerrPersonCreditCast.toPersonCredit(): SeerrPersonCredit? {
    val type = mediaType.toPersonCreditMediaType() ?: return null
    return SeerrPersonCredit(
        tmdbId = id,
        mediaType = type,
        title = displayTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        voteAverage = voteAverage,
        mediaInfo = mediaInfo,
        creditRole = character?.takeIf { it.isNotBlank() },
        episodeCount = episodeCount
    )
}

private fun SeerrPersonCreditCrew.toPersonCredit(): SeerrPersonCredit? {
    val type = mediaType.toPersonCreditMediaType() ?: return null
    return SeerrPersonCredit(
        tmdbId = id,
        mediaType = type,
        title = displayTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        voteAverage = voteAverage,
        mediaInfo = mediaInfo,
        creditRole = job?.takeIf { it.isNotBlank() },
        episodeCount = episodeCount
    )
}

private fun String?.toPersonCreditMediaType(): SeerrMediaType? = when (this?.lowercase()) {
    "movie" -> SeerrMediaType.MOVIE
    "tv" -> SeerrMediaType.TV
    else -> null
}
