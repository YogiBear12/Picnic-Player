package app.picnic.player.data.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TrickplayTest {

    // 4x3 cells per sheet, one thumbnail per 10s, 30 thumbnails => 3 sheets.
    private val tiles = TrickplayTiles(
        width = 320,
        height = 180,
        tileWidth = 4,
        tileHeight = 3,
        thumbnailCount = 30,
        intervalMs = 10_000
    )

    private fun trickplay(t: TrickplayTiles = tiles) = Trickplay(t) { index -> "sheet/$index" }

    @Test
    fun frameFor_picksTheCellForThePosition() {
        // 13th thumbnail (index 12) is the first cell of the second sheet.
        val frame = trickplay().frameFor(125_000)
        assertEquals("sheet/1", frame.url)
        assertEquals(0, frame.row)
        assertEquals(0, frame.column)
        assertEquals(4, frame.columns)
        assertEquals(3, frame.rows)
    }

    @Test
    fun frameFor_clampsPastTheEnd() {
        val frame = trickplay().frameFor(9_999_999)
        // Last thumbnail (index 29): sheet 2, row 1, column 1.
        assertEquals("sheet/2", frame.url)
        assertEquals(1, frame.row)
        assertEquals(1, frame.column)
    }

    @Test
    fun frameFor_fallsBackToSixteenNineWithoutGeometry() {
        val frame = trickplay(tiles.copy(width = 0, height = 0)).frameFor(0)
        assertEquals(16f / 9f, frame.aspect, 0.001f)
    }

    @Test
    fun tileUrls_coverEveryThumbnail() {
        assertEquals(listOf("sheet/0", "sheet/1", "sheet/2"), trickplay().tileUrls())
    }

    @Test
    fun tileUrls_emptyWhenGeometryIsUnusable() {
        assertEquals(emptyList<String>(), trickplay(tiles.copy(tileWidth = 0)).tileUrls())
    }
}
