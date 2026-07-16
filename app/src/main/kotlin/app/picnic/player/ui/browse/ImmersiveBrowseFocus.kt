@file:OptIn(ExperimentalFoundationApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * Owns browse focus mechanics: row/card requesters, row scroll, and vertical list scroll.
 * Hoisted at [BrowseShellHost] so tab switches preserve list positions.
 */
internal class ImmersiveBrowseFocus(
    val listState: LazyListState,
    val rowListStates: List<LazyListState>,
    val rowFocusRequesters: List<FocusRequester>,
    /** Attached to the persisted focused card per row (or index 0 when none). */
    val rowCardFocus: List<FocusRequester>,
    val focusedRowIndex: Int,
    val defaultRowBringIntoView: BringIntoViewSpec,
    val columnBringIntoView: BringIntoViewSpec
)

@Composable
internal fun rememberImmersiveBrowseFocus(
    rowCount: Int,
    focusedRowIndex: Int,
    scrollEnabled: Boolean = true
): ImmersiveBrowseFocus {
    val RowCacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.25f) }
    val ColumnCacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f) }

    val listState = rememberLazyListState(cacheWindow = ColumnCacheWindow)
    val defaultRowBringIntoView = LocalBringIntoViewSpec.current
    val columnBringIntoView = remember { SuppressVerticalBringIntoView }
    val rowListStates = remember(rowCount) { List(rowCount) { LazyListState(cacheWindow = RowCacheWindow) } }
    val rowFocusRequesters = remember(rowCount) { List(rowCount) { FocusRequester() } }
    val rowCardFocus = remember(rowCount) { List(rowCount) { FocusRequester() } }

    // Vertical row scrolling is now framework-driven by the column's ScrollToTop bring-into-view
    // spec (see ImmersiveBrowseScaffold) — no manual animateScrollToItem.

    return ImmersiveBrowseFocus(
        listState = listState,
        rowListStates = rowListStates,
        rowFocusRequesters = rowFocusRequesters,
        rowCardFocus = rowCardFocus,
        focusedRowIndex = focusedRowIndex,
        defaultRowBringIntoView = defaultRowBringIntoView,
        columnBringIntoView = columnBringIntoView
    )
}
