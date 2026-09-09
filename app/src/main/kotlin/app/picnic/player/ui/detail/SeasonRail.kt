package app.picnic.player.ui.detail

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.BaseItemDto

@Stable
class SeasonRail(val listState: LazyListState) {
    var hasFocus by mutableStateOf(false)

    private val requesters = mutableMapOf<Int, FocusRequester>()

    fun requesterFor(index: Int): FocusRequester? = if (index < 0) null else requesters.getOrPut(index) { FocusRequester() }

    suspend fun bringIntoView(index: Int) {
        listState.scrollToItem((index - 3).coerceAtLeast(0))
    }
}

private fun LazyListLayoutInfo.fullyShows(index: Int): Boolean {
    val item = visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset >= viewportStartOffset && item.offset + item.size <= viewportEndOffset
}

@Composable
fun rememberSeasonRail(
    seasons: List<BaseItemDto>,
    selectedIndex: Int,
    onVisibleIndices: (List<Int>) -> Unit
): SeasonRail {
    val listState = rememberLazyListState()
    val rail = remember(listState) { SeasonRail(listState) }

    LaunchedEffect(rail, seasons.size, selectedIndex) {
        if (seasons.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }.collect { info ->
            if (rail.hasFocus || info.fullyShows(selectedIndex)) return@collect
            rail.bringIntoView(selectedIndex)
        }
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
