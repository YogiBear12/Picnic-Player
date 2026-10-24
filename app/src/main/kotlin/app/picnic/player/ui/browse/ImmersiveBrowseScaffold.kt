@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.media.HomeRow
import app.picnic.player.data.media.PersonalLibrary
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun ImmersiveBrowseScaffold(
    rows: List<HomeRow>,
    ambientLoader: AmbientPaletteLoader,
    focusedItem: BaseItemDto?,
    focusedRowIndex: Int,
    focusedItemId: UUID?,
    rowFocusedItemIds: Map<Int, UUID>,
    focusedRowContinueWatching: Boolean,
    seasonCounts: Map<java.util.UUID, Int>,
    heroStreams: Map<java.util.UUID, List<org.jellyfin.sdk.model.api.MediaStream>>,
    focus: ImmersiveBrowseFocus,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onBrowseItemFocused: (rowIndex: Int, item: BaseItemDto) -> Unit,
    onItem: (BaseItemDto, String?) -> Unit,
    onPersonalItem: (PersonalLibrary, BaseItemDto) -> Unit = { _, _ -> },
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics,
    skeletonRows: List<SkeletonRow> = emptyList()
) {
    ImmersiveBrowseLayout(
        rows = rows,
        rowKey = { it.key },
        rowItems = { it.items },
        itemKey = { it.id },
        focusedItem = focusedItem,
        focusedRowIndex = focusedRowIndex,
        focusedItemKey = focusedItemId,
        rowFocusedKeys = rowFocusedItemIds,
        onItemFocused = onBrowseItemFocused,
        ambientLoader = ambientLoader,
        focus = focus,
        seedContentFocus = seedContentFocus,
        onContentFocusSeeded = onContentFocusSeeded,
        horizontalInset = horizontalInset,
        metrics = metrics,
        skeletonRows = skeletonRows,
        hero = { heroModifier ->
            val restoreRow = focusedRowIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            if (rows.getOrNull(restoreRow)?.personalLibrary != null) {
                PersonalHomeHero(
                    name = focusedItem?.name.orEmpty(),
                    logoHeight = metrics.logoHeight,
                    modifier = heroModifier
                )
            } else {
                BrowseHero(
                    item = focusedItem,
                    logoHeight = metrics.logoHeight,
                    continueWatching = focusedRowContinueWatching,
                    seasonCount = focusedItem?.id?.let { seasonCounts[it] },
                    streamsOverride = focusedItem?.id?.let { heroStreams[it] },
                    modifier = heroModifier
                )
            }
        },
        row = { rowIndex, row ->
            BrowseRowSection(
                row = row,
                hInset = horizontalInset,
                style = browseCardStyle(row.continueWatching || row.personalLibrary != null, metrics.sy),
                spacing = metrics.cardSpacing,
                rowListState = focus.rowListStates[rowIndex],
                rowFocus = focus.rowFocusRequesters[rowIndex],
                rowCardFocus = focus.rowCardFocus[rowIndex],
                focusedItemId = rowFocusedItemIds[rowIndex],
                rowBringIntoView = focus.defaultRowBringIntoView,
                rowIndex = rowIndex,
                onItem = onItem,
                onPersonalItem = onPersonalItem,
                onFocusItem = { _, item -> onBrowseItemFocused(rowIndex, item) }
            )
        }
    )
}

@Composable
private fun PersonalHomeHero(name: String, logoHeight: Dp, modifier: Modifier) {
    Column(modifier.height(heroBlockHeight(logoHeight))) {
        Box(
            Modifier.height(logoHeight).fillMaxWidth(),
            contentAlignment = Alignment.BottomStart
        ) {
            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun rememberHomeBrowseFocus(
    rowCount: Int,
    focusedRowIndex: Int,
    scrollEnabled: Boolean = true
): ImmersiveBrowseFocus = rememberImmersiveBrowseFocus(rowCount, focusedRowIndex, scrollEnabled)
