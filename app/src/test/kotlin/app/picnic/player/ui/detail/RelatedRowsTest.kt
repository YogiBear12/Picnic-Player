package app.picnic.player.ui.detail

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedRowsTest {
    private fun person(name: String, kind: PersonKind) = BaseItemPerson(id = UUID.randomUUID(), name = name, type = kind)

    private fun item(
        type: BaseItemKind = BaseItemKind.MOVIE,
        people: List<BaseItemPerson> = emptyList(),
        genres: List<NameGuidPair> = emptyList()
    ) = BaseItemDto(id = UUID.randomUUID(), type = type, people = people, genreItems = genres)

    @Test
    fun similarSpecComesFirstForEveryItemType() {
        BaseItemKind.entries.forEach { type ->
            val item = item(type = type)
            val spec = relatedRowSpecs(item).first()

            assertEquals(ROW_SIMILAR, spec.key)
            assertEquals("More like this", spec.title)
            assertEquals(RelatedRowQuery.Similar, spec.query)
            assertTrue(spec.eager)
        }
    }

    @Test
    fun firstActorInBillingOrderIsPicked() {
        val director = person("Director", PersonKind.DIRECTOR)
        val first = person("Lead", PersonKind.ACTOR)
        val second = person("Second", PersonKind.ACTOR)
        val movie = item(people = listOf(director, first, second))
        val spec = relatedRowSpecs(movie).first { it.key == ROW_FEATURING }

        assertEquals("Featuring Lead", spec.title)
        assertEquals(RelatedRowQuery.Person(first.id, PersonKind.ACTOR, BaseItemKind.MOVIE), spec.query)
        assertFalse(spec.eager)
    }

    @Test
    fun movieUsesFirstDirector() {
        val first = person("First", PersonKind.DIRECTOR)
        val specs = relatedRowSpecs(
            item(people = listOf(person("Creator", PersonKind.CREATOR), first, person("Second", PersonKind.DIRECTOR)))
        )

        assertEquals(listOf("More like this", "Directed by First"), specs.map { it.title })
        assertEquals(RelatedRowQuery.Person(first.id, PersonKind.DIRECTOR, BaseItemKind.MOVIE), specs.last().query)
    }

    @Test
    fun samePersonKeepsSeparateActingAndDirectingKinds() {
        val actor = person("Lead", PersonKind.ACTOR)
        val director = actor.copy(type = PersonKind.DIRECTOR)
        val specs = relatedRowSpecs(item(people = listOf(actor, director)))

        assertEquals(
            listOf(RelatedRowQuery.Similar, RelatedRowQuery.Person(actor.id, PersonKind.ACTOR, BaseItemKind.MOVIE), RelatedRowQuery.Person(actor.id, PersonKind.DIRECTOR, BaseItemKind.MOVIE)),
            specs.map { it.query }
        )
    }

    @Test
    fun movieDoesNotFallBackToWriter() {
        assertEquals(listOf(RelatedRowQuery.Similar), relatedRowSpecs(item(people = listOf(person("Writer", PersonKind.WRITER)))).map { it.query })
    }

    @Test
    fun seriesUsesFirstCreatorBeforeWriter() {
        val creator = person("Creator", PersonKind.CREATOR)
        val specs = relatedRowSpecs(
            item(
                type = BaseItemKind.SERIES,
                people = listOf(person("Writer", PersonKind.WRITER), creator, person("Other", PersonKind.CREATOR))
            )
        )

        assertEquals(listOf("More like this", "Created by Creator"), specs.map { it.title })
        assertEquals(RelatedRowQuery.Person(creator.id, PersonKind.CREATOR, BaseItemKind.SERIES), specs.last().query)
        assertFalse(specs.last().eager)
    }

    @Test
    fun seriesWithoutCreatorUsesFirstWriter() {
        val writer = person("Writer", PersonKind.WRITER)
        val specs = relatedRowSpecs(
            item(type = BaseItemKind.SERIES, people = listOf(writer, person("Other", PersonKind.WRITER)))
        )

        assertEquals(listOf("More like this", "Written by Writer"), specs.map { it.title })
        assertEquals(RelatedRowQuery.Person(writer.id, PersonKind.WRITER, BaseItemKind.SERIES), specs.last().query)
    }

    @Test
    fun seriesWithoutCreatorOrWriterHasNoCreditRow() {
        val specs = relatedRowSpecs(item(type = BaseItemKind.SERIES, people = listOf(person("Director", PersonKind.DIRECTOR))))
        assertEquals(listOf(RelatedRowQuery.Similar), specs.map { it.query })
    }

    @Test
    fun firstGenreFollowsActorAndCredit() {
        val genre = NameGuidPair(name = "Drama", id = UUID.randomUUID())
        val specs = relatedRowSpecs(
            item(
                people = listOf(person("Lead", PersonKind.ACTOR), person("Director", PersonKind.DIRECTOR)),
                genres = listOf(genre, NameGuidPair(name = "Comedy", id = UUID.randomUUID()))
            )
        )

        assertEquals(listOf("More like this", "Featuring Lead", "Directed by Director", "More in Drama"), specs.map { it.title })
        assertEquals(RelatedRowQuery.Genre(genre.id, BaseItemKind.MOVIE), specs.last().query)
        assertEquals(listOf(ROW_SIMILAR, ROW_FEATURING, ROW_CREDIT, ROW_GENRE), specs.map { it.key })
        assertEquals(listOf(true, false, false, false), specs.map { it.eager })
    }

    @Test
    fun missingGenreHasNoGenreRow() {
        assertEquals(listOf(RelatedRowQuery.Similar), relatedRowSpecs(item()).map { it.query })
    }

    @Test
    fun otherItemTypesHaveOnlySimilarRow() {
        BaseItemKind.entries.filter { it != BaseItemKind.MOVIE && it != BaseItemKind.SERIES }.forEach { type ->
            val specs = relatedRowSpecs(
                item(
                    type = type,
                    people = listOf(person("Lead", PersonKind.ACTOR), person("Director", PersonKind.DIRECTOR)),
                    genres = listOf(NameGuidPair(name = "Drama", id = UUID.randomUUID()))
                )
            )
            assertEquals("Unexpected rows for $type", listOf(RelatedRowQuery.Similar), specs.map { it.query })
        }
    }

    @Test
    fun nothingFocusableAutoLoadsRelatedRows() {
        assertTrue(needsAutoLoad(hasPeople = false, eagerRowsEmpty = true, hasCollections = false))
    }

    @Test
    fun castPresentWaitsToLoadRelatedRows() {
        assertFalse(needsAutoLoad(hasPeople = true, eagerRowsEmpty = true, hasCollections = false))
    }

    @Test
    fun loadedRowsWaitBehindSkeletonsWhileEmptyRowsStayHidden() {
        val loaded = RelatedRow(ROW_SIMILAR, "More like this", listOf(item()))
        val loading = RelatedRow(ROW_FEATURING, "Featuring Lead")
        val hidden = RelatedRow(ROW_CREDIT, "Directed by Director", emptyList())
        val later = RelatedRow(ROW_GENRE, "More in Drama", listOf(item()))

        assertEquals(
            listOf(loaded, loading, later.copy(items = null)),
            rowsInDisplayOrder(listOf(loaded, loading, hidden, later))
        )
        assertEquals(listOf(loaded, later), rowsInDisplayOrder(listOf(loaded, loading.copy(items = emptyList()), hidden, later)))
        val settled = loading.copy(items = listOf(item()))
        assertEquals(listOf(loaded, settled, later), rowsInDisplayOrder(listOf(loaded, settled, hidden, later)))
    }
}
