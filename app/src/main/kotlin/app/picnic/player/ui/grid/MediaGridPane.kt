@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package app.picnic.player.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.LoadFailedState
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.rememberRetrySeed
import app.picnic.player.ui.common.rememberSeededFocus
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.BaseItemDto

internal fun letterBucket(name: String?): String? {
    val c = name?.trim()?.firstOrNull()?.uppercaseChar() ?: return null
    return when {
        c.isDigit() -> "#"
        c in 'A'..'Z' -> c.toString()
        else -> null
    }
}

@Composable
internal fun MediaGridPane(
    state: LibraryGridViewModel.UiState,
    viewModel: LibraryGridViewModel,
    metrics: BrowseLayoutMetrics,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit,
    offeredFilters: Set<GridFilterSection> = setOf(GridFilterSection.GENRES),
    title: String? = null,
    startInset: androidx.compose.ui.unit.Dp = GridStartInset,
    upExitFocus: FocusRequester? = null,
    emptyStateFocus: FocusRequester? = null,
    onEmptyFilteredChange: (Boolean) -> Unit = {}
) {
    val retrySeed = rememberRetrySeed()
    when {
        state.error != null -> {
            val retryFocus = rememberSeededFocus(seedContentFocus, onContentFocusSeeded)
            LoadFailedState(
                message = state.error,
                retryFocus = retryFocus,
                onRetry = {
                    retrySeed.arm()
                    viewModel.retry()
                },
                retrying = state.loading,
                upExitFocus = upExitFocus
            )
        }
        state.loading && state.session == null ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
        else -> MediaGridBody(
            metrics = metrics,
            totalCount = state.totalCount,
            pendingScrollIndex = state.pendingScrollIndex,
            scrollNonce = state.scrollNonce,
            revision = state.revision,
            seedContentFocus = seedContentFocus || retrySeed.pending,
            onContentFocusSeeded = {
                retrySeed.consume()
                onContentFocusSeeded()
            },
            sort = state.sort,
            filter = state.filter,
            facets = state.facets,
            refreshing = state.loading,
            isJumpingToLetter = state.isJumpingToLetter,
            onApplyFilterSort = viewModel::applyFilterSort,
            onItem = onItem,
            onLetter = viewModel::jumpToLetter,
            itemAt = viewModel::itemAt,
            onVisibleIndex = viewModel::onVisibleIndex,
            onChromeVisibleChange = onChromeVisibleChange,
            onPanelOpened = viewModel::ensureFacetsLoaded,
            offeredFilters = offeredFilters,
            title = title,
            startInset = startInset,
            upExitFocus = upExitFocus,
            emptyStateFocus = emptyStateFocus,
            onEmptyFilteredChange = onEmptyFilteredChange
        )
    }
}

private val GridCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 0.5f)

private class GridCenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - (containerSize - size) / 2f
}

