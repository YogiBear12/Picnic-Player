package app.picnic.player.ui.common

import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

internal const val ROW_PAGE_PREFETCH = 5

internal fun needsNextPage(focusedIndex: Int, loaded: Int, endReached: Boolean, inFlight: Boolean): Boolean = !endReached && !inFlight && focusedIndex >= loaded - ROW_PAGE_PREFETCH

internal data class RowPage(val items: List<BaseItemDto>, val endReached: Boolean)

internal class RowCursor(val pageSize: Int, var next: Int = 0, var ids: List<UUID>? = null)

internal suspend fun loadOrderedPage(
    cursor: RowCursor,
    fetchIds: suspend () -> List<UUID>,
    fetchItems: suspend (List<UUID>) -> List<BaseItemDto>
): RowPage {
    val ids = cursor.ids ?: fetchIds().distinct().also { cursor.ids = it }
    val startIndex = cursor.next
    val slice = ids.drop(startIndex).take(cursor.pageSize)
    val byId = fetchItems(slice).associateBy { it.id }
    val items = slice.mapNotNull { byId[it] }
    check(slice.isEmpty() || items.isNotEmpty()) { "Ordered page hydration returned no items" }
    cursor.next = startIndex + slice.size
    return RowPage(items, cursor.next >= ids.size)
}

internal class RowPager(
    private val scope: CoroutineScope,
    private val onPage: (key: String, items: List<BaseItemDto>) -> Unit
) {
    private class Row(val cursor: RowCursor, val fetch: suspend (RowCursor) -> RowPage) {
        var job: Job? = null
        var ended = cursor.ids?.let { cursor.next >= it.size } ?: false
    }

    private val rows = mutableMapOf<String, Row>()

    fun add(key: String, cursor: RowCursor, fetch: suspend (RowCursor) -> RowPage) {
        rows[key] = Row(cursor, fetch)
    }

    fun onFocused(key: String, focusedIndex: Int, loaded: Int) {
        val row = rows[key] ?: return
        if (needsNextPage(focusedIndex, loaded, row.ended, row.job != null)) load(key)
    }

    fun load(key: String): Job? {
        val row = rows[key] ?: return null
        if (row.ended || row.job != null) return row.job
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = row.fetch(row.cursor)
                row.ended = page.endReached
                if (rows[key] === row) onPage(key, page.items)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                row.ended = true
                Log.w(TAG, "Page failed for $key", failure)
                if (rows[key] === row) onPage(key, emptyList())
            } finally {
                if (row.job === coroutineContext.job) row.job = null
            }
        }
        row.job = job
        job.start()
        return job
    }

    fun clear() {
        rows.values.forEach { it.job?.cancel() }
        rows.clear()
    }

    private companion object {
        const val TAG = "RowPager"
    }
}
