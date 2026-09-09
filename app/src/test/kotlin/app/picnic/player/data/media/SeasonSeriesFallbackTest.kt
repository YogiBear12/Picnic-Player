package app.picnic.player.data.media

import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonSeriesFallbackTest {

    private val series = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.SERIES,
        overview = "Show synopsis",
        genres = listOf("Comedy"),
        communityRating = 8.5f,
        officialRating = "TV-14",
        productionYear = 2018,
        status = "Continuing"
    )

    private fun episode(year: Int) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.EPISODE,
        premiereDate = LocalDateTime.of(year, 1, 1, 0, 0)
    )

    private fun season(
        overview: String? = null,
        genres: List<String>? = null,
        productionYear: Int? = null
    ) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.SEASON,
        seriesId = series.id,
        overview = overview,
        genres = genres,
        productionYear = productionYear
    )

    @Test
    fun emptySeasonFieldsTakeTheSeriesValues() {
        val merged = season().withSeriesFallback(series)
        assertEquals("Show synopsis", merged.overview)
        assertEquals(listOf("Comedy"), merged.genres)
        assertEquals(8.5f, merged.communityRating)
        assertEquals("TV-14", merged.officialRating)
        assertEquals(2018, merged.productionYear)
        assertEquals("Continuing", merged.status)
    }

    @Test
    fun seasonKeepsItsOwnValues() {
        val merged = season(overview = "Season synopsis", genres = listOf("Drama")).withSeriesFallback(series)
        assertEquals("Season synopsis", merged.overview)
        assertEquals(listOf("Drama"), merged.genres)
    }

    @Test
    fun aDatedSeasonDoesNotInheritTheSeriesRun() {
        val merged = season(productionYear = 2021).withSeriesFallback(series)
        assertEquals(2021, merged.productionYear)
        assertNull(merged.status)
        assertNull(merged.endDate)
    }

    @Test
    fun anUndatedSeasonTakesTheFirstEpisodeYearOverTheSeriesRun() {
        val merged = season().withSeriesFallback(series, episode(2021))
        assertEquals(2021, merged.productionYear)
        assertNull(merged.status)
    }

    @Test
    fun theSeasonOwnYearBeatsTheFirstEpisodeYear() {
        val merged = season(productionYear = 2020).withSeriesFallback(series, episode(2021))
        assertEquals(2020, merged.productionYear)
    }

    @Test
    fun anUndatedSeasonTakesTheWholeSeriesRunTogether() {
        val undatedSeries = series.copy(productionYear = null, premiereDate = LocalDateTime.of(2018, 4, 1, 0, 0))
        val merged = season().copy(premiereDate = LocalDateTime.of(2021, 7, 1, 0, 0))
            .withSeriesFallback(undatedSeries)
        assertEquals(2018, merged.premiereDate?.year)
        assertEquals("Continuing", merged.status)
    }

    @Test
    fun theMapOverloadResolvesBothSources() {
        val s = season()
        val merged = s.withSeriesFallback(mapOf(series.id to series), mapOf(s.id to episode(2021)))
        assertEquals("Show synopsis", merged.overview)
        assertEquals(2021, merged.productionYear)
    }

    @Test
    fun nonSeasonsAndMissingSeriesAreUntouched() {
        val movie = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE)
        assertNull(movie.withSeriesFallback(series).overview)
        assertNull(season().withSeriesFallback(null).overview)
    }
}
