package app.picnic.player.ui.common

import app.picnic.player.ui.detail.RELATED_ID_CAP
import app.picnic.player.ui.detail.RELATED_PAGE_SIZE
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RowPagerTest {
    private fun item() = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE)

    private fun TestScope.pager(pages: MutableList<Pair<String, List<BaseItemDto>>>) = RowPager(backgroundScope) { key, items -> pages += key to items }

    @Test
    fun aRowRunsOnePageAtATime() = runTest {
        val pages = mutableListOf<Pair<String, List<BaseItemDto>>>()
        val pager = pager(pages)
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val page = List(3) { item() }
        pager.add("row", RowCursor(3)) {
            fetches++
            gate.await()
            RowPage(page, endReached = false)
        }
        pager.load("row")
        runCurrent()
        pager.onFocused("row", focusedIndex = 0, loaded = 0)
        pager.load("row")
        runCurrent()
        assertEquals(1, fetches)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("row" to page), pages)
        pager.onFocused("row", focusedIndex = 2, loaded = 3)
        runCurrent()
        assertEquals(2, fetches)
    }

    @Test
    fun aFailedPageEndsTheRowAndReportsAnEmptyPage() = runTest {
        val pages = mutableListOf<Pair<String, List<BaseItemDto>>>()
        val pager = pager(pages)
        var fetches = 0
        pager.add("row", RowCursor(3)) {
            fetches++
            error("offline")
        }
        pager.load("row")
        runCurrent()
        pager.onFocused("row", focusedIndex = 0, loaded = 0)
        assertNull(pager.load("row"))
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(listOf("row" to emptyList<BaseItemDto>()), pages)
    }

    @Test
    fun clearCancelsARunningPageWithoutReportingIt() = runTest {
        val pages = mutableListOf<Pair<String, List<BaseItemDto>>>()
        val pager = pager(pages)
        val gate = CompletableDeferred<Unit>()
        pager.add("row", RowCursor(3)) {
            gate.await()
            RowPage(listOf(item()), endReached = false)
        }
        val job = pager.load("row")
        runCurrent()
        pager.clear()
        gate.complete(Unit)
        runCurrent()
        assertTrue(job!!.isCancelled)
        assertTrue(pages.isEmpty())
        assertNull(pager.load("row"))
    }

    @Test
    fun aSeededCursorAlreadyAtItsEndNeverFetches() = runTest {
        val pager = pager(mutableListOf())
        val ids = List(5) { UUID.randomUUID() }
        pager.add("row", RowCursor(12, next = 12, ids = ids)) {
            fail("Nothing left to fetch")
            error("unreachable")
        }
        pager.onFocused("row", focusedIndex = 4, loaded = 5)
        assertNull(pager.load("row"))
    }

    @Test
    fun unknownRowsAreIgnored() = runTest {
        val pager = pager(mutableListOf())
        pager.onFocused("missing", focusedIndex = 0, loaded = 0)
        assertNull(pager.load("missing"))
    }

    @Test
    fun orderedPageFollowsIdSliceAndOmitsMissingItems() = runTest {
        val first = item()
        val second = item()
        val unrelated = item()
        val missing = UUID.randomUUID()
        val page = loadOrderedPage(
            RowCursor(RELATED_PAGE_SIZE),
            fetchIds = { listOf(second.id, missing, first.id) },
            fetchItems = { listOf(first, unrelated, second) }
        )

        assertEquals(listOf(second, first), page.items)
        assertTrue(page.endReached)
    }

    @Test
    fun orderedPageHonorsAPageSizeAndResumesFromASeededCursor() = runTest {
        val items = List(40) { item() }
        val cursor = RowCursor(12, next = 12, ids = items.map { it.id })
        val page = loadOrderedPage(cursor, { error("Seeded ids are not refetched") }, { ids -> items.filter { it.id in ids } })

        assertEquals(items.subList(12, 24), page.items)
        assertEquals(24, cursor.next)
        assertFalse(page.endReached)
    }

    @Test
    fun orderedPagesFetchIdsOnceSliceInOrderAndEndAtLastSlice() = runTest {
        for (total in listOf(0, 7, RELATED_PAGE_SIZE * 2, RELATED_PAGE_SIZE * 2 + 3, RELATED_ID_CAP)) {
            val ranked = List(total) { item() }
            val ids = ranked.map { it.id }
            val cursor = RowCursor(RELATED_PAGE_SIZE)
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
        val cursor = RowCursor(RELATED_PAGE_SIZE)
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
        assertTrue(needsNextPage(RELATED_PAGE_SIZE - ROW_PAGE_PREFETCH, RELATED_PAGE_SIZE, endReached = false, inFlight = false))
        assertTrue(needsNextPage(RELATED_PAGE_SIZE - 1, RELATED_PAGE_SIZE, endReached = false, inFlight = false))
    }

    @Test
    fun farFromEndDoesNotLoadNextPage() {
        assertFalse(needsNextPage(RELATED_PAGE_SIZE - ROW_PAGE_PREFETCH - 1, RELATED_PAGE_SIZE, endReached = false, inFlight = false))
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
