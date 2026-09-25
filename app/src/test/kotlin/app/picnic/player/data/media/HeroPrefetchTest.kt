package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroPrefetchTest {

    private fun item(kind: BaseItemKind, id: UUID = UUID.randomUUID()) = BaseItemDto(id = id, type = kind)

    private fun row(vararg items: BaseItemDto) = HomeRow(title = "r", items = items.toList(), continueWatching = false, key = "r")

    // --- planHeroStreamPrefetch ------------------------------------------------

    @Test
    fun emptyRows_emptyPlan() {
        val plan = planHeroStreamPrefetch(emptyList(), 0, emptyMap(), emptySet())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun movies_allBatchedAcrossFocusedBand() {
        val a = item(BaseItemKind.MOVIE)
        val b = item(BaseItemKind.EPISODE)
        val plan = planHeroStreamPrefetch(listOf(row(a, b)), 0, emptyMap(), emptySet())
        assertEquals(listOf(a.id, b.id), plan.streamIds)
        assertTrue(plan.seriesIds.isEmpty())
    }

    @Test
    fun alreadyFetched_excluded() {
        val a = item(BaseItemKind.MOVIE)
        val b = item(BaseItemKind.MOVIE)
        val plan = planHeroStreamPrefetch(listOf(row(a, b)), 0, emptyMap(), setOf(a.id))
        assertEquals(listOf(b.id), plan.streamIds)
    }

    @Test
    fun series_limitedToSlidingWindow() {
        // 10 series; focus at index 5 -> window (3..9). Indices 0,1,2 excluded.
        val series = (0 until 10).map { item(BaseItemKind.SERIES) }
        val theRow = row(*series.toTypedArray())
        val plan = planHeroStreamPrefetch(
            rows = listOf(theRow),
            focusedRowIndex = 0,
            rowFocusedItemIds = mapOf(0 to series[5].id),
            alreadyFetched = emptySet()
        )
        val expected = series.subList(3, 10).map { it.id }
        assertEquals(expected, plan.seriesIds)
        assertTrue(plan.streamIds.isEmpty())
    }

    @Test
    fun seasons_landInSeasonBucketWithParentSeries() {
        val seriesId = UUID.randomUUID()
        val s = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, seriesId = seriesId)
        val plan = planHeroStreamPrefetch(listOf(row(s)), 0, mapOf(0 to s.id), emptySet())
        assertEquals(listOf(SeasonRef(s.id, seriesId)), plan.seasons)
        assertTrue(plan.seriesIds.isEmpty())
        assertTrue(plan.streamIds.isEmpty())
    }

    @Test
    fun seasons_replannedWhileTheParentSeriesIsStillMissing() {
        val seriesId = UUID.randomUUID()
        val s = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, seriesId = seriesId)
        val rows = listOf(row(s))

        val streamsOnly = planHeroStreamPrefetch(rows, 0, mapOf(0 to s.id), setOf(s.id))
        assertEquals(listOf(SeasonRef(s.id, seriesId)), streamsOnly.seasons)

        val settled = planHeroStreamPrefetch(rows, 0, mapOf(0 to s.id), setOf(s.id), knownSeries = setOf(seriesId))
        assertTrue(settled.isEmpty)
    }

    @Test
    fun seasons_withNoParentSeriesAreSkipped() {
        val s = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON)
        val plan = planHeroStreamPrefetch(listOf(row(s)), 0, mapOf(0 to s.id), emptySet())
        assertTrue(plan.isEmpty)
    }

    @Test
    fun seasons_shareTheSeriesWindow() {
        val seasons = (0 until 10).map {
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, seriesId = UUID.randomUUID())
        }
        val plan = planHeroStreamPrefetch(
            rows = listOf(row(*seasons.toTypedArray())),
            focusedRowIndex = 0,
            rowFocusedItemIds = mapOf(0 to seasons[5].id),
            alreadyFetched = emptySet()
        )
        assertEquals(seasons.subList(3, 10).map { it.id }, plan.seasons.map { it.seasonId })
    }

    @Test
    fun lookAhead_onlyFocusRowPlusMinusOne() {
        val r0 = row(item(BaseItemKind.MOVIE))
        val r1 = row(item(BaseItemKind.MOVIE))
        val r2 = row(item(BaseItemKind.MOVIE))
        val r3 = row(item(BaseItemKind.MOVIE)) // outside band from focus 1
        val plan = planHeroStreamPrefetch(listOf(r0, r1, r2, r3), 1, emptyMap(), emptySet())
        val included = (plan.streamIds).toSet()
        assertTrue(r0.items[0].id in included)
        assertTrue(r1.items[0].id in included)
        assertTrue(r2.items[0].id in included)
        assertTrue(r3.items[0].id !in included)
    }

    // --- seriesNeedingSeasonCount ----------------------------------------------

    @Test
    fun seasonCount_seriesOnly_distinct_minusCounted() {
        val s1 = item(BaseItemKind.SERIES)
        val s2 = item(BaseItemKind.SERIES)
        val movie = item(BaseItemKind.MOVIE)
        val rows = listOf(row(s1, movie, s1), row(s2))
        val need = seriesNeedingSeasonCount(rows, alreadyCounted = setOf(s2.id))
        assertEquals(listOf(s1.id), need)
    }
}
