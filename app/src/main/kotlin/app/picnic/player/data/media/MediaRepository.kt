package app.picnic.player.data.media

import android.util.Log
import app.picnic.player.BuildConfig
import app.picnic.player.data.auth.UserSession
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.api.client.extensions.filterApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.suggestionsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CultureDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserConfiguration
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest

internal const val MEDIA_GRID_PAGE_SIZE = 100

data class WatchStats(val movies: Int, val shows: Int, val episodes: Int)

private const val LIBRARY_LOG_TAG = "PicnicLibrary"

@Singleton
class MediaRepository @Inject constructor(
    private val source: SessionApi
) {
    private suspend fun session(): UserSession = source.session()

    private suspend fun api() = source.client()

    private suspend fun <T> onIo(block: suspend () -> T): T = source.onIo(block)

    suspend fun userViews(): List<BaseItemDto> = onIo {
        api().userViewsApi.getUserViews().content.items.orEmpty()
    }

    suspend fun cultures(): List<CultureDto> = onIo {
        api().localizationApi.getCultures().content
    }

    suspend fun userConfiguration(): UserConfiguration? = onIo {
        api().userApi.getCurrentUser().content.configuration
    }

    suspend fun canTranscodeVideo(): Boolean = onIo {
        api().userApi.getCurrentUser().content.policy?.enableVideoPlaybackTranscoding ?: true
    }

    suspend fun resumeItems(limit: Int = LATEST_ROW_LIMIT): List<BaseItemDto> = onIo {
        api().itemsApi.getResumeItems(
            limit = limit,
            fields = CONTINUE_FIELDS,
            enableImageTypes = IMAGE_TYPES
        ).content.items.orEmpty()
    }

    suspend fun nextUp(limit: Int = LATEST_ROW_LIMIT): List<BaseItemDto> = onIo {
        api().tvShowsApi.getNextUp(
            limit = limit,
            fields = CONTINUE_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableResumable = false
        ).content.items.orEmpty()
    }

    private suspend fun resumeEpisodesForSeries(seriesId: UUID): List<BaseItemDto> = onIo {
        runCatching {
            api().itemsApi.getResumeItems(
                userId = session().userUuid,
                parentId = seriesId,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                fields = CONTINUE_FIELDS,
                enableImageTypes = IMAGE_TYPES
            ).content.items.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun nextEpisodeForSeries(seriesId: UUID): BaseItemDto? = coroutineScope {
        val resumable = async { resumeEpisodesForSeries(seriesId) }
        val nextUp = async { nextUpForSeries(seriesId) }
        preferResumeEpisode(nextUp.await(), resumable.await())
    }

    private suspend fun nextUpForSeries(seriesId: UUID): BaseItemDto? = onIo {
        val nextUp = runCatching {
            api().tvShowsApi.getNextUp(
                seriesId = seriesId,
                userId = session().userUuid,
                fields = CONTINUE_FIELDS,
                enableImageTypes = IMAGE_TYPES
            ).content.items.orEmpty().firstOrNull()
        }.getOrNull()

        if (nextUp != null) {
            return@onIo nextUp
        }

        val episodes = runCatching {
            api().tvShowsApi.getEpisodes(
                seriesId = seriesId,
                userId = session().userUuid,
                fields = CONTINUE_FIELDS,
                enableImageTypes = IMAGE_TYPES
            ).content.items.orEmpty()
        }.getOrNull() ?: emptyList()

        firstEpisodeToPlay(episodes)
    }

    suspend fun latestInLibrary(
        parentId: UUID
    ): List<BaseItemDto> = onIo {
        api().userLibraryApi.getLatestMedia(
            parentId = parentId,
            limit = LATEST_ROW_LIMIT,
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            groupItems = true
        ).content
    }

    suspend fun latestMedia(
        includeItemTypes: List<BaseItemKind>,
        limit: Int = LATEST_ROW_LIMIT,
        groupItems: Boolean = true
    ): List<BaseItemDto> = onIo {
        api().userLibraryApi.getLatestMedia(
            limit = limit,
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            includeItemTypes = includeItemTypes,
            groupItems = groupItems
        ).content
    }

    suspend fun suggestions(
        limit: Int = LATEST_ROW_LIMIT
    ): List<BaseItemDto> = onIo {
        api().suggestionsApi.getSuggestions(
            userId = session().userUuid,
            mediaType = listOf(MediaType.VIDEO),
            type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            limit = limit,
            enableTotalRecordCount = false
        ).content.items.orEmpty()
    }

    suspend fun similarItems(itemId: UUID): List<BaseItemDto> = onIo {
        api().libraryApi.getSimilarItems(
            GetSimilarItemsRequest(
                itemId = itemId,
                limit = 12,
                fields = BROWSE_FIELDS,
                userId = session().userUuid
            )
        ).content.items.orEmpty()
    }

    suspend fun collectionsContaining(itemId: UUID): List<BaseItemDto> = onIo {
        try {
            val boxSets = api().itemsApi.getItems(
                userId = session().userUuid,
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                recursive = true,
                fields = BROWSE_FIELDS,
                enableImageTypes = IMAGE_TYPES,
                enableUserData = false,
                enableTotalRecordCount = false,
                limit = BOX_SET_LIMIT
            ).content.items.orEmpty()

            coroutineScope {
                val gate = Semaphore(BOX_SET_FETCH_CONCURRENCY)
                boxSets.map { boxSet ->
                    async {
                        val ids = runCatching {
                            gate.withPermit { boxSetChildIds(boxSet.id) }
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

    private suspend fun boxSetChildIds(boxSetId: UUID): Set<UUID> {
        val now = System.currentTimeMillis()
        boxSetChildIdsCache[boxSetId]
            ?.takeIf { now - it.atMillis < BOX_SET_CACHE_TTL_MS }
            ?.let { return it.ids }
        val ids = api().itemsApi.getItems(
            userId = session().userUuid,
            parentId = boxSetId,
            enableImages = false,
            enableUserData = false,
            enableTotalRecordCount = false,
            limit = BOX_SET_LIMIT
        ).content.items.orEmpty().map { it.id }.toSet()
        boxSetChildIdsCache[boxSetId] = CachedChildIds(ids, now)
        return ids
    }

    fun collectionChildren(collectionId: UUID): Flow<List<BaseItemDto>> = pagedItems { startIndex ->
        childPage(collectionId, startIndex, excludeItemTypes = NON_VIDEO_KINDS)
    }

    fun collectionQueue(collectionId: UUID): Flow<List<BaseItemDto>> = pagedItems { startIndex ->
        childPage(collectionId, startIndex, includeItemTypes = PLAYABLE_KINDS, recursive = true)
    }

    private fun pagedItems(page: suspend (startIndex: Int) -> MediaGridPage): Flow<List<BaseItemDto>> = flow {
        val loaded = mutableListOf<BaseItemDto>()
        var total = Int.MAX_VALUE
        while (loaded.size < total) {
            val next = page(loaded.size)
            if (next.items.isEmpty()) break
            total = next.totalCount
            loaded += next.items
            emit(loaded.toList())
        }
    }

    private suspend fun childPage(
        parentId: UUID,
        startIndex: Int,
        includeItemTypes: List<BaseItemKind>? = null,
        excludeItemTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false
    ): MediaGridPage = onIo {
        val response = api().itemsApi.getItems(
            userId = session().userUuid,
            parentId = parentId,
            recursive = recursive,
            includeItemTypes = includeItemTypes,
            excludeItemTypes = excludeItemTypes,
            startIndex = startIndex,
            limit = MEDIA_GRID_PAGE_SIZE,
            fields = GRID_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableTotalRecordCount = true
        ).content
        MediaGridPage(
            items = response.items.orEmpty(),
            totalCount = response.totalRecordCount ?: response.items.orEmpty().size
        )
    }

    suspend fun getPerson(personId: UUID): BaseItemDto = onIo {
        api().userLibraryApi.getItem(itemId = personId).content
    }

    suspend fun getItemsByPerson(
        personId: UUID,
        types: List<BaseItemKind>
    ): List<BaseItemDto> = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            personIds = listOf(personId),
            includeItemTypes = types,
            fields = PERSON_ITEM_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.DESCENDING, SortOrder.DESCENDING, SortOrder.ASCENDING),
            recursive = true
        ).content.items.orEmpty()
    }

    suspend fun item(itemId: UUID): BaseItemDto = onIo {
        api().userLibraryApi.getItem(itemId = itemId).content
    }

    suspend fun localTrailers(itemId: UUID): List<BaseItemDto> = onIo {
        api().userLibraryApi.getLocalTrailers(itemId = itemId, userId = session().userUuid).content.orEmpty()
    }

    suspend fun seasonCount(seriesId: UUID): Int = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            parentId = seriesId,
            includeItemTypes = listOf(BaseItemKind.SEASON),
            recursive = true,
            enableImages = false,
            enableUserData = false,
            enableTotalRecordCount = true,
            limit = 0
        ).content.totalRecordCount ?: 0
    }

    suspend fun seasonCounts(seasonIds: List<UUID>): Map<UUID, Int> = onIo {
        if (seasonIds.isEmpty()) return@onIo emptyMap()
        runCatching {
            api().itemsApi.getItems(
                userId = session().userUuid,
                ids = seasonIds,
                fields = listOf(ItemFields.CHILD_COUNT)
            ).content.items.orEmpty().associate { it.id to (it.childCount ?: 0) }
        }.getOrDefault(emptyMap())
    }

    suspend fun episodeNumbersBySeason(seriesId: UUID): Map<Int, List<Int>> = onIo {
        runCatching {
            api().tvShowsApi.getEpisodes(
                seriesId = seriesId,
                userId = session().userUuid,
                isMissing = false,
                fields = emptyList()
            ).content.items.orEmpty()
        }.getOrDefault(emptyList())
            .mapNotNull { episode ->
                val season = episode.parentIndexNumber ?: return@mapNotNull null
                val number = episode.indexNumber ?: return@mapNotNull null
                season to number
            }
            .groupBy({ it.first }, { it.second })
    }

    suspend fun seriesLeadStreams(seriesId: UUID): List<MediaStream> = onIo {
        runCatching {
            api().tvShowsApi.getEpisodes(
                seriesId = seriesId,
                userId = session().userUuid,
                isMissing = false,
                limit = 1,
                fields = listOf(ItemFields.MEDIA_STREAMS)
            ).content.items.orEmpty().firstOrNull()?.mediaStreams.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun items(itemIds: List<UUID>): List<BaseItemDto> = onIo {
        if (itemIds.isEmpty()) return@onIo emptyList()
        try {
            coroutineScope {
                val gate = Semaphore(ITEM_FETCH_CONCURRENCY)
                itemIds.chunked(ITEM_FETCH_CHUNK)
                    .map { chunk -> async { gate.withPermit { itemPage(chunk) } } }
                    .awaitAll()
                    .flatten()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(LIBRARY_LOG_TAG, "bulk item fetch failed", failure)
            emptyList()
        }
    }

    private suspend fun itemPage(ids: List<UUID>): List<BaseItemDto> = api().itemsApi.getItems(
        userId = session().userUuid,
        ids = ids,
        fields = LATEST_FIELDS,
        enableImageTypes = IMAGE_TYPES
    ).content.items.orEmpty()

    suspend fun refreshChanged(shown: List<BaseItemDto>, changedIds: Set<String>): Map<UUID, BaseItemDto> {
        if (changedIds.isEmpty()) return emptyMap()
        val stale = shown.filter { it.id.toString() in changedIds }.distinctBy { it.id }
        if (stale.isEmpty()) return emptyMap()
        return items(stale.map { it.id }).associateBy { it.id }
    }

    suspend fun itemStreams(itemIds: List<UUID>): Map<UUID, List<MediaStream>> = onIo {
        if (itemIds.isEmpty()) return@onIo emptyMap()
        runCatching {
            api().itemsApi.getItems(
                userId = session().userUuid,
                ids = itemIds,
                fields = listOf(ItemFields.MEDIA_STREAMS)
            ).content.items.orEmpty().associate { it.id to it.mediaStreams.orEmpty() }
        }.getOrDefault(emptyMap())
    }

    suspend fun themeSongUrl(itemId: UUID): String? = onIo {
        val api = api()
        val theme = api.libraryApi.getThemeSongs(itemId = itemId)
            .content.items.orEmpty().randomOrNull() ?: return@onIo null
        val url = api.universalAudioApi.getUniversalAudioStreamUrl(
            itemId = theme.id,
            container = listOf("opus", "mp3", "aac", "flac")
        )
        url + (if ('?' in url) "&" else "?") + "api_key=" + session().accessToken
    }

    suspend fun nextEpisode(episodeId: UUID): BaseItemDto? = onIo {
        val api = api()
        val userId = session().userUuid

        val currentEp = runCatching { api.userLibraryApi.getItem(episodeId).content }.getOrNull()
            ?: return@onIo null

        val seriesId = currentEp.seriesId ?: return@onIo null

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
            episodes.firstOrNull { it.id != episodeId }
        }
    }

    suspend fun filteredItems(
        kinds: List<BaseItemKind>,
        filter: MediaGridFilter,
        sort: GridSortSpec,
        startIndex: Int,
        limit: Int = MEDIA_GRID_PAGE_SIZE,
        nameLessThan: String? = null
    ): MediaGridPage = onIo {
        val direction = if (sort.ascending) SortOrder.ASCENDING else SortOrder.DESCENDING
        val (sortBy, sortOrder) = when (sort.field) {
            GridSortField.NAME -> listOf(ItemSortBy.SORT_NAME) to listOf(direction)
            GridSortField.RANDOM -> listOf(ItemSortBy.RANDOM) to listOf(direction)
            else -> listOf(sort.field.sortBy, ItemSortBy.SORT_NAME) to
                listOf(direction, SortOrder.ASCENDING)
        }
        val response = api().itemsApi.getItems(
            userId = session().userUuid,
            includeItemTypes = filter.contentType.itemKinds(kinds),
            recursive = true,
            parentId = filter.libraryId,
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
        if (BuildConfig.DEBUG) {
            Log.d(
                LIBRARY_LOG_TAG,
                "grid parent=${filter.libraryId} " +
                    "kinds=${filter.contentType.itemKinds(kinds)} " +
                    "sort=${sortBy.firstOrNull()} start=$startIndex limit=$limit " +
                    "got=${response.items.orEmpty().size} total=${response.totalRecordCount}"
            )
        }
        MediaGridPage(
            items = response.items.orEmpty(),
            totalCount = response.totalRecordCount ?: response.items.orEmpty().size
        )
    }

    suspend fun filteredIndexBeforeLetter(
        kinds: List<BaseItemKind>,
        filter: MediaGridFilter,
        letter: Char
    ): Int {
        if (filter.libraryId == null &&
            filter.genreId == null &&
            kinds != listOf(BaseItemKind.BOX_SET)
        ) {
            return 0
        }
        return filteredItems(
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

    suspend fun gridFilterFacets(
        kinds: List<BaseItemKind>,
        libraryId: UUID? = null
    ): GridFilterFacets = onIo {
        kotlinx.coroutines.coroutineScope {
            val userId = session().userUuid
            val genres = async {
                runCatching {
                    api().genresApi.getGenres(
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
                    api().studiosApi.getStudios(
                        userId = userId,
                        parentId = libraryId,
                        includeItemTypes = kinds
                    ).content.items.orEmpty()
                        .sortedBy { it.name?.lowercase().orEmpty() }
                }.getOrDefault(emptyList())
            }
            val legacyFilters = async {
                runCatching {
                    api().filterApi.getQueryFiltersLegacy(
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

    suspend fun search(
        query: String,
        kind: BaseItemKind,
        limit: Int = SEARCH_ROW_LIMIT
    ): List<BaseItemDto> = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            searchTerm = query,
            includeItemTypes = listOf(kind),
            recursive = true,
            limit = limit,
            fields = GRID_FIELDS,
            enableImageTypes = IMAGE_TYPES
        ).content.items.orEmpty()
    }

    suspend fun searchPersons(
        query: String,
        limit: Int = SEARCH_ROW_LIMIT
    ): List<BaseItemDto> = onIo {
        api().personsApi.getPersons(
            userId = session().userUuid,
            searchTerm = query,
            limit = limit,
            enableImageTypes = IMAGE_TYPES
        ).content.items.orEmpty()
    }

    suspend fun genres(
        libraryId: UUID? = null,
        kinds: List<BaseItemKind> = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
    ): List<BaseItemDto> = onIo {
        api().genresApi.getGenres(
            userId = session().userUuid,
            parentId = libraryId,
            includeItemTypes = kinds,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING)
        ).content.items.orEmpty()
    }

    suspend fun collectionCount(): Int = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            includeItemTypes = listOf(BaseItemKind.BOX_SET),
            recursive = true,
            limit = 0,
            enableTotalRecordCount = true
        ).content.totalRecordCount ?: 0
    }

    suspend fun recentlyWatched(
        libraryId: UUID,
        kinds: List<BaseItemKind>,
        limit: Int
    ): List<BaseItemDto> = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            parentId = libraryId,
            recursive = true,
            includeItemTypes = watchHistoryKinds(kinds),
            isPlayed = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = limit,
            fields = emptyList(),
            imageTypeLimit = 0,
            enableTotalRecordCount = false
        ).content.items.orEmpty()
    }

    suspend fun watchStats(): WatchStats = coroutineScope {
        val movies = async { watchedCount(BaseItemKind.MOVIE) }
        val series = mutableSetOf<UUID>()
        val episodes = collectPages(WATCHED_EPISODE_PAGE_SIZE, ::watchedEpisodePage) { items ->
            items.forEach { item -> item.seriesId?.let(series::add) }
        }
        WatchStats(movies = movies.await(), shows = series.size, episodes = episodes)
    }

    private suspend fun collectPages(
        pageSize: Int,
        fetch: suspend (startIndex: Int) -> BaseItemDtoQueryResult,
        onPage: (List<BaseItemDto>) -> Unit
    ): Int {
        var startIndex = 0
        var total = 0
        while (true) {
            val page = fetch(startIndex)
            val items = page.items.orEmpty()
            onPage(items)
            total = page.totalRecordCount
            startIndex += items.size
            if (items.size < pageSize || startIndex >= total) break
        }
        return total
    }

    private suspend fun watchedCount(kind: BaseItemKind): Int = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            includeItemTypes = listOf(kind),
            recursive = true,
            isPlayed = true,
            limit = 0,
            enableTotalRecordCount = true
        ).content.totalRecordCount
    }

    private suspend fun watchedEpisodePage(startIndex: Int): BaseItemDtoQueryResult = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            includeItemTypes = listOf(BaseItemKind.EPISODE),
            recursive = true,
            isPlayed = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = WATCHED_EPISODE_PAGE_SIZE,
            fields = emptyList(),
            enableImages = false,
            enableUserData = false,
            enableTotalRecordCount = true
        ).content
    }

    suspend fun favoriteItems(): List<BaseItemDto> {
        val favorites = mutableListOf<BaseItemDto>()
        collectPages(MEDIA_GRID_PAGE_SIZE, ::favoritePage) { favorites += it }
        return favorites
    }

    private suspend fun favoritePage(startIndex: Int): BaseItemDtoQueryResult = onIo {
        api().itemsApi.getItems(
            userId = session().userUuid,
            recursive = true,
            includeItemTypes = listOf(
                BaseItemKind.MOVIE,
                BaseItemKind.SERIES,
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                BaseItemKind.BOX_SET,
                BaseItemKind.PLAYLIST
            ),
            isFavorite = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED, ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = MEDIA_GRID_PAGE_SIZE,
            fields = LATEST_FIELDS,
            enableImageTypes = IMAGE_TYPES,
            enableTotalRecordCount = true
        ).content
    }

    private companion object {
        const val LATEST_ROW_LIMIT = 25

        const val SEARCH_ROW_LIMIT = 24

        const val UHD_MIN_WIDTH = 3200

        const val NEXT_EPISODE_LOOKAHEAD = 2

        const val BOX_SET_LIMIT = 500
        const val BOX_SET_FETCH_CONCURRENCY = 4
        const val BOX_SET_CACHE_TTL_MS = 5 * 60 * 1000L

        const val WATCHED_EPISODE_PAGE_SIZE = 2000

        const val ITEM_FETCH_CHUNK = 100
        const val ITEM_FETCH_CONCURRENCY = 4
    }
}
