package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy

/**
 * Grid filter state (library + genre grids). Every field maps to a native server
 * query parameter, so paging, total counts, and the alphabet rail stay exact.
 * Defaults mean "no filtering" — [isActive] drives the rail indicator dot.
 */
data class MediaGridFilter(
    /**
     * Library SCOPE, not a user filter: set once by the owning screen (each library is its
     * own drawer destination) and never touched by the filter panel. Excluded from
     * [isActive] and preserved across "Clear filters".
     */
    val libraryId: UUID? = null,
    /**
     * Genre SCOPE, not a user filter: set by the genre destination and preserved across
     * "Clear filters". It is deliberately separate from [genreIds], which remains the
     * clearable multi-select filter used by library grids.
     */
    val genreId: UUID? = null,
    /**
     * Collection (box set) SCOPE, not a user filter: set by the collection destination and
     * preserved across "Clear filters". Queries the box set's direct children.
     */
    val collectionId: UUID? = null,
    /** The genre destination's mixed-grid narrowing control. */
    val contentType: GridContentType = GridContentType.ALL,
    val watched: WatchedFilter = WatchedFilter.ALL,
    val favoritesOnly: Boolean = false,
    val genreIds: Set<UUID> = emptySet(),
    val studioIds: Set<UUID> = emptySet(),
    /** Minimum community rating (0–10 scale, inclusive); null = any. */
    val minCommunityRating: Int? = null,
    /** Minimum critic rating (0–100 scale, inclusive); null = any. Dormant: no panel
     *  section sets it — the server ignores the parameter (verified against 10.10),
     *  so the UI was pulled until server support exists. Query plumbing kept. */
    val minCriticRating: Int? = null,
    /** Explicitly included parental ratings; empty = no filtering (default off). */
    val parentalRatings: Set<String> = emptySet(),
    val resolution: ResolutionFilter = ResolutionFilter.ANY,
    /** Selected decade start years (e.g. 1990); empty = all. */
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

    /** Resets user-selected filters while retaining the destination's immutable scope. */
    fun clearUserFilters(): MediaGridFilter = MediaGridFilter(
        libraryId = libraryId,
        genreId = genreId,
        collectionId = collectionId
    )
}

enum class GridContentType(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series")
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

/** Sort field + direction. The rail's letter jump only holds for name ascending. */
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

    /** Series bubble up when a new episode lands. */
    RECENTLY_UPDATED("Recently updated", ItemSortBy.DATE_LAST_CONTENT_ADDED),
    LAST_PLAYED("Last played", ItemSortBy.DATE_PLAYED),
    COMMUNITY_RATING("Community rating", ItemSortBy.COMMUNITY_RATING),
    CRITIC_RATING("Critic rating", ItemSortBy.CRITIC_RATING),
    PARENTAL_RATING("Parental rating", ItemSortBy.OFFICIAL_RATING),
    RANDOM("Random", ItemSortBy.RANDOM)
}

/**
 * Values the filter panel offers, restricted to what actually exists in the
 * user's libraries (server-derived, not hardcoded).
 */
data class GridFilterFacets(
    val genres: List<BaseItemDto> = emptyList(),
    val studios: List<BaseItemDto> = emptyList(),
    val parentalRatings: List<String> = emptyList(),
    val decades: List<Int> = emptyList()
)
