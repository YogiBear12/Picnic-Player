package app.picnic.player.data.seerr

fun formatSeerrYearLabel(
    mediaType: SeerrMediaType,
    releaseDate: String?,
    lastAirDate: String? = null,
    seriesStatus: String? = null
): String? = when (mediaType) {
    SeerrMediaType.MOVIE -> releaseDate?.take(4)?.takeIf { it.length == 4 }
    SeerrMediaType.TV ->
        formatSeerrSeriesYears(releaseDate, lastAirDate, seriesStatus).takeIf { it.isNotEmpty() }
}

fun formatSeerrSeriesYears(
    firstAirDate: String?,
    lastAirDate: String?,
    seriesStatus: String?
): String {
    val start = firstAirDate?.take(4)?.toIntOrNull() ?: return ""
    val status = seriesStatus.orEmpty()
    if (status.equals("Returning Series", ignoreCase = true) ||
        status.equals("In Production", ignoreCase = true) ||
        status.equals("Planned", ignoreCase = true) ||
        status.equals("Pilot", ignoreCase = true)
    ) {
        return "$start - Present"
    }
    val end = lastAirDate?.take(4)?.toIntOrNull()
    if (status.equals("Ended", ignoreCase = true) ||
        status.equals("Cancelled", ignoreCase = true) ||
        end != null
    ) {
        if (end == null || end == start) return start.toString()
        return "$start - $end"
    }
    return start.toString()
}

internal fun SeerrCachedTitle.hasYearMeta(mediaType: SeerrMediaType): Boolean = formatSeerrYearLabel(mediaType, releaseDate, lastAirDate, seriesStatus) != null

fun formatSeerrSeasonCountLabel(seasonCount: Int?): String? {
    if (seasonCount == null || seasonCount <= 0) return null
    return if (seasonCount == 1) "1 season" else "$seasonCount seasons"
}

fun formatRequestedSeasonsLabel(seasonNumbers: List<Int>): String? {
    if (seasonNumbers.isEmpty()) return null
    val hasSpecials = seasonNumbers.any { it <= 0 }
    val numbered = seasonNumbers.filter { it > 0 }.distinct().sorted()
    val parts = buildList {
        if (hasSpecials) add("Specials")
        if (numbered.isNotEmpty()) {
            val noun = if (numbered.size == 1) "Season" else "Seasons"
            add("$noun ${formatSeasonNumberRanges(numbered)}")
        }
    }
    return parts.joinToString(", ").takeIf { it.isNotEmpty() }
}

internal fun formatSeasonNumberRanges(sortedDistinct: List<Int>): String {
    if (sortedDistinct.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var rangeStart = sortedDistinct.first()
    var rangeEnd = rangeStart
    for (season in sortedDistinct.drop(1)) {
        if (season == rangeEnd + 1) {
            rangeEnd = season
        } else {
            ranges += formatSeasonRange(rangeStart, rangeEnd)
            rangeStart = season
            rangeEnd = season
        }
    }
    ranges += formatSeasonRange(rangeStart, rangeEnd)
    return ranges.joinToString(", ")
}

private fun formatSeasonRange(start: Int, end: Int): String = if (start == end) start.toString() else "$start\u2013$end"
