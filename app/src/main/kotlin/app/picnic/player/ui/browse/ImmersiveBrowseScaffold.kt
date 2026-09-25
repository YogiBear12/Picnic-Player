@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.picnic.player.data.media.HomeRow
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.ambient.LocalAmbientPaletteLoader
import app.picnic.player.ui.common.requestFocusWhenAttached
import java.util.UUID
import kotlinx.coroutines.flow.first
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
    onItem: (BaseItemDto, String?, String?) -> Unit,
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics,
    skeletonRows: List<SkeletonRow> = emptyList()
) {
    val restoreRow = focusedRowIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    val loadingFocus = remember { FocusRequester() }
    var loadingFocused by remember { mutableStateOf(false) }
    val holdLoadingFocus = (rows.isEmpty() && skeletonRows.isNotEmpty()) || loadingFocused
    val currentEntryFocus = { if (holdLoadingFocus) loadingFocus else focus.entryFocus() }
    DeclarePaneEntry(currentEntryFocus)

    LaunchedEffect(seedContentFocus, rows.size, focusedRowIndex, holdLoadingFocus) {
        if (!seedContentFocus) return@LaunchedEffect
        if (rows.isEmpty()) {
            if (holdLoadingFocus) loadingFocus.requestFocusWhenAttached()
            return@LaunchedEffect
        }
        val rowIndex = focusedRowIndex.coerceIn(0, rows.lastIndex)
        val savedCardIndex = rowFocusedItemIds[rowIndex]
            ?.let { id -> rows[rowIndex].items.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        if (savedCardIndex != null && savedCardIndex > 0) {
            runCatching { focus.rowListStates.getOrNull(rowIndex)?.scrollToItem(savedCardIndex) }
        }
        val landed = focus.rowCardFocus.getOrNull(rowIndex)
            ?.requestFocusWhenAttached(maxFrames = 30) == true
        if (!landed) {
            runCatching { focus.rowFocusRequesters.getOrNull(rowIndex)?.requestFocus() }
        }
        onContentFocusSeeded()
    }

    val focusedRowItems = rows.getOrNull(restoreRow)?.items.orEmpty()
    val savedSlot = focusedItemId?.let { id -> focusedRowItems.indexOfFirst { it.id == id } } ?: -1
    var lastFocusedSlot by remember { mutableIntStateOf(0) }
    LaunchedEffect(savedSlot) {
        if (savedSlot >= 0) lastFocusedSlot = savedSlot
    }
    LaunchedEffect(focusedRowItems) {
        if (seedContentFocus || rows.isEmpty() || focusedItemId == null || savedSlot >= 0) {
            return@LaunchedEffect
        }
        if (focusedRowItems.isEmpty()) {
            focus.rowFocusRequesters.getOrNull(restoreRow)?.requestFocusWhenAttached()
            return@LaunchedEffect
        }
        val slot = lastFocusedSlot.coerceIn(0, focusedRowItems.lastIndex)
        onBrowseItemFocused(restoreRow, focusedRowItems[slot])
        focus.rowCardFocus.getOrNull(restoreRow)?.requestFocusWhenAttached(maxFrames = 20)
    }

    val spaceAbovePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        metrics.rowTitleHeight.toPx()
    }
    val rowColumnPivot = androidx.compose.runtime.remember(spaceAbovePx) {
        ScrollToTopBringIntoView(spaceAbovePx)
    }

    LaunchedEffect(focusedRowIndex, spaceAbovePx) {
        snapshotFlow { focus.listState.isScrollInProgress }.first { !it }
        val onScreen = focus.listState.layoutInfo.visibleItemsInfo.any { it.index == focusedRowIndex }
        if (!onScreen) {
            runCatching { focus.listState.animateScrollToItem(focusedRowIndex, -spaceAbovePx.toInt()) }
        }
    }

    CompositionLocalProvider(LocalAmbientPaletteLoader provides ambientLoader) {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties {
                    onEnter = { runCatching { currentEntryFocus().requestFocus() } }
                }
        ) {
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                if (holdLoadingFocus) {
                    Box(
                        Modifier
                            .size(LoadingFocusHolderSize)
                            .focusRequester(loadingFocus)
                            .onFocusChanged { loadingFocused = it.isFocused }
                            .focusable()
                    )
                }
                val heroModifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = horizontalInset,
                        end = horizontalInset,
                        bottom = metrics.heroGap
                    )
                    .width(metrics.heroContentWidth)
                if (focusedItem == null && skeletonRows.isNotEmpty()) {
                    BrowseHeroSkeleton(
                        logoHeight = metrics.logoHeight,
                        width = metrics.heroContentWidth,
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
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowColumnPivot) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.rowsRegionHeight.coerceAtLeast(0.dp))
                        .offset(y = metrics.rowsViewportOffset)
                        .focusProperties { enter = { currentEntryFocus() } },
                    state = focus.listState,
                    contentPadding = PaddingValues(bottom = metrics.bottomInset),
                    verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing)
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key }
                    ) { rowIndex, row ->
                        BrowseRowSection(
                            row = row,
                            hInset = horizontalInset,
                            style = browseCardStyle(row.continueWatching, metrics.sy),
                            spacing = metrics.cardSpacing,
                            rowListState = focus.rowListStates[rowIndex],
                            rowFocus = focus.rowFocusRequesters[rowIndex],
                            rowCardFocus = focus.rowCardFocus[rowIndex],
                            focusedItemId = rowFocusedItemIds[rowIndex],
                            rowBringIntoView = focus.defaultRowBringIntoView,
                            rowIndex = rowIndex,
                            onItem = onItem,
                            onFocusItem = { _, item -> onBrowseItemFocused(rowIndex, item) }
                        )
                    }
                    items(items = skeletonRows, key = { it.key }) { row ->
                        BrowseRowSkeleton(
                            row = row,
                            style = browseCardStyle(row.landscape, metrics.sy),
                            hInset = horizontalInset,
                            spacing = metrics.cardSpacing,
                            modifier = if (row.title != null) Modifier.animateItem(fadeInSpec = null) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberHomeBrowseFocus(
    rowCount: Int,
    focusedRowIndex: Int,
    scrollEnabled: Boolean = true
): ImmersiveBrowseFocus = rememberImmersiveBrowseFocus(rowCount, focusedRowIndex, scrollEnabled)

private val LoadingFocusHolderSize = 1.dp
