package app.picnic.player.data.seerr

import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrPersonCreditsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun hasLibraryLink_eitherIdCounts() {
        assertFalse(null.hasLibraryLink())
        assertFalse(SeerrMediaInfo().hasLibraryLink())
        assertFalse(SeerrMediaInfo(jellyfinMediaId = "  ").hasLibraryLink())
        assertTrue(SeerrMediaInfo(jellyfinMediaId = "abc").hasLibraryLink())
        assertTrue(SeerrMediaInfo(jellyfinMediaId4k = "4k-id").hasLibraryLink())
        assertTrue(
            SeerrMediaInfo(jellyfinMediaId = "sd", jellyfinMediaId4k = "4k").hasLibraryLink()
        )
    }

    @Test
    fun mixPersonCredits_dedupesCastAndCrewByMediaTypeAndId() {
        val combined = json.decodeFromString<SeerrPersonCombinedCredits>(
            """
            {
              "id": 1,
              "cast": [
                {"id":10,"mediaType":"movie","title":"Cast Movie","posterPath":"/c.jpg"},
                {"id":20,"mediaType":"tv","name":"Cast Show"}
              ],
              "crew": [
                {"id":10,"mediaType":"movie","title":"Crew Same Movie","job":"Director"},
                {"id":30,"mediaType":"movie","title":"Crew Only","job":"Writer"},
                {"id":20,"mediaType":"movie","title":"Same Id Different Type"}
              ]
            }
            """.trimIndent()
        )

        val mixed = mixPersonCredits(combined.cast, combined.crew)

        assertEquals(
            listOf(
                SeerrMediaType.MOVIE to 10,
                SeerrMediaType.TV to 20,
                SeerrMediaType.MOVIE to 30,
                SeerrMediaType.MOVIE to 20
            ),
            mixed.map { it.mediaType to it.tmdbId }
        )
        assertEquals("Cast Movie", mixed.first().title)
    }

    @Test
    fun mixPersonCredits_carriesCharacterJobAndEpisodeCount() {
        val combined = json.decodeFromString<SeerrPersonCombinedCredits>(
            """
            {
              "cast": [
                {
                  "id": 1,
                  "mediaType": "tv",
                  "name": "Show A",
                  "character": "Himself",
                  "episodeCount": 3
                }
              ],
              "crew": [
                {
                  "id": 2,
                  "mediaType": "movie",
                  "title": "Film B",
                  "job": "Director",
                  "episodeCount": 0
                },
                {
                  "id": 1,
                  "mediaType": "tv",
                  "name": "Show A Crew",
                  "job": "Producer",
                  "episodeCount": 99
                }
              ]
            }
            """.trimIndent()
        )

        val mixed = mixPersonCredits(combined.cast, combined.crew)

        assertEquals(2, mixed.size)
        val show = mixed.first { it.tmdbId == 1 }
        assertEquals("Himself", show.creditRole)
        assertEquals(3, show.episodeCount)
        val film = mixed.first { it.tmdbId == 2 }
        assertEquals("Director", film.creditRole)
        assertEquals(0, film.episodeCount)
    }

    @Test
    fun personCreditMetaline_roleAndDetailLines() {
        assertEquals("Neo", personCreditRoleLine("Neo"))
        assertNull(personCreditRoleLine("  "))

        assertEquals("1999", personCreditDetailLine(SeerrMediaType.MOVIE, "1999-03-31", null))
        assertNull(personCreditDetailLine(SeerrMediaType.MOVIE, null, null))

        assertEquals("12 episodes", personCreditDetailLine(SeerrMediaType.TV, null, 12))
        assertEquals("2 episodes", personCreditDetailLine(SeerrMediaType.TV, null, 2))
        assertEquals("1 episode", personCreditDetailLine(SeerrMediaType.TV, null, 1))
        assertNull(personCreditDetailLine(SeerrMediaType.TV, null, 0))
    }

    @Test
    fun toCatalogItem_carriesPersonCreditFields() {
        val item = credit(
            tmdbId = 42,
            type = SeerrMediaType.TV,
            creditRole = "Lead",
            episodeCount = 8
        ).toCatalogItem()

        assertEquals("Lead", item.creditRole)
        assertEquals(8, item.episodeCount)
    }

    @Test
    fun libraryLinkedPersonCredits_keepsEitherJellyfinId() {
        val credits = listOf(
            credit(1, SeerrMediaType.MOVIE, jellyfinMediaId = "jf-1"),
            credit(2, SeerrMediaType.MOVIE, jellyfinMediaId4k = "jf-4k"),
            credit(3, SeerrMediaType.TV),
            credit(4, SeerrMediaType.TV, jellyfinMediaId = "  ")
        )

        assertEquals(
            listOf(1, 2),
            libraryLinkedPersonCredits(credits).map { it.tmdbId }
        )
    }

    @Test
    fun parseReleaseDate_acceptsIsoPrefix() {
        assertEquals(LocalDate.of(2020, 1, 15), parseReleaseDate("2020-01-15"))
        assertEquals(LocalDate.of(2020, 1, 15), parseReleaseDate("2020-01-15T12:00:00Z"))
        assertNull(parseReleaseDate(null))
        assertNull(parseReleaseDate(""))
        assertNull(parseReleaseDate("not-a-date"))
        assertNull(parseReleaseDate("2020-13-01"))
    }

    @Test
    fun sortedByReleaseDateDesc_newestFirst_nullsLast() {
        val credits = listOf(
            credit(1, SeerrMediaType.MOVIE, releaseDate = "2010-01-01"),
            credit(2, SeerrMediaType.MOVIE, releaseDate = null),
            credit(3, SeerrMediaType.TV, releaseDate = "2022-06-01"),
            credit(4, SeerrMediaType.MOVIE, releaseDate = "bogus"),
            credit(5, SeerrMediaType.TV, releaseDate = "2022-06-02")
        )

        assertEquals(
            listOf(5, 3, 1, 2, 4),
            credits.sortedByReleaseDateDesc { it.releaseDate }.map { it.tmdbId }
        )
    }

    @Test
    fun toCatalogItem_mapsMediaInfoForResolveSeerrNavKey() {
        val item = credit(
            tmdbId = 99,
            type = SeerrMediaType.TV,
            jellyfinMediaId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            status = SeerrMediaStatus.AVAILABLE
        ).toCatalogItem()

        assertEquals(99, item.tmdbId)
        assertEquals(SeerrMediaType.TV, item.mediaType)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", item.jellyfinMediaId)
        assertEquals(SeerrMediaStatus.AVAILABLE, item.mediaStatus)
    }

    // Person credits now carry both ids separately; the Hybrid Detail gate owns
    // the HD-else-4K fallback (previously coalesced here).
    @Test
    fun toCatalogItem_4kOnlyId_carriesFourKForGate() {
        val item = credit(
            tmdbId = 7,
            type = SeerrMediaType.MOVIE,
            jellyfinMediaId4k = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        ).toCatalogItem()

        assertNull(item.jellyfinMediaId)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", item.jellyfinMediaId4k)
        // Gate routes a 4K-only credit to Jellyfin Detail.
        assertTrue(seerrDetailTarget(item.jellyfinMediaId, item.jellyfinMediaId4k) is DetailTarget.Jellyfin)
    }

    @Test
    fun toCatalogItem_bothIds_prefersSdHd() {
        val item = credit(
            tmdbId = 8,
            type = SeerrMediaType.MOVIE,
            jellyfinMediaId = "cccccccccccccccccccccccccccccccc",
            jellyfinMediaId4k = "dddddddddddddddddddddddddddddddd"
        ).toCatalogItem()

        assertEquals("cccccccccccccccccccccccccccccccc", item.jellyfinMediaId)
        assertEquals("dddddddddddddddddddddddddddddddd", item.jellyfinMediaId4k)
        assertEquals(
            DetailTarget.Jellyfin("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            seerrDetailTarget(item.jellyfinMediaId, item.jellyfinMediaId4k)
        )
    }

    @Test
    fun personDetails_parsesCamelCaseFields() {
        val person = json.decodeFromString<SeerrPersonDetails>(
            """
            {
              "id": 287,
              "name": "Brad Pitt",
              "biography": "Actor.",
              "birthday": "1963-12-18",
              "placeOfBirth": "Shawnee, Oklahoma",
              "profilePath": "/brad.jpg",
              "gender": 2,
              "popularity": 12.5
            }
            """.trimIndent()
        )

        assertEquals(287, person.id)
        assertEquals("Brad Pitt", person.name)
        assertEquals("/brad.jpg", person.profilePath)
        assertEquals("1963-12-18", person.birthday)
    }

    private fun credit(
        tmdbId: Int,
        type: SeerrMediaType,
        jellyfinMediaId: String? = null,
        jellyfinMediaId4k: String? = null,
        status: Int? = null,
        releaseDate: String? = null,
        creditRole: String? = null,
        episodeCount: Int? = null
    ) = SeerrPersonCredit(
        tmdbId = tmdbId,
        mediaType = type,
        title = "Title $tmdbId",
        releaseDate = releaseDate,
        creditRole = creditRole,
        episodeCount = episodeCount,
        mediaInfo = if (jellyfinMediaId != null || jellyfinMediaId4k != null || status != null) {
            SeerrMediaInfo(
                status = status,
                jellyfinMediaId = jellyfinMediaId,
                jellyfinMediaId4k = jellyfinMediaId4k
            )
        } else {
            null
        }
    )
}
