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
import java.util.UUID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.BaseItemDto

@Stable
class SeasonRail(val listState: LazyListState) {
    private val requesters = mutableMapOf<Int, FocusRequester>()

    fun requesterFor(index: Int): FocusRequester? = if (index < 0) null else requesters.getOrPut(index) { FocusRequester() }

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
    val rail = remember(listState) { SeasonRail(listState) }

    val seasonIds: List<UUID> = remember(seasons) { seasons.map { it.id } }

    LaunchedEffect(seasonIds) {
        if (seasonIds.isEmpty()) return@LaunchedEffect
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
