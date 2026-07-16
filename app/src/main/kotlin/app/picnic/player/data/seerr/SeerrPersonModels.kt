package app.picnic.player.data.seerr

import kotlinx.serialization.Serializable

/** Seerr `GET /person/{id}` — TMDB person detail. */
@Serializable
data class SeerrPersonDetails(
    val id: Int,
    val name: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val placeOfBirth: String? = null,
    val profilePath: String? = null,
    val knownForDepartment: String? = null,
    val homepage: String? = null,
    val imdbId: String? = null,
    val adult: Boolean = false
)

/** Seerr `GET /person/{id}/combined_credits`. */
@Serializable
data class SeerrPersonCombinedCredits(
    val id: Int? = null,
    val cast: List<SeerrPersonCreditCast> = emptyList(),
    val crew: List<SeerrPersonCreditCrew> = emptyList()
)

@Serializable
data class SeerrPersonCreditCast(
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
    val character: String? = null,
    val episodeCount: Int? = null,
    val adult: Boolean = false,
    val mediaInfo: SeerrMediaInfo? = null
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
}

@Serializable
data class SeerrPersonCreditCrew(
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
    val department: String? = null,
    val job: String? = null,
    val episodeCount: Int? = null,
    val adult: Boolean = false,
    val mediaInfo: SeerrMediaInfo? = null
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
}

/** Normalized credit for Hybrid Person rows (after mix/dedupe). */
data class SeerrPersonCredit(
    val tmdbId: Int,
    val mediaType: SeerrMediaType,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: SeerrMediaInfo? = null,
    /** Cast `character` or crew `job` after mix (cast wins on dedupe). */
    val creditRole: String? = null,
    val episodeCount: Int? = null
)
