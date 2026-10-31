package app.picnic.player.data.media

import app.picnic.player.data.seerr.tmdbIdFromProviderIds
import java.time.LocalDate
import java.util.UUID
import kotlin.math.ceil
import kotlin.random.Random
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind

@Serializable
internal data class DiscoveryPlan(val date: String, val candidates: List<DiscoveryCandidate>, val version: Int = 0)

internal const val DISCOVERY_PLAN_VERSION = 3

@Serializable
internal enum class DiscoveryKind { TOP_PICKS, BECAUSE_WATCHED, BECAUSE_LIKED, STARRING, DIRECTED_BY, GENRE, TOP_RATED }

@Serializable
internal data class DiscoveryCandidate(
    val key: String,
    val kind: DiscoveryKind,
    val type: DiscoveryType,
    val title: String,
    val seedId: String? = null,
    val personId: String? = null,
    val genres: List<String> = emptyList(),
    val itemIds: List<String>? = null,
    val dropped: Boolean = false,
    val seedType: DiscoveryType? = null,
    val seedKey: String? = null
)

internal data class DiscoveryInputs(
    val date: String,
    // History arrives newest first, so its position supplies the recency weight.
    val played: List<BaseItemDto>,
    val series: Map<UUID, BaseItemDto> = emptyMap(),
    val favorites: List<BaseItemDto> = emptyList(),
    val people: Map<UUID, List<BaseItemPerson>> = emptyMap(),
    val availableGenres: Map<BaseItemKind, Set<String>> = emptyMap()
)

internal const val DISCOVERY_TARGET = 21
private const val TOP_PICKS_CAP = 1
private const val TV_STARRING_CAP = 2
private const val OTHER_PEOPLE_CAP = 2
private const val MIXED_GENRE_CAP = 5
private const val WATCHED_CAP = 4
private const val LIKED_CAP = 1
private const val TYPED_GENRE_CAP = 2
private const val TOP_RATED_CAP = 1
private const val LIBRARY_DISCOVERY_TARGET = 16
private const val LIBRARY_PEOPLE_CAP = 4
private const val LIBRARY_GENRE_CAP = 6

// Person cap keys use the seed bucket; person rows themselves always contain mixed items.
internal val DISCOVERY_CAPS = mapOf(
    (DiscoveryKind.TOP_PICKS to DiscoveryType.MIXED) to TOP_PICKS_CAP,
    (DiscoveryKind.TOP_PICKS to DiscoveryType.MOVIE) to TOP_PICKS_CAP,
    (DiscoveryKind.TOP_PICKS to DiscoveryType.TV) to TOP_PICKS_CAP,
    (DiscoveryKind.STARRING to DiscoveryType.TV) to TV_STARRING_CAP,
    (DiscoveryKind.STARRING to DiscoveryType.MIXED) to OTHER_PEOPLE_CAP,
    (DiscoveryKind.GENRE to DiscoveryType.MIXED) to MIXED_GENRE_CAP,
    (DiscoveryKind.BECAUSE_WATCHED to DiscoveryType.MIXED) to WATCHED_CAP,
    (DiscoveryKind.BECAUSE_LIKED to DiscoveryType.MIXED) to LIKED_CAP,
    (DiscoveryKind.GENRE to DiscoveryType.MOVIE) to TYPED_GENRE_CAP,
    (DiscoveryKind.GENRE to DiscoveryType.TV) to TYPED_GENRE_CAP,
    (DiscoveryKind.TOP_RATED to DiscoveryType.MOVIE) to TOP_RATED_CAP,
    (DiscoveryKind.TOP_RATED to DiscoveryType.TV) to TOP_RATED_CAP
)

internal data class DiscoveryProfile(
    val types: List<DiscoveryType>,
    val caps: Map<Pair<DiscoveryKind, DiscoveryType>, Int>,
    val target: Int,
    val untypedTitles: Boolean
) {
    val pinnedPicks: DiscoveryType get() = types.singleOrNull() ?: DiscoveryType.MIXED
}

internal val HOME_DISCOVERY = DiscoveryProfile(DiscoveryType.entries, DISCOVERY_CAPS, DISCOVERY_TARGET, untypedTitles = false)

private fun libraryDiscovery(type: DiscoveryType) = DiscoveryProfile(
    types = listOf(type),
    caps = mapOf(
        (DiscoveryKind.TOP_PICKS to type) to TOP_PICKS_CAP,
        (DiscoveryKind.TOP_RATED to type) to TOP_RATED_CAP,
        (DiscoveryKind.BECAUSE_WATCHED to DiscoveryType.MIXED) to WATCHED_CAP,
        (DiscoveryKind.BECAUSE_LIKED to DiscoveryType.MIXED) to LIKED_CAP,
        // TV seeds only feed Starring; movie seeds feed Starring and Directed by.
        (DiscoveryKind.STARRING to if (type == DiscoveryType.TV) DiscoveryType.TV else DiscoveryType.MIXED) to LIBRARY_PEOPLE_CAP,
        (DiscoveryKind.GENRE to type) to LIBRARY_GENRE_CAP
    ),
    target = LIBRARY_DISCOVERY_TARGET,
    untypedTitles = true
)

