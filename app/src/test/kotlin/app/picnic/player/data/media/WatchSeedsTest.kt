package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSeedsTest {

    private fun item(
        name: String? = null,
        seriesName: String? = null,
        kind: BaseItemKind = BaseItemKind.EPISODE
    ) = BaseItemDto(id = java.util.UUID.randomUUID(), type = kind, name = name, seriesName = seriesName)

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
    fun seedNamePrefersTheSeries() {
        val episode = item(name = "Pilot", seriesName = "The Wire")
        assertEquals("The Wire", episode.seedName)
        assertEquals("Heat", item(kind = BaseItemKind.MOVIE, name = "Heat").seedName)
    }
}
