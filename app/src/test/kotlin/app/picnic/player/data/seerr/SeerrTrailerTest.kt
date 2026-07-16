package app.picnic.player.data.seerr

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeerrTrailerTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun youtubeTrailerUrl_picksHighestSizeTrailer() {
        val videos = listOf(
            SeerrRelatedVideo(
                url = "https://www.youtube.com/watch?v=teaser",
                key = "teaser",
                type = "Teaser",
                size = 2160,
                site = "YouTube"
            ),
            SeerrRelatedVideo(
                url = "https://www.youtube.com/watch?v=low",
                key = "low",
                type = "Trailer",
                size = 720,
                site = "YouTube"
            ),
            SeerrRelatedVideo(
                url = "https://www.youtube.com/watch?v=hi",
                key = "hi",
                type = "Trailer",
                size = 1080,
                site = "YouTube"
            )
        )

        assertEquals("https://www.youtube.com/watch?v=hi", videos.youtubeTrailerUrl())
    }

    @Test
    fun youtubeTrailerUrl_buildsFromKeyWhenUrlMissing() {
        val videos = listOf(
            SeerrRelatedVideo(key = "abc123", type = "Trailer", size = 1080, site = "YouTube")
        )

        assertEquals("https://www.youtube.com/watch?v=abc123", videos.youtubeTrailerUrl())
    }

    @Test
    fun youtubeTrailerUrl_nullWhenNoTrailerType() {
        val videos = listOf(
            SeerrRelatedVideo(
                url = "https://www.youtube.com/watch?v=clip",
                type = "Clip",
                size = 1080,
                site = "YouTube"
            )
        )

        assertNull(videos.youtubeTrailerUrl())
    }

    @Test
    fun movieDetails_toCatalogItem_mapsRelatedVideosTrailer() {
        val details = json.decodeFromString<SeerrMovieDetails>(
            """
            {
              "id": 9,
              "title": "Film",
              "relatedVideos": [
                {
                  "url": "https://www.youtube.com/watch?v=9qhL2_UxXM0",
                  "key": "9qhL2_UxXM0",
                  "name": "Official Trailer",
                  "size": 1080,
                  "type": "Trailer",
                  "site": "YouTube"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            "https://www.youtube.com/watch?v=9qhL2_UxXM0",
            details.toCatalogItem().trailerUrl
        )
    }
}
