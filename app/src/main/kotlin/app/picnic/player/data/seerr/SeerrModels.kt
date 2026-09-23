package app.picnic.player.data.seerr

import java.util.Locale
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SeerrAuthMethod {
    JELLYFIN,
    JELLYFIN_TOKEN,
    LOCAL
}

enum class SeerrMediaType {
    MOVIE,
    TV
}

object SeerrMediaStatus {
    const val UNKNOWN = 1
    const val PENDING = 2
    const val PROCESSING = 3
    const val PARTIALLY_AVAILABLE = 4
    const val AVAILABLE = 5
    const val DELETED = 6

    fun label(code: Int?): String = when (code) {
        PENDING -> "Pending"
        PROCESSING -> "Processing"
        PARTIALLY_AVAILABLE -> "Partially available"
        AVAILABLE -> "Available"
        DELETED -> "Deleted"
        else -> "Unknown"
    }
}

object SeerrIssueStatus {
    const val RESOLVED = 2
}

object SeerrRequestStatus {
    const val PENDING = 1
    const val APPROVED = 2
    const val DECLINED = 3
    const val FAILED = 4
    const val COMPLETED = 5

    fun label(code: Int?): String = when (code) {
        PENDING -> "Pending"
        APPROVED -> "Approved"
        DECLINED -> "Declined"
        FAILED -> "Failed"
        COMPLETED -> "Completed"
        else -> "Unknown"
    }
}

object SeerrPermission {
    const val NONE: Long = 0
    const val ADMIN: Long = 2
    const val MANAGE_SETTINGS: Long = 4
    const val MANAGE_USERS: Long = 8
    const val MANAGE_REQUESTS: Long = 16
    const val REQUEST: Long = 32
    const val VOTE: Long = 64
    const val AUTO_APPROVE: Long = 128
    const val AUTO_APPROVE_MOVIE: Long = 256
    const val AUTO_APPROVE_TV: Long = 512
    const val REQUEST_4K: Long = 1024
    const val REQUEST_4K_MOVIE: Long = 2048
    const val REQUEST_4K_TV: Long = 4096
    const val REQUEST_ADVANCED: Long = 8192
    const val REQUEST_VIEW: Long = 16384
    const val AUTO_APPROVE_4K: Long = 32768
    const val AUTO_APPROVE_4K_MOVIE: Long = 65536
    const val AUTO_APPROVE_4K_TV: Long = 131072
    const val REQUEST_MOVIE: Long = 262144
    const val REQUEST_TV: Long = 524288
    const val MANAGE_ISSUES: Long = 1048576
    const val VIEW_ISSUES: Long = 2097152
    const val CREATE_ISSUES: Long = 4194304
    const val AUTO_REQUEST: Long = 8388608
    const val AUTO_REQUEST_MOVIE: Long = 16777216
    const val AUTO_REQUEST_TV: Long = 33554432
    const val RECENT_VIEW: Long = 67108864
    const val WATCHLIST_VIEW: Long = 134217728
    const val MANAGE_BLACKLIST: Long = 268435456
    const val VIEW_BLACKLIST: Long = 1073741824

    fun has(permissions: Long, flag: Long): Boolean = permissions and ADMIN == ADMIN || permissions and flag == flag
}

fun canRequestSeerrMedia(
    user: SeerrUser?,
    mediaType: SeerrMediaType,
    status: Int?,
    hasActiveRequest: Boolean = false
): Boolean {
    if (user == null) return false
    val perms = user.permissions
    val typeOk = when (mediaType) {
        SeerrMediaType.MOVIE ->
            SeerrPermission.has(perms, SeerrPermission.REQUEST) ||
                SeerrPermission.has(perms, SeerrPermission.REQUEST_MOVIE)
        SeerrMediaType.TV ->
            SeerrPermission.has(perms, SeerrPermission.REQUEST) ||
                SeerrPermission.has(perms, SeerrPermission.REQUEST_TV)
    }
    if (!typeOk) return false
    return when (status) {
        SeerrMediaStatus.PENDING,
        SeerrMediaStatus.PROCESSING,
        SeerrMediaStatus.AVAILABLE
        -> false
        SeerrMediaStatus.PARTIALLY_AVAILABLE -> mediaType == SeerrMediaType.TV
        else -> !(mediaType == SeerrMediaType.MOVIE && hasActiveRequest)
    }
}

@Serializable
data class SeerrStatusResponse(
    val version: String? = null,
    val commitTag: String? = null
)

