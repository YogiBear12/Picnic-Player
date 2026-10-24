package app.picnic.player.data.media

import java.io.IOException
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryLoadingTest {
    private fun item(number: Int, played: Boolean = false, rating: Float = 8f) = BaseItemDto(
        id = UUID(0, number.toLong()),
        name = "Movie $number",
        type = BaseItemKind.MOVIE,
        providerIds = mapOf("Tmdb" to number.toString()),
        userData = UserItemDataDto(
            played = played,
            playbackPositionTicks = 0,
            playCount = if (played) 1 else 0,
            isFavorite = false,
            key = "test",
            itemId = UUID(0, number.toLong())
        ),
        communityRating = rating
    )

    private fun candidate(key: String, ids: List<Int>? = null) = DiscoveryCandidate(
        key,
        DiscoveryKind.GENRE,
        DiscoveryType.MOVIE,
        "Movies",
        itemIds = ids?.map { item(it).id.toString() }
    )

    @Test
    fun savedPagesAndFreshQueriesStartBeforeMainRowsButPublicationWaits() = runTest {
        val mainLoading = MutableStateFlow(true)
        var savedRead = false
        var freshRead = false
        var published: List<List<BaseItemDto>> = emptyList()
        val candidates = listOf(candidate("saved", listOf(1)), candidate("fresh"))
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = {
                savedRead = true
                listOf(item(1))
            },
            query = {
                freshRead = true
                listOf(item(2))
            },
            awaitPublication = { mainLoading.first { !it } }
        )
        val job = launch { published = loader.load(candidates) }
        runCurrent()
        assertTrue(savedRead)
        assertTrue(freshRead)
        assertTrue(published.isEmpty())
        assertTrue(job.isActive)
        mainLoading.value = false
        runCurrent()
        assertEquals(listOf(listOf(item(1)), listOf(item(2))), published)
        assertTrue(job.isCompleted)
    }

    @Test
    fun cancellationWhileWaitingForMainRowsDoesNotPublish() = runTest {
        val mainLoading = MutableStateFlow(true)
        var published = false
        val candidates = listOf(candidate("fresh"))
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = { error("No saved rows") },
            query = { listOf(item(1)) },
            awaitPublication = { mainLoading.first { !it } }
        )
        val job = launch {
            loader.load(candidates)
            published = true
        }
        runCurrent()
        job.cancel()
        mainLoading.value = false
        runCurrent()
        assertFalse(published)
        assertTrue(job.isCancelled)
    }

    @Test
    fun fullPlanSharesOneReadAcrossPublicationBatches() = runTest {
        var calls = 0
        val candidates = (1..20).map { candidate(it.toString(), listOf(it, 21, 22)) } +
            candidate("dropped", listOf(23)).copy(dropped = true)
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = { ids ->
                calls++
                assertEquals((1..22).map { item(it).id }.toSet(), ids.toSet())
                assertEquals(22, ids.size)
                (1..21).reversed().map { item(it) }
            },
            query = { error("Saved rows must not be queried again") }
        )
        val rows = candidates.chunked(4).flatMap { loader.load(it) }
        assertEquals(1, calls)
        assertEquals((1..20).map { listOf(item(it), item(21)) } + listOf(emptyList<BaseItemDto>()), rows)
    }

    @Test
    fun earlyRowsDoNotWaitForLaterPagesAndPageRequestsStayBounded() = runTest {
        var active = 0
        var peak = 0
        var calls = 0
        val candidates = (0..2).map { page -> candidate("page$page", (page * 100 + 1..page * 100 + 100).toList()) }
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = { ids ->
                calls++
                assertEquals(100, ids.size)
                active++
                peak = maxOf(peak, active)
                delay(if (ids.first() == item(101).id) 1_000 else 10)
                active--
                ids.map { item(it.leastSignificantBits.toInt()) }.reversed()
            },
            query = { error("Unexpected query") }
        )
        assertEquals(listOf((1..100).map { item(it) }), loader.load(candidates.take(1)))
        assertEquals(10L, currentTime)
        assertEquals(listOf((201..300).map { item(it) }), loader.load(candidates.takeLast(1)))
        assertEquals(20L, currentTime)
        assertEquals(listOf((101..200).map { item(it) }), loader.load(candidates.subList(1, 2)))
        assertEquals(1_000L, currentTime)
        assertEquals(3, calls)
        assertEquals(2, peak)
    }

    @Test
    fun emptyAndFreshPlansDoNotFetchSavedItems() = runTest {
        for (candidates in listOf(emptyList(), listOf(candidate("fresh")), listOf(candidate("dropped", listOf(1)).copy(dropped = true)))) {
            val loader = DiscoveryItemLoader(this, candidates, fetchSaved = { error("No saved IDs to fetch") }, query = { listOf(item(1)) })
            assertEquals(candidates.map { if (it.dropped) emptyList() else listOf(item(1)) }, loader.load(candidates))
        }
    }

    @Test
    fun successfulEmptyHydrationIsNotRetried() = runTest {
        var calls = 0
        val candidates = listOf(candidate("deleted", listOf(1)))
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = {
                calls++
                emptyList()
            },
            query = { error("Unexpected query") }
        )
        assertEquals(listOf(emptyList<BaseItemDto>()), loader.load(candidates))
        assertEquals(1, calls)
    }

    @Test
    fun failedHydrationRetriesWithoutLosingSavedOrder() = runTest {
        var calls = 0
        val candidates = listOf(candidate("saved", listOf(2, 1)))
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = {
                calls++
                if (calls < 3) throw IOException("Offline")
                listOf(item(1), item(2))
            },
            query = { error("Unexpected query") }
        )
        assertEquals(listOf(listOf(item(2), item(1))), loader.load(candidates))
        assertEquals(3, calls)
        assertEquals(4_000L, currentTime)
    }

    @Test
    fun exhaustedRetriesLeaveTheReadAvailableForResume() = runTest {
        var calls = 0
        try {
            retryDiscovery {
                calls++
                throw IOException("Offline")
            }
            error("Expected failure")
        } catch (_: IOException) {
            assertEquals(3, calls)
        }
        assertEquals("Recovered", retryDiscovery { "Recovered" })
    }

    @Test
    fun aFailedFreshQueryRetriesAloneWithoutRepeatingItsSiblings() = runTest {
        val calls = mutableMapOf<String, Int>()
        val candidates = listOf(candidate("flaky"), candidate("steady"))
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = { emptyList() },
            query = { candidate ->
                val count = calls.merge(candidate.key, 1, Int::plus)!!
                if (candidate.key == "flaky" && count == 1) throw IOException("Offline")
                listOf(item(if (candidate.key == "flaky") 1 else 2))
            }
        )

        val rows = loader.load(candidates)

        assertEquals(listOf(listOf(item(1)), listOf(item(2))), rows)
        assertEquals(mapOf("flaky" to 2, "steady" to 1), calls)
    }

    @Test
    fun cancellationNeverRetries() = runTest {
        var calls = 0
        try {
            retryDiscovery {
                calls++
                throw CancellationException("Leaving Home")
            }
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, calls)
            assertEquals(0L, currentTime)
        }
    }

    @Test
    fun freshQueriesStayBoundedAndPublishInCandidateOrder() = runTest {
        var active = 0
        var peak = 0
        val candidates = (1..4).map { candidate(it.toString()) }
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = { error("No saved rows") },
            query = { row ->
                active++
                peak = maxOf(peak, active)
                delay(if (row.key == "1") 100 else 10)
                active--
                listOf(item(row.key.toInt()))
            }
        )
        assertEquals((1..4).map { listOf(item(it)) }, loader.load(candidates))
        assertEquals(2, peak)
    }

    @Test
    fun picksPreferUnwatchedButKeepSomeWatchedTitles() {
        val pool = (1..80).map { item(it) } + (81..160).map { item(it, played = true) }
        val selected = sampleDiscoveryItems(pool, Random(42))
        assertEquals(12, selected.size)
        assertEquals(9, selected.count { it.userData?.played != true })
        assertEquals(3, selected.count { it.userData?.played == true })
        assertEquals(selected, sampleDiscoveryItems(pool, Random(42)))
        assertFalse(selected == sampleDiscoveryItems(pool, Random(43)))
    }

    @Test
    fun picksFillFromEitherPoolWhenTheOtherIsSmall() {
        val watched = (1..15).map { item(it, played = true) }
        val unwatched = (16..30).map { item(it) }
        assertEquals(12, sampleDiscoveryItems(watched + unwatched.take(2), Random(42)).size)
        assertEquals(12, sampleDiscoveryItems(unwatched + watched.take(1), Random(42)).size)
        assertEquals(12, sampleDiscoveryItems(watched, Random(42)).size)
        assertEquals(12, sampleDiscoveryItems(unwatched, Random(42)).size)
        assertTrue(sampleDiscoveryItems(emptyList(), Random(42)).isEmpty())
    }

    @Test
    fun picksFavorRatingsAndDeduplicateBeforeSampling() {
        val strong = (1..60).map { item(it, rating = 9f) }
        val weak = (61..100).map { item(it, rating = 3f) }
        val copies = strong.map { it.copy(id = UUID.randomUUID()) }
        val selected = sampleDiscoveryItems(strong + copies + weak, Random(42))
        assertEquals(12, selected.size)
        assertTrue(selected.all { it.communityRating == 9f })
        assertEquals(selected.size, selected.distinctBy { it.discoveryItemKey() }.size)
    }

    @Test
    fun savedPagesShareConcurrencyBudgetWithFreshQueries() = runTest {
        var active = 0
        var peak = 0
        suspend fun fetch(number: Int): List<BaseItemDto> {
            active++
            peak = maxOf(peak, active)
            delay(100)
            active--
            return listOf(item(number))
        }
        val candidates = listOf(candidate("saved", listOf(1)), candidate("2"), candidate("3"))
        val loader = DiscoveryItemLoader(
            this,
            candidates,
            fetchSaved = { fetch(1) },
            query = { fetch(it.key.toInt()) }
        )
        assertEquals((1..3).map { listOf(item(it)) }, loader.load(candidates))
        assertEquals(2, peak)
    }
}
