@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package app.picnic.player.ui.grid

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
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.theme.PicnicColors
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.BaseItemDto

/** Bucket an item name's first character into an alphabet-rail letter. */
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
    showGenres: Boolean = true,
    showContentType: Boolean = false,
    title: String? = null,
    startInset: androidx.compose.ui.unit.Dp = GridStartInset,
    /** Where Up from the grid's top row lands (a host's tab row); null = default search. */
    upExitFocus: FocusRequester? = null,
    /** Also attached to the zero-result Clear button so a host can make Down from its tab
     *  land there (spatial search otherwise grabs the rail's filter icon). */
    emptyStateFocus: FocusRequester? = null,
    /** Reports whether the pane is showing the zero-result-with-active-filter state, so the
     *  host can route its tab's Down to [emptyStateFocus] only while it's reachable. */
    onEmptyFilteredChange: (Boolean) -> Unit = {}
) {
    when {
        // Initial load only. Refilter/re-sort reloads keep the body (and the open
        // panel) MOUNTED — tearing it down dropped focus into the chrome.
        state.loading && state.session == null ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
        state.session == null || state.error != null ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(state.error ?: "Nothing here")
            }
        else -> MediaGridBody(
            session = state.session,
            metrics = metrics,
            totalCount = state.totalCount,
            pendingScrollIndex = state.pendingScrollIndex,
            scrollNonce = state.scrollNonce,
            revision = state.revision,
            seedContentFocus = seedContentFocus,
            onContentFocusSeeded = onContentFocusSeeded,
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
            showGenres = showGenres,
            showContentType = showContentType,
            title = title,
            startInset = startInset,
            upExitFocus = upExitFocus,
            emptyStateFocus = emptyStateFocus,
            onEmptyFilteredChange = onEmptyFilteredChange
        )
    }
}

/** Keeps extra items composed around the viewport so focus survives a fast scroll. */
private val GridCacheWindow = LazyLayoutCacheWindow(aheadFraction = 2f, behindFraction = 0.5f)

/**
 * Centers the focused row in the viewport (clamped at the list edges): the row above and the
 * row below both overflow the screen edges, so ~3 rows are visible with only the focused one
 * fully on screen, and every move scrolls smoothly under focus.
 */
private class GridCenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - (containerSize - size) / 2f
}