@Serializable
data class SeerrJellyfinAuthBody(
    val username: String,
    val password: String,
    val email: String? = null
)

@Serializable
data class SeerrLocalAuthBody(
    val email: String,
    val password: String
)

@Serializable
data class SeerrUser(
    val id: Int,
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val permissions: Long = 0
)

@Serializable
data class SeerrPublicSettings(
    val initialized: Boolean = true,
    val movie4kEnabled: Boolean = false,
    val series4kEnabled: Boolean = false,
    val cacheImages: Boolean = false,
    val localLogin: Boolean = true
)

@Serializable
data class SeerrMediaInfo(
    val id: Int? = null,
    val tmdbId: Int? = null,
    val mediaType: String? = null,
    val status: Int? = null,
    val jellyfinMediaId: String? = null,
    val jellyfinMediaId4k: String? = null,
    val seasons: List<SeerrMediaSeason> = emptyList(),
    val requests: List<SeerrMediaRequest> = emptyList()
) {
    val resolvedType: SeerrMediaType?
        get() = when (mediaType?.lowercase()) {
            "movie" -> SeerrMediaType.MOVIE
            "tv" -> SeerrMediaType.TV
            else -> null
        }
}

@Serializable
data class SeerrMediaSeason(
    val id: Int? = null,
    val seasonNumber: Int = 0,
    val status: Int? = null,
    val status4k: Int? = null
)

@Serializable
data class SeerrMediaRequest(
    val id: Int,
    val status: Int? = null,
    val is4k: Boolean = false,
    val mediaType: String? = null,
    val requestedBy: SeerrUser? = null,
    val seasons: List<SeerrRequestSeason> = emptyList(),
    val media: SeerrRequestMediaRef? = null
)

@Serializable
data class SeerrRequestSeason(
    val id: Int? = null,
    val seasonNumber: Int? = null,
    val status: Int? = null
)

@Serializable
data class SeerrRequestMediaRef(
    val tmdbId: Int? = null,
    val status: Int? = null,
    val mediaType: String? = null,
    val jellyfinMediaId: String? = null,
    val jellyfinMediaId4k: String? = null,
    val title: String? = null,
    val name: String? = null,
    val posterPath: String? = null
)

@Serializable
data class SeerrSearchPage(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<SeerrSearchResult> = emptyList()
)

@Serializable
data class SeerrGenre(
    val id: Int,
    val name: String? = null
)

@Serializable
data class SeerrContentRating(
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    val rating: String? = null
)

@Serializable
data class SeerrContentRatings(
    val results: List<SeerrContentRating> = emptyList()
)

@Serializable
data class SeerrMovieReleases(
    val results: List<SeerrMovieReleaseCountry> = emptyList()
)

@Serializable
data class SeerrMovieReleaseCountry(
    @SerialName("iso_3166_1")
    val iso31661: String? = null,
    @SerialName("release_dates")
    val releaseDates: List<SeerrMovieReleaseDate> = emptyList()
)

@Serializable
data class SeerrMovieReleaseDate(
    val certification: String? = null,
    val type: Int? = null
)

@Serializable
data class SeerrSearchResult(
    val id: Int,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
    val resolvedType: SeerrMediaType?
        get() = resolvedType()

    fun resolvedType(defaultType: SeerrMediaType? = null): SeerrMediaType? = when (mediaType?.lowercase()) {
        "movie" -> SeerrMediaType.MOVIE
        "tv" -> SeerrMediaType.TV
        else -> defaultType
    }
}

@Serializable
data class SeerrCredits(
    val cast: List<SeerrCastMember> = emptyList()
)

@Serializable
data class SeerrCastMember(
    val id: Int? = null,
    val name: String? = null,
    val character: String? = null,
    val profilePath: String? = null,
    val order: Int? = null
)

@Serializable
data class SeerrRelatedVideo(
    val url: String? = null,
    val key: String? = null,
    val name: String? = null,
    val size: Int? = null,
    val type: String? = null,
    val site: String? = null
)

@Serializable
data class SeerrMovieDetails(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val runtime: Int? = null,
    val voteAverage: Double? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val releases: SeerrMovieReleases? = null,
    val credits: SeerrCredits? = null,
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null
)

@Serializable
data class SeerrTvDetails(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val lastAirDate: String? = null,
    val voteAverage: Double? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val numberOfSeasons: Int? = null,
    val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    val contentRatings: SeerrContentRatings? = null,
    val seasons: List<SeerrTvSeason> = emptyList(),
    val credits: SeerrCredits? = null,
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null
)

