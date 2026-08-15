package app.picnic.player.ui.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.BaseItemDto

@Stable
class SeasonRail(
    val listState: LazyListState,
    private val requesters: Map<Int, FocusRequester>
) {
    fun requesterFor(index: Int): FocusRequester? = requesters[index]

    // Scroll so the selected season is slightly down the list (e.g. 4th item)
    // so that previous seasons are naturally visible without scrolling.
    suspend fun bringIntoView(index: Int) {
        listState.scrollToItem((index - 3).coerceAtLeast(0))
    }
}

@Composable
fun rememberSeasonRail(
    seasons: List<BaseItemDto>,
    selectedIndex: Int,
    onVisibleIndices: (List<Int>) -> Unit
): SeasonRail {
    val listState = rememberLazyListState()
    val requesters = remember(seasons) { seasons.indices.associateWith { FocusRequester() } }
    val rail = remember(listState, requesters) { SeasonRail(listState, requesters) }

    // Bring the selected season into view once seasons load. Without this, arriving directly on
    // a later season (e.g. from a Next Up episode card) leaves that season's ListItem off-screen
    // and uncomposed, so D-pad Left / Back can't focus it and the episode list becomes a dead end.
    LaunchedEffect(seasons) {
        if (seasons.isEmpty()) return@LaunchedEffect
        rail.bringIntoView(selectedIndex.coerceAtLeast(0))
    }

    val onVisible by rememberUpdatedState(onVisibleIndices)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { items -> items.map { it.index } }
            .distinctUntilChanged()
            .collect { onVisible(it) }
    }
    return rail
}
