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
import app.picnic.player.ui.common.growTo
import app.picnic.player.ui.common.rememberRowFocusRequesters

internal class ImmersiveBrowseFocus(
    val listState: LazyListState,
    val rowListStates: List<LazyListState>,
    val rowFocusRequesters: List<FocusRequester>,
    val rowCardFocus: List<FocusRequester>,
    val focusedRowIndex: Int,
    val defaultRowBringIntoView: BringIntoViewSpec
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
    val rowListStates = remember { mutableListOf<LazyListState>() }
        .growTo(rowCount) { LazyListState(cacheWindow = RowCacheWindow) }
    val rowFocusRequesters = rememberRowFocusRequesters(rowCount)
    val rowCardFocus = rememberRowFocusRequesters(rowCount)

    return ImmersiveBrowseFocus(
        listState = listState,
        rowListStates = rowListStates,
        rowFocusRequesters = rowFocusRequesters,
        rowCardFocus = rowCardFocus,
        focusedRowIndex = focusedRowIndex,
        defaultRowBringIntoView = defaultRowBringIntoView
    )
}
