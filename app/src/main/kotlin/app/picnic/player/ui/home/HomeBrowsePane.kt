package app.picnic.player.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.ImmersiveBrowseFocus
import app.picnic.player.ui.browse.ImmersiveBrowseScaffold
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun HomeBrowsePane(
    state: HomeViewModel.UiState,
    viewModel: HomeViewModel,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    focus: ImmersiveBrowseFocus,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit
) {
    when {
        state.loading ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
        state.rows.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text(state.error ?: "Nothing here") }
        else -> ImmersiveBrowseScaffold(
            rows = state.rows,
            session = state.session!!,
            ambientLoader = viewModel.ambientLoader,
            focusedItem = viewModel.focusedItem(state),
            focusedRowIndex = state.focusedRowIndex,
            focusedItemId = state.focusedItemId,
            rowFocusedItemIds = state.rowFocusedItemIds,
            focusedRowContinueWatching = viewModel.focusedRow(state)?.continueWatching == true,
            seasonCounts = state.seasonCounts,
            heroStreams = state.heroStreams,
            focus = focus,
            seedContentFocus = seedContentFocus,
            onContentFocusSeeded = onContentFocusSeeded,
            onBrowseItemFocused = viewModel::onBrowseItemFocused,
            onItem = onItem,
            horizontalInset = horizontalInset,
            metrics = metrics
        )
    }
}