internal val MOVIE_LIBRARY_DISCOVERY = libraryDiscovery(DiscoveryType.MOVIE)
internal val TV_LIBRARY_DISCOVERY = libraryDiscovery(DiscoveryType.TV)

// A library holding both movies and series is planned with Home's profile.
internal data class DiscoveryScope(val libraryId: UUID?, val kinds: List<BaseItemKind>) {
    val profile: DiscoveryProfile
        get() = when {
            libraryId == null || (BaseItemKind.MOVIE in kinds && BaseItemKind.SERIES in kinds) -> HOME_DISCOVERY
            BaseItemKind.SERIES in kinds -> TV_LIBRARY_DISCOVERY
            else -> MOVIE_LIBRARY_DISCOVERY
        }

    companion object {
        val HOME = DiscoveryScope(null, listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES))
    }
}

private const val SPARE_FRACTION = 0.3
private const val TOP_GENRE_COUNT = 3
private const val PERSON_POOL_SIZE = 3
internal const val DISCOVERY_ROW_CAP = 40
internal const val DISCOVERY_PAGE_SIZE = 12
private const val ROW_MINIMUM = 5
private const val OVERLAP_PERCENT = 75
private const val PERCENT = 100

private fun DiscoveryCandidate.capKey(): Pair<DiscoveryKind, DiscoveryType> = when (kind) {
    DiscoveryKind.STARRING, DiscoveryKind.DIRECTED_BY ->
        DiscoveryKind.STARRING to
            if (kind == DiscoveryKind.STARRING && seedType == DiscoveryType.TV) DiscoveryType.TV else DiscoveryType.MIXED
    DiscoveryKind.GENRE, DiscoveryKind.TOP_PICKS, DiscoveryKind.TOP_RATED -> kind to type
    else -> kind to DiscoveryType.MIXED
}

internal fun shouldShow(
    candidate: DiscoveryCandidate,
    shown: List<DiscoveryCandidate>,
    profile: DiscoveryProfile = HOME_DISCOVERY
): Boolean = !candidate.dropped &&
    shown.size < profile.target &&
    shown.count { it.capKey() == candidate.capKey() } < (profile.caps[candidate.capKey()] ?: 0)

internal fun nextDiscoveryBatch(
    candidates: List<DiscoveryCandidate>,
    shown: List<DiscoveryCandidate>,
    limit: Int,
    profile: DiscoveryProfile = HOME_DISCOVERY
): List<DiscoveryCandidate> {
    val reserved = shown.toMutableList()
    return candidates.take(limit).takeWhile { candidate ->
        if (!shouldShow(candidate, shown, profile)) {
            true
        } else if (shouldShow(candidate, reserved, profile)) {
            reserved += candidate
            true
        } else {
            false
        }
    }
}

internal fun pendingDiscoveryCandidates(
    candidates: List<DiscoveryCandidate>,
    shown: List<DiscoveryCandidate>,
    profile: DiscoveryProfile = HOME_DISCOVERY
): List<DiscoveryCandidate> {
    val accepted = shown.toMutableList()
    return candidates.filter { candidate -> shouldShow(candidate, accepted, profile).also { if (it) accepted += candidate } }
}