@Composable
private fun MediaGridBody(
    session: app.picnic.player.data.auth.UserSession,
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
    showGenres: Boolean,
    showContentType: Boolean,
    title: String?,
    startInset: androidx.compose.ui.unit.Dp,
    upExitFocus: FocusRequester?,
    emptyStateFocus: FocusRequester?,
    onEmptyFilteredChange: (Boolean) -> Unit
) {
    val gridState = rememberLazyGridState(cacheWindow = GridCacheWindow)
    val firstFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    // One PERMANENTLY-ATTACHED requester per rail letter cell, owned here. Rail entry
    // imperatively focuses the current letter's cell from the grid's Right-exit. A single
    // requester that migrates between cells (the previous design) double-attaches or is
    // briefly detached mid-move, and the focus system then falls back to the rail's
    // previously-focused child — the verified "lands on G" bug.
    val railLetterFocus = remember { GridAlphabetLetters.map { FocusRequester() } }
    // Warm the detail wash cache on selection (app-scoped, gated on the setting).
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current

    // The grid owns the live focused index locally — D-pad moves update this, NOT the VM
    // state. Only the focused/previously-focused cards recompose (cardStyle is a stable
    // remembered instance), so there is no per-focus storm.
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }

    val focusOn = remember { { index: Int -> focusedIndex = index } }

    // Filter/sort panel.
    var panelOpen by remember { mutableStateOf(false) }
    var panelCloseNonce by remember { mutableIntStateOf(0) }
    val adjustFiltersFocus = remember { FocusRequester() }
    val openPanel = { panelOpen = true }

    LaunchedEffect(panelOpen) {
        if (panelOpen) onPanelOpened()
    }

    // Clearing filters from the zero-result state removes the button (its own composable) the
    // moment the refilter starts — focus would fall to the chrome. Re-seed onto the first card
    // once the cleared list lands (the nav-return effect above is gated on seedContentFocus and
    // won't fire here).
    var pendingClearSeed by remember { mutableStateOf(false) }
    LaunchedEffect(pendingClearSeed, totalCount, refreshing) {
        if (!pendingClearSeed || refreshing) return@LaunchedEffect
        // The held button unmounts this same frame; re-request across a frame so the hand-off
        // to the freshly-composed first card lands rather than falling to the drawer.
        runCatching { firstFocus.requestFocus() }
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
        pendingClearSeed = false
    }

    // Returning from navigation (Detail): scroll state and [focusedIndex] survived, but the
    // composition — and with it the focused node — did not. Re-seed focus onto the saved card
    // (or the empty state's adjust-filters button). Runs post-composition, so the target is
    // attached and a single request lands.
    LaunchedEffect(seedContentFocus, totalCount, refreshing) {
        if (!seedContentFocus) return@LaunchedEffect
        // Still loading with nothing to focus (first visit): neither the cards nor the
        // empty-state button exist yet. This effect re-runs when the data lands
        // (totalCount/refreshing change).
        if (totalCount == 0 && refreshing) return@LaunchedEffect
        val target = if (totalCount == 0) adjustFiltersFocus else firstFocus
        runCatching { target.requestFocus() }
        onContentFocusSeeded()
    }

    // A changed query = a new list: snap to its top THE MOMENT the filter/sort changes (the
    // old scroll offset is meaningless against new contents — left alone, the grid showed a
    // clamped end-of-list position until the panel closed). Skips first composition.
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

    // Panel close: focus returns to the filter button — D-pad Left re-enters the grid. A
    // focusable Popup hands window focus back to the host a few frames after onClose, so a
    // single request can be immediately stolen back; re-request each frame UNTIL the button
    // actually reports focus (bounded), then stop — no blind fixed-count loop.
    var filterButtonFocused by remember { mutableStateOf(false) }
    LaunchedEffect(panelCloseNonce) {
        if (panelCloseNonce == 0) return@LaunchedEffect
        repeat(30) {
            if (filterButtonFocused) return@LaunchedEffect
            runCatching { filterFocus.requestFocus() }
            withFrameNanos { }
        }
    }

    // Page loading follows the visible range, not focus, so cards keep loading through a fast
    // scroll even if focus is briefly lost.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onVisibleIndex(it) }
    }

    // Card size is a single remembered, immutable instance — a fresh instance per
    // recomposition would defeat skipping and recompose every visible card.
    val cardStyle = remember(metrics) { posterCardStyle(sy = metrics.sy) }
    val cellHeight = remember(cardStyle) { gridCellHeight(cardStyle) }

    // derivedStateOf so the rail (and its rider) only recompose when the active *letter*
    // changes, not on every D-pad step — focusedIndex moves constantly, the bucket rarely.
    // Keyed on revision so a page load (which changes what itemAt returns) rebuilds it.
    val activeLetter by remember(revision) {
        derivedStateOf {
            // Use sortName (articles stripped) to match the grid's server sort, fall back to name.
            letterBucket(itemAt(focusedIndex)?.let { it.sortName ?: it.name })
        }
    }
    // Read inside the grid's exit lambda at exit time — never from a stale capture.
    val railActiveIndex = rememberUpdatedState(
        GridAlphabetLetters.indexOf(activeLetter).coerceIn(0, GridAlphabetLetters.lastIndex)
    )

    // Letter jump scrolls the grid under the rail — focus STAYS on the clicked letter.
    // (focusedIndex still moves so the active letter, its rider, and grid re-entry follow.)
    // Land the target row CENTRED — the same position the focus centre-bring-into-view keeps
    // it — so re-entering the grid (D-pad Left) doesn't scroll the content to re-centre it.
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
        // Width is stable across the chrome animation; column count stays fixed (~7 on 1080p).
        // The drawer flanks the left edge, the alphabet rail the right.
        val spacing = metrics.cardSpacing
        val contentWidth = maxWidth - startInset - GridSideInset - GridEdgeControlWidth
        val columns = maxOf(
            2,
            ((contentWidth + spacing) / (cardStyle.width + spacing)).toInt()
        )

        // Chrome (tab row) follows the focused ROW, never scroll offset — a scroll-driven
        // toggle feeds the AnimatedVisibility relayout back into scroll state (ANR loop).
        val topRowFocused = focusedIndex < columns
        LaunchedEffect(topRowFocused) { onChromeVisibleChange(topRowFocused) }

        val bringIntoViewSpec = remember { GridCenterBringIntoViewSpec() }

        Row(
            Modifier
                .fillMaxSize()
                // Left spacing comes from the collapsed drawer; only the right edge keeps
                // the full inset (alphabet rail sits there).
                .padding(start = startInset, end = GridSideInset),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(GridVerticalSpacing),
                    // Top room so the first row's focus glow fades inside the viewport instead
                    // of being hard-clipped at the tab-row edge.
                    contentPadding = PaddingValues(
                        top = if (title == null) 28.dp else 82.dp,
                        bottom = 16.dp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        // D-pad Left from the first column falls through to the focus search,
                        // which lands on the nav drawer (the panel now opens from the filter
                        // icon atop the alphabet rail instead).
                        .focusGroup()
                        .focusProperties {
                            enter = { firstFocus }
                            // Only the grid group's own exit is consulted when focus leaves
                            // it: Up lands on the active drawer item, Right lands on the
                            // rail's current letter — never a spatial winner.
                            // Deprecated-exit contract: RETURN the target requester — an
                            // imperative requestFocus inside this lambda is rolled back by
                            // the in-flight focus transaction. The requester is chosen by a
                            // FRESH index read at exit time and is permanently attached to
                            // its letter cell, so resolution can't drift or fall back.
                            exit = { direction ->
                                when (direction) {
                                    androidx.compose.ui.focus.FocusDirection.Right ->
                                        railLetterFocus[railActiveIndex.value]
                                    // Up from the top row: the host's tab row when it has
                                    // one (tabs are visible whenever the top row is
                                    // focused, so the target is always composed).
                                    androidx.compose.ui.focus.FocusDirection.Up ->
                                        upExitFocus ?: FocusRequester.Default
                                    // Down past the last row has nowhere to go — the rail is a
                                    // sibling to the right, so a focus search finds it sideways.
                                    androidx.compose.ui.focus.FocusDirection.Down ->
                                        FocusRequester.Cancel
                                    else -> FocusRequester.Default
                                }
                            }
                        }
                ) {
                    items(count = totalCount) { index ->
                        // Reading [revision] subscribes this cell to page-load updates.
                        val item = remember(index, revision) { itemAt(index) }
                        if (item != null) {
                            // Direct read so firstFocus follows the focused index (needed for the
                            // jump to land and for focusRestorer to track it). Only the two
                            // changed cards recompose — cardStyle/item are stable, so the rest skip.
                            val focusRequester = if (index == focusedIndex) firstFocus else null
                            MediaGridCard(
                                item = item,
                                session = session,
                                style = cardStyle,
                                focusRequester = focusRequester,
                                upFocus = null,
                                onClick = {
                                    val nav = app.picnic.player.data.jellyfin.JellyfinImages.navImages(session, item)
                                    ambientPrewarmer.warm(nav.ambUrl)
                                    onItem(item, nav.bgUrl, nav.ambUrl)
                                },
                                onFocused = { focusOn(index) },
                                onLongClick = { contextMenu.show(item) }
                            )
                        } else {
                            // Focusable placeholder allows D-pad to enter and triggers Paging to load the missing page
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
                // Letter jumps only make sense while the grid is name-ascending.
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

        title?.let {
            Text(
                text = it,
                color = PicnicColors.OnDark,
                style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = startInset, top = 18.dp)
            )
        }

        // Zero-result-with-active-filter: the host routes its tab's Down here (see
        // emptyStateFocus). Clearing flips filter.isActive false at once, and a Clear-driven
        // refilter (pendingClearSeed) then holds the button MOUNTED so focus can't fall out to
        // the drawer while the grid reloads — it hands off to the first card once results land.
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

        // Refilter in flight: spinner OVER the (kept-mounted) grid — never a teardown,
        // so focus stays wherever it is (usually inside the panel above this). Suppressed
        // during a Clear hold, where the button (not a spinner) carries the transition.
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
                showGenres = showGenres,
                showContentType = showContentType,
                onFilterChange = { onApplyFilterSort(it, sort) },
                onSortChange = { onApplyFilterSort(filter, it) },
                onResetAll = {
                    // Clear USER filters only — destination scopes are never clearable.
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

/** Right alphabet rail allowance: 28dp column + 8dp gap. */
internal val GridEdgeControlWidth = 36.dp
internal val GridSideInset = 24.dp

/** Vertical gap between grid rows — tighter than the horizontal card gap. */
internal val GridVerticalSpacing = 10.dp

/** Left inset inside the shell — the collapsed drawer already provides the visual gutter. */
internal val GridStartInset = 4.dp

/** Request focus without crashing if the target node isn't attached yet (e.g. mid-scroll). */
private fun FocusRequester.tryRequestFocus() = runCatching { requestFocus() }
