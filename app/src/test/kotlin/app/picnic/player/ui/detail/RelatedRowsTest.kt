package app.picnic.player.ui.detail

import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    @Test
    fun orderedPageFollowsIdSliceAndOmitsMissingItems() = runTest {
        val first = item()
        val second = item()
        val unrelated = item()
        val missing = UUID.randomUUID()
        val page = loadOrderedPage(
            RowCursor(),
            fetchIds = { listOf(second.id, missing, first.id) },
            fetchItems = { listOf(first, unrelated, second) }
        )

        assertEquals(listOf(second, first), page.items)
        assertTrue(page.endReached)
    }

    @Test
    fun orderedPagesFetchIdsOnceSliceInOrderAndEndAtLastSlice() = runTest {
        for (total in listOf(0, 7, RELATED_PAGE_SIZE * 2, RELATED_PAGE_SIZE * 2 + 3, RELATED_ID_CAP)) {
            val ranked = List(total) { item() }
            val ids = ranked.map { it.id }
            val cursor = RowCursor()
            var idFetches = 0
            val slices = mutableListOf<List<UUID>>()
            val loaded = mutableListOf<BaseItemDto>()
            var pageCount = 0
            do {
                val start = cursor.next
                val expectedSlice = ids.drop(start).take(RELATED_PAGE_SIZE)
                val page = loadOrderedPage(
                    cursor,
                    fetchIds = {
                        idFetches++
                        ids + ids.take(1)
                    },
                    fetchItems = { slice ->
                        slices.add(slice)
                        ranked.filter { it.id in slice }.reversed()
                    }
                )
                pageCount++
                assertEquals(expectedSlice, slices.last())
                assertEquals(expectedSlice, page.items.map { it.id })
                assertEquals(start + expectedSlice.size, cursor.next)
                assertEquals(cursor.next == total, page.endReached)
                loaded.addAll(page.items)
            } while (!page.endReached)

            assertEquals(1, idFetches)
            assertEquals(ranked, loaded)
            if (total == RELATED_ID_CAP) assertEquals(5, pageCount)
        }
    }

    @Test
    fun failedHydrateThrowsWithoutAdvancingCursor() = runTest {
        val ranked = List(RELATED_PAGE_SIZE + 1) { item() }
        val ids = ranked.map { it.id }
        val cursor = RowCursor()
        val fetchIds: suspend () -> List<UUID> = { ids }
        val first = loadOrderedPage(cursor, fetchIds) { ranked.take(RELATED_PAGE_SIZE) }
        assertFalse(first.endReached)

        try {
            loadOrderedPage(cursor, fetchIds) { slice ->
                assertEquals(ids.takeLast(1), slice)
                emptyList()
            }
            fail("Expected empty hydration to throw")
        } catch (_: IllegalStateException) {
            assertEquals(RELATED_PAGE_SIZE, cursor.next)
        }
    }

    @Test
    fun nearEndLoadsNextPage() {
        assertTrue(needsNextPage(RELATED_PAGE_SIZE - RELATED_PREFETCH, RELATED_PAGE_SIZE, endReached = false, inFlight = false))
        assertTrue(needsNextPage(RELATED_PAGE_SIZE - 1, RELATED_PAGE_SIZE, endReached = false, inFlight = false))
    }

    @Test
    fun farFromEndDoesNotLoadNextPage() {
        assertFalse(needsNextPage(RELATED_PAGE_SIZE - RELATED_PREFETCH - 1, RELATED_PAGE_SIZE, endReached = false, inFlight = false))
    }

    @Test
    fun endReachedDoesNotLoadNextPage() {
        assertFalse(needsNextPage(RELATED_PAGE_SIZE - 1, RELATED_PAGE_SIZE, endReached = true, inFlight = false))
    }

    @Test
    fun inFlightDoesNotLoadNextPage() {
        assertFalse(needsNextPage(RELATED_PAGE_SIZE - 1, RELATED_PAGE_SIZE, endReached = false, inFlight = true))
    }
}
