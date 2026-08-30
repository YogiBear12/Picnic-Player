package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContentTest {

    private fun item(
        id: UUID = UUID.randomUUID(),
        seriesId: UUID? = null,
        name: String? = null,
        kind: BaseItemKind = BaseItemKind.EPISODE
    ) = BaseItemDto(id = id, type = kind, seriesId = seriesId, name = name)

    private fun resumed(ticks: Long, id: UUID = UUID.randomUUID()) = BaseItemDto(
        id = id,
        type = BaseItemKind.EPISODE,
        userData = UserItemDataDto(
            playbackPositionTicks = ticks,
            playCount = 0,
            isFavorite = false,
            played = false,
            key = "test",
            itemId = id
        )
    )

    @Test
    fun hiddenItem_atSamePosition_isFiltered() {
        val hiddenItem = resumed(ticks = 1200L)
        val other = resumed(ticks = 900L)
        val out = HomeContent.withoutHidden(
            listOf(hiddenItem, other),
            mapOf(hiddenItem.id.toString() to 1200L)
        )
        assertEquals(listOf(other.id), out.map { it.id })
    }

    @Test
    fun hiddenItem_returnsAfterPositionChanges() {
        val watchedAgain = resumed(ticks = 5000L)
        val out = HomeContent.withoutHidden(
            listOf(watchedAgain),
            mapOf(watchedAgain.id.toString() to 1200L)
        )
        assertEquals(listOf(watchedAgain.id), out.map { it.id })
    }

    @Test
    fun hiddenNextUpItem_withNoProgress_isFiltered() {
        val nextUpEpisode = item()
        val out = HomeContent.withoutHidden(
            listOf(nextUpEpisode),
            mapOf(nextUpEpisode.id.toString() to 0L)
        )
        assertEquals(emptyList<UUID>(), out.map { it.id })
    }

    @Test
    fun resumeFirst_thenNextUp_inOrder() {
        val r = item(name = "resume")
        val n = item(name = "nextup")
        val out = HomeContent.combineContinueWatching(listOf(r), listOf(n))
        assertEquals(listOf(r.id, n.id), out.map { it.id })
    }

    @Test
    fun nextUp_dedupedByItemId() {
        val shared = UUID.randomUUID()
        val r = item(id = shared)
        val n = item(id = shared)
        val out = HomeContent.combineContinueWatching(listOf(r), listOf(n))
        assertEquals(1, out.size)
    }

    @Test
    fun nextUp_dedupedBySeriesAlreadyResuming() {
        val series = UUID.randomUUID()
        val resumeEp = item(seriesId = series)
        val nextEp = item(seriesId = series)
        val out = HomeContent.combineContinueWatching(listOf(resumeEp), listOf(nextEp))
        assertEquals(listOf(resumeEp.id), out.map { it.id })
    }

    @Test
    fun nextUp_differentSeries_kept() {
        val a = item(seriesId = UUID.randomUUID())
        val b = item(seriesId = UUID.randomUUID())
        val out = HomeContent.combineContinueWatching(listOf(a), listOf(b))
        assertEquals(2, out.size)
    }

    @Test
    fun continueWatchingRow_firstAndFlagged_whenNonEmpty() {
        val resume = item()
        val lib = item(name = "Movies", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = listOf(resume),
            nextUp = emptyList(),
            latestByLibrary = listOf(lib to listOf(item(kind = BaseItemKind.MOVIE)))
        )
        assertEquals("Continue watching", rows.first().title)
        assertEquals(true, rows.first().continueWatching)
        assertEquals("Recently added in Movies", rows[1].title)
    }

    @Test
    fun noContinueWatchingRow_whenEmpty() {
        val lib = item(name = "Shows", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = emptyList(),
            nextUp = emptyList(),
            latestByLibrary = listOf(lib to listOf(item(kind = BaseItemKind.SERIES)))
        )
        assertEquals(1, rows.size)
        assertEquals("Recently added in Shows", rows.first().title)
    }

    @Test
    fun emptyLibraries_skipped() {
        val libA = item(name = "A", kind = BaseItemKind.COLLECTION_FOLDER)
        val libB = item(name = "B", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = emptyList(),
            nextUp = emptyList(),
            latestByLibrary = listOf(
                libA to emptyList(),
                libB to listOf(item(kind = BaseItemKind.MOVIE))
            )
        )
        assertEquals(1, rows.size)
        assertEquals("Recently added in B", rows.first().title)
    }

    @Test
    fun pinnedOrder_filtersAndOrdersLibraryRows() {
        val libA = item(name = "A", kind = BaseItemKind.COLLECTION_FOLDER)
        val libB = item(name = "B", kind = BaseItemKind.COLLECTION_FOLDER)
        val libC = item(name = "C", kind = BaseItemKind.COLLECTION_FOLDER)
        val rows = HomeContent.buildHomeRows(
            resume = emptyList(),
            nextUp = emptyList(),
            latestByLibrary = listOf(
                libA to listOf(item(kind = BaseItemKind.MOVIE)),
                libB to listOf(item(kind = BaseItemKind.MOVIE)),
                libC to listOf(item(kind = BaseItemKind.MOVIE))
            ),
            pinnedLibraryIds = listOf(libC.id, libA.id)
        )
        assertEquals(
            listOf("Recently added in C", "Recently added in A"),
            rows.map { it.title }
        )
    }
}
