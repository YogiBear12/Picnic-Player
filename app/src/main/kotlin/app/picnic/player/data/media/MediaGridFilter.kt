package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy

data class MediaGridFilter(
    val libraryId: UUID? = null,
    val genreId: UUID? = null,
    val contentType: GridContentType = GridContentType.ALL,
    val watched: WatchedFilter = WatchedFilter.ALL,
    val favoritesOnly: Boolean = false,
    val genreIds: Set<UUID> = emptySet(),
    val studioIds: Set<UUID> = emptySet(),
    val minCommunityRating: Int? = null,
    val minCriticRating: Int? = null,
    val parentalRatings: Set<String> = emptySet(),
    val resolution: ResolutionFilter = ResolutionFilter.ANY,
    val decades: Set<Int> = emptySet()
) {
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
            decades.isNotEmpty()

    fun clearUserFilters(): MediaGridFilter = MediaGridFilter(
        libraryId = libraryId,
        genreId = genreId
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
    val decades: List<Int> = emptyList()
)
