package app.picnic.player.data.seerr

/**
 * Per-season requestability for the TV season picker.
 *
 * Mirrors Seerr's TvRequestModal: seasons already in library (available /
 * partially available / processing) or on an active non-4k request are not
 * selectable. Does not invent availability when the API omits season status.
 *
 * Library-blocked rows show a trailing badge from [seasonLibraryBadgeLabel] on
 * [SeerrSeasonPickItem.mediaStatus] (Available / Partially available / Processing).
 */
enum class SeerrSeasonAvailability {
    Selectable,

    /** Blocked by library / download state — see [SeerrSeasonPickItem.mediaStatus]. */
    Available,
    Requested
}

data class SeerrSeasonPickItem(
    val seasonNumber: Int,
    val availability: SeerrSeasonAvailability,
    /** Raw [SeerrMediaStatus] when [availability] is [SeerrSeasonAvailability.Available]. */
    val mediaStatus: Int? = null
) {
    val selectable: Boolean get() = availability == SeerrSeasonAvailability.Selectable
}

/**
 * Trailing badge copy for library-blocked season rows.
 * Returns null when [mediaStatus] is not a picker-surfaced library state.
 */
fun seasonLibraryBadgeLabel(mediaStatus: Int?): String? = when (mediaStatus) {
    SeerrMediaStatus.AVAILABLE,
    SeerrMediaStatus.PARTIALLY_AVAILABLE,
    SeerrMediaStatus.PROCESSING
    -> SeerrMediaStatus.label(mediaStatus)
    else -> null
}

/**
 * Builds picker rows from TMDB season list + [SeerrMediaInfo] season/request state.
 * Specials (`seasonNumber <= 0`) are omitted. Non-4k only (4K UI out of scope).
 */
fun buildSeasonPickItems(
    seasons: List<SeerrTvSeason>,
    mediaInfo: SeerrMediaInfo?,
    isActiveRequest: (SeerrMediaRequest) -> Boolean
): List<SeerrSeasonPickItem> {
    val libraryStatusBySeason = mediaInfo?.seasons.orEmpty()
        .asSequence()
        .filter { season ->
            when (season.status) {
                SeerrMediaStatus.AVAILABLE,
                SeerrMediaStatus.PARTIALLY_AVAILABLE,
                SeerrMediaStatus.PROCESSING
                -> true
                else -> false
            }
        }
        .associate { it.seasonNumber to it.status }

    val requestedNumbers = mediaInfo?.requests.orEmpty()
        .asSequence()
        .filter { !it.is4k && isActiveRequest(it) }
        .flatMap { request -> request.seasons.asSequence() }
        .mapNotNull { it.seasonNumber }
        .toSet()

    return seasons
        .asSequence()
        .filter { it.seasonNumber > 0 }
        .distinctBy { it.seasonNumber }
        .sortedBy { it.seasonNumber }
        .map { season ->
            val num = season.seasonNumber
            when {
                num in libraryStatusBySeason -> SeerrSeasonPickItem(
                    seasonNumber = num,
                    availability = SeerrSeasonAvailability.Available,
                    mediaStatus = libraryStatusBySeason[num]
                )
                num in requestedNumbers -> SeerrSeasonPickItem(
                    seasonNumber = num,
                    availability = SeerrSeasonAvailability.Requested
                )
                else -> SeerrSeasonPickItem(
                    seasonNumber = num,
                    availability = SeerrSeasonAvailability.Selectable
                )
            }
        }
        .toList()
}

/** First active non-4K TV request; 4K request ids must never be updated by v1 UI. */
fun activeNon4kRequest(
    requests: List<SeerrMediaRequest>,
    isActiveRequest: (SeerrMediaRequest) -> Boolean
): SeerrMediaRequest? = requests.firstOrNull { !it.is4k && isActiveRequest(it) }

/** Hybrid Detail Request more visibility/submit gate. */
fun canRequestMoreSeasons(
    seasons: List<SeerrSeasonPickItem>,
    user: SeerrUser?,
    mediaStatus: Int?,
    hasActiveRequest: Boolean
): Boolean = seasons.any { it.selectable } &&
    canRequestSeerrMedia(
        user = user,
        mediaType = SeerrMediaType.TV,
        status = mediaStatus,
        hasActiveRequest = hasActiveRequest
    )

/**
 * Payload for create/update: newly picked seasons, plus seasons already on
 * [existingRequest] when updating (Seerr PUT replaces the request's season set).
 */
fun seasonsForTvRequest(
    selected: List<Int>,
    existingRequest: SeerrMediaRequest?
): List<Int> {
    val fromExisting = existingRequest?.seasons.orEmpty().mapNotNull { it.seasonNumber }
    return (fromExisting + selected).distinct().sorted()
}