internal fun buildDiscoveryPlan(inputs: DiscoveryInputs, random: Random, profile: DiscoveryProfile = HOME_DISCOVERY): DiscoveryPlan {
    val groups = genreGroups(inputs.availableGenres)
    val history = inputs.played.filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.EPISODE }
    val seeds = uniqueSeeds(history, inputs.series)
    val candidates = mutableListOf<DiscoveryCandidate>()
    val budgets = candidateBudgets(profile)
    val seedWeights = seeds.mapIndexed { index, seed -> seed.seedId to seeds.size - index }.toMap()
    fun seedWeight(seed: BaseItemDto): Int = seedWeights[seed.seedId] ?: 1
    fun genreWeight(group: DiscoveryGenreGroup, type: DiscoveryType): Int = seeds.sumOf { item ->
        val itemType = item.discoveryType()
        if ((type == DiscoveryType.MIXED || type == itemType) &&
            (inputs.series[item.seedId] ?: item).genres.orEmpty().any { genre ->
                group.genres.any { it.equals(genre, ignoreCase = true) }
            }
        ) {
            seedWeight(item)
        } else {
            0
        }
    }
    for (type in profile.types) {
        val eligible = if (type == DiscoveryType.MIXED) groups.values.flatten().distinctBy { it.name } else groups.getValue(type)
        val preferred = eligible.filter { genreWeight(it, type) > 0 }.ifEmpty { eligible }
        val topGenres = preferred.sortedByDescending { genreWeight(it, type) }
            .take(TOP_GENRE_COUNT).flatMap { it.genres }.distinct()
        if (topGenres.isEmpty()) continue
        val title = when (type) {
            DiscoveryType.MIXED -> "Top picks for you"
            DiscoveryType.MOVIE -> "Movies we think you'll like"
            DiscoveryType.TV -> "Shows we think you'll like"
        }
        candidates += DiscoveryCandidate("top-picks:${type.name}", DiscoveryKind.TOP_PICKS, type, title, genres = topGenres)
    }
    for (type in profile.types.filter { it != DiscoveryType.MIXED }) {
        val title = when {
            profile.untypedTitles -> "Top rated"
            type == DiscoveryType.MOVIE -> "Top rated movies"
            else -> "Top rated shows"
        }
        candidates += DiscoveryCandidate("top-rated:${type.name}", DiscoveryKind.TOP_RATED, type, title)
    }
    fun addSimilar(pool: List<BaseItemDto>, kind: DiscoveryKind, prefix: String) {
        val limit = budgets[kind to DiscoveryType.MIXED] ?: 0
        weightedOrder(pool, random, ::seedWeight)
            .take(limit).forEach { seed ->
                candidates += DiscoveryCandidate(
                    "${kind.name}:${seed.seedId}",
                    kind,
                    seed.discoveryType(),
                    "$prefix ${seed.seedName}",
                    seedId = seed.seedId.toString(),
                    seedType = seed.discoveryType(),
                    seedKey = seed.discoverySeed(inputs.series).discoveryItemKey()
                )
            }
    }
    addSimilar(seeds, DiscoveryKind.BECAUSE_WATCHED, "Because you watched")
    addSimilar(uniqueSeeds(inputs.favorites, inputs.series), DiscoveryKind.BECAUSE_LIKED, "Because you liked")
    val usedPeople = mutableSetOf<UUID>()
    fun addPeople(tvStarring: Boolean) {
        val bucket = DiscoveryKind.STARRING to if (tvStarring) DiscoveryType.TV else DiscoveryType.MIXED
        var remaining = budgets[bucket] ?: 0
        val eligible = seeds.filter { !tvStarring || it.discoveryType() == DiscoveryType.TV }
        for (seed in weightedOrder(eligible, random, ::seedWeight)) {
            val kinds = if (tvStarring) listOf(PersonKind.ACTOR) else listOf(PersonKind.ACTOR, PersonKind.DIRECTOR).shuffled(random)
            for (personKind in kinds) {
                if (remaining == 0) return
                if (!tvStarring && seed.discoveryType() == DiscoveryType.TV && personKind == PersonKind.ACTOR) continue
                val person = inputs.people[seed.seedId].orEmpty().filter { it.type == personKind }
                    .take(PERSON_POOL_SIZE).filter { it.id !in usedPeople && !it.name.isNullOrBlank() }.randomOrNull(random) ?: continue
                usedPeople += person.id
                val kind = if (personKind == PersonKind.ACTOR) DiscoveryKind.STARRING else DiscoveryKind.DIRECTED_BY
                val prefix = if (kind == DiscoveryKind.STARRING) "Starring" else "Directed by"
                candidates += DiscoveryCandidate(
                    "person:${person.id}",
                    kind,
                    DiscoveryType.MIXED,
                    "$prefix ${person.name}",
                    seedId = seed.seedId.toString(),
                    personId = person.id.toString(),
                    seedType = seed.discoveryType(),
                    seedKey = seed.discoverySeed(inputs.series).discoveryItemKey()
                )
                remaining--
            }
        }
    }
    addPeople(tvStarring = true)
    addPeople(tvStarring = false)
    for (type in profile.types) {
        weightedOrder(groups.getValue(type), random) { genreWeight(it, type) }
            .take(budgets[DiscoveryKind.GENRE to type] ?: 0).forEach { group ->
                candidates += DiscoveryCandidate(
                    "genre:${type.name}:${group.name}",
                    DiscoveryKind.GENRE,
                    type,
                    if (profile.untypedTitles) group.mixedTitle ?: group.name else requireNotNull(group.title(type)),
                    genres = group.genres
                )
            }
    }
    val (top, rest) = candidates.partition { it.kind == DiscoveryKind.TOP_PICKS && it.type == profile.pinnedPicks }
    return DiscoveryPlan(inputs.date, top + shuffleRows(rest, random), DISCOVERY_PLAN_VERSION)
}

