package app.picnic.player.data.seerr

enum class SeerrSeasonAvailability {
    Selectable,

    Available,
    Requested
}

data class SeerrSeasonPickItem(
    val seasonNumber: Int,
    val availability: SeerrSeasonAvailability,
    val mediaStatus: Int? = null
) {
    val selectable: Boolean get() = availability == SeerrSeasonAvailability.Selectable
}

fun seasonLibraryBadgeLabel(mediaStatus: Int?): String? = when (mediaStatus) {
    SeerrMediaStatus.AVAILABLE,
    SeerrMediaStatus.PARTIALLY_AVAILABLE,
    SeerrMediaStatus.PROCESSING
    -> SeerrMediaStatus.label(mediaStatus)
    else -> null
}

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

fun activeNon4kRequest(
    requests: List<SeerrMediaRequest>,
    isActiveRequest: (SeerrMediaRequest) -> Boolean
): SeerrMediaRequest? = requests.firstOrNull { !it.is4k && isActiveRequest(it) }

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

fun seasonsForTvRequest(
    selected: List<Int>,
    existingRequest: SeerrMediaRequest?
): List<Int> {
    val fromExisting = existingRequest?.seasons.orEmpty().mapNotNull { it.seasonNumber }
    return (fromExisting + selected).distinct().sorted()
}
