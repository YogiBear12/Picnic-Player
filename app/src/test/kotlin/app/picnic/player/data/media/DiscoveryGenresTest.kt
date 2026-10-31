package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryGenresTest {
    @Test
    fun mergedGenresProduceOneGroupWithEveryAlias() {
        val merged = mapOf(
            "Action and adventure" to listOf("Action", "Adventure", "Action & Adventure"),
            "Sci-fi and fantasy" to listOf("Science Fiction", "Fantasy", "Sci-Fi & Fantasy"),
            "Kids and family" to listOf("Kids", "Family"),
            "Horror and thriller" to listOf("Horror", "Thriller", "Suspense"),
            "War and history" to listOf("War", "History", "War & Politics"),
            "News and talk" to listOf("News", "Talk", "Talk Show", "Talk-Show"),
            "Soap operas and romance" to listOf("Soap", "Romance")
        )
        for ((name, genres) in merged) {
            for (available in listOf(genres) + genres.map { listOf(it.lowercase()) }) {
                val groups = genreGroups(mapOf(BaseItemKind.SERIES to available.toSet()))
                val group = groups.getValue(DiscoveryType.TV).single()
                assertEquals(name, group.name)
                assertEquals(genres, group.genres)
            }
        }
    }

    @Test
    fun availabilityIsSpecificToEachType() {
        val groups = genreGroups(
            mapOf(
                BaseItemKind.MOVIE to setOf("Action", "Comedy", "Reality", "News"),
                BaseItemKind.SERIES to setOf("Drama", "Reality", "Talk")
            )
        )
        assertEquals(listOf("Action and adventure", "Comedy"), groups.getValue(DiscoveryType.MOVIE).map { it.name })
        assertEquals(listOf("News and talk", "Drama", "Reality"), groups.getValue(DiscoveryType.TV).map { it.name })
        assertTrue(groups.getValue(DiscoveryType.MIXED).isEmpty())
    }

    @Test
    fun mixedRequiresBothUsableTypesAndMixedTitle() {
        val groups = genreGroups(
            mapOf(
                BaseItemKind.MOVIE to setOf("ACTION", "Comedy", "News", "Reality", "Sports"),
                BaseItemKind.SERIES to setOf("action & adventure", "Drama", "News", "Reality", "sports")
            )
        )
        assertEquals(listOf("Action and adventure"), groups.getValue(DiscoveryType.MIXED).map { it.name })
    }

    @Test
    fun tvMovieIsExcludedRegardlessOfCase() {
        val groups = genreGroups(
            mapOf(BaseItemKind.MOVIE to setOf("TV Movie"), BaseItemKind.SERIES to setOf("tv movie", "TV MOVIE"))
        )
        assertTrue(groups.values.all { it.isEmpty() })
    }

    @Test
    fun unknownGenresUseSuffixesWithoutMixedForm() {
        val groups = genreGroups(
            mapOf(BaseItemKind.MOVIE to setOf("Sports"), BaseItemKind.SERIES to setOf("SPORTS", "Music"))
        )
        val sports = groups.getValue(DiscoveryType.MOVIE).single()
        assertEquals("Sports", sports.name)
        assertEquals(listOf("Sports"), sports.genres)
        assertEquals("Sports movies", sports.title(DiscoveryType.MOVIE))
        assertEquals("Sports TV shows", sports.title(DiscoveryType.TV))
        assertNull(sports.title(DiscoveryType.MIXED))
        assertEquals(listOf("Sports", "Music"), groups.getValue(DiscoveryType.TV).map { it.name })
        assertEquals("Music TV shows", groups.getValue(DiscoveryType.TV).last().title(DiscoveryType.TV))
        assertTrue(groups.getValue(DiscoveryType.MIXED).isEmpty())
    }

    @Test
    fun showGenreSpellingsJoinTheirGroupsWithoutDoubledWords() {
        val tv = genreGroups(mapOf(BaseItemKind.SERIES to setOf("Talk Show", "Talk-Show", "Game Show", "Game-Show", "Reality", "Mini-Series", "Miniseries")))
            .getValue(DiscoveryType.TV)
        assertEquals(listOf("News and talk shows", "Game shows", "Reality TV shows", "Miniseries"), tv.map { it.title(DiscoveryType.TV) })
    }

    @Test
    fun emptyOrUnrelatedKindsHaveNoGroups() {
        assertTrue(genreGroups(emptyMap()).values.all { it.isEmpty() })
        assertTrue(genreGroups(mapOf(BaseItemKind.EPISODE to setOf("Comedy"))).values.all { it.isEmpty() })
    }

    @Test
    fun everyTableTitleMatchesThePlan() {
        val expected = listOf(
            listOf("Action and adventure", "Action and adventure movies", "Action and adventure TV shows", "Action and adventure"),
            listOf("Sci-fi and fantasy", "Sci-fi and fantasy movies", "Sci-fi and fantasy TV shows", "Sci-fi and fantasy"),
            listOf("Kids and family", "Kids and family movies", "Kids and family TV shows", "Kids and family"),
            listOf("Horror and thriller", "Horror and thriller movies", "Horror and thriller TV shows", "Horror and thrillers"),
            listOf("War and history", "War and history movies", "War and politics TV shows", "War and history"),
            listOf("News and talk", null, "News and talk shows", null),
            listOf("Game shows", null, "Game shows", null),
            listOf("Soap operas and romance", "Romance movies", "Soap operas and romance TV shows", "Romance"),
            listOf("Animation", "Animated movies", "Animated TV shows", "Animation"),
            listOf("Documentary", "Documentaries", "Documentary TV shows", "Documentaries"),
            listOf("Comedy", "Comedy movies", "Comedy TV shows", "Comedies"),
            listOf("Drama", "Drama movies", "Drama TV shows", "Dramas"),
            listOf("Mystery", "Mystery movies", "Mystery TV shows", "Mysteries"),
            listOf("Western", "Western movies", "Western TV shows", "Westerns"),
            listOf("Reality", null, "Reality TV shows", null),
            listOf("Miniseries", null, "Miniseries", null)
        )
        val genres = setOf(
            "Action", "Science Fiction", "Kids", "Horror", "War", "News", "Soap",
            "Animation", "Documentary", "Comedy", "Drama", "Mystery", "Western", "Reality", "Game Show", "Mini-Series"
        )
        val groups = genreGroups(mapOf(BaseItemKind.MOVIE to genres, BaseItemKind.SERIES to genres))
        val allGroups = groups.getValue(DiscoveryType.TV)
        assertEquals(expected, allGroups.map { group -> listOf(group.name) + DiscoveryType.entries.map { group.title(it) } })
        for ((index, type) in DiscoveryType.entries.withIndex()) {
            assertEquals(
                expected.filter { it[index + 1] != null }.map { it.first() },
                groups.getValue(type).map { it.name }
            )
        }
    }
}
