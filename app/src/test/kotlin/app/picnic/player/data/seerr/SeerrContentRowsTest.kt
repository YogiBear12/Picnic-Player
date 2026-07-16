package app.picnic.player.data.seerr

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeerrContentRowsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun movieDetails_parseCreditsCast_orderAndCap() {
        val castJson = (0..24).joinToString(separator = ",") { index ->
            val order = 24 - index
            """{"id":$index,"name":"Person $index","character":"Role $index","profilePath":"/p$index.jpg","order":$order}"""
        }
        val details = json.decodeFromString<SeerrMovieDetails>(
            """
            {
              "id": 42,
              "title": "Movie",
              "credits": {
                "cast": [$castJson]
              }
            }
            """.trimIndent()
        )

        val cast = details.castRow()

        assertEquals(SeerrCastLimit, cast.size)
        assertEquals("Person 24", cast.first().name)
        assertEquals("Role 24", cast.first().character)
        assertEquals("/p24.jpg", cast.first().profilePath)
        assertEquals("Person 5", cast.last().name)
    }

    @Test
    fun tvDetails_parseCreditsCast_filtersBlankNames() {
        val details = json.decodeFromString<SeerrTvDetails>(
            """
            {
              "id": 77,
              "name": "Series",
              "credits": {
                "cast": [
                  {"id":1,"name":"","character":"Hidden","order":0},
                  {"id":2,"name":"Visible","character":"Lead","order":1}
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(listOf("Visible"), details.castRow().map { it.name })
    }

    @Test
    fun recommendationResult_usesDefaultTypeAndMediaInfo() {
        val item = SeerrSearchResult(
            id = 100,
            mediaType = null,
            title = "Recommended Movie",
            genreIds = listOf(28, 18),
            mediaInfo = SeerrMediaInfo(
                status = SeerrMediaStatus.AVAILABLE,
                jellyfinMediaId = "abc"
            )
        ).toCatalogItem(
            genreNamesById = mapOf(28 to "Action", 18 to "Drama"),
            defaultMediaType = SeerrMediaType.MOVIE
        )

        requireNotNull(item)
        assertEquals(SeerrMediaType.MOVIE, item.mediaType)
        assertEquals("Recommended Movie", item.title)
        assertEquals(SeerrMediaStatus.AVAILABLE, item.mediaStatus)
        assertEquals("abc", item.jellyfinMediaId)
        assertEquals(listOf("Action", "Drama"), item.genreNames)
    }

    @Test
    fun recommendationResult_withoutTypeOrDefault_returnsNull() {
        assertNull(SeerrSearchResult(id = 100, mediaType = null).toCatalogItem())
    }
}
