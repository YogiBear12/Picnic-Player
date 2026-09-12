package app.picnic.player.data.seerr

import app.picnic.player.text.countLabel
import java.time.LocalDate

fun SeerrMediaInfo?.hasLibraryLink(): Boolean {
    if (this == null) return false
    return !jellyfinMediaId.isNullOrBlank() || !jellyfinMediaId4k.isNullOrBlank()
}

fun dedupePersonCredits(credits: List<SeerrPersonCredit>): List<SeerrPersonCredit> {
    val seen = LinkedHashSet<Pair<SeerrMediaType, Int>>()
    val out = ArrayList<SeerrPersonCredit>(credits.size)
    for (credit in credits) {
        if (seen.add(credit.mediaType to credit.tmdbId)) out += credit
    }
    return out
}

fun mixPersonCredits(
    cast: List<SeerrPersonCreditCast>,
    crew: List<SeerrPersonCreditCrew>
): List<SeerrPersonCredit> {
    val mixed = ArrayList<SeerrPersonCredit>(cast.size + crew.size)
    cast.mapNotNullTo(mixed) { it.toPersonCredit() }
    crew.mapNotNullTo(mixed) { it.toPersonCredit() }
    return dedupePersonCredits(mixed)
}

fun libraryLinkedPersonCredits(credits: List<SeerrPersonCredit>): List<SeerrPersonCredit> = credits.filter { it.mediaInfo.hasLibraryLink() }

fun personCreditRoleLine(creditRole: String?): String? = creditRole?.takeIf { it.isNotBlank() }

fun personCreditDetailLine(
    mediaType: SeerrMediaType,
    releaseDate: String?,
    episodeCount: Int?
): String? = when (mediaType) {
    SeerrMediaType.MOVIE -> releaseDate?.take(4)?.takeIf { it.length == 4 }
    SeerrMediaType.TV -> episodeCount?.takeIf { it > 0 }?.let { count ->
        countLabel(count, "episode")
    }
}

fun parseReleaseDate(raw: String?): LocalDate? {
    val slice = raw?.take(10)?.takeIf { it.length == 10 } ?: return null
    return runCatching { LocalDate.parse(slice) }.getOrNull()
}

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

fun <T> List<T>.sortedByReleaseDateDesc(dateOf: (T) -> String?): List<T> = sortedWith(releaseDateDescComparator(dateOf))

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
    jellyfinMediaId = mediaInfo?.jellyfinMediaId?.takeIf { it.isNotBlank() },
    jellyfinMediaId4k = mediaInfo?.jellyfinMediaId4k?.takeIf { it.isNotBlank() },
    creditRole = creditRole,
    episodeCount = episodeCount
)

internal fun SeerrPersonCreditCast.toPersonCredit(): SeerrPersonCredit? {
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

internal fun SeerrPersonCreditCrew.toPersonCredit(): SeerrPersonCredit? {
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
