package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSeedsTest {

    private fun item(
        id: UUID = UUID.randomUUID(),
        seriesId: UUID? = null,
        name: String? = null,
        seriesName: String? = null,
        kind: BaseItemKind = BaseItemKind.EPISODE
    ) = BaseItemDto(id = id, type = kind, seriesId = seriesId, name = name, seriesName = seriesName)

    @Test
    fun seriesLibraryReadsHistoryFromEpisodes() {
        assertEquals(
            listOf(BaseItemKind.EPISODE),
            watchHistoryKinds(listOf(BaseItemKind.SERIES))
        )
    }

    @Test
    fun movieKindsAreLeftAlone() {
        assertEquals(
            listOf(BaseItemKind.MOVIE),
            watchHistoryKinds(listOf(BaseItemKind.MOVIE))
        )
    }

    @Test
    fun mixedKindsMapOnlyTheSeriesEntry() {
        assertEquals(
            listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
            watchHistoryKinds(listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES))
        )
    }

    @Test
    fun episodesOfOneSeriesCollapseToOneSeed() {
        val series = UUID.randomUUID()
        val pool = listOf(item(seriesId = series), item(seriesId = series), item(seriesId = series))
        assertEquals(listOf(series), chooseWatchSeeds(pool).map { it.seedId })
    }

    @Test
    fun moviesSeedOnTheirOwnId() {
        val movie = item(kind = BaseItemKind.MOVIE, name = "Heat")
        assertEquals(listOf(movie.id), chooseWatchSeeds(listOf(movie)).map { it.seedId })
    }

    @Test
    fun everyDistinctSeriesSurvivesForBackfill() {
        val pool = List(30) { item(seriesId = UUID.randomUUID()) }
        assertEquals(30, chooseWatchSeeds(pool).size)
    }

    @Test
    fun seedNamePrefersTheSeries() {
        val episode = item(name = "Pilot", seriesName = "The Wire")
        assertEquals("The Wire", episode.seedName)
        assertEquals("Heat", item(kind = BaseItemKind.MOVIE, name = "Heat").seedName)
    }
}
