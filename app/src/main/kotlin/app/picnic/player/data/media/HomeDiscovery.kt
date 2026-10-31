package app.picnic.player.data.media

import android.os.SystemClock
import android.util.Log
import app.picnic.player.data.auth.UserScope
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind

@Singleton
internal class HomeDiscovery @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val store: DiscoveryStore
) {
    private val saves = Mutex()
    private val folderInLibrary = ConcurrentHashMap<Pair<UUID, UUID>, Boolean>()

    suspend fun plan(scope: UserScope, userId: UUID, today: LocalDate, target: DiscoveryScope = DiscoveryScope.HOME): DiscoveryPlan {
        val started = SystemClock.elapsedRealtime()
        var action = "build"
        try {
            return saves.withLock {
                val stored = store.load(scope, target.libraryId)
                if (stored?.date == today.toString() && stored.version == DISCOVERY_PLAN_VERSION) {
                    action = "load"
                    stored
                } else {
                    val inputs = inputs(today, target)
                    val seed = userId.hashCode() xor today.toEpochDay().toInt() xor (target.libraryId?.hashCode() ?: 0)
                    val built = buildDiscoveryPlan(inputs, Random(seed), target.profile)
                    store.save(scope, built, target.libraryId)
                    built
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "plan $action failed", failure)
            throw failure
        } finally {
            Log.d(TAG, "plan $action ${SystemClock.elapsedRealtime() - started}ms")
        }
    }

    private suspend fun inputs(today: LocalDate, target: DiscoveryScope): DiscoveryInputs = coroutineScope {
        val played = async {
            mediaRepository.recentlyWatched(target.libraryId, target.kinds, PLAYED_LIMIT, SEED_FIELDS)
        }
        val favorites = async {
            mediaRepository.discoveryItems(
                emptyList(),
                target.kinds,
                ItemSortBy.RANDOM,
                FAVORITES_LIMIT,
                isFavorite = true,
                parentId = target.libraryId
            )
        }
        val metadata = async {
            val ids = played.await().map { it.seedId }.distinct()
            mediaRepository.itemsOrThrow(ids, SEED_FIELDS + ItemFields.PEOPLE).associateBy { it.id }
        }
        val genres = target.kinds.associateWith { kind ->
            async { mediaRepository.genres(target.libraryId, listOf(kind)).mapNotNull { it.name }.toSet() }
        }
        DiscoveryInputs(
            date = today.toString(),
            played = played.await(),
            series = metadata.await().filterValues { it.type == BaseItemKind.SERIES },
            favorites = favorites.await(),
            people = metadata.await().mapValues { it.value.people.orEmpty() },
            availableGenres = genres.mapValues { it.value.await() }
        )
    }

    fun loader(
        scope: CoroutineScope,
        plan: DiscoveryPlan,
        candidates: List<DiscoveryCandidate>,
        target: DiscoveryScope,
        awaitPublication: suspend () -> Unit
    ): DiscoveryItemLoader = DiscoveryItemLoader(
        scope,
        candidates,
        fetchSaved = { mediaRepository.itemsOrThrow(it, DISCOVERY_FIELDS) },
        query = { query(plan, it, target) },
        awaitPublication = awaitPublication
    )

    suspend fun fill(
        scope: UserScope,
        plan: DiscoveryPlan,
        candidates: List<DiscoveryCandidate>,
        earlier: List<List<BaseItemDto>>,
        loader: DiscoveryItemLoader,
        target: DiscoveryScope
    ): Pair<DiscoveryPlan, List<List<BaseItemDto>>> {
        val started = SystemClock.elapsedRealtime()
        try {
            val (updated, firstPages) = acceptDiscoveryRows(candidates, loader.load(candidates), earlier)
            val updates = updated.associateBy { it.key }
            if (candidates.all { updates[it.key] == it }) return plan to firstPages
            val latest = saves.withLock {
                val current = store.load(scope, target.libraryId) ?: plan
                check(current.date == plan.date && current.version == plan.version) { "Discovery plan changed during fill" }
                current.copy(candidates = current.candidates.map { updates[it.key] ?: it }).also { store.save(scope, it, target.libraryId) }
            }
            return latest to firstPages
        } finally {
            Log.d(TAG, "fill batch rows=${candidates.size} ${SystemClock.elapsedRealtime() - started}ms")
        }
    }

    private suspend fun query(plan: DiscoveryPlan, candidate: DiscoveryCandidate, target: DiscoveryScope): List<BaseItemDto> {
        val random = Random((plan.date + candidate.key).hashCode())
        val library = target.libraryId
        return when (candidate.kind) {
            DiscoveryKind.TOP_PICKS -> sampleDiscoveryItems(
                mediaRepository.discoveryItems(candidate.genres, candidate.type.kinds(), ItemSortBy.RANDOM, TOP_PICKS_LIMIT, parentId = library),
                random
            )
            DiscoveryKind.TOP_RATED -> mediaRepository.discoveryItems(
                emptyList(),
                candidate.type.kinds(),
                ItemSortBy.RANDOM,
                DISCOVERY_ROW_CAP,
                parentId = library,
                minCommunityRating = TOP_RATED_MINIMUM
            )
            DiscoveryKind.BECAUSE_WATCHED, DiscoveryKind.BECAUSE_LIKED -> {
                val similar = mediaRepository.similarItems(
                    UUID.fromString(requireNotNull(candidate.seedId)),
                    limit = SIMILAR_LIMIT,
                    fields = if (library == null) DISCOVERY_FIELDS else DISCOVERY_FIELDS + ItemFields.PARENT_ID
                )
                if (library == null) similar else inLibrary(similar, library)
            }
            DiscoveryKind.STARRING, DiscoveryKind.DIRECTED_BY -> mediaRepository.getItemsByPerson(
                personId = UUID.fromString(requireNotNull(candidate.personId)),
                types = target.kinds,
                personType = if (candidate.kind == DiscoveryKind.STARRING) PersonKind.ACTOR else PersonKind.DIRECTOR,
                limit = PERSON_LIMIT,
                fields = DISCOVERY_FIELDS,
                sortBy = listOf(ItemSortBy.RANDOM),
                parentId = library
            ).shuffled(random)
            DiscoveryKind.GENRE -> mediaRepository.discoveryItems(
                candidate.genres,
                candidate.type.kinds(),
                ItemSortBy.RANDOM,
                GENRE_LIMIT,
                parentId = library
            )
            DiscoveryKind.DECADE -> {
                val decade = requireNotNull(candidate.decade)
                mediaRepository.discoveryItems(
                    emptyList(),
                    candidate.type.kinds(),
                    ItemSortBy.RANDOM,
                    GENRE_LIMIT,
                    parentId = library,
                    years = (decade until decade + DECADE_YEARS).toList()
                )
            }
            DiscoveryKind.STUDIO -> mediaRepository.discoveryItems(
                emptyList(),
                candidate.type.kinds(),
                ItemSortBy.COMMUNITY_RATING,
                STUDIO_POOL,
                parentId = library,
                studioIds = listOf(UUID.fromString(requireNotNull(candidate.studioId)))
            ).shuffled(random)
            // The server ignores minCriticRating, so the best-rated pool is fetched and filtered here.
            DiscoveryKind.CRITIC -> mediaRepository.discoveryItems(
                emptyList(),
                candidate.type.kinds(),
                ItemSortBy.CRITIC_RATING,
                CRITIC_POOL,
                parentId = library
            ).filter { (it.criticRating ?: 0f) >= CRITIC_MINIMUM }.shuffled(random)
            DiscoveryKind.WATCH_AGAIN -> error("Watch again is built with its item ids")
        }
    }

    // The similar endpoint has no library filter, and /Items ignores parentId when ids are given.
    private suspend fun inLibrary(items: List<BaseItemDto>, libraryId: UUID): List<BaseItemDto> = keepInLibrary(items, libraryId) { folder, member ->
        folderInLibrary.getOrPut(folder to libraryId) {
            mediaRepository.folderAncestors(member).any { it.id == libraryId }
        }
    }

    private companion object {
        const val TAG = "HomeDiscovery"
        const val PLAYED_LIMIT = 120
        const val FAVORITES_LIMIT = 10
        const val TOP_PICKS_LIMIT = 160
        const val SIMILAR_LIMIT = DISCOVERY_ROW_CAP
        const val PERSON_LIMIT = 40
        const val GENRE_LIMIT = 40
        const val TOP_RATED_MINIMUM = 8.0
        const val STUDIO_POOL = 60
        const val CRITIC_POOL = 300
        const val CRITIC_MINIMUM = 80f
        val SEED_FIELDS = listOf(ItemFields.GENRES, ItemFields.PROVIDER_IDS, ItemFields.STUDIOS)
    }
}

internal fun acceptDiscoveryRows(
    candidates: List<DiscoveryCandidate>,
    raw: List<List<BaseItemDto>>,
    earlier: List<List<BaseItemDto>>
): Pair<List<DiscoveryCandidate>, List<List<BaseItemDto>>> {
    val accepted = earlier.map { it.take(DISCOVERY_PAGE_SIZE) }.toMutableList()
    val rows = candidates.zip(raw).map { (candidate, items) ->
        val row = rowItems(items, candidate.seedId?.let(UUID::fromString), candidate.seedKey)
        val firstPage = row.take(DISCOVERY_PAGE_SIZE)
        when {
            row.isEmpty() || overlapsEarlier(firstPage, accepted) -> emptyList()
            else -> row.also { accepted += firstPage }
        }
    }
    val updated = candidates.zip(rows).map { (candidate, row) ->
        // A saved row arrives as its first page only, so its full id list stays as saved.
        val ids = candidate.itemIds?.takeIf { row.isNotEmpty() } ?: row.map { it.id.toString() }
        candidate.copy(itemIds = ids, dropped = row.isEmpty())
    }
    return updated to rows.map { it.take(DISCOVERY_PAGE_SIZE) }
}

/** Items without a parent folder are dropped: their library cannot be proven. */
internal suspend fun keepInLibrary(
    items: List<BaseItemDto>,
    libraryId: UUID,
    folderInLibrary: suspend (folderId: UUID, memberId: UUID) -> Boolean
): List<BaseItemDto> {
    val kept = items.filter { it.parentId != null }.groupBy { requireNotNull(it.parentId) }
        .filter { (folder, members) -> folder == libraryId || folderInLibrary(folder, members.first().id) }.keys
    return items.filter { it.parentId in kept }
}

internal fun restoreDiscoveryOrder(ids: List<String>, items: List<BaseItemDto>): List<BaseItemDto> {
    val byId = items.associateBy { it.id.toString() }
    return ids.mapNotNull { byId[it] }
}

internal fun sampleDiscoveryItems(items: List<BaseItemDto>, random: Random): List<BaseItemDto> {
    val unique = items.sortedBy { it.userData?.played == true }.distinctBy { it.discoveryItemKey() }
    val (watched, unwatched) = unique.partition { it.userData?.played == true }
    fun sample(pool: List<BaseItemDto>) = pool.shuffled(random)
        .sortedByDescending { it.communityRating ?: 0f }.take(DISCOVERY_RATED_POOL).shuffled(random)
    val fresh = sample(unwatched)
    val familiar = sample(watched)
    return (fresh.take(DISCOVERY_UNWATCHED_TARGET) + familiar + fresh.drop(DISCOVERY_UNWATCHED_TARGET))
        .take(DISCOVERY_ROW_CAP).shuffled(random)
}

internal class DiscoveryItemLoader(
    scope: CoroutineScope,
    candidates: List<DiscoveryCandidate>,
    fetchSaved: suspend (List<UUID>) -> List<BaseItemDto>,
    private val query: suspend (DiscoveryCandidate) -> List<BaseItemDto>,
    private val awaitPublication: suspend () -> Unit = {}
) {
    private val gate = Semaphore(DISCOVERY_QUERY_CONCURRENCY)
    private val pages = candidates.filterNot { it.dropped }.flatMap { it.firstPageIds() }.distinct()
        .chunked(ITEM_FETCH_CHUNK).flatMap { ids ->
            val page = scope.async {
                retryDiscovery { gate.withPermit { fetchSaved(ids.map(UUID::fromString)) } }
            }
            ids.map { it to page }
        }.toMap()

    suspend fun load(candidates: List<DiscoveryCandidate>): List<List<BaseItemDto>> = coroutineScope {
        candidates.map { candidate ->
            async {
                when {
                    candidate.dropped -> emptyList()
                    candidate.itemIds != null -> {
                        val ids = candidate.firstPageIds()
                        val items = ids.map { pages.getValue(it) }.distinct().awaitAll().flatten()
                        restoreDiscoveryOrder(ids, items)
                    }
                    else -> retryDiscovery { gate.withPermit { query(candidate) } }
                }
            }
        }.awaitAll().also { awaitPublication() }
    }
}

private fun DiscoveryCandidate.firstPageIds(): List<String> = itemIds.orEmpty().take(DISCOVERY_PAGE_SIZE)

internal suspend fun <T> retryDiscovery(block: suspend () -> T): T {
    for (delayMs in DISCOVERY_RETRY_DELAYS) {
        try {
            return block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            delay(delayMs)
        }
    }
    return block()
}

private fun DiscoveryType.kinds(): List<BaseItemKind> = when (this) {
    DiscoveryType.MOVIE -> listOf(BaseItemKind.MOVIE)
    DiscoveryType.TV -> listOf(BaseItemKind.SERIES)
    DiscoveryType.MIXED -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
}

private const val DISCOVERY_UNWATCHED_TARGET = 30
private const val DISCOVERY_RATED_POOL = 60
private const val DISCOVERY_QUERY_CONCURRENCY = 2
private val DISCOVERY_RETRY_DELAYS = listOf(1_000L, 3_000L)
