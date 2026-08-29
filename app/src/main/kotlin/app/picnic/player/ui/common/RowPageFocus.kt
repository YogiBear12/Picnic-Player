@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val FOCUS_RESTORE_FAILSAFE_MS = 2000L
private const val HERO_ITEMS = 1

@Immutable
data class PageRow(val key: String, val itemKeys: List<String>)

@Stable
class RowPageFocus internal constructor(
    val listState: LazyListState,
    val building: State<Boolean>,
    val buttons: RowFocusState,
    private val rows: () -> List<PageRow>,
    private val rowStates: Map<String, RowFocusState>,
    lastRowKeyState: MutableState<String?>
) {
    var lastRowKey: String? by lastRowKeyState
        private set

    val lastFocusedButton: FocusRequester get() = buttons.requesterAt(buttons.focusedIndex)

    fun rowFocus(rowKey: String): RowFocusState = rowStates.getValue(rowKey)

    fun onButtonFocused() {
        lastRowKey = null
    }

    fun onRowFocused(rowKey: String, index: Int, itemKey: String) {
        rowStates[rowKey]?.onItemFocused(index, itemKey)
        lastRowKey = rowKey
    }

    internal fun lazyIndexFor(rowKey: String): Int? = rows().indexOfFirst { it.key == rowKey }.takeIf { it >= 0 }?.plus(HERO_ITEMS)

    internal fun resolveRow(rowKey: String): Boolean {
        val row = rows().firstOrNull { it.key == rowKey } ?: return false
        return rowStates[rowKey]?.resolveAgainst(row.itemKeys) { it } == true
    }

    internal suspend fun restoreRow(rowKey: String): Boolean {
        val row = rowStates[rowKey] ?: return false
        if (!row.restoreFocus()) return false
        lastRowKey = rowKey
        return true
    }

    internal fun clearRow() {
        lastRowKey = null
    }
}

@Composable
fun rememberRowPageFocus(
    rows: List<PageRow>,
    ready: Boolean = true,
    moreRowsPending: Boolean = false,
    onSettled: () -> Unit = {}
): RowPageFocus {
    val listState = rememberLazyListState(cacheWindow = ContentCacheWindow)
    val building = rememberPageBuilding(listState)
    val buttons = rememberRowFocusState()
    val rowStates = rows.associate { row -> row.key to key(row.key) { rememberRowFocusState() } }
    val lastRowKey = rememberSaveable { mutableStateOf<String?>(null) }
    val latestRows = rememberUpdatedState(rows)

    val focus = remember(buttons, rowStates) {
        RowPageFocus(
            listState = listState,
            building = building,
            buttons = buttons,
            rows = { latestRows.value },
            rowStates = rowStates,
            lastRowKeyState = lastRowKey
        )
    }

    RestoreRowPageFocus(focus, rows, ready, moreRowsPending, onSettled)
    return focus
}

@Composable
private fun rememberPageBuilding(listState: LazyListState): State<Boolean> {
    val building = remember {
        mutableStateOf(Snapshot.withoutReadObservation { listState.isScrollInProgress })
    }
    LaunchedEffect(Unit) {
        if (building.value) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            building.value = false
        }
    }
    return building
}

@Composable
private fun RestoreRowPageFocus(
    page: RowPageFocus,
    revision: Any?,
    ready: Boolean,
    moreRowsPending: Boolean,
    onSettled: () -> Unit
) {
    var restored by remember { mutableStateOf(false) }
    val settle by rememberUpdatedState(onSettled)

    LaunchedEffect(revision, ready) {
        if (restored || !ready) return@LaunchedEffect
        val rowKey = page.lastRowKey
        if (rowKey == null) {
            restored = true
            page.lastFocusedButton.requestFocusWhenAttached()
            settle()
            return@LaunchedEffect
        }
        val lazyIndex = page.lazyIndexFor(rowKey) ?: return@LaunchedEffect
        if (!page.resolveRow(rowKey)) return@LaunchedEffect
        restored = true
        runCatching { page.listState.scrollToItem(lazyIndex) }
        if (!page.restoreRow(rowKey)) {
            page.clearRow()
            runCatching { page.listState.scrollToItem(0) }
            page.lastFocusedButton.requestFocusWhenAttached()
        }
        settle()
    }

    LaunchedEffect(ready, moreRowsPending) {
        if (!ready || moreRowsPending) return@LaunchedEffect
        delay(FOCUS_RESTORE_FAILSAFE_MS)
        if (!restored) {
            restored = true
            page.lastFocusedButton.requestFocusWhenAttached()
            settle()
        }
    }
}