@Serializable
data class SeerrTvSeason(
    val id: Int? = null,
    val name: String? = null,
    val seasonNumber: Int = 0,
    val episodeCount: Int? = null,
    val posterPath: String? = null
)

@Serializable
data class SeerrCreateRequestBody(
    val mediaType: String,
    val mediaId: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val seasons: List<Int>? = null,
    val is4k: Boolean = false
)

@Serializable
data class SeerrCreateIssueBody(
    val issueType: Int,
    val message: String,
    val mediaId: Int,
    val problemSeason: Int = 0,
    val problemEpisode: Int = 0
)

@Serializable
data class SeerrIssue(
    val id: Int,
    val issueType: Int? = null,
    val status: Int? = null,
    val problemSeason: Int = 0,
    val problemEpisode: Int = 0,
    val media: SeerrMediaInfo? = null,
    val createdBy: SeerrUser? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val comments: List<SeerrIssueComment> = emptyList()
)

@Serializable
data class SeerrIssueComment(
    val id: Int? = null,
    val message: String? = null,
    val user: SeerrUser? = null,
    val createdAt: String? = null
)

@Serializable
data class SeerrIssueCommentBody(
    val message: String
)

@Serializable
data class SeerrIssueListPage(
    val pageInfo: SeerrPageInfo? = null,
    val results: List<SeerrIssue> = emptyList()
)

@Serializable
data class SeerrUpdateRequestBody(
    val mediaType: String,
    val seasons: List<Int>,
    val is4k: Boolean = false
)

@Serializable
data class SeerrRequestListPage(
    val pageInfo: SeerrPageInfo? = null,
    val results: List<SeerrMediaRequest> = emptyList()
)

@Serializable
data class SeerrPageInfo(
    val pages: Int = 1,
    val pageSize: Int = 20,
    val results: Int = 0,
    val page: Int = 1
)

data class SeerrCatalogItem(
    val tmdbId: Int,
    val mediaType: SeerrMediaType,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val lastAirDate: String? = null,
    val seriesStatus: String? = null,
    val voteAverage: Double? = null,
    val mediaStatus: Int? = null,
    val jellyfinMediaId: String? = null,
    val jellyfinMediaId4k: String? = null,
    val genreNames: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val seasonCount: Int? = null,
    val certificate: String? = null,
    val detailEnriched: Boolean = false,
    val creditRole: String? = null,
    val episodeCount: Int? = null,
    val trailerUrl: String? = null
)

val SeerrCatalogItem.catalogKey: String get() = "$mediaType-$tmdbId"

data class SeerrDiscoverRow(
    val title: String,
    val items: List<SeerrCatalogItem>
)

const val SeerrCastLimit = 20
const val SeerrRecommendationsLimit = 20

fun SeerrSearchResult.toCatalogItem(
    genreNamesById: Map<Int, String> = emptyMap(),
    defaultMediaType: SeerrMediaType? = null
): SeerrCatalogItem? {
    val type = resolvedType(defaultMediaType) ?: return null
    return SeerrCatalogItem(
        tmdbId = id,
        mediaType = type,
        title = displayTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        voteAverage = voteAverage,
        mediaStatus = mediaInfo?.status,
        jellyfinMediaId = mediaInfo?.jellyfinMediaId,
        jellyfinMediaId4k = mediaInfo?.jellyfinMediaId4k,
        genreNames = genreIds.mapNotNull { genreNamesById[it] }.take(3)
    )
}

fun List<SeerrCastMember>.orderedCast(limit: Int = SeerrCastLimit): List<SeerrCastMember> = asSequence()
    .filter { !it.name.isNullOrBlank() }
    .withIndex()
    .sortedWith(
        compareBy<IndexedValue<SeerrCastMember>> { it.value.order ?: Int.MAX_VALUE }
            .thenBy { it.index }
    )
    .map { it.value }
    .take(limit)
    .toList()

fun SeerrMovieDetails.castRow(): List<SeerrCastMember> = credits?.cast.orEmpty().orderedCast()

fun SeerrTvDetails.castRow(): List<SeerrCastMember> = credits?.cast.orEmpty().orderedCast()

