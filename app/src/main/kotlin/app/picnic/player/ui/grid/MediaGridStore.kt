package app.picnic.player.ui.grid

import app.picnic.player.data.media.GridSortSpec
import app.picnic.player.data.media.MEDIA_GRID_PAGE_SIZE
import app.picnic.player.data.media.MediaGridFilter
import app.picnic.player.data.media.MediaRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

internal class MediaGridStore(
    private val kinds: List<BaseItemKind>,
    private var sort: GridSortSpec,
    private var filter: MediaGridFilter,
    private val repository: MediaRepository
) {
    private val mutex = Mutex()
    val pageSize = MEDIA_GRID_PAGE_SIZE
    private val loadedPages = mutableMapOf<Int, List<BaseItemDto>>()

    var totalCount: Int = 0
        private set

    fun slot(index: Int): BaseItemDto? {
        val page = index / pageSize
        val offset = index % pageSize
        return loadedPages[page]?.getOrNull(offset)
    }

    suspend fun reset(newSort: GridSortSpec, newFilter: MediaGridFilter) {
        mutex.withLock {
            sort = newSort
            filter = newFilter
            loadedPages.clear()
            totalCount = 0
        }
        ensurePage(0)
    }

    suspend fun ensureIndex(index: Int): Boolean {
        if (index < 0) return false
        return ensurePage(index / pageSize)
    }

    suspend fun jumpToLetter(letter: String): Int {
        val anchor = if (letter == "#") "A" else letter
        val index = repository.filteredIndexBeforeLetter(kinds, filter, anchor.first())
        ensurePage(index / pageSize)
        return index.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
    }

    suspend fun containsItem(itemId: String): Boolean = mutex.withLock {
        loadedPages.values.any { page -> page.any { it.id.toString() == itemId } }
    }

    suspend fun replaceItem(fresh: BaseItemDto): Boolean = mutex.withLock {
        val target = fresh.id.toString()
        var replaced = false
        for ((page, list) in loadedPages.toMap()) {
            val idx = list.indexOfFirst { it.id.toString() == target }
            if (idx >= 0) {
                loadedPages[page] = list.toMutableList().also { it[idx] = fresh }
                replaced = true
            }
        }
        replaced
    }

    private suspend fun ensurePage(page: Int): Boolean {
        if (page < 0) return false
        mutex.withLock {
            if (page in loadedPages) return false
        }
        val startIndex = page * pageSize
        val result = repository.filteredItems(kinds, filter, sort, startIndex, pageSize)
        mutex.withLock {
            if (totalCount == 0) totalCount = result.totalCount
            loadedPages[page] = result.items
        }
        return true
    }
}
