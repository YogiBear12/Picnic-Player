package app.picnic.player.data.media

import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPlanTest {
    private fun id(number: Int) = UUID(0, number.toLong())

    private fun studio(number: Int) = NameGuidPair(name = "Studio $number", id = id(5000 + number))

    private fun movie(
        number: Int,
        name: String = "Movie $number",
        tmdb: Int? = number,
        genres: List<String> = listOf("Drama"),
        year: Int = 2000,
        studios: List<NameGuidPair>? = null
    ) = BaseItemDto(
        id = id(number),
        name = name,
        type = BaseItemKind.MOVIE,
        productionYear = year,
        providerIds = tmdb?.let { mapOf("Tmdb" to it.toString()) },
        genres = genres,
        studios = studios
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
        val movies = (1..20).map { movie(it, genres = listOf(genres[it % genres.size]), studios = listOf(studio(it % 8))) }
        val episodes = (1..20).map { episode(100 + it, 200 + it) }
        val series = (1..20).associate { number ->
            id(200 + number) to BaseItemDto(
                id = id(200 + number),
                type = BaseItemKind.SERIES,
                genres = listOf(genres[number % genres.size]),
                providerIds = mapOf("Tmdb" to (200 + number).toString()),
                productionYear = 1980 + number,
                studios = listOf(studio(8 + number % 4))
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
        assertEquals(42, candidates.size)
        assertEquals(candidates.size, candidates.map { it.key }.distinct().size)
        assertTrue(candidates.count { it.kind == DiscoveryKind.BECAUSE_WATCHED } > 4)
        for (type in DiscoveryType.entries) {
            val cap = DISCOVERY_CAPS.getValue(DiscoveryKind.GENRE to type)
            assertTrue(candidates.count { it.kind == DiscoveryKind.GENRE && it.type == type } > cap)
        }
        assertEquals(3, candidates.count { it.kind == DiscoveryKind.TOP_PICKS })
        assertEquals(
            setOf("Top rated movies", "Top rated shows"),
            candidates.filter { it.kind == DiscoveryKind.TOP_RATED }.map { it.title }.toSet()
        )
    }

    @Test
    fun shouldShowEnforcesEveryCapAndCombinedSeedTypes() {
        assertTrue(DISCOVERY_CAPS.values.sum() > DISCOVERY_TARGET)
        assertEquals(2, DISCOVERY_CAPS.filterKeys { it.first == DiscoveryKind.TOP_RATED }.values.sum())
        for ((bucket, cap) in DISCOVERY_CAPS) {
            val (kind, type) = bucket
            val row = when {
                kind == DiscoveryKind.STARRING && type == DiscoveryType.TV -> candidate(kind, seedType = DiscoveryType.TV)
                kind == DiscoveryKind.STARRING -> candidate(DiscoveryKind.DIRECTED_BY, seedType = DiscoveryType.TV)
                kind == DiscoveryKind.BECAUSE_WATCHED || kind == DiscoveryKind.BECAUSE_LIKED || kind == DiscoveryKind.WATCH_AGAIN -> candidate(kind)
                else -> candidate(kind, type)
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
        assertTrue(shown.filter { it.personId != null }.all { it.type == DiscoveryType.MIXED })
    }

    @Test
    fun genreCandidatesSurviveStronglySkewedHistory() {
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

    @Test
    fun shuffledRowsNeverRepeatKindsWhenAvoidable() {
        for (seed in 0..40) {
            val rows = buildDiscoveryPlan(fullInputs(), Random(seed)).candidates
            assertEquals(DiscoveryKind.TOP_PICKS, rows.first().kind)
            assertTrue(rows.zipWithNext().all { (left, right) -> left.orderKind() != right.orderKind() })
        }
    }

    @Test
    fun unavoidableNeighborsAreKeptToMinimum() {
        val inputs = fullInputs().copy(played = emptyList(), favorites = emptyList(), people = emptyMap())
        for (seed in 0..40) {
            val shuffled = buildDiscoveryPlan(inputs, Random(seed)).candidates.drop(1)
            val largest = shuffled.groupingBy { it.orderKind() }.eachCount().values.max()
            val repeats = shuffled.zipWithNext().count { (left, right) -> left.orderKind() == right.orderKind() }
            assertEquals(maxOf(0, largest - (shuffled.size - largest) - 1), repeats)
        }
        val rows = buildDiscoveryPlan(inputs, Random(2)).candidates
        assertEquals(DiscoveryKind.TOP_PICKS, rows.first().kind)
        assertTrue(
            rows.drop(1).all {
                it.kind in setOf(DiscoveryKind.GENRE, DiscoveryKind.TOP_PICKS, DiscoveryKind.TOP_RATED, DiscoveryKind.CRITIC, DiscoveryKind.DECADE)
            }
        )
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
        val historyFree = setOf(DiscoveryKind.TOP_RATED, DiscoveryKind.CRITIC, DiscoveryKind.DECADE)
        assertTrue(buildDiscoveryPlan(DiscoveryInputs("2026-10-24", emptyList()), Random(1)).candidates.all { it.kind in historyFree })
        assertFalse(buildDiscoveryPlan(fullInputs().copy(favorites = emptyList()), Random(1)).candidates.any { it.kind == DiscoveryKind.BECAUSE_LIKED })
    }

    @Test
    fun rowItemsRemoveSeedKeepFirstCopyAndCapAtForty() {
        val first = movie(2, "HD")
        val duplicate = movie(3, "4K", tmdb = 2)
        val raw = listOf(movie(1), first, duplicate) + (4..60).map { movie(it) }
        val row = rowItems(raw, id(1))
        assertEquals(40, row.size)
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
        assertEquals("Movies you might like", rows.single { it.type == DiscoveryType.MOVIE }.title)
        assertEquals("TV shows you might like", rows.single { it.type == DiscoveryType.TV }.title)
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

    private fun libraryInputs(kind: BaseItemKind): DiscoveryInputs {
        val full = fullInputs()
        val genres = full.availableGenres.getValue(kind) + "Reality" + "Anime"
        return if (kind == BaseItemKind.MOVIE) {
            full.copy(played = full.played.filter { it.type == BaseItemKind.MOVIE }, availableGenres = mapOf(kind to genres))
        } else {
            full.copy(
                played = full.played.filter { it.type == BaseItemKind.EPISODE },
                favorites = emptyList(),
                availableGenres = mapOf(kind to genres)
            )
        }
    }

    @Test
    fun movieLibraryPinsItsPicksAndBuildsOnlyMovieRows() {
        val candidates = buildDiscoveryPlan(libraryInputs(BaseItemKind.MOVIE), Random(3), MOVIE_LIBRARY_DISCOVERY).candidates
        assertEquals("Movies you might like", candidates.first().title)
        assertTrue(candidates.none { it.type == DiscoveryType.TV })
        assertTrue(candidates.none { it.kind == DiscoveryKind.GENRE && it.type == DiscoveryType.MIXED })
        assertEquals(listOf("Top rated"), candidates.filter { it.kind == DiscoveryKind.TOP_RATED }.map { it.title })
        assertTrue(candidates.any { it.kind == DiscoveryKind.DIRECTED_BY })
        val titles = candidates.filter { it.kind == DiscoveryKind.GENRE }.map { it.title }
        assertTrue(titles.isNotEmpty() && titles.none { it.endsWith(" movies") })
    }

    @Test
    fun tvLibraryHasNoDirectorsAndUntypedGenreTitles() {
        val candidates = buildDiscoveryPlan(libraryInputs(BaseItemKind.SERIES), Random(4), TV_LIBRARY_DISCOVERY).candidates
        assertEquals("TV shows you might like", candidates.first().title)
        assertTrue(candidates.none { it.kind == DiscoveryKind.DIRECTED_BY })
        assertTrue(candidates.any { it.kind == DiscoveryKind.STARRING })
        val genres = candidates.filter { it.kind == DiscoveryKind.GENRE }
        assertTrue(genres.size > LIBRARY_GENRE_ROWS)
        assertTrue(genres.none { it.title.endsWith(" TV") || it.title.endsWith(" shows") })
        val noHistory = libraryInputs(BaseItemKind.SERIES).copy(played = emptyList())
        val allTitles = (1..60).flatMap { seed ->
            buildDiscoveryPlan(noHistory, Random(seed), TV_LIBRARY_DISCOVERY).candidates.map { it.title }
        }.toSet()
        assertTrue("Reality" in allTitles && "Anime" in allTitles && "Dramas" in allTitles)
    }

    @Test
    fun libraryProfilesShowAboutTwentyFourRowsWithRoomForDrops() {
        for (profile in listOf(MOVIE_LIBRARY_DISCOVERY, TV_LIBRARY_DISCOVERY)) {
            assertEquals(24, profile.target)
            assertTrue(profile.caps.values.sum() > profile.target)
            assertEquals(LIBRARY_GENRE_ROWS, profile.caps.getValue(DiscoveryKind.GENRE to profile.types.single()))
        }
        assertEquals(HOME_DISCOVERY, DiscoveryScope.HOME.profile)
        assertEquals(HOME_DISCOVERY, DiscoveryScope(id(1), listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)).profile)
        assertEquals(TV_LIBRARY_DISCOVERY, DiscoveryScope(id(1), listOf(BaseItemKind.SERIES)).profile)
        assertEquals(MOVIE_LIBRARY_DISCOVERY, DiscoveryScope(id(1), listOf(BaseItemKind.MOVIE)).profile)
    }

    @Test
    fun catalogRowsUseSharedTitlesAndHistoryOnHome() {
        val rows = buildDiscoveryPlan(fullInputs(), Random(5)).candidates
        val decades = rows.filter { it.kind == DiscoveryKind.DECADE }
        assertTrue(decades.isNotEmpty() && decades.all { it.type == DiscoveryType.MIXED && it.title.startsWith("Made in the ") })
        val studios = rows.filter { it.kind == DiscoveryKind.STUDIO }
        assertTrue(studios.isNotEmpty() && studios.all { it.type == DiscoveryType.MIXED && it.title.startsWith("Best of Studio ") })
        assertEquals(listOf("Critically acclaimed movies"), rows.filter { it.kind == DiscoveryKind.CRITIC }.map { it.title })
        assertEquals(DiscoveryType.MOVIE, rows.single { it.kind == DiscoveryKind.CRITIC }.type)
    }

    @Test
    fun decadeTitlesSwitchToFourDigitsFromTwoThousand() {
        val titles = (0..40).flatMap { seed ->
            buildDiscoveryPlan(DiscoveryInputs("2026-10-24", emptyList()), Random(seed)).candidates.filter { it.kind == DiscoveryKind.DECADE }.map { it.title }
        }.toSet()
        assertTrue("Made in the 80s" in titles && "Made in the 50s" in titles)
        assertTrue("Made in the 2010s" in titles && "Made in the 2020s" in titles)
        assertFalse("Made in the 2030s" in titles || "Made in the 1980s" in titles)
    }

    @Test
    fun decadesAndStudiosFollowWatchHistory() {
        val heavy = studio(99)
        val played = (1..30).map { movie(it, year = 1985, studios = listOf(heavy)) } +
            (31..50).map { movie(it, year = 2015, studios = listOf(studio(it))) }
        var eighties = 0
        var heavyPicked = 0
        for (seed in 0..40) {
            val rows = buildDiscoveryPlan(DiscoveryInputs("2026-10-24", played), Random(seed)).candidates
            if (rows.any { it.kind == DiscoveryKind.DECADE && it.decade == 1980 }) eighties++
            if (rows.any { it.kind == DiscoveryKind.STUDIO && it.studioId == heavy.id.toString() }) heavyPicked++
        }
        assertEquals(41, eighties)
        assertEquals(41, heavyPicked)
    }

    @Test
    fun watchAgainHoldsHistorySeedsWithSeriesForEpisodes() {
        val played = listOf(movie(1), episode(2, 30), episode(3, 30), episode(4, 40))
        val row = buildDiscoveryPlan(DiscoveryInputs("2026-10-24", played), Random(1)).candidates.single { it.kind == DiscoveryKind.WATCH_AGAIN }
        assertEquals("Watch again", row.title)
        assertEquals(setOf(id(1), id(30), id(40)).map { it.toString() }.toSet(), requireNotNull(row.itemIds).toSet())
        val many = (1..60).map { movie(it) }
        val capped = buildDiscoveryPlan(DiscoveryInputs("2026-10-24", many), Random(1)).candidates.single { it.kind == DiscoveryKind.WATCH_AGAIN }
        assertEquals(DISCOVERY_ROW_CAP, capped.itemIds?.size)
    }

    @Test
    fun criticRowsOnlyWhereMoviesAre() {
        val tv = buildDiscoveryPlan(libraryInputs(BaseItemKind.SERIES), Random(4), TV_LIBRARY_DISCOVERY).candidates
        assertTrue(tv.none { it.kind == DiscoveryKind.CRITIC })
        assertTrue(tv.filter { it.kind == DiscoveryKind.DECADE || it.kind == DiscoveryKind.STUDIO }.all { it.type == DiscoveryType.TV })
        val movies = buildDiscoveryPlan(libraryInputs(BaseItemKind.MOVIE), Random(3), MOVIE_LIBRARY_DISCOVERY).candidates
        assertEquals(1, movies.count { it.kind == DiscoveryKind.CRITIC })
    }

    @Test
    fun criticAndTopRatedNeverSitSideBySide() {
        for (seed in 0..40) {
            val rows = buildDiscoveryPlan(fullInputs(), Random(seed)).candidates.drop(1)
            assertTrue(
                rows.zipWithNext().none { (left, right) ->
                    setOf(left.kind, right.kind).all { it == DiscoveryKind.CRITIC || it == DiscoveryKind.TOP_RATED }
                }
            )
        }
    }

    @Test
    fun heavierGroupsClaimEarlierSlots() {
        var watchedAhead = 0
        for (seed in 0..40) {
            val rows = buildDiscoveryPlan(fullInputs(), Random(seed)).candidates
            val firstWatched = rows.indexOfFirst { it.kind == DiscoveryKind.BECAUSE_WATCHED }
            val firstWatchAgain = rows.indexOfFirst { it.kind == DiscoveryKind.WATCH_AGAIN }
            if (firstWatched < firstWatchAgain) watchedAhead++
        }
        assertTrue(watchedAhead > 25)
    }

    private companion object {
        const val LIBRARY_GENRE_ROWS = 8
    }
}
