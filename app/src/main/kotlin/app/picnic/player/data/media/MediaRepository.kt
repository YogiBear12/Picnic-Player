package app.picnic.player.data.media

import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.jellyfin.JellyfinFactory
import app.picnic.player.di.IoDispatcher
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.suggestionsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CultureDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest

internal const val MEDIA_GRID_PAGE_SIZE = 100

/**
 * Reads home/browse content from a Jellyfin server via the SDK.
 * Returns SDK [BaseItemDto]s directly; image/display helpers live in
 * `JellyfinImages` and the UI layer.
 */
@Singleton
class MediaRepository @Inject constructor(
    private val jellyfin: JellyfinFactory,
    private val changeBus: LibraryChangeBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private fun api(session: UserSession) = jellyfin.api(session.server.baseUrl, session.accessToken)

    /**
     * Runs a network + deserialize [block] on the IO dispatcher. The Jellyfin SDK resumes
     * its OkHttp continuation on the caller's dispatcher, so without this the kotlinx
     * .serialization parse of the response runs wherever the caller is — for ViewModels that
     * is the main thread. Wrapping every read here keeps parsing off the UI thread. Nested
     * calls are cheap (already on IO → no re-dispatch).
     */
    private suspend inline fun <T> onIo(crossinline block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    suspend fun userViews(session: UserSession): List<BaseItemDto> = onIo {
        api(session).userViewsApi.getUserViews().content.items.orEmpty()
    }

    /** Jellyfin `/Localization/Cultures` — display names for the Languages settings picker. */
    suspend fun cultures(session: UserSession): List<CultureDto> = onIo {
        api(session).localizationApi.getCultures().content
    }

    suspend fun resumeItems(session: UserSession, limit: Int = LATEST_ROW_LIMIT): List<BaseItemDto> = onIo {
        api(session).itemsApi.getResumeItems(
            limit = limit,
            fields = CONTINUE_FIELDS,
            enableImageTypes = IMAGE_TYPES
        ).content.items.orEmpty()
    }

    suspend fun nextUp(session: UserSession, limit: Int = LATEST_ROW_LIMIT): List<BaseItemDto> = onIo {
        api(session).tvShowsApi.getNextUp(
            limit = limit,
            fields = CONTINUE_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableResumable = false
        ).content.items.orEmpty()
    }

    suspend fun nextEpisodeForSeries(session: UserSession, seriesId: UUID): BaseItemDto? = onIo {
        // First try to get the Next Up episode for this specific series
        val nextUp = runCatching {
            api(session).tvShowsApi.getNextUp(
                seriesId = seriesId,
                userId = UUID.fromString(session.userId),
                fields = CONTINUE_FIELDS,
                enableImageTypes = IMAGE_TYPES
            ).content.items.orEmpty().firstOrNull()
        }.getOrNull()

        if (nextUp != null) {
            return@onIo nextUp
        }

        // Fallback: If no next up exists (all watched, or none watched),
        // fetch episodes, ignore specials (Season 0) unless it's the only season, and get the first one.
        val episodes = runCatching {
            api(session).tvShowsApi.getEpisodes(
                seriesId = seriesId,
                userId = UUID.fromString(session.userId),
                fields = CONTINUE_FIELDS,
                enableImageTypes = IMAGE_TYPES
            ).content.items.orEmpty()
        }.getOrNull() ?: emptyList()

        if (episodes.isEmpty()) return@onIo null

        // Try to find the first episode of Season 1 (or above)
        val nonSpecial = episodes
            .filter { (it.parentIndexNumber ?: 1) > 0 }
            .sortedWith(compareBy({ it.parentIndexNumber }, { it.indexNumber }))
            .firstOrNull()

        if (nonSpecial != null) {
            return@onIo nonSpecial
        }

        // If only specials exist, fallback to the first special
        episodes
            .sortedWith(compareBy({ it.parentIndexNumber }, { it.indexNumber }))
            .firstOrNull()
    }

    suspend fun latestInLibrary(
        session: UserSession,
        parentId: UUID
    ): List<BaseItemDto> = onIo {
        api(session).userLibraryApi.getLatestMedia(
            parentId = parentId,
            limit = LATEST_ROW_LIMIT,
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            groupItems = true
        ).content
    }

    /**
     * Recently added across the whole library (no parent), server-sorted.
     * Used by Android TV home channels so rows are global — not concatenated
     * per-view (which stacks one library after another).
     */
    suspend fun latestMedia(
        session: UserSession,
        includeItemTypes: List<BaseItemKind>,
        limit: Int = LATEST_ROW_LIMIT,
        groupItems: Boolean = true
    ): List<BaseItemDto> = onIo {
        api(session).userLibraryApi.getLatestMedia(
            limit = limit,
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            includeItemTypes = includeItemTypes,
            groupItems = groupItems
        ).content
    }

    /**
     * Jellyfin "Because you watched…" style suggestions (movies + series).
     * Used for the Android TV Recommendations preview channel.
     */
    suspend fun suggestions(
        session: UserSession,
        limit: Int = LATEST_ROW_LIMIT
    ): List<BaseItemDto> = onIo {
        api(session).suggestionsApi.getSuggestions(
            userId = UUID.fromString(session.userId),
            mediaType = listOf(MediaType.VIDEO),
            type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            limit = limit,
            enableTotalRecordCount = false
        ).content.items.orEmpty()
    }

    suspend fun similarItems(session: UserSession, itemId: UUID): List<BaseItemDto> = onIo {
        api(session).libraryApi.getSimilarItems(
            GetSimilarItemsRequest(
                itemId = itemId,
                limit = 12,
                fields = BROWSE_FIELDS,
                userId = UUID.fromString(session.userId)
            )
        ).content.items.orEmpty()
    }

    /**
     * Collections (box sets) that contain [itemId], for the detail screen's "Appears in"
     * row. No stable server exposes a direct "collections containing item" endpoint yet
     * (jellyfin/jellyfin#15515 is still open as of 10.11), so this enumerates the user's
     * box sets and checks membership against their (cached) child id sets. Any failure
     * resolves to an empty list and the row simply doesn't show.
     */
    suspend fun collectionsContaining(session: UserSession, itemId: UUID): List<BaseItemDto> = onIo {
        try {
            val boxSets = api(session).itemsApi.getItems(
                userId = UUID.fromString(session.userId),
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                recursive = true,
                fields = BROWSE_FIELDS,
                enableImageTypes = IMAGE_TYPES,
                enableUserData = false,
                enableTotalRecordCount = false,
                limit = BOX_SET_LIMIT
            ).content.items.orEmpty()

            kotlinx.coroutines.coroutineScope {
                val gate = kotlinx.coroutines.sync.Semaphore(BOX_SET_FETCH_CONCURRENCY)
                boxSets.map { boxSet ->
                    async {
                        val ids = runCatching {
                            gate.withPermit { boxSetChildIds(session, boxSet.id) }
                        }.getOrDefault(emptySet())
                        boxSet.takeIf { itemId in ids }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class CachedChildIds(val ids: Set<UUID>, val atMillis: Long)

    private val boxSetChildIdsCache =
        java.util.concurrent.ConcurrentHashMap<UUID, CachedChildIds>()

    /** Child item ids of a box set, cached briefly — detail screens reopen often. */
    private suspend fun boxSetChildIds(session: UserSession, boxSetId: UUID): Set<UUID> {
        val now = System.currentTimeMillis()
        boxSetChildIdsCache[boxSetId]
            ?.takeIf { now - it.atMillis < BOX_SET_CACHE_TTL_MS }
            ?.let { return it.ids }
        val ids = api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            parentId = boxSetId,
            enableImages = false,
            enableUserData = false,
            enableTotalRecordCount = false,
            limit = BOX_SET_LIMIT
        ).content.items.orEmpty().map { it.id }.toSet()
        boxSetChildIdsCache[boxSetId] = CachedChildIds(ids, now)
        return ids
    }

    suspend fun getPerson(session: UserSession, personId: UUID): BaseItemDto = onIo {
        api(session).userLibraryApi.getItem(itemId = personId).content
    }

    suspend fun getItemsByPerson(
        session: UserSession,
        personId: UUID,
        types: List<BaseItemKind>
    ): List<BaseItemDto> = onIo {
        api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            personIds = listOf(personId),
            includeItemTypes = types,
            fields = CONTINUE_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.DESCENDING, SortOrder.DESCENDING, SortOrder.ASCENDING),
            recursive = true
        ).content.items.orEmpty()
    }

    suspend fun item(session: UserSession, itemId: UUID): BaseItemDto = onIo {
        api(session).userLibraryApi.getItem(itemId = itemId).content
    }

    suspend fun localTrailers(session: UserSession, itemId: UUID): List<BaseItemDto> = onIo {
        api(session).userLibraryApi.getLocalTrailers(itemId = itemId, userId = UUID.fromString(session.userId)).content.orEmpty()
    }

    /**
     * Season count for a single series, resolved via a count-only query — `limit = 0` +
     * `enableTotalRecordCount` returns the total without shipping a single season DTO. This
     * replaces the old whole-library [seasonCounts] sweep (up to 5000 season DTOs per library)
     * for the home hero, which only ever needs the count of the one focused series.
     */
    suspend fun seasonCount(session: UserSession, seriesId: UUID): Int = onIo {
        api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            parentId = seriesId,
            includeItemTypes = listOf(BaseItemKind.SEASON),
            recursive = true,
            enableImages = false,
            enableUserData = false,
            enableTotalRecordCount = true,
            limit = 0
        ).content.totalRecordCount ?: 0
    }

    /**
     * Fetches episode counts for a batch of specific seasons.
     * Used for opportunistic prefetching of season episode counts in Series detail views.
     */
    suspend fun seasonCounts(session: UserSession, seasonIds: List<UUID>): Map<UUID, Int> = onIo {
        if (seasonIds.isEmpty()) return@onIo emptyMap()
        runCatching {
            api(session).itemsApi.getItems(
                userId = UUID.fromString(session.userId),
                ids = seasonIds,
                fields = listOf(ItemFields.CHILD_COUNT)
            ).content.items.orEmpty().associate { it.id to (it.childCount ?: 0) }
        }.getOrDefault(emptyMap())
    }

    /**
     * Media streams of a series' first playable episode — a series item carries no streams
     * of its own, so the hero's technical badges borrow the lead episode's (resolution/HDR/
     * audio are near-uniform within a show). Empty when the series has no playable episodes.
     */
    suspend fun seriesLeadStreams(session: UserSession, seriesId: UUID): List<MediaStream> = onIo {
        runCatching {
            api(session).tvShowsApi.getEpisodes(
                seriesId = seriesId,
                userId = UUID.fromString(session.userId),
                isMissing = false,
                limit = 1,
                fields = listOf(ItemFields.MEDIA_STREAMS)
            ).content.items.orEmpty().firstOrNull()?.mediaStreams.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches media streams for a batch of items (e.g. movies or episodes).
     * Used for the home screen's look-ahead prefetch to populate the hero badge rail without
     * bloating the home-row cache.
     */
    suspend fun itemStreams(session: UserSession, itemIds: List<UUID>): Map<UUID, List<MediaStream>> = onIo {
        if (itemIds.isEmpty()) return@onIo emptyMap()
        runCatching {
            api(session).itemsApi.getItems(
                userId = UUID.fromString(session.userId),
                ids = itemIds,
                fields = listOf(ItemFields.MEDIA_STREAMS)
            ).content.items.orEmpty().associate { it.id to it.mediaStreams.orEmpty() }
        }.getOrDefault(emptyMap())
    }

    /**
     * Streamable URL for one of [itemId]'s theme songs (random pick when the server has
     * several), or null when the item has none. The universal endpoint lets the server
     * pick direct play or transcode to a widely supported container; auth travels as an
     * `api_key` query param because the audio player fetches outside the SDK client.
     */
    suspend fun themeSongUrl(session: UserSession, itemId: UUID): String? = onIo {
        val api = api(session)
        val theme = api.libraryApi.getThemeSongs(itemId = itemId)
            .content.items.orEmpty().randomOrNull() ?: return@onIo null
        val url = api.universalAudioApi.getUniversalAudioStreamUrl(
            itemId = theme.id,
            container = listOf("opus", "mp3", "aac", "flac")
        )
        url + (if ('?' in url) "&" else "?") + "api_key=" + session.accessToken
    }

    /**
     * The episode that should play after [episodeId], or null at series end / when the item is
     * not an episode. Resolves across season boundaries: the season finale returns the first
     * episode of the next season.
     */
    suspend fun nextEpisode(session: UserSession, episodeId: UUID): BaseItemDto? = onIo {
        val api = api(session)
        val userId = UUID.fromString(session.userId)

        val currentEp = runCatching { api.userLibraryApi.getItem(episodeId).content }.getOrNull()
            ?: return@onIo null

        val seriesId = currentEp.seriesId ?: return@onIo null

        // Fetch the series' episodes in playback order, positioned at the current one via
        // startItemId (which starts the list at that episode). The entry immediately after is the
        // one to play next. Passing no seasonId means the server returns the whole-series ordering,
        // so specials are interleaved at their AirsBefore/AfterSeason/Episode positions and surface
        // as the next episode when their metadata says they belong there. isMissing = false drops
        // metadata-only episodes that have no playable media. limit is small because we only need
        // the next entry after the current one.
        val episodes = runCatching {
            api.tvShowsApi.getEpisodes(
                seriesId = seriesId,
                userId = userId,
                startItemId = episodeId,
                isMissing = false,
                fields = NEXT_EPISODE_FIELDS,
                enableImages = true,
                enableImageTypes = IMAGE_TYPES,
                limit = NEXT_EPISODE_LOOKAHEAD
            ).content.items
        }.getOrNull().orEmpty()

        val currentIdx = episodes.indexOfFirst { it.id == episodeId }
        if (currentIdx >= 0) {
            episodes.getOrNull(currentIdx + 1)
        } else {
            // startItemId positioned the list past the current episode; first entry is next.
            episodes.firstOrNull { it.id != episodeId }
        }
    }

    suspend fun setWatched(
        session: UserSession,
        itemId: UUID,
        played: Boolean,
        seriesId: UUID? = null
    ) = onIo {
        (
            if (played) {
                api(session).playStateApi.markPlayedItem(itemId).content
            } else {
                api(session).playStateApi.markUnplayedItem(itemId).content
            }
            ).also { notifyItemChanged(itemId, seriesId) }
    }

    suspend fun setFavorite(
        session: UserSession,
        itemId: UUID,
        favorite: Boolean,
        seriesId: UUID? = null
    ) = onIo {
        (
            if (favorite) {
                api(session).userLibraryApi.markFavoriteItem(itemId).content
            } else {
                api(session).userLibraryApi.unmarkFavoriteItem(itemId).content
            }
            ).also { notifyItemChanged(itemId, seriesId) }
    }

    private fun notifyItemChanged(itemId: UUID, seriesId: UUID?) {
        changeBus.emit(LibraryChange.ItemUpdated(itemId.toString(), seriesId?.toString()))
    }

    /** Paged grid across all libraries for [kind]. */
    /**
     * One page of a filtered, sorted grid query. Every [MediaGridFilter] field maps to a
     * native server parameter so counts/paging stay exact. Non-name sorts get SORT_NAME
     * as a stable tie-breaker (except RANDOM, whose pages the server shuffles per call).
     */
    suspend fun filteredItems(
        session: UserSession,
        kinds: List<BaseItemKind>,
        filter: MediaGridFilter,
        sort: GridSortSpec,
        startIndex: Int,
        limit: Int = MEDIA_GRID_PAGE_SIZE,
        parentIdOverride: UUID? = null,
        nameLessThan: String? = null
    ): MediaGridPage = onIo {
        val direction = if (sort.ascending) SortOrder.ASCENDING else SortOrder.DESCENDING
        val (sortBy, sortOrder) = when (sort.field) {
            GridSortField.NAME -> listOf(ItemSortBy.SORT_NAME) to listOf(direction)
            GridSortField.RANDOM -> listOf(ItemSortBy.RANDOM) to listOf(direction)
            else -> listOf(sort.field.sortBy, ItemSortBy.SORT_NAME) to
                listOf(direction, SortOrder.ASCENDING)
        }
        val response = api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            includeItemTypes = filter.contentType.itemKinds(kinds),
            // A library grid lists one direct view and a collection its direct children;
            // a genre scope searches recursively (within its library when scoped, else
            // across all), and a box-set listing must recurse to reach the collections folder.
            recursive = filter.genreId != null ||
                (filter.collectionId == null && kinds == listOf(BaseItemKind.BOX_SET)),
            parentId = parentIdOverride ?: filter.collectionId ?: filter.libraryId,
            startIndex = startIndex,
            limit = limit,
            nameLessThan = nameLessThan,
            sortBy = sortBy,
            sortOrder = sortOrder,
            isPlayed = when (filter.watched) {
                WatchedFilter.WATCHED -> true
                WatchedFilter.UNWATCHED -> false
                else -> null
            },
            filters = if (filter.watched == WatchedFilter.IN_PROGRESS) {
                listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_RESUMABLE)
            } else {
                null
            },
            isFavorite = if (filter.favoritesOnly) true else null,
            // Jellyfin treats this list as OR. The destination's fixed genre scope is kept
            // distinct from the library panel's clearable genre selections in the UI model.
            genreIds = (listOfNotNull(filter.genreId) + filter.genreIds)
                .takeIf { it.isNotEmpty() },
            studioIds = filter.studioIds.toList().takeIf { it.isNotEmpty() },
            minCommunityRating = filter.minCommunityRating?.toDouble(),
            minCriticRating = filter.minCriticRating?.toDouble(),
            officialRatings = filter.parentalRatings.toList().takeIf { it.isNotEmpty() },
            years = filter.decades.flatMap { decade -> decade..decade + 9 }
                .takeIf { it.isNotEmpty() },
            minWidth = if (filter.resolution == ResolutionFilter.UHD_4K) UHD_MIN_WIDTH else null,
            maxWidth = if (filter.resolution == ResolutionFilter.HD) UHD_MIN_WIDTH - 1 else null,
            isHd = when (filter.resolution) {
                ResolutionFilter.HD -> true
                ResolutionFilter.SD -> false
                else -> null
            },
            fields = GRID_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableTotalRecordCount = true
        ).content
        MediaGridPage(
            items = response.items.orEmpty(),
            totalCount = response.totalRecordCount ?: response.items.orEmpty().size
        )
    }

    /**
     * Count of filtered items sorting before [letter] — the alphabet-rail jump target.
     * Only meaningful for name-ascending sort. All-libraries counts one library at a
     * time (a combined recursive count is pathologically slow on large servers).
     */
    suspend fun filteredIndexBeforeLetter(
        session: UserSession,
        kinds: List<BaseItemKind>,
        filter: MediaGridFilter,
        letter: Char
    ): Int {
        if (filter.libraryId == null &&
            filter.genreId == null &&
            filter.collectionId == null &&
            kinds != listOf(BaseItemKind.BOX_SET)
        ) {
            return 0
        }
        return filteredItems(
            session = session,
            kinds = kinds,
            filter = filter,
            sort = GridSortSpec(GridSortField.NAME, ascending = true),
            startIndex = 0,
            limit = 0,
            nameLessThan = letter.toString()
        ).totalCount
    }

    private fun GridContentType.itemKinds(defaultKinds: List<BaseItemKind>): List<BaseItemKind> = when (this) {
        GridContentType.ALL -> defaultKinds
        GridContentType.MOVIES -> listOf(BaseItemKind.MOVIE)
        GridContentType.SERIES -> listOf(BaseItemKind.SERIES)
    }

    /**
     * Filter-panel options, restricted to values present in the user's libraries —
     * or in a single library when [libraryId] is set, so the panel's choices track
     * the library selection.
     */
    suspend fun gridFilterFacets(
        session: UserSession,
        kinds: List<BaseItemKind>,
        libraryId: UUID? = null
    ): GridFilterFacets = onIo {
        kotlinx.coroutines.coroutineScope {
            val userId = UUID.fromString(session.userId)
            val genres = async {
                runCatching {
                    api(session).genresApi.getGenres(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = kinds,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING)
                    ).content.items.orEmpty()
                }.getOrDefault(emptyList())
            }
            val studios = async {
                runCatching {
                    api(session).studiosApi.getStudios(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = kinds
                    ).content.items.orEmpty()
                        .sortedBy { it.name?.lowercase().orEmpty() }
                }.getOrDefault(emptyList())
            }
            val legacyFilters = async {
                runCatching {
                    api(session).filterApi.getQueryFiltersLegacy(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = kinds
                    ).content
                }.getOrNull()
            }
            val legacy = legacyFilters.await()
            GridFilterFacets(
                genres = genres.await(),
                studios = studios.await(),
                parentalRatings = legacy?.officialRatings.orEmpty(),
                decades = legacy?.years.orEmpty()
                    .map { it / 10 * 10 }
                    .distinct()
                    .sorted()
            )
        }
    }

    /** Title search within one item kind. The server matches loosely; UI re-ranks by relevance. */
    suspend fun search(
        session: UserSession,
        query: String,
        kind: BaseItemKind,
        limit: Int = SEARCH_ROW_LIMIT
    ): List<BaseItemDto> = onIo {
        api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            searchTerm = query,
            includeItemTypes = listOf(kind),
            recursive = true,
            limit = limit,
            // GRID_FIELDS: SORT_NAME feeds relevance tie-breaking, CHILD_COUNT the
            // series cards' season label.
            fields = GRID_FIELDS,
            enableImageTypes = IMAGE_TYPES
        ).content.items.orEmpty()
    }

    /**
     * Movie/series genres for a browse-by-genre grid: all libraries when [libraryId] is
     * null (the search tab), one library's genres when set (a library's Genres tab).
     */
    suspend fun genres(
        session: UserSession,
        libraryId: UUID? = null,
        kinds: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
    ): List<BaseItemDto> = onIo {
        api(session).genresApi.getGenres(
            userId = UUID.fromString(session.userId),
            parentId = libraryId,
            includeItemTypes = kinds,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING)
        ).content.items.orEmpty()
    }

    /** Count of the user's collections (box sets) — gates the library Collections tab. */
    suspend fun collectionCount(session: UserSession): Int = onIo {
        api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            includeItemTypes = listOf(BaseItemKind.BOX_SET),
            recursive = true,
            limit = 0,
            enableTotalRecordCount = true
        ).content.totalRecordCount ?: 0
    }

    /** Random watched items in one library — the "Because you watched …" row seeds. */
    suspend fun randomWatched(
        session: UserSession,
        libraryId: UUID,
        kinds: List<BaseItemKind>,
        limit: Int
    ): List<BaseItemDto> = onIo {
        api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            parentId = libraryId,
            includeItemTypes = kinds,
            isPlayed = true,
            sortBy = listOf(ItemSortBy.RANDOM),
            limit = limit,
            fields = BROWSE_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableTotalRecordCount = false
        ).content.items.orEmpty()
    }

    /**
     * The subset of [ids] that live inside [libraryId], with browse-row fields. Server
     * intersects `ids` with the recursive parent scope — the one-call way to filter a
     * global recommendation list down to a single library (order is NOT preserved;
     * callers re-order against their source list).
     */
    suspend fun itemsInLibrary(
        session: UserSession,
        libraryId: UUID,
        ids: List<UUID>
    ): List<BaseItemDto> = onIo {
        if (ids.isEmpty()) return@onIo emptyList()
        api(session).itemsApi.getItems(
            userId = UUID.fromString(session.userId),
            parentId = libraryId,
            recursive = true,
            ids = ids,
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableTotalRecordCount = false
        ).content.items.orEmpty()
    }

    private companion object {
        /** Bounded latest fetch (Jellyfin API default is 20 if omitted). */
        const val LATEST_ROW_LIMIT = 25

        /** Per-kind cap for search result rows. */
        const val SEARCH_ROW_LIMIT = 24

        /** Width threshold separating 4K/UHD from HD in the resolution filter. */
        const val UHD_MIN_WIDTH = 3200

        val BROWSE_FIELDS = listOf(
            ItemFields.OVERVIEW,
            ItemFields.GENRES,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
        )
        val CONTINUE_FIELDS = BROWSE_FIELDS
        val LATEST_FIELDS = BROWSE_FIELDS + ItemFields.CHILD_COUNT

        // SORT_NAME so the alphabet rail's active letter matches the server sort (articles
        // stripped: "The Hard Way" → "Hard Way" → H, not T). CHILD_COUNT feeds the series
        // cards' "N seasons" label.
        val GRID_FIELDS = BROWSE_FIELDS + ItemFields.SORT_NAME + ItemFields.CHILD_COUNT

        /** Fields needed to render the next-up overlay card. */
        val NEXT_EPISODE_FIELDS = listOf(
            ItemFields.OVERVIEW,
            ItemFields.PRIMARY_IMAGE_ASPECT_RATIO
        )

        // startItemId starts the list at the current episode, so the current + next entries are
        // all we need to resolve the following episode.
        const val NEXT_EPISODE_LOOKAHEAD = 2

        /** "Appears in" membership scan (no server endpoint yet — see collectionsContaining). */
        const val BOX_SET_LIMIT = 500
        const val BOX_SET_FETCH_CONCURRENCY = 4
        const val BOX_SET_CACHE_TTL_MS = 5 * 60 * 1000L
        val IMAGE_TYPES = listOf(
            ImageType.PRIMARY,
            ImageType.BACKDROP,
            ImageType.THUMB,
            ImageType.LOGO
        )
    }
}
