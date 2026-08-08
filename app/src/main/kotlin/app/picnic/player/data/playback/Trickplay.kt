package app.picnic.player.data.playback

/** One trickplay thumbnail: the sprite-sheet URL plus the cell to crop to. */
data class TrickplayFrame(
    val url: String,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
    val aspect: Float
)

data class TrickplaySheet(
    val url: String,
    val columns: Int,
    val rows: Int,
    val firstFrameIndex: Int,
    val intervalMs: Int
) {
    fun positionMsOf(cell: Int): Long = (firstFrameIndex + cell).toLong() * intervalMs
}

/**
 * The trickplay sprite sheets for one item, bound to how its URLs are built.
 *
 * Callers ask for the frame at a position, or for every sheet to warm a cache with; which sheet a
 * position lands on, where in the grid it sits, and how many sheets exist are all arithmetic that
 * stays in here. A source with no usable geometry still answers — [frameFor] falls back to 16:9
 * and cell 0 rather than returning null, so scrubbing never has to special-case it.
 */
class Trickplay(
    private val tiles: TrickplayTiles,
    private val tileUrl: (tileIndex: Int) -> String
) {
    fun frameFor(positionMs: Long): TrickplayFrame {
        val (tileIndex, row, column) = tiles.tileFor(positionMs)
        return TrickplayFrame(
            url = tileUrl(tileIndex),
            column = column,
            row = row,
            columns = tiles.tileWidth.coerceAtLeast(1),
            rows = tiles.tileHeight.coerceAtLeast(1),
            aspect = if (tiles.height > 0) tiles.width.toFloat() / tiles.height else DEFAULT_ASPECT
        )
    }

    /** Every sheet URL for the item, in play order. Empty when the geometry is unusable. */
    fun tileUrls(): List<String> {
        val perTile = tiles.tileWidth * tiles.tileHeight
        if (perTile <= 0) return emptyList()
        val count = (tiles.thumbnailCount + perTile - 1) / perTile
        return (0 until count).map(tileUrl)
    }

    /** Every sheet with its grid, in play order. Empty when the geometry is unusable. */
    fun sheets(): List<TrickplaySheet> {
        val columns = tiles.tileWidth.coerceAtLeast(1)
        val rows = tiles.tileHeight.coerceAtLeast(1)
        return tileUrls().mapIndexed { index, url ->
            TrickplaySheet(url, columns, rows, index * columns * rows, tiles.intervalMs)
        }
    }

    private companion object {
        const val DEFAULT_ASPECT = 16f / 9f
    }
}
