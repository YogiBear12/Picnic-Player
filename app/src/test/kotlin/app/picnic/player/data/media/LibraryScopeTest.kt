package app.picnic.player.data.media

import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScopeTest {
    private fun id(number: Int) = UUID(0, number.toLong())

    private fun movie(number: Int, day: Int) = BaseItemDto(
        id = id(number),
        type = BaseItemKind.MOVIE,
        premiereDate = LocalDateTime.of(2026, 10, day, 0, 0)
    )

    private fun episode(number: Int, series: Int, day: Int) = BaseItemDto(
        id = id(number),
        type = BaseItemKind.EPISODE,
        seriesId = id(series),
        premiereDate = LocalDateTime.of(2026, 10, day, 0, 0)
    )

    @Test
    fun releaseOrderFoldsEpisodesIntoTheirSeriesAtTheNewestDate() {
        val items = listOf(movie(1, 20), episode(10, 100, 25), episode(11, 100, 22), movie(2, 23), episode(12, 200, 21))
        assertEquals(listOf(id(100), id(2), id(200), id(1)), releaseOrder(items, 40))
        assertEquals(listOf(id(100), id(2)), releaseOrder(items, 2))
    }

    @Test
    fun releaseOrderSkipsUndatedItems() {
        val undated = BaseItemDto(id = id(3), type = BaseItemKind.MOVIE)
        assertEquals(listOf(id(1)), releaseOrder(listOf(undated, movie(1, 1)), 40))
    }

    @Test
    fun keepInLibraryChecksEachFolderOnceAndDropsUnknownParents() = runTest {
        val library = id(1)
        val inside = id(2)
        val outside = id(3)
        fun item(number: Int, parent: UUID?) = BaseItemDto(id = id(number), type = BaseItemKind.MOVIE, parentId = parent)
        val items = listOf(item(10, inside), item(11, outside), item(12, inside), item(13, null), item(14, library))
        val asked = mutableListOf<UUID>()
        val kept = keepInLibrary(items, library) { folder, _ ->
            asked += folder
            folder == inside
        }
        assertEquals(listOf(id(10), id(12), id(14)), kept.map { it.id })
        assertEquals(listOf(inside, outside), asked)
    }
}
