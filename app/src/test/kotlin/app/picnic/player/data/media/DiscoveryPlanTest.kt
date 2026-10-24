package app.picnic.player.data.media

import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPlanTest {
    private fun id(number: Int) = UUID(0, number.toLong())

    private fun movie(number: Int, name: String = "Movie $number", tmdb: Int? = number, genres: List<String> = listOf("Drama")) = BaseItemDto(
        id = id(number),
        name = name,
        type = BaseItemKind.MOVIE,
        productionYear = 2000,
        providerIds = tmdb?.let { mapOf("Tmdb" to it.toString()) },
        genres = genres
    )

    private fun episode(number: Int, series: Int, name: String = "Series $series") = BaseItemDto(
        id = id(number),
        name = "Episode $number",
        type = BaseItemKind.EPISODE,
        seriesId = id(series),
        seriesName = name
    )

    private fun fullInputs(): DiscoveryInputs {
        val genres = listOf("Action", "Fantasy", "Family", "Horror", "History", "Romance", "Animation", "Documentary", "Comedy", "Drama", "Mystery", "Western")
        val movies = (1..20).map { movie(it, genres = listOf(genres[it % genres.size])) }
        val episodes = (1..20).map { episode(100 + it, 200 + it) }
        val series = (1..20).associate { number ->
            id(200 + number) to BaseItemDto(
                id = id(200 + number),
                type = BaseItemKind.SERIES,
                genres = listOf(genres[number % genres.size]),
                providerIds = mapOf("Tmdb" to (200 + number).toString())
            )
        }
        val people = (movies + episodes).associate { seed ->
            seed.seedId to listOf(
                BaseItemPerson(id = id(seed.seedId.leastSignificantBits.toInt() + 1000), name = "Actor ${seed.seedId}", type = PersonKind.ACTOR),
                BaseItemPerson(id = id(seed.seedId.leastSignificantBits.toInt() + 2000), name = "Director ${seed.seedId}", type = PersonKind.DIRECTOR)
            )
        }
        return DiscoveryInputs(
            date = "2026-10-24",
            played = movies + episodes,
            series = series,
            favorites = movies,
            people = people,
            availableGenres = mapOf(BaseItemKind.MOVIE to genres.toSet(), BaseItemKind.SERIES to genres.toSet())
        )
    }

    private fun candidate(kind: DiscoveryKind, type: DiscoveryType = DiscoveryType.MIXED, seedType: DiscoveryType? = null) = DiscoveryCandidate("test", kind, type, "Test", seedType = seedType)

    @Test
    fun candidatesIncludeProportionalSparesBeyondDisplayedCaps() {
        val candidates = buildDiscoveryPlan(fullInputs(), Random(42)).candidates
        assertEquals(28, candidates.size)
        assertEquals(candidates.size, candidates.map { it.key }.distinct().size)
        assertTrue(candidates.count { it.kind == DiscoveryKind.BECAUSE_WATCHED } > 4)
        for (type in DiscoveryType.entries) {
            val cap = DISCOVERY_CAPS.getValue(DiscoveryKind.GENRE to type)
            assertTrue(candidates.count { it.kind == DiscoveryKind.GENRE && it.type == type } > cap)
        }
        assertEquals(3, candidates.count { it.kind == DiscoveryKind.TOP_PICKS })
    }

    @Test
    fun shouldShowEnforcesEveryCapAndCombinedSeedTypes() {
        assertEquals(DISCOVERY_TARGET, DISCOVERY_CAPS.values.sum())
        for ((bucket, cap) in DISCOVERY_CAPS) {
            val (kind, type) = bucket
            val row = when {
                kind == DiscoveryKind.STARRING && type == DiscoveryType.TV -> candidate(kind, seedType = DiscoveryType.TV)
                kind == DiscoveryKind.STARRING -> candidate(DiscoveryKind.DIRECTED_BY, seedType = DiscoveryType.TV)
                kind == DiscoveryKind.GENRE || kind == DiscoveryKind.TOP_PICKS -> candidate(kind, type)
                else -> candidate(kind)
            }
            assertTrue(shouldShow(row, List(cap - 1) { row }))
            assertFalse(shouldShow(row, List(cap) { row }))
        }
        val watchedMovie = candidate(DiscoveryKind.BECAUSE_WATCHED, DiscoveryType.MOVIE, DiscoveryType.MOVIE)
        val watchedTv = candidate(DiscoveryKind.BECAUSE_WATCHED, DiscoveryType.TV, DiscoveryType.TV)
        assertFalse(shouldShow(watchedTv, List(4) { watchedMovie }))
        val director = candidate(DiscoveryKind.DIRECTED_BY, seedType = DiscoveryType.TV)
        val actor = candidate(DiscoveryKind.STARRING, seedType = DiscoveryType.MOVIE)
        assertFalse(shouldShow(actor, List(2) { director }))
        assertTrue(shouldShow(candidate(DiscoveryKind.STARRING, seedType = DiscoveryType.TV), List(2) { director }))
    }

    @Test
    fun shouldShowStopsAtTotalTargetAndRejectsDroppedRows() {
        val row = candidate(DiscoveryKind.BECAUSE_LIKED)
        assertFalse(shouldShow(row, List(DISCOVERY_TARGET) { candidate(DiscoveryKind.GENRE) }))
        assertFalse(shouldShow(row.copy(dropped = true), emptyList()))
    }

    @Test
    fun displayedPlanReachesTargetAndReservesTwoTvStarringRows() {
        val shown = mutableListOf<DiscoveryCandidate>()
        buildDiscoveryPlan(fullInputs(), Random(42)).candidates.forEach { if (shouldShow(it, shown)) shown += it }
        assertEquals(DISCOVERY_TARGET, shown.size)
        assertEquals(2, shown.count { it.kind == DiscoveryKind.STARRING && it.seedType == DiscoveryType.TV })
        for (type in DiscoveryType.entries) {
            assertTrue(shown.count { it.kind == DiscoveryKind.GENRE && it.type == type } >= 2)
        }
        assertTrue(shown.filter { it.personId != null }.all { it.type == DiscoveryType.MIXED })
    }

    @Test
    fun genreGuaranteeSurvivesStronglySkewedHistory() {
        val inputs = fullInputs().copy(played = (1..100).map { movie(it, genres = listOf("Drama")) })
        val rows = buildDiscoveryPlan(inputs, Random(9)).candidates
        for (type in DiscoveryType.entries) {
            assertTrue(rows.count { it.kind == DiscoveryKind.GENRE && it.type == type } >= 2)
        }
    }

    @Test
    fun topPicksLeadAndUseHighestWeightGroupsIncludingEpisodeSeries() {
        val inputs = DiscoveryInputs(
            date = "2026-10-24",
            played = listOf(movie(1, genres = listOf("Comedy")), episode(2, 30), episode(3, 30), episode(4, 30)),
            series = mapOf(id(30) to BaseItemDto(id = id(30), type = BaseItemKind.SERIES, genres = listOf("Drama"))),
            availableGenres = mapOf(BaseItemKind.MOVIE to setOf("Comedy", "Drama", "Action", "Horror"), BaseItemKind.SERIES to setOf("Drama"))
        )
        val top = buildDiscoveryPlan(inputs, Random(42)).candidates.first()
        assertEquals(DiscoveryKind.TOP_PICKS, top.kind)
        assertEquals("Top picks for you", top.title)
        assertEquals(listOf("Comedy", "Drama"), top.genres)
    }

    private fun orderKind(row: DiscoveryCandidate) = if (row.kind == DiscoveryKind.DIRECTED_BY) DiscoveryKind.STARRING else row.kind

    @Test
    fun shuffledRowsNeverRepeatKindsWhenAvoidable() {
        for (seed in 0..40) {
            val rows = buildDiscoveryPlan(fullInputs(), Random(seed)).candidates
            assertEquals(DiscoveryKind.TOP_PICKS, rows.first().kind)
            assertTrue(rows.drop(1).zipWithNext().all { (left, right) -> orderKind(left) != orderKind(right) })
        }
    }

    @Test
    fun unavoidableNeighborsAreKeptToMinimum() {
        val inputs = fullInputs().copy(played = emptyList(), favorites = emptyList(), people = emptyMap())
        val rows = buildDiscoveryPlan(inputs, Random(2)).candidates
        assertEquals(DiscoveryKind.TOP_PICKS, rows.first().kind)
        assertTrue(rows.drop(1).all { it.kind == DiscoveryKind.GENRE || it.kind == DiscoveryKind.TOP_PICKS })
    }

    @Test
    fun sameInputsAndRandomSeedProduceSameSerializablePlan() {
        val inputs = fullInputs()
        val plan = buildDiscoveryPlan(inputs, Random(42))
        assertEquals(plan, buildDiscoveryPlan(inputs, Random(42)))
        assertEquals(plan, Json.decodeFromString<DiscoveryPlan>(Json.encodeToString(plan)))
        assertTrue(plan != buildDiscoveryPlan(inputs, Random(43)))
    }

    @Test
    fun watchSeedsDeduplicateTmdbCopiesAndEpisodeSeriesNames() {
        val played = listOf(
            movie(1),
            movie(2, tmdb = 1),
            episode(3, 30, "The Show"),
            episode(4, 40, "The Show"),
            episode(5, 50, "Other Show"),
            episode(6, 60, "Renamed Show")
        )
        val series = mapOf(
            id(50) to BaseItemDto(id = id(50), type = BaseItemKind.SERIES, providerIds = mapOf("tmdb" to "500")),
            id(60) to BaseItemDto(id = id(60), type = BaseItemKind.SERIES, providerIds = mapOf("TMDB" to "500"))
        )
        val rows = buildDiscoveryPlan(DiscoveryInputs("2026-10-24", played, series), Random(1)).candidates
            .filter { it.kind == DiscoveryKind.BECAUSE_WATCHED }
        assertEquals(setOf(id(1).toString(), id(30).toString(), id(50).toString()), rows.map { it.seedId }.toSet())
        assertTrue(rows.all { it.seedType == it.type })
        assertEquals("TV:tmdb:500", rows.single { it.seedId == id(50).toString() }.seedKey)
    }

    @Test
    fun personRowsCarryTheirSeedKeySoOtherCopiesAreRemoved() {
        val inputs = fullInputs()
        val movies = inputs.played.filter { it.type == BaseItemKind.MOVIE }.associateBy { it.id.toString() }
        val people = buildDiscoveryPlan(inputs, Random(1)).candidates
            .filter { it.kind == DiscoveryKind.STARRING || it.kind == DiscoveryKind.DIRECTED_BY }
        assertTrue(people.isNotEmpty() && people.all { it.seedKey != null })
        people.forEach { candidate -> movies[candidate.seedId]?.let { assertEquals(it.discoveryItemKey(), candidate.seedKey) } }
    }

    @Test
    fun personSelectionUsesFirstThreeAndNeverRepeatsPerson() {
        val inputs = fullInputs()
        val people = inputs.people.mapValues { (_, list) ->
            listOf(
                BaseItemPerson(id = id(9000), name = "Shared", type = PersonKind.ACTOR)
            ) + list + listOf(
                BaseItemPerson(id = id(9001), name = "Third", type = PersonKind.ACTOR),
                BaseItemPerson(id = id(9002), name = "Fourth", type = PersonKind.ACTOR)
            )
        }
        val rows = buildDiscoveryPlan(inputs.copy(people = people), Random(4)).candidates.filter { it.personId != null }
        assertEquals(rows.size, rows.map { it.personId }.distinct().size)
        assertFalse(rows.any { it.personId == id(9002).toString() })
    }

    @Test
    fun emptyHistoryAndFavoritesProduceNoSeedRows() {
        assertTrue(buildDiscoveryPlan(DiscoveryInputs("2026-10-24", emptyList()), Random(1)).candidates.isEmpty())
        assertFalse(buildDiscoveryPlan(fullInputs().copy(favorites = emptyList()), Random(1)).candidates.any { it.kind == DiscoveryKind.BECAUSE_LIKED })
    }

    @Test
    fun rowItemsRemoveSeedKeepFirstCopyAndCapAtTwelve() {
        val first = movie(2, "HD")
        val duplicate = movie(3, "4K", tmdb = 2)
        val raw = listOf(movie(1), first, duplicate) + (4..20).map { movie(it) }
        val row = rowItems(raw, id(1))
        assertEquals(12, row.size)
        assertEquals(first, row.first())
        assertFalse(row.any { it.id == id(1) || it.id == duplicate.id })
    }

    @Test
    fun rowItemsRemoveOtherCopiesOfTheSeed() {
        val seedCopy = movie(2, "4K", tmdb = 1)
        val raw = listOf(seedCopy) + (3..8).map { movie(it) }
        val row = rowItems(raw, id(1), movie(1).discoveryItemKey())
        assertFalse(row.any { it.id == seedCopy.id })
        assertEquals((3..8).map { movie(it) }, row)
    }

    @Test
    fun rowItemsFallbackToNameAndYearAndDropUnderFive() {
        val first = movie(1, "A Movie", tmdb = null)
        val duplicate = movie(2, "A Movie", tmdb = null)
        val remake = movie(3, "A Movie", tmdb = null).copy(productionYear = 2020)
        assertTrue(rowItems(listOf(first, duplicate) + (4..6).map { movie(it) }, null).isEmpty())
        val row = rowItems(listOf(first, duplicate, remake) + (4..6).map { movie(it) }, null)
        assertEquals(listOf(first, remake) + (4..6).map { movie(it) }, row)
    }

    @Test
    fun overlapRequiresSeventyFivePercentInOneEarlierRow() {
        val row = (1..8).map { movie(it) }
        val copies = (1..6).map { movie(100 + it, tmdb = it) }
        assertTrue(overlapsEarlier(row, listOf(copies)))
        assertFalse(overlapsEarlier(row, listOf(copies.take(5))))
        assertFalse(overlapsEarlier(row, listOf(row.take(4), row.drop(4))))
        assertFalse(overlapsEarlier(emptyList(), listOf(row)))
        assertTrue(overlapsEarlier(row.take(5), listOf(row.take(4))))
        val noProvider = (1..8).map { movie(it, tmdb = null) }
        assertTrue(overlapsEarlier(noProvider, listOf(noProvider.take(6).map { it.copy(id = id(100 + it.id.leastSignificantBits.toInt())) })))
    }

    @Test
    fun movieAndSeriesWithTheSameTmdbIdRemainDistinct() {
        val movie = movie(1)
        val series = movie(2, tmdb = 1).copy(type = BaseItemKind.SERIES)
        val row = rowItems(listOf(movie, series) + (3..6).map { movie(it) }, null)
        assertTrue(movie in row && series in row)
        assertFalse(overlapsEarlier(listOf(movie), listOf(listOf(series))))
        val noProviderMovie = movie.copy(providerIds = null)
        val noProviderSeries = noProviderMovie.copy(id = id(2), type = BaseItemKind.SERIES)
        assertFalse(noProviderMovie.discoveryItemKey() == noProviderSeries.discoveryItemKey())
    }

    @Test
    fun additionalEpisodesAndDuplicateCopiesDoNotChangeGenrePreferences() {
        val inputs = fullInputs()
        val original = buildDiscoveryPlan(inputs, Random(42))
        val repeated = inputs.played + inputs.played.filter { it.type == BaseItemKind.EPISODE }.flatMap { episode ->
            List(10) { episode.copy(id = UUID.randomUUID()) }
        } + inputs.played.filter { it.type == BaseItemKind.MOVIE }.map { it.copy(id = UUID.randomUUID()) }
        assertEquals(original, buildDiscoveryPlan(inputs.copy(played = repeated), Random(42)))
    }

    @Test
    fun picksUsePreferencesForTheirOwnMediaType() {
        val inputs = DiscoveryInputs(
            date = "2026-10-24",
            played = listOf(movie(1, genres = listOf("Comedy")), episode(2, 30)),
            series = mapOf(id(30) to BaseItemDto(id = id(30), type = BaseItemKind.SERIES, genres = listOf("Drama"))),
            availableGenres = mapOf(BaseItemKind.MOVIE to setOf("Comedy"), BaseItemKind.SERIES to setOf("Drama"))
        )
        val rows = buildDiscoveryPlan(inputs, Random(42)).candidates.filter { it.kind == DiscoveryKind.TOP_PICKS }
        assertEquals(DiscoveryType.MIXED, rows.first().type)
        assertEquals(listOf("Comedy"), rows.single { it.type == DiscoveryType.MOVIE }.genres)
        assertEquals(listOf("Drama"), rows.single { it.type == DiscoveryType.TV }.genres)
        assertEquals("Movies we think you'll like", rows.single { it.type == DiscoveryType.MOVIE }.title)
        assertEquals("Shows we think you'll like", rows.single { it.type == DiscoveryType.TV }.title)
        assertEquals(DISCOVERY_PLAN_VERSION, buildDiscoveryPlan(inputs, Random(42)).version)
    }

    @Test
    fun nextDayPlanWaitsUntilFocusLeavesDiscovery() {
        val plan = buildDiscoveryPlan(fullInputs(), Random(42))
        val today = LocalDate.parse(plan.date)
        assertFalse(shouldRenewDiscovery(plan, today, focusedDiscovery = false))
        assertFalse(shouldRenewDiscovery(plan, today.plusDays(1), focusedDiscovery = true))
        assertTrue(shouldRenewDiscovery(plan, today.plusDays(1), focusedDiscovery = false))
    }

    @Test
    fun pendingRowsRespectProjectedMovieAndTvGenreCaps() {
        val movies = (1..3).map { candidate(DiscoveryKind.GENRE, DiscoveryType.MOVIE).copy(key = "movie-$it") }
        val shows = (1..3).map { candidate(DiscoveryKind.GENRE, DiscoveryType.TV).copy(key = "show-$it") }
        assertEquals(movies.take(2) + shows.take(2), pendingDiscoveryCandidates(movies + shows, emptyList()))
    }

    @Test
    fun pendingRowsStopAtTargetIncludingAlreadyShownRows() {
        val shown = List(DISCOVERY_TARGET - 1) { candidate(DiscoveryKind.GENRE).copy(key = "shown-$it") }
        val movie = candidate(DiscoveryKind.TOP_PICKS, DiscoveryType.MOVIE)
        val show = candidate(DiscoveryKind.TOP_PICKS, DiscoveryType.TV)
        assertEquals(listOf(movie), pendingDiscoveryCandidates(listOf(movie, show), shown))
        assertTrue(pendingDiscoveryCandidates(listOf(show), shown + movie).isEmpty())
    }

    @Test
    fun pendingRowsSkipDroppedCandidatesAndKeepSpareTitlesInOrder() {
        val first = candidate(DiscoveryKind.BECAUSE_LIKED).copy(key = "first", title = "First")
        val spare = first.copy(key = "spare", title = "Spare")
        val genre = candidate(DiscoveryKind.GENRE).copy(key = "genre", title = "Genre")
        assertEquals(listOf(first, genre), pendingDiscoveryCandidates(listOf(first, spare, genre), emptyList()))
        assertEquals(listOf(spare, genre), pendingDiscoveryCandidates(listOf(first.copy(dropped = true), spare, genre), emptyList()))
    }

    @Test
    fun pendingRowsCountAlreadyShownCapsAndHandleExhaustedPlans() {
        val watched = candidate(DiscoveryKind.BECAUSE_WATCHED)
        val shown = List(4) { watched.copy(key = "shown-$it") }
        val liked = candidate(DiscoveryKind.BECAUSE_LIKED)
        assertEquals(listOf(liked), pendingDiscoveryCandidates(listOf(watched, liked), shown))
        assertTrue(pendingDiscoveryCandidates(emptyList(), shown).isEmpty())
        assertTrue(pendingDiscoveryCandidates(listOf(liked.copy(dropped = true)), shown).isEmpty())
    }

    @Test
    fun spareRemainsAvailableWhenTheFirstCandidateForTheLastSlotDrops() {
        val watched = candidate(DiscoveryKind.BECAUSE_WATCHED)
        val shown = List(3) { watched.copy(key = "shown-$it") }
        val first = watched.copy(key = "first")
        val spare = watched.copy(key = "spare")
        val genre = candidate(DiscoveryKind.GENRE)
        val candidates = listOf(first, spare, genre)
        assertEquals(listOf(first), nextDiscoveryBatch(candidates, shown, limit = 4))
        assertEquals(listOf(spare, genre), nextDiscoveryBatch(candidates.drop(1), shown, limit = 4))
        val afterSuccess = nextDiscoveryBatch(candidates.drop(1), shown + first, limit = 4)
        assertEquals(listOf(genre), afterSuccess.filter { shouldShow(it, shown + first) })
    }
}
