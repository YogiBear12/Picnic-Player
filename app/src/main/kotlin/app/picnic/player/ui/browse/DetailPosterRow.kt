@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellSlot
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.api.BaseItemDto

private const val NO_KEPT_CARD = -1

@Composable
internal fun DetailPosterRow(
    title: String,
    items: List<BaseItemDto>,
    metrics: BrowseLayoutMetrics,
    cardStyle: BrowseCardStyle,
    horizontalRowSpec: BringIntoViewSpec,
    rowFocus: RowFocusState,
    onClick: (BaseItemDto) -> Unit,
    onLongClick: (BaseItemDto) -> Unit,
    onFocused: (Int, BaseItemDto) -> Unit,
    listState: LazyListState,
    upFocus: (() -> FocusRequester)? = null
) {
    val revealed = rememberRowReveal(listState)
    val keptIndex = if (revealed.value) NO_KEPT_CARD else rowFocus.focusedIndex
    DetailMediaRow(
        title = title,
        items = items,
        endInset = metrics.hInset,
        cardSpacing = metrics.cardSpacing,
        horizontalRowSpec = horizontalRowSpec,
        rowFocus = rowFocus.rowModifier(),
        key = { _, it -> it.id }
    ) { index, rowItem ->
        if (keptIndex != NO_KEPT_CARD && index != keptIndex) {
            Spacer(Modifier.gridCellSlot(cardStyle))
            return@DetailMediaRow
        }
        DetailRowCard(
            item = rowItem,
            style = cardStyle,
            focusRequester = rowFocus.requesterAt(index),
            firstInRow = index == 0,
            upFocus = upFocus,
            onClick = { onClick(rowItem) },
            onLongClick = { onLongClick(rowItem) },
            onFocused = { onFocused(index, rowItem) }
        )
    }
}

@Composable
private fun rememberRowReveal(listState: LazyListState): State<Boolean> {
    val revealed = remember {
        mutableStateOf(Snapshot.withoutReadObservation { !listState.isScrollInProgress })
    }
    LaunchedEffect(Unit) {
        if (!revealed.value) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            revealed.value = true
        }
    }
    return revealed
}

@Composable
internal fun DetailRowCard(
    item: BaseItemDto,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    firstInRow: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    upFocus: (() -> FocusRequester)? = null,
    onFocused: () -> Unit
) {
    Box(
        Modifier.gridCellSlot(style),
        contentAlignment = Alignment.TopCenter
    ) {
        MediaGridCard(
            item = item,
            style = style,
            focusRequester = focusRequester,
            upFocus = upFocus,
            leftFocus = if (firstInRow) FocusRequester.Cancel else null,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            modifier = Modifier.padding(top = style.topInset)
        )
    }
}
