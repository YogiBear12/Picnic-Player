package app.picnic.player.data.seerr

/**
 * Year label for Settings request rows and Seerr hero metadata.
 * Movies use release year only; TV uses [formatSeerrSeriesYears] (Seerr/TMDB status rules).
 */
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

/**
 * Same rules as [app.picnic.player.ui.browse.formatSeriesYears] for Seerr/TMDB TV status strings.
 * [firstAirDate] is typically `firstAirDate` from TV detail or [SeerrCatalogItem.releaseDate].
 */
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

/** `1 season` / `N seasons`; null when count unknown or zero. */
fun formatSeerrSeasonCountLabel(seasonCount: Int?): String? {
    if (seasonCount == null || seasonCount <= 0) return null
    return if (seasonCount == 1) "1 season" else "$seasonCount seasons"
}

/**
 * Requested-season clause for Settings request rows (#58).
 * Positive seasons collapse to compact ranges (`Seasons 1–5, 7–8`); a single
 * numbered season uses singular `Season 3`. Season `<= 0` → `Specials`.
 * When both are present, **Specials leads**: `Specials, Seasons 1–2`.
 */
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

/** Contiguous positive season numbers as `1`, `3–5`, or `1–5, 7–8` (en-dash U+2013). */
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
