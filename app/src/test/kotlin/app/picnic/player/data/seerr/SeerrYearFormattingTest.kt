package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrYearFormattingTest {

    @Test
    fun formatSeerrYearLabel_movie_usesReleaseYear() {
        assertEquals("2019", formatSeerrYearLabel(SeerrMediaType.MOVIE, "2019-05-17"))
        assertNull(formatSeerrYearLabel(SeerrMediaType.MOVIE, null))
        assertNull(formatSeerrYearLabel(SeerrMediaType.MOVIE, "201"))
    }

    @Test
    fun formatSeerrSeriesYears_singleYear() {
        assertEquals(
            "2022",
            formatSeerrSeriesYears("2022-01-01", "2022-12-01", "Ended")
        )
    }

    @Test
    fun formatSeerrSeriesYears_multiYearEnded() {
        assertEquals(
            "2022 - 2023",
            formatSeerrSeriesYears("2022-01-01", "2023-06-15", "Ended")
        )
    }

    @Test
    fun formatSeerrSeriesYears_activePresent() {
        assertEquals(
            "2022 - Present",
            formatSeerrSeriesYears("2022-01-01", null, "Returning Series")
        )
        assertEquals(
            "2022 - Present",
            formatSeerrSeriesYears("2022-01-01", null, "In Production")
        )
    }

    @Test
    fun formatSeerrYearLabel_series_delegatesToSeriesYears() {
        assertEquals(
            "2022 - Present",
            formatSeerrYearLabel(SeerrMediaType.TV, "2022-03-01", null, "Planned")
        )
        assertEquals(
            "2022 - 2023",
            formatSeerrYearLabel(SeerrMediaType.TV, "2022-01-01", "2023-12-01", "Cancelled")
        )
    }

    @Test
    fun hasYearMeta_requiresFormattedYear() {
        val movieWithYear = SeerrCachedTitle("T", null, releaseDate = "2019-01-01")
        val movieMissing = SeerrCachedTitle("T", null)
        assertTrue(movieWithYear.hasYearMeta(SeerrMediaType.MOVIE))
        assertFalse(movieMissing.hasYearMeta(SeerrMediaType.MOVIE))

        val tvWithYear = SeerrCachedTitle(
            "S",
            null,
            releaseDate = "2022-01-01",
            lastAirDate = "2023-01-01",
            seriesStatus = "Ended"
        )
        val tvMissing = SeerrCachedTitle("S", null, releaseDate = "2022-01-01")
        assertTrue(tvWithYear.hasYearMeta(SeerrMediaType.TV))
        assertTrue(tvMissing.hasYearMeta(SeerrMediaType.TV))
    }

    @Test
    fun formatSeerrSeasonCountLabel_singularAndPlural() {
        assertEquals("1 season", formatSeerrSeasonCountLabel(1))
        assertEquals("5 seasons", formatSeerrSeasonCountLabel(5))
        assertNull(formatSeerrSeasonCountLabel(null))
        assertNull(formatSeerrSeasonCountLabel(0))
    }

    @Test
    fun formatRequestedSeasonsLabel_empty() {
        assertNull(formatRequestedSeasonsLabel(emptyList()))
    }

    @Test
    fun formatRequestedSeasonsLabel_rangesAndSpecials() {
        assertEquals("Seasons 1–5, 7–8", formatRequestedSeasonsLabel(listOf(1, 2, 3, 4, 5, 7, 8)))
        assertEquals("Season 3", formatRequestedSeasonsLabel(listOf(3)))
        assertEquals("Specials", formatRequestedSeasonsLabel(listOf(0)))
        assertEquals("Specials", formatRequestedSeasonsLabel(listOf(-1)))
        assertEquals("Specials, Seasons 1–2", formatRequestedSeasonsLabel(listOf(0, 1, 2)))
        assertEquals("Specials, Season 1", formatRequestedSeasonsLabel(listOf(0, 1)))
    }

    @Test
    fun formatSeasonNumberRanges_collapsesContiguous() {
        assertEquals("1–5, 7–8", formatSeasonNumberRanges(listOf(1, 2, 3, 4, 5, 7, 8)))
        assertEquals("1, 3–5", formatSeasonNumberRanges(listOf(1, 3, 4, 5)))
    }
}
