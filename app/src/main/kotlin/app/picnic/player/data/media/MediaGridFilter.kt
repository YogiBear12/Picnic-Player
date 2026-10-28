package app.picnic.player.data.media

import app.picnic.player.data.playback.CulturePickerOption
import app.picnic.player.data.playback.pinnedLanguageOptions
import app.picnic.player.util.LanguageDisplay
import app.picnic.player.util.iso3ToIso2
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy

data class MediaGridFilter(
    val libraryId: UUID? = null,
    val genreId: UUID? = null,
    val folderId: UUID? = null,
    val contentType: GridContentType = GridContentType.ALL,
    val watched: WatchedFilter = WatchedFilter.ALL,
    val favoritesOnly: Boolean = false,
    val genreIds: Set<UUID> = emptySet(),
    val studioIds: Set<UUID> = emptySet(),
    val minCommunityRating: Int? = null,
    val minCriticRating: Int? = null,
    val parentalRatings: Set<String> = emptySet(),
    val resolution: ResolutionFilter = ResolutionFilter.ANY,
    val audioLanguages: Set<String> = emptySet(),
    val subtitleLanguages: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet()
) {
    val tracksUserStatus: Boolean
        get() = favoritesOnly || watched != WatchedFilter.ALL

    val isActive: Boolean
        get() = contentType != GridContentType.ALL ||
            watched != WatchedFilter.ALL ||
            favoritesOnly ||
            genreIds.isNotEmpty() ||
            studioIds.isNotEmpty() ||
            minCommunityRating != null ||
            minCriticRating != null ||
            parentalRatings.isNotEmpty() ||
            resolution != ResolutionFilter.ANY ||
            audioLanguages.isNotEmpty() ||
            subtitleLanguages.isNotEmpty() ||
            decades.isNotEmpty()

    fun clearUserFilters(): MediaGridFilter = MediaGridFilter(
        libraryId = libraryId,
        genreId = genreId,
        folderId = folderId
    )
}

enum class GridContentType(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Shows")
}

enum class WatchedFilter(val label: String) {
    ALL("All"),
    UNWATCHED("Unwatched"),
    WATCHED("Watched"),
    IN_PROGRESS("In progress")
}

enum class ResolutionFilter(val label: String) {
    ANY("Any"),
    UHD_4K("4K"),
    HD("HD"),
    SD("SD")
}

@Serializable
data class GridSortSpec(
    val field: GridSortField = GridSortField.NAME,
    val ascending: Boolean = true
) {
    val supportsLetterJump: Boolean get() = this.field == GridSortField.NAME && ascending
}

enum class GridSortField(val label: String, internal val sortBy: ItemSortBy) {
    NAME("Name", ItemSortBy.SORT_NAME),
    RELEASE_DATE("Release date", ItemSortBy.PREMIERE_DATE),
    DATE_ADDED("Date added", ItemSortBy.DATE_CREATED),

    RECENTLY_UPDATED("Recently updated", ItemSortBy.DATE_LAST_CONTENT_ADDED),
    LAST_PLAYED("Last played", ItemSortBy.DATE_PLAYED),
    COMMUNITY_RATING("Community rating", ItemSortBy.COMMUNITY_RATING),
    CRITIC_RATING("Critic rating", ItemSortBy.CRITIC_RATING),
    PARENTAL_RATING("Parental rating", ItemSortBy.OFFICIAL_RATING),
    RANDOM("Random", ItemSortBy.RANDOM)
}

data class GridFilterFacets(
    val genres: List<BaseItemDto> = emptyList(),
    val studios: List<BaseItemDto> = emptyList(),
    val parentalRatings: List<String> = emptyList(),
    val audioLanguages: List<LanguageFacet> = emptyList(),
    val subtitleLanguages: List<LanguageFacet> = emptyList(),
    val decades: List<Int> = emptyList()
)

data class LanguageFacet(val label: String, val codes: Set<String>)

private val UNSPOKEN_LANGUAGE_LABELS = mapOf("und" to "Unknown", "zxx" to "No dialogue")

fun languageFacets(codes: List<String>, deviceLanguage: String): List<LanguageFacet> {
    val (unspoken, spoken) = codes.distinct().partition { it.lowercase(Locale.ROOT) in UNSPOKEN_LANGUAGE_LABELS }
    val byLanguage = spoken.groupBy { iso3ToIso2(it) ?: it.lowercase(Locale.ROOT) }
    val pinned = pinnedLanguageOptions(
        byLanguage
            .map { (language, group) -> CulturePickerOption(languageCode = language, displayName = LanguageDisplay.name(group.first())) }
            .sortedBy { it.displayName.lowercase(Locale.ROOT) },
        deviceLanguage
    )
    return pinned.options.map { LanguageFacet(it.displayName, byLanguage.getValue(it.languageCode).toSet()) } +
        unspoken.map { LanguageFacet(UNSPOKEN_LANGUAGE_LABELS.getValue(it.lowercase(Locale.ROOT)), setOf(it)) }
}
