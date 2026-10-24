package app.picnic.player.data.media

import android.os.SystemClock
import android.util.Log
import app.picnic.player.data.auth.UserScope
import java.time.LocalDate
import java.util.UUID
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

    suspend fun plan(scope: UserScope, userId: UUID, today: LocalDate): DiscoveryPlan {
        val started = SystemClock.elapsedRealtime()
        var action = "build"
        try {
            return saves.withLock {
                val stored = store.load(scope)
                if (stored?.date == today.toString() && stored.version == DISCOVERY_PLAN_VERSION) {
                    action = "load"
                    stored
                } else {
                    val inputs = inputs(today)
                    val built = buildDiscoveryPlan(inputs, Random(userId.hashCode() xor today.toEpochDay().toInt()))
                    store.save(scope, built)
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

    private suspend fun inputs(today: LocalDate): DiscoveryInputs = coroutineScope {
        val played = async {
            mediaRepository.recentlyWatched(null, MIXED_KINDS, PLAYED_LIMIT, SEED_FIELDS)
        }
        val favorites = async {
            mediaRepository.discoveryItems(emptyList(), MIXED_KINDS, ItemSortBy.RANDOM, FAVORITES_LIMIT, isFavorite = true)
        }
        val metadata = async {
            val ids = played.await().map { it.seedId }.distinct()
            mediaRepository.itemsOrThrow(ids, SEED_FIELDS + ItemFields.PEOPLE).associateBy { it.id }
        }
        val movieGenres = async { mediaRepository.genres(kinds = listOf(BaseItemKind.MOVIE)).mapNotNull { it.name }.toSet() }
        val tvGenres = async { mediaRepository.genres(kinds = listOf(BaseItemKind.SERIES)).mapNotNull { it.name }.toSet() }
        DiscoveryInputs(
            date = today.toString(),
            played = played.await(),
            series = metadata.await().filterValues { it.type == BaseItemKind.SERIES },
            favorites = favorites.await(),
            people = metadata.await().mapValues { it.value.people.orEmpty() },
            availableGenres = mapOf(BaseItemKind.MOVIE to movieGenres.await(), BaseItemKind.SERIES to tvGenres.await())
        )
    }

    fun loader(scope: CoroutineScope, plan: DiscoveryPlan, candidates: List<DiscoveryCandidate>, awaitPublication: suspend () -> Unit): DiscoveryItemLoader = DiscoveryItemLoader(
        scope,
        candidates,
        fetchSaved = { mediaRepository.itemsOrThrow(it, DISCOVERY_FIELDS) },
        query = { query(plan, it) },
        awaitPublication = awaitPublication
    )

    suspend fun fill(
        scope: UserScope,
        plan: DiscoveryPlan,
        candidates: List<DiscoveryCandidate>,
        earlier: List<List<BaseItemDto>>,
        loader: DiscoveryItemLoader
    ): Pair<DiscoveryPlan, List<List<BaseItemDto>>> {
        val started = SystemClock.elapsedRealtime()
        try {
            val raw = loader.load(candidates)
            val accepted = earlier.toMutableList()
            val rows = candidates.zip(raw).map { (candidate, items) ->
                rowItems(items, candidate.seedId?.let(UUID::fromString), candidate.seedKey)
                    .takeUnless { overlapsEarlier(it, accepted) }.orEmpty()
                    .also { if (it.isNotEmpty()) accepted += it }
            }
            val updates = candidates.zip(rows).associate { (candidate, row) ->
                candidate.key to candidate.copy(itemIds = row.map { it.id.toString() }, dropped = row.isEmpty())
            }
            if (candidates.all { updates[it.key] == it }) return plan to rows
            val latest = saves.withLock {
                val current = store.load(scope) ?: plan
                check(current.date == plan.date && current.version == plan.version) { "Discovery plan changed during fill" }
                current.copy(candidates = current.candidates.map { updates[it.key] ?: it }).also { store.save(scope, it) }
            }
            return latest to rows
        } finally {
            Log.d(TAG, "fill batch rows=${candidates.size} ${SystemClock.elapsedRealtime() - started}ms")
        }
    }

    private suspend fun query(plan: DiscoveryPlan, candidate: DiscoveryCandidate): List<BaseItemDto> {
        val random = Random((plan.date + candidate.key).hashCode())
        return when (candidate.kind) {
            DiscoveryKind.TOP_PICKS -> sampleDiscoveryItems(
                mediaRepository.discoveryItems(candidate.genres, candidate.type.kinds(), ItemSortBy.RANDOM, TOP_PICKS_LIMIT),
                random
            )
            DiscoveryKind.BECAUSE_WATCHED, DiscoveryKind.BECAUSE_LIKED -> mediaRepository.similarItems(
                UUID.fromString(requireNotNull(candidate.seedId)),
                limit = SIMILAR_LIMIT,
                fields = DISCOVERY_FIELDS
            )
            DiscoveryKind.STARRING, DiscoveryKind.DIRECTED_BY -> mediaRepository.getItemsByPerson(
                personId = UUID.fromString(requireNotNull(candidate.personId)),
                types = MIXED_KINDS,
                personType = if (candidate.kind == DiscoveryKind.STARRING) PersonKind.ACTOR else PersonKind.DIRECTOR,
                limit = PERSON_LIMIT,
                fields = DISCOVERY_FIELDS,
                sortBy = listOf(ItemSortBy.RANDOM)
            ).shuffled(random)
            DiscoveryKind.GENRE -> mediaRepository.discoveryItems(
                candidate.genres,
                candidate.type.kinds(),
                ItemSortBy.RANDOM,
                GENRE_LIMIT
            )
        }
    }

    private companion object {
        const val TAG = "HomeDiscovery"
        const val PLAYED_LIMIT = 120
        const val FAVORITES_LIMIT = 10
        const val TOP_PICKS_LIMIT = 160
        const val SIMILAR_LIMIT = 24
        const val PERSON_LIMIT = 40
        const val GENRE_LIMIT = 40
        val MIXED_KINDS = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
        val SEED_FIELDS = listOf(ItemFields.GENRES, ItemFields.PROVIDER_IDS)
    }
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
        .take(DISCOVERY_SAMPLE_SIZE).shuffled(random)
}

internal class DiscoveryItemLoader(
    scope: CoroutineScope,
    candidates: List<DiscoveryCandidate>,
    fetchSaved: suspend (List<UUID>) -> List<BaseItemDto>,
    private val query: suspend (DiscoveryCandidate) -> List<BaseItemDto>,
    private val awaitPublication: suspend () -> Unit = {}
) {
    private val gate = Semaphore(DISCOVERY_QUERY_CONCURRENCY)
    private val pages = candidates.filterNot { it.dropped }.flatMap { it.itemIds.orEmpty() }.distinct()
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
                        val items = candidate.itemIds.map { pages.getValue(it) }.distinct().awaitAll().flatten()
                        restoreDiscoveryOrder(candidate.itemIds, items)
                    }
                    else -> retryDiscovery { gate.withPermit { query(candidate) } }
                }
            }
        }.awaitAll().also { awaitPublication() }
    }
}

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

private const val DISCOVERY_SAMPLE_SIZE = 12
private const val DISCOVERY_UNWATCHED_TARGET = 9
private const val DISCOVERY_RATED_POOL = 60
private const val DISCOVERY_QUERY_CONCURRENCY = 2
private val DISCOVERY_RETRY_DELAYS = listOf(1_000L, 3_000L)
