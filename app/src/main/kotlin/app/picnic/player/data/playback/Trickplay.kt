package app.picnic.player.data.playback

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

class Trickplay(
    private val tiles: TrickplayTiles,
    private val tileUrl: (tileIndex: Int) -> String
) {
    /** Never null: unusable geometry falls back to 16:9 and cell 0. */
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

    fun tileUrls(): List<String> {
        val perTile = tiles.tileWidth * tiles.tileHeight
        if (perTile <= 0) return emptyList()
        val count = (tiles.thumbnailCount + perTile - 1) / perTile
        return (0 until count).map(tileUrl)
    }

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

fun trickplaySheetCacheKey(url: String): String = url