private fun candidateBudgets(profile: DiscoveryProfile): Map<Pair<DiscoveryKind, DiscoveryType>, Int> {
    val budgets = profile.caps.toMutableMap()
    val extra = ceil(profile.target * SPARE_FRACTION).toInt()
    val eligible = profile.caps.filterKeys { it.first != DiscoveryKind.TOP_PICKS && it.first != DiscoveryKind.TOP_RATED }
    val additions = eligible.mapValues { (_, cap) -> (cap * SPARE_FRACTION).toInt() }
    for ((key, count) in additions) budgets[key] = budgets.getValue(key) + count
    eligible.keys.sortedByDescending { eligible.getValue(it) * SPARE_FRACTION - additions.getValue(it) }
        .take(extra - additions.values.sum()).forEach { budgets[it] = budgets.getValue(it) + 1 }
    return budgets
}

private fun BaseItemDto.discoveryType(): DiscoveryType = if (type == BaseItemKind.MOVIE) DiscoveryType.MOVIE else DiscoveryType.TV

private fun BaseItemDto.discoverySeed(series: Map<UUID, BaseItemDto>): BaseItemDto = series[seedId]
    ?: if (type == BaseItemKind.EPISODE) copy(type = BaseItemKind.SERIES, name = seriesName, productionYear = null, providerIds = null) else this

private fun uniqueSeeds(pool: List<BaseItemDto>, series: Map<UUID, BaseItemDto>): List<BaseItemDto> {
    val ids = mutableSetOf<UUID>()
    val keys = mutableSetOf<String>()
    return pool.filter { seed ->
        !seed.seedName.isNullOrBlank() && ids.add(seed.seedId) && keys.add(seed.discoverySeed(series).discoveryItemKey())
    }
}

private fun <T> weightedOrder(items: List<T>, random: Random, weight: (T) -> Int): List<T> {
    val remaining = items.toMutableList()
    return buildList {
        while (remaining.isNotEmpty()) {
            var draw = random.nextInt(remaining.sumOf { weight(it).coerceAtLeast(1) })
            val index = remaining.indexOfFirst {
                draw -= weight(it).coerceAtLeast(1)
                draw < 0
            }
            add(remaining.removeAt(index))
        }
    }
}

private fun DiscoveryCandidate.orderKind(): DiscoveryKind = when (kind) {
    DiscoveryKind.DIRECTED_BY -> DiscoveryKind.STARRING
    else -> kind
}

private fun shuffleRows(candidates: List<DiscoveryCandidate>, random: Random): List<DiscoveryCandidate> {
    val remaining = candidates.shuffled(random).groupBy { it.orderKind() }.mapValues { it.value.toMutableList() }.toMutableMap()
    return buildList {
        while (remaining.isNotEmpty()) {
            val eligible = remaining.keys.filter { it != lastOrNull()?.orderKind() }.ifEmpty { remaining.keys.toList() }
            val kind = eligible.shuffled(random).maxBy { remaining.getValue(it).size }
            val rows = remaining.getValue(kind)
            add(rows.removeAt(rows.lastIndex))
            if (rows.isEmpty()) remaining.remove(kind)
        }
    }
}

internal fun BaseItemDto.discoveryItemKey(): String {
    val identity = tmdbIdFromProviderIds(providerIds)?.let { "tmdb:$it" }
        ?: "name:${name.orEmpty().trim().lowercase()}:$productionYear"
    return "${discoveryType().name}:$identity"
}

internal fun rowItems(raw: List<BaseItemDto>, seedId: UUID?, seedKey: String? = null): List<BaseItemDto> = raw
    .filter { it.id != seedId && (seedKey == null || it.discoveryItemKey() != seedKey) }.distinctBy { it.discoveryItemKey() }.take(DISCOVERY_ROW_CAP)
    .takeIf { it.size >= ROW_MINIMUM }.orEmpty()

internal fun overlapsEarlier(row: List<BaseItemDto>, earlier: List<List<BaseItemDto>>): Boolean {
    val keys = row.map { it.discoveryItemKey() }.toSet()
    return keys.isNotEmpty() &&
        earlier.any { previous ->
            val previousKeys = previous.map { it.discoveryItemKey() }.toSet()
            keys.count { it in previousKeys } * PERCENT >= keys.size * OVERLAP_PERCENT
        }
}

internal fun shouldRenewDiscovery(plan: DiscoveryPlan, today: LocalDate, focusedDiscovery: Boolean): Boolean = plan.date != today.toString() && !focusedDiscovery