fun List<SeerrRelatedVideo>.youtubeTrailerUrl(): String? {
    val trailer = asSequence()
        .filter { it.type.equals("Trailer", ignoreCase = true) }
        .maxByOrNull { it.size ?: 0 }
        ?: return null
    trailer.url?.takeIf { it.isNotBlank() }?.let { return it }
    val key = trailer.key?.takeIf { it.isNotBlank() } ?: return null
    return "https://www.youtube.com/watch?v=$key"
}

fun SeerrMovieDetails.toCatalogItem(): SeerrCatalogItem = SeerrCatalogItem(
    tmdbId = id,
    mediaType = SeerrMediaType.MOVIE,
    title = title ?: "Untitled",
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = releaseDate,
    voteAverage = voteAverage,
    mediaStatus = mediaInfo?.status,
    jellyfinMediaId = mediaInfo?.jellyfinMediaId,
    jellyfinMediaId4k = mediaInfo?.jellyfinMediaId4k,
    genreNames = genres.mapNotNull { it.name?.takeIf(String::isNotBlank) }.take(3),
    runtimeMinutes = runtime?.takeIf { it > 0 },
    certificate = movieCertificate(releases),
    detailEnriched = true,
    trailerUrl = relatedVideos.youtubeTrailerUrl()
)

fun SeerrTvDetails.derivedSeasonCount(): Int? {
    val seasonsFromField = numberOfSeasons?.takeIf { it > 0 }
    val seasonsFromList = seasons.count { it.seasonNumber > 0 }.takeIf { it > 0 }
    return seasonsFromField ?: seasonsFromList
}

fun SeerrTvDetails.toCatalogItem(): SeerrCatalogItem = SeerrCatalogItem(
    tmdbId = id,
    mediaType = SeerrMediaType.TV,
    title = name ?: "Untitled",
    overview = overview,
    posterPath = posterPath,
    backdropPath = backdropPath,
    releaseDate = firstAirDate,
    lastAirDate = lastAirDate,
    seriesStatus = status,
    voteAverage = voteAverage,
    mediaStatus = mediaInfo?.status,
    jellyfinMediaId = mediaInfo?.jellyfinMediaId,
    jellyfinMediaId4k = mediaInfo?.jellyfinMediaId4k,
    genreNames = genres.mapNotNull { it.name?.takeIf(String::isNotBlank) }.take(3),
    runtimeMinutes = episodeRunTime.firstOrNull()?.takeIf { it > 0 },
    seasonCount = derivedSeasonCount(),
    certificate = tvCertificate(contentRatings),
    detailEnriched = true,
    trailerUrl = relatedVideos.youtubeTrailerUrl()
)

private fun movieCertificate(releases: SeerrMovieReleases?): String? {
    val countries = releases?.results.orEmpty()
    if (countries.isEmpty()) return null
    val preferred = preferredReleaseCountry(countries) ?: return null
    val dates = preferred.releaseDates
    return dates.firstOrNull { it.type == 3 && !it.certification.isNullOrBlank() }?.certification
        ?: dates.firstOrNull { !it.certification.isNullOrBlank() }?.certification
        ?: countries.asSequence()
            .flatMap { it.releaseDates.asSequence() }
            .firstOrNull { !it.certification.isNullOrBlank() }
            ?.certification
}

private fun preferredReleaseCountry(
    countries: List<SeerrMovieReleaseCountry>
): SeerrMovieReleaseCountry? {
    val localeCountry = Locale.getDefault().country.takeIf { it.isNotBlank() }
    return countries.firstOrNull { country ->
        localeCountry != null && country.iso31661.equals(localeCountry, ignoreCase = true)
    }
        ?: countries.firstOrNull { it.iso31661.equals("US", ignoreCase = true) }
        ?: countries.firstOrNull { it.releaseDates.any { d -> !d.certification.isNullOrBlank() } }
        ?: countries.firstOrNull()
}

private fun tvCertificate(ratings: SeerrContentRatings?): String? {
    val results = ratings?.results.orEmpty()
    if (results.isEmpty()) return null
    val localeCountry = Locale.getDefault().country.takeIf { it.isNotBlank() }
    return results.firstOrNull { entry ->
        localeCountry != null &&
            entry.iso31661.equals(localeCountry, ignoreCase = true) &&
            !entry.rating.isNullOrBlank()
    }?.rating
        ?: results.firstOrNull {
            it.iso31661.equals("US", ignoreCase = true) && !it.rating.isNullOrBlank()
        }?.rating
        ?: results.firstOrNull { !it.rating.isNullOrBlank() }?.rating
}
