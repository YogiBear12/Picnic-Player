package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeerrRequestDisplayTest {

    @Test
    fun mediaTitleOrNull_prefersTitleThenName() {
        val withTitle = SeerrMediaRequest(
            id = 1,
            media = SeerrRequestMediaRef(tmdbId = 10, title = "Fight Club", name = "Ignored")
        )
        assertEquals("Fight Club", withTitle.mediaTitleOrNull())

        val withName = SeerrMediaRequest(
            id = 2,
            media = SeerrRequestMediaRef(tmdbId = 11, name = "The Expanse")
        )
        assertEquals("The Expanse", withName.mediaTitleOrNull())

        val blank = SeerrMediaRequest(
            id = 3,
            media = SeerrRequestMediaRef(tmdbId = 12, title = "  ", name = "")
        )
        assertNull(blank.mediaTitleOrNull())
        assertNull(SeerrMediaRequest(id = 4, media = null).mediaTitleOrNull())
    }

    @Test
    fun requestDisplaysFromCache_usesInlineThenCacheThenPlaceholder() {
        val inline = SeerrMediaRequest(
            id = 1,
            mediaType = "movie",
            media = SeerrRequestMediaRef(
                tmdbId = 100,
                mediaType = "movie",
                title = "Inline Title",
                posterPath = "/inline.jpg"
            )
        )
        val cachedOnly = SeerrMediaRequest(
            id = 2,
            mediaType = "tv",
            media = SeerrRequestMediaRef(tmdbId = 200, mediaType = "tv")
        )
        val missing = SeerrMediaRequest(
            id = 3,
            mediaType = "movie",
            media = SeerrRequestMediaRef(tmdbId = 300, mediaType = "movie")
        )
        val cache = mapOf(
            SeerrTitleCacheKey(SeerrMediaType.TV, 200) to SeerrCachedTitle(
                "Cached Show",
                "/c.jpg",
                releaseDate = "2020-01-01",
                lastAirDate = "2021-06-01",
                seriesStatus = "Ended"
            )
        )

        val rows = requestDisplaysFromCache(listOf(inline, cachedOnly, missing), cache)
        assertEquals("Inline Title", rows[0].title)
        assertEquals("/inline.jpg", rows[0].posterPath)
        assertNull(rows[0].yearLabel)
        assertEquals("Cached Show", rows[1].title)
        assertEquals("/c.jpg", rows[1].posterPath)
        assertEquals("2020", rows[1].yearLabel)
        assertEquals("Request #3", rows[2].title)
        assertNull(rows[2].posterPath)
        assertNull(rows[2].yearLabel)
    }

    @Test
    fun titleCacheKeyOrNull_requiresTmdbAndType() {
        assertNull(
            SeerrMediaRequest(id = 1, media = SeerrRequestMediaRef(tmdbId = null)).titleCacheKeyOrNull()
        )
        assertNull(
            SeerrMediaRequest(
                id = 2,
                media = SeerrRequestMediaRef(tmdbId = 1, mediaType = "music")
            ).titleCacheKeyOrNull()
        )
        assertEquals(
            SeerrTitleCacheKey(SeerrMediaType.MOVIE, 55),
            SeerrMediaRequest(
                id = 3,
                mediaType = "movie",
                media = SeerrRequestMediaRef(tmdbId = 55)
            ).titleCacheKeyOrNull()
        )
    }
}
