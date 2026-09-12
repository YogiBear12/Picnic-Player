package app.picnic.player.data.seerr

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrFilmographyTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun emptyPayload_producesNoTabs() {
        val tabs = seerrFilmography(SeerrPersonCombinedCredits(), knownForDepartment = "Acting")
        assertEquals(emptyList<FilmographyTab>(), tabs.map { it.tab })
    }

    @Test
    fun selfDetection_blankStaysActor_hostAndSelfGoToAppearances() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"Blank","character":""},
                {"id":2,"mediaType":"movie","title":"Spaces","character":"  "},
                {"id":3,"mediaType":"movie","title":"Bare Self","character":"Self"},
                {"id":4,"mediaType":"tv","name":"Host","character":"Self - Host"},
                {"id":5,"mediaType":"movie","title":"Archive","character":"Himself (archive footage)"},
                {"id":6,"mediaType":"movie","title":"Herself","character":"Herself"},
                {"id":7,"mediaType":"tv","name":"Them","character":"Themselves"},
                {"id":8,"mediaType":"movie","title":"Neo","character":"Neo"},
                {"id":9,"mediaType":"movie","title":"Host Role","character":"Host"},
                {"id":10,"mediaType":"movie","title":"Selfish","character":"Selfish"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals(
            listOf(FilmographyTab.Actor, FilmographyTab.Appearances),
            tabs.map { it.tab }
        )
        assertEquals(
            listOf("Blank", "Spaces", "Neo", "Selfish"),
            tabs.actorTitles()
        )
        assertNull(tabs.row(FilmographyTab.Actor, 1).roleAs)
        assertNull(tabs.row(FilmographyTab.Actor, 2).roleAs)
        assertEquals(
            listOf("Bare Self", "Host", "Archive", "Herself", "Them", "Host Role"),
            tabs.tabTitles(FilmographyTab.Appearances)
        )
        assertEquals("Self", tabs.row(FilmographyTab.Appearances, 3).roleAs)
        assertEquals("Himself (archive footage)", tabs.row(FilmographyTab.Appearances, 5).roleAs)
        assertEquals("Herself", tabs.row(FilmographyTab.Appearances, 6).roleAs)
        assertEquals("Themselves", tabs.row(FilmographyTab.Appearances, 7).roleAs)
        assertEquals("Host", tabs.row(FilmographyTab.Appearances, 9).roleAs)
    }

    @Test
    fun departmentMap_unmappedLandsInAdditionalCredits() {
        val combined = decode(
            """
            {
              "crew": [
                {"id":1,"mediaType":"movie","title":"Dir","department":"Directing","job":"Director"},
                {"id":2,"mediaType":"movie","title":"Write","department":"Writing","job":"Writer"},
                {"id":3,"mediaType":"movie","title":"Prod","department":"Production","job":"Producer"},
                {"id":4,"mediaType":"movie","title":"Score","department":"Sound","job":"Original Music Composer"},
                {"id":5,"mediaType":"movie","title":"Cam","department":"Camera","job":"Director of Photography"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Directing")
        assertEquals(
            listOf(
                FilmographyTab.Director,
                FilmographyTab.Writer,
                FilmographyTab.Producer,
                FilmographyTab.Composer,
                FilmographyTab.AdditionalCredits
            ),
            tabs.map { it.tab }
        )
        assertEquals("Director of Photography", tabs.row(FilmographyTab.AdditionalCredits, 5).roleAs)
        assertEquals("Original Music Composer", tabs.row(FilmographyTab.Composer, 4).roleAs)
        assertNull(tabs.row(FilmographyTab.Director, 1).roleAs)
        assertNull(tabs.row(FilmographyTab.Writer, 2).roleAs)
        assertNull(tabs.row(FilmographyTab.Producer, 3).roleAs)
    }

    @Test
    fun soundDepartment_onlyCompositionJobsGoToComposer() {
        val combined = decode(
            """
            {
              "crew": [
                {"id":1,"mediaType":"movie","title":"Score","department":"Sound","job":"Original Music Composer","releaseDate":"2020-01-01"},
                {"id":2,"mediaType":"movie","title":"Tune","department":"Sound","job":"Composer","releaseDate":"2021-01-01"},
                {"id":3,"mediaType":"movie","title":"Cue","department":"Sound","job":"Music","releaseDate":"2022-01-01"},
                {"id":4,"mediaType":"movie","title":"Track","department":"Sound","job":"Songs","releaseDate":"2023-01-01"},
                {"id":5,"mediaType":"movie","title":"Foley","department":"Sound","job":"Foley Artist","releaseDate":"2024-01-01"},
                {"id":6,"mediaType":"movie","title":"Mix","department":"Sound","job":"Sound Mixer","releaseDate":"2019-01-01"},
                {"id":7,"mediaType":"movie","title":"Sup","department":"Sound","job":"Music Supervisor","releaseDate":"2018-01-01"},
                {"id":8,"mediaType":"movie","title":"Both","department":"Sound","job":"Original Music Composer","releaseDate":"2017-01-01"},
                {"id":8,"mediaType":"movie","title":"Both","department":"Sound","job":"Sound Designer","releaseDate":"2017-01-01"},
                {"id":9,"mediaType":"movie","title":"Hidden","department":"Sound","job":"Composer (uncredited)","releaseDate":"2016-01-01"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Sound")
        assertEquals(
            listOf(FilmographyTab.Composer, FilmographyTab.AdditionalCredits),
            tabs.map { it.tab }
        )
        assertEquals(
            listOf("Track", "Cue", "Tune", "Score", "Both", "Hidden"),
            tabs.tabTitles(FilmographyTab.Composer)
        )
        assertNull(tabs.row(FilmographyTab.Composer, 2).roleAs)
        assertEquals("Music", tabs.row(FilmographyTab.Composer, 3).roleAs)
        assertEquals("Songs", tabs.row(FilmographyTab.Composer, 4).roleAs)
        assertEquals(
            listOf("Foley", "Mix", "Sup", "Both"),
            tabs.tabTitles(FilmographyTab.AdditionalCredits)
        )
        assertEquals("Sound Designer", tabs.row(FilmographyTab.AdditionalCredits, 8).roleAs)
        assertEquals("Composer (uncredited)", tabs.row(FilmographyTab.Composer, 9).roleAs)
    }

    @Test
    fun perTabDedup_joinsRolesAndDropsCanonicalJob() {
        val combined = decode(
            """
            {
              "crew": [
                {"id":10,"mediaType":"movie","title":"Dune","department":"Production","job":"Producer","releaseDate":"2021-01-01"},
                {"id":10,"mediaType":"movie","title":"Dune","department":"Production","job":"Executive Producer","releaseDate":"2021-01-01"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Production")
        assertEquals(1, tabs.single().rows.size)
        val row = tabs.single().rows.single()
        assertEquals("Dune", row.title)
        assertEquals("Executive Producer", row.roleAs)
    }

    @Test
    fun sameTitle_canAppearInTwoTabs() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":10,"mediaType":"movie","title":"Dune","character":"Leto","releaseDate":"2021-01-01"}
              ],
              "crew": [
                {"id":10,"mediaType":"movie","title":"Dune","department":"Directing","job":"Director","releaseDate":"2021-01-01"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals(listOf(FilmographyTab.Actor, FilmographyTab.Director), tabs.map { it.tab })
        assertEquals("Leto", tabs.row(FilmographyTab.Actor, 10).roleAs)
        assertNull(tabs.row(FilmographyTab.Director, 10).roleAs)
    }

    @Test
    fun rows_newestFirst_undatedLast() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"Old","character":"A","releaseDate":"2010-01-01"},
                {"id":2,"mediaType":"movie","title":"None","character":"B"},
                {"id":3,"mediaType":"movie","title":"New","character":"C","releaseDate":"2022-06-02"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("New", "Old", "None"),
            seerrFilmography(combined, knownForDepartment = "Acting").actorTitles()
        )
    }

    @Test
    fun missingYear_isBlankAndLibraryFlagFollowsJellyfinId() {
        val combined = decode(
            """
            {
              "cast": [
                {
                  "id": 1,
                  "mediaType": "movie",
                  "title": "Linked",
                  "character": "Hero",
                  "releaseDate": "1999-03-31",
                  "mediaInfo": { "jellyfinMediaId": "abc" }
                },
                {
                  "id": 2,
                  "mediaType": "tv",
                  "name": "Undated",
                  "character": "Guest"
                }
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        val linked = tabs.row(FilmographyTab.Actor, 1)
        assertEquals("1999", linked.year)
        assertTrue(linked.inLibrary)

        val undated = tabs.row(FilmographyTab.Appearances, 2)
        assertNull(undated.year)
        assertFalse(undated.inLibrary)
    }

    @Test
    fun knownForDepartment_drivesTabOrder() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"Act","character":"A"},
                {"id":6,"mediaType":"tv","name":"Talk","character":"Self"}
              ],
              "crew": [
                {"id":2,"mediaType":"movie","title":"Dir","department":"Directing","job":"Director"},
                {"id":3,"mediaType":"movie","title":"Write","department":"Writing","job":"Writer"},
                {"id":4,"mediaType":"movie","title":"Prod","department":"Production","job":"Producer"},
                {"id":5,"mediaType":"movie","title":"Score","department":"Sound","job":"Original Music Composer"},
                {"id":7,"mediaType":"movie","title":"Cam","department":"Camera","job":"Director of Photography"}
              ]
            }
            """.trimIndent()
        )
        val crewFirst = listOf(
            FilmographyTab.Director,
            FilmographyTab.Writer,
            FilmographyTab.Producer,
            FilmographyTab.Composer,
            FilmographyTab.Actor,
            FilmographyTab.Appearances,
            FilmographyTab.AdditionalCredits
        )
        val expected = mapOf(
            "Acting" to listOf(FilmographyTab.Actor) + crewFirst.filter { it != FilmographyTab.Actor },
            "Directing" to crewFirst,
            "Sound" to listOf(FilmographyTab.Composer) + crewFirst.filter { it != FilmographyTab.Composer },
            "Camera" to crewFirst
        )

        expected.forEach { (department, order) ->
            assertEquals(order, seerrFilmography(combined, department).map { it.tab })
        }
    }

    @Test
    fun knownForMissing_firstTabIsLargestNamed() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"A1","character":"A"},
                {"id":2,"mediaType":"movie","title":"A2","character":"B"},
                {"id":6,"mediaType":"tv","name":"Talk","character":"Self"},
                {"id":7,"mediaType":"tv","name":"Talk 2","character":"Self"}
              ],
              "crew": [
                {"id":3,"mediaType":"movie","title":"P1","department":"Production","job":"Producer"},
                {"id":4,"mediaType":"movie","title":"P2","department":"Production","job":"Producer"},
                {"id":5,"mediaType":"movie","title":"P3","department":"Production","job":"Producer"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = null)
        assertEquals(
            listOf(FilmographyTab.Producer, FilmographyTab.Actor, FilmographyTab.Appearances),
            tabs.map { it.tab }
        )
        assertEquals(3, tabs[0].rows.size)
    }

    @Test
    fun twoActorCharacters_joinInFirstSeenOrder() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"Film","character":"Jesse Pinkman","releaseDate":"2008-01-01"},
                {"id":1,"mediaType":"movie","title":"Film","character":"Heisenberg","releaseDate":"2008-01-01"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            "Jesse Pinkman, Heisenberg",
            seerrFilmography(combined, knownForDepartment = "Acting").row(FilmographyTab.Actor, 1).roleAs
        )
    }

    @Test
    fun characterClassification_wholeSegmentDecidesAppearances() {
        val actor = listOf(
            "Selfish",
            "Hostage",
            "Scare Center Host 2",
            "The Host",
            "Party Guest"
        )
        val appearances = listOf(
            "Host",
            "Co-Host",
            "Guest",
            "Guest Star",
            "Special Guest",
            "Self",
            "Self - Host",
            "Host (uncredited)"
        )
        val cast = (actor + appearances).mapIndexed { index, character ->
            """{"id":${index + 1},"mediaType":"movie","title":"$character","character":"$character"}"""
        }
        val combined = decode("""{"cast":[${cast.joinToString(",")}]}""")

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals(actor, tabs.actorTitles())
        assertEquals(appearances, tabs.tabTitles(FilmographyTab.Appearances))
    }

    @Test
    fun mixedCharacter_appearsInActorAndAppearances() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"Doc","character":"Himself / Narrator","releaseDate":"2019-01-01"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals(listOf(FilmographyTab.Actor, FilmographyTab.Appearances), tabs.map { it.tab })
        assertEquals("Narrator", tabs.row(FilmographyTab.Actor, 1).roleAs)
        assertEquals("Himself", tabs.row(FilmographyTab.Appearances, 1).roleAs)
    }

    @Test
    fun crewInActingDepartment_landsInAdditionalCredits() {
        val combined = decode(
            """
            {
              "crew": [
                {"id":1,"mediaType":"movie","title":"Voices","department":"Acting","job":"Voice"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals(listOf(FilmographyTab.AdditionalCredits), tabs.map { it.tab })
        assertEquals("Voice", tabs.row(FilmographyTab.AdditionalCredits, 1).roleAs)
    }

    @Test
    fun appearancesMerge_keepsHostGuestAndDropsBareSelf() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"tv","name":"Talk","character":"Self","firstAirDate":"2020-01-01"},
                {"id":1,"mediaType":"tv","name":"Talk","character":"Self - Guest","firstAirDate":"2020-01-01"},
                {"id":2,"mediaType":"tv","name":"Only Self","character":"Herself","firstAirDate":"2021-01-01"},
                {"id":3,"mediaType":"tv","name":"Both","character":"Self - Host","firstAirDate":"2022-01-01"},
                {"id":3,"mediaType":"tv","name":"Both","character":"Self - Guest","firstAirDate":"2022-01-01"},
                {"id":4,"mediaType":"tv","name":"Bare","character":"Host","firstAirDate":"2023-01-01"},
                {"id":4,"mediaType":"tv","name":"Bare","character":"Guest","firstAirDate":"2023-01-01"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals("Self - Guest", tabs.row(FilmographyTab.Appearances, 1).roleAs)
        assertEquals("Herself", tabs.row(FilmographyTab.Appearances, 2).roleAs)
        assertEquals("Self - Host, Guest", tabs.row(FilmographyTab.Appearances, 3).roleAs)
        assertEquals("Self - Host, Guest", tabs.row(FilmographyTab.Appearances, 4).roleAs)
    }

    @Test
    fun multiSegmentCharacter_joinsEverySegment() {
        val combined = decode(
            """
            {
              "cast": [
                {"id":1,"mediaType":"movie","title":"Casper's Scare School","character":"Punk Kid / Scare Center Host 2 / Pumpkinhead","releaseDate":"2006-01-01"}
              ]
            }
            """.trimIndent()
        )

        val tabs = seerrFilmography(combined, knownForDepartment = "Acting")
        assertEquals(listOf(FilmographyTab.Actor), tabs.map { it.tab })
        assertEquals(
            "Punk Kid, Scare Center Host 2, Pumpkinhead",
            tabs.row(FilmographyTab.Actor, 1).roleAs
        )
    }

    private fun decode(raw: String): SeerrPersonCombinedCredits = json.decodeFromString(raw)

    private fun List<FilmographyTabContent>.actorTitles(): List<String> = tabTitles(FilmographyTab.Actor)

    private fun List<FilmographyTabContent>.tabTitles(tab: FilmographyTab): List<String> = first { it.tab == tab }.rows.map { it.title }

    private fun List<FilmographyTabContent>.row(tab: FilmographyTab, tmdbId: Int): FilmographyRow = first { it.tab == tab }.rows.first { it.item.tmdbId == tmdbId }
}