@Composable
private fun MediaGridBody(
    metrics: BrowseLayoutMetrics,
    totalCount: Int,
    pendingScrollIndex: Int,
    scrollNonce: Int,
    revision: Int,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    sort: app.picnic.player.data.media.GridSortSpec,
    filter: app.picnic.player.data.media.MediaGridFilter,
    facets: app.picnic.player.data.media.GridFilterFacets,
    refreshing: Boolean,
    isJumpingToLetter: Boolean,
    onApplyFilterSort: (app.picnic.player.data.media.MediaGridFilter, app.picnic.player.data.media.GridSortSpec) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onLetter: (String) -> Unit,
    itemAt: (Int) -> org.jellyfin.sdk.model.api.BaseItemDto?,
    onVisibleIndex: (Int) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit,
    onPanelOpened: () -> Unit,
    offeredFilters: Set<GridFilterSection>,
    title: String?,
    startInset: androidx.compose.ui.unit.Dp,
    upExitFocus: FocusRequester?,
    emptyStateFocus: FocusRequester?,
    onEmptyFilteredChange: (Boolean) -> Unit
) {
    val gridState = rememberLazyGridState(cacheWindow = GridCacheWindow)
    val firstFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val railLetterFocus = remember { GridAlphabetLetters.map { FocusRequester() } }
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current
    val images = LocalImageUrls.current

    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }

    val focusOn = remember { { index: Int -> focusedIndex = index } }

    var panelOpen by remember { mutableStateOf(false) }
    var panelCloseNonce by remember { mutableIntStateOf(0) }
    val adjustFiltersFocus = remember { FocusRequester() }
    val openPanel = { panelOpen = true }

    LaunchedEffect(panelOpen) {
        if (panelOpen) onPanelOpened()
    }

    var pendingClearSeed by remember { mutableStateOf(false) }
    LaunchedEffect(pendingClearSeed, totalCount, refreshing) {
        if (!pendingClearSeed || refreshing) return@LaunchedEffect
        firstFocus.requestFocusWhenAttached()
        pendingClearSeed = false
    }

    LaunchedEffect(seedContentFocus, totalCount, refreshing) {
        if (!seedContentFocus) return@LaunchedEffect
        if (totalCount == 0 && refreshing) return@LaunchedEffect
        val target = if (totalCount == 0) adjustFiltersFocus else firstFocus
        runCatching { target.requestFocus() }
        onContentFocusSeeded()
    }

    var lastQuery by remember {
        mutableStateOf<Pair<app.picnic.player.data.media.MediaGridFilter, app.picnic.player.data.media.GridSortSpec>?>(null)
    }
    LaunchedEffect(filter, sort) {
        val previous = lastQuery
        lastQuery = filter to sort
        if (previous != null && previous != (filter to sort)) {
            focusOn(0)
            runCatching { gridState.scrollToItem(0) }
        }
    }

    var filterButtonFocused by remember { mutableStateOf(false) }
    LaunchedEffect(panelCloseNonce) {
        if (panelCloseNonce == 0) return@LaunchedEffect
        repeat(30) {
            if (filterButtonFocused) return@LaunchedEffect
            runCatching { filterFocus.requestFocus() }
            withFrameNanos { }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onVisibleIndex(it) }
    }

    val cardStyle = remember(metrics) { posterCardStyle(sy = metrics.sy) }
    val cellHeight = remember(cardStyle) { gridCellHeight(cardStyle) }

    val activeLetter by remember(revision) {
        derivedStateOf {
            letterBucket(itemAt(focusedIndex)?.let { it.sortName ?: it.name })
        }
    }
    val railActiveIndex = rememberUpdatedState(
        GridAlphabetLetters.indexOf(activeLetter).coerceIn(0, GridAlphabetLetters.lastIndex)
    )

    var lastScrollNonce by remember { mutableIntStateOf(scrollNonce) }
    LaunchedEffect(scrollNonce) {
        if (scrollNonce != lastScrollNonce && scrollNonce > 0 && totalCount > 0) {
            lastScrollNonce = scrollNonce
            val target = pendingScrollIndex.coerceIn(0, totalCount - 1)
            gridState.scrollToItem(target)
            val info = gridState.layoutInfo
            info.visibleItemsInfo.firstOrNull { it.index == target }?.let { item ->
                val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
                val centerOffset = (viewportHeight - item.size.height) / 2
                gridState.scrollToItem(target, -centerOffset)
            }
            focusOn(target)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val spacing = metrics.cardSpacing
        val contentWidth = maxWidth - startInset - GridSideInset - GridEdgeControlWidth
        val columns = maxOf(
            2,
            ((contentWidth + spacing) / (cardStyle.width + spacing)).toInt()
        )

        val topRowFocused = focusedIndex < columns
        LaunchedEffect(topRowFocused) { onChromeVisibleChange(topRowFocused) }

        val bringIntoViewSpec = remember { GridCenterBringIntoViewSpec() }

        Row(
            Modifier
                .fillMaxSize()
                .padding(start = startInset, end = GridSideInset),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(GridVerticalSpacing),
                    contentPadding = PaddingValues(
                        top = if (title == null) 28.dp else 82.dp,
                        bottom = 16.dp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .focusGroup()
                        .focusProperties {
                            enter = { firstFocus }
                            exit = { direction ->
                                when (direction) {
                                    androidx.compose.ui.focus.FocusDirection.Right ->
                                        railLetterFocus[railActiveIndex.value]
                                    androidx.compose.ui.focus.FocusDirection.Up ->
                                        upExitFocus ?: FocusRequester.Cancel
                                    androidx.compose.ui.focus.FocusDirection.Down ->
                                        FocusRequester.Cancel
                                    else -> FocusRequester.Default
                                }
                            }
                        }
                ) {
                    items(count = totalCount) { index ->
                        val item = remember(index, revision) { itemAt(index) }
                        if (item != null) {
                            val focusRequester = if (index == focusedIndex) firstFocus else null
                            MediaGridCard(
                                item = item,
                                style = cardStyle,
                                focusRequester = focusRequester,
                                upFocus = null,
                                onClick = {
                                    val nav = images.navImages(item)
                                    ambientPrewarmer.warm(nav.ambUrl)
                                    onItem(item, nav.bgUrl, nav.ambUrl)
                                },
                                onFocused = { focusOn(index) },
                                onLongClick = { contextMenu.show(item) }
                            )
                        } else {
                            val focusRequester = if (index == focusedIndex) firstFocus else null
                            Box(
                                Modifier
                                    .width(cardStyle.width)
                                    .height(cellHeight)
                                    .onFocusChanged { if (it.hasFocus) focusOn(index) }
                                    .focusRequester(focusRequester ?: FocusRequester.Default)
                                    .focusable()
                            )
                        }
                    }
                }
            }
            GridAlphabetRail(
                activeLetter = activeLetter,
                enabled = totalCount > 0 && sort.supportsLetterJump,
                onLetter = onLetter,
                onOpenFilter = openPanel,
                filterActive = filter.isActive || sort != app.picnic.player.data.media.GridSortSpec(),
                filterFocusRequester = filterFocus,
                onFilterFocusChanged = { filterButtonFocused = it },
                letterFocusRequesters = railLetterFocus,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        title?.let { text ->
            AnimatedVisibility(
                visible = topRowFocused,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it }
            ) {
                Text(
                    text = text,
                    color = PicnicColors.OnDark,
                    style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = startInset, top = 18.dp)
                )
            }
        }

        val emptyFiltered = totalCount == 0 && filter.isActive
        LaunchedEffect(emptyFiltered) { onEmptyFilteredChange(emptyFiltered) }
        if (totalCount == 0 && (!refreshing || pendingClearSeed)) {
            GridEmptyFilteredState(
                filterActive = filter.isActive || pendingClearSeed,
                focusRequester = adjustFiltersFocus,
                downEntryFocus = emptyStateFocus,
                upExitFocus = upExitFocus,
                clearing = pendingClearSeed && refreshing,
                onClearFilters = {
                    pendingClearSeed = true
                    onApplyFilterSort(filter.clearUserFilters(), sort)
                }
            )
        }

        if ((refreshing && !pendingClearSeed) || isJumpingToLetter) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
        }

        if (panelOpen) {
            GridFilterPanel(
                filter = filter,
                sort = sort,
                facets = facets,
                offered = offeredFilters,
                onFilterChange = { onApplyFilterSort(it, sort) },
                onSortChange = { onApplyFilterSort(filter, it) },
                onResetAll = {
                    onApplyFilterSort(
                        filter.clearUserFilters(),
                        app.picnic.player.data.media.GridSortSpec()
                    )
                },
                onClose = {
                    panelOpen = false
                    panelCloseNonce++
                }
            )
        }
    }
}

internal val GridEdgeControlWidth = 36.dp
internal val GridSideInset = 24.dp

internal val GridVerticalSpacing = 10.dp

internal val GridStartInset = 4.dp
