package app.picnic.player.data.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContentTest {

    private fun item(
        id: UUID = UUID.randomUUID(),
        seriesId: UUID? = null,
        name: String? = null,
        kind: BaseItemKind = BaseItemKind.EPISODE
    ) = BaseItemDto(id = id, type = kind, seriesId = seriesId, name = name)

    // --- combineContinueWatching ----------------------------------------------

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
        val nextEp = item(seriesId = series) // different episode id, same series
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

    // --- buildHomeRows ---------------------------------------------------------

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
}
