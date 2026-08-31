package app.picnic.player.data.media

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryChangeBatchTest {

    @Test
    fun burstOfItemChangesCoalescesIntoOneBatch() = runTest {
        val bus = LibraryChangeBus()
        val batches = mutableListOf<LibraryChangeBatch>()
        backgroundScope.launch { bus.batches().toList(batches) }
        runCurrent()

        repeat(50) { index -> bus.emit(LibraryChange.ItemUpdated("item-$index")) }
        advanceTimeBy(COALESCE_WINDOW_MS + 1)

        assertEquals(1, batches.size)
        assertEquals(50, batches.single().itemIds.size)
    }

    @Test
    fun quietGapStartsANewBatch() = runTest {
        val bus = LibraryChangeBus()
        val batches = mutableListOf<LibraryChangeBatch>()
        backgroundScope.launch { bus.batches().toList(batches) }
        runCurrent()

        bus.emit(LibraryChange.ItemUpdated("a"))
        advanceTimeBy(COALESCE_WINDOW_MS + 1)
        bus.emit(LibraryChange.ItemUpdated("b"))
        advanceTimeBy(COALESCE_WINDOW_MS + 1)

        assertEquals(listOf(setOf("a"), setOf("b")), batches.map { it.itemIds })
    }

    @Test
    fun seriesIdIsCarriedIntoTheBatch() = runTest {
        val bus = LibraryChangeBus()
        val batches = mutableListOf<LibraryChangeBatch>()
        backgroundScope.launch { bus.batches().toList(batches) }
        runCurrent()

        bus.emit(LibraryChange.ItemUpdated("episode", seriesId = "series"))
        advanceTimeBy(COALESCE_WINDOW_MS + 1)

        assertEquals(setOf("episode", "series"), batches.single().itemIds)
    }

    @Test
    fun libraryContentChangedMarksTheBatch() = runTest {
        val bus = LibraryChangeBus()
        val batches = mutableListOf<LibraryChangeBatch>()
        backgroundScope.launch { bus.batches().toList(batches) }
        runCurrent()

        bus.emit(LibraryChange.LibraryContentChanged)
        bus.emit(LibraryChange.ItemUpdated("a"))
        advanceTimeBy(COALESCE_WINDOW_MS + 1)

        val batch = batches.single()
        assertTrue(batch.contentChanged)
        assertEquals(setOf("a"), batch.itemIds)
    }

    @Test
    fun contentChangedDoesNotLeakIntoTheNextBatch() = runTest {
        val bus = LibraryChangeBus()
        val batches = mutableListOf<LibraryChangeBatch>()
        backgroundScope.launch { bus.batches().toList(batches) }
        runCurrent()

        bus.emit(LibraryChange.LibraryContentChanged)
        advanceTimeBy(COALESCE_WINDOW_MS + 1)
        bus.emit(LibraryChange.ItemUpdated("a"))
        advanceTimeBy(COALESCE_WINDOW_MS + 1)

        assertEquals(listOf(true, false), batches.map { it.contentChanged })
    }
}
