@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.BrowseDest
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.DeclarePaneEntry
import app.picnic.player.ui.browse.ImmersiveBrowseScaffold
import app.picnic.player.ui.browse.PaneEntryScope
import app.picnic.player.ui.browse.rememberHomeBrowseFocus
import app.picnic.player.ui.browse.untitledSkeletonRows
import app.picnic.player.ui.common.LoadFailedState
import app.picnic.player.ui.common.rememberOneShotFocus
import app.picnic.player.ui.common.rememberRetrySeed
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.genre.GenreBrowseGrid
import app.picnic.player.ui.genre.GenreGridColumns
import app.picnic.player.ui.genre.GenreGridSkeleton
import app.picnic.player.ui.grid.LibraryGridViewModel
import app.picnic.player.ui.grid.MediaGridPane
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

internal fun libraryPaneVmKey(destKey: String) = "libpane:$destKey"
internal fun forYouVmKey(destKey: String) = "libforyou:$destKey"

internal fun collectionsGridVmKey(destKey: String) = "grid:collections:$destKey"

@Composable
internal fun LibraryPane(
    dest: BrowseDest.Library,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onItem: (BaseItemDto, String?) -> Unit,
    onGenre: (BaseItemDto) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onSessionExpired: (String) -> Unit
) {
    val paneViewModel: LibraryPaneViewModel = hiltViewModel(key = libraryPaneVmKey(dest.key))
    val forYouViewModel: ForYouViewModel = hiltViewModel(key = forYouVmKey(dest.key))
    val gridViewModel: LibraryGridViewModel = hiltViewModel(key = dest.key)
    val collectionsViewModel: LibraryGridViewModel = hiltViewModel(key = collectionsGridVmKey(dest.key))

    LaunchedEffect(dest) { paneViewModel.bind(dest.id, dest.kinds) }

    val paneState by paneViewModel.state.collectAsStateWithLifecycle()
    val gridState by gridViewModel.state.collectAsStateWithLifecycle()
    val forYouState by forYouViewModel.state.collectAsStateWithLifecycle()
    val collectionsState by collectionsViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(gridState.sessionExpiredServerId, forYouState.sessionExpiredServerId, collectionsState.sessionExpiredServerId) {
        gridState.sessionExpiredServerId?.let {
            gridViewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
        forYouState.sessionExpiredServerId?.let {
            forYouViewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
        collectionsState.sessionExpiredServerId?.let {
            collectionsViewModel.consumeSessionExpired()
            onSessionExpired(it)
        }
    }

    val selectedTab = paneState.selectedTab
    val tabFocus = remember { FocusRequester() }
    var lastTabFocusNonce by remember { mutableIntStateOf(paneState.tabFocusNonce) }
    LaunchedEffect(paneState.tabFocusNonce) {
        if (paneState.tabFocusNonce != lastTabFocusNonce) {
            lastTabFocusNonce = paneState.tabFocusNonce
            tabFocus.requestFocusWhenAttached()
        }
    }
    DeclarePaneEntry { tabFocus }
    var tabRowFocused by remember { mutableStateOf(false) }
    val libraryEmptyFocus = remember { FocusRequester() }
    val collectionsEmptyFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    var libraryEmptyFiltered by remember { mutableStateOf(false) }
    var collectionsEmptyFiltered by remember { mutableStateOf(false) }
    val contentDownFocus = when (selectedTab) {
        LibraryTab.LIBRARY -> libraryEmptyFocus.takeIf { libraryEmptyFiltered }
        LibraryTab.COLLECTIONS -> collectionsEmptyFocus.takeIf { collectionsEmptyFiltered }
        else -> null
    } ?: contentFocus
    var libraryChromeVisible by rememberSaveable { mutableStateOf(true) }
    var collectionsChromeVisible by rememberSaveable { mutableStateOf(true) }
    val contentChromeVisible = when (selectedTab) {
        LibraryTab.LIBRARY -> libraryChromeVisible
        LibraryTab.FOR_YOU -> forYouState.focusedRowIndex == 0
        LibraryTab.GENRES -> paneState.focusedGenreIndex < GenreGridColumns
        LibraryTab.COLLECTIONS -> collectionsChromeVisible
    }
    val tabsVisible = tabRowFocused || contentChromeVisible
    var tabRowHeightPx by rememberSaveable { mutableIntStateOf(0) }
    val tabRowHeight = with(LocalDensity.current) { tabRowHeightPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(contentFocus)
                .focusGroup()
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Up) tabFocus else FocusRequester.Default
                    }
                }
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(260)) },
                label = "libraryTabContent"
            ) { tab ->
                PaneEntryScope(active = tab == selectedTab) {
                    when (tab) {
                        LibraryTab.LIBRARY -> {
                            LaunchedEffect(dest) {
                                gridViewModel.bindLibrary(dest.id, dest.kinds, dest.title)
                            }
                            MediaGridPane(
                                state = gridState,
                                viewModel = gridViewModel,
                                metrics = metrics,
                                seedContentFocus = seedContentFocus && tab == selectedTab,
                                onContentFocusSeeded = onContentFocusSeeded,
                                onItem = onItem,
                                onChromeVisibleChange = { libraryChromeVisible = it },
                                upExitFocus = tabFocus,
                                emptyStateFocus = libraryEmptyFocus,
                                onEmptyFilteredChange = { libraryEmptyFiltered = it },
                                chromeInset = tabRowHeight
                            )
                        }
                        LibraryTab.FOR_YOU -> ForYouTabContent(
                            state = forYouState,
                            viewModel = forYouViewModel,
                            dest = dest,
                            metrics = metrics,
                            horizontalInset = horizontalInset,
                            tabFocus = tabFocus,
                            active = tab == selectedTab,
                            seedContentFocus = seedContentFocus && tab == selectedTab,
                            onContentFocusSeeded = onContentFocusSeeded,
                            onItem = onItem
                        )
                        LibraryTab.GENRES -> GenresTabContent(
                            paneState = paneState,
                            metrics = metrics,
                            horizontalInset = horizontalInset,
                            chromeInset = tabRowHeight,
                            tabFocus = tabFocus,
                            seedContentFocus = seedContentFocus && tab == selectedTab,
                            onContentFocusSeeded = onContentFocusSeeded,
                            onGenreFocused = paneViewModel::onGenreFocused,
                            onRetryGenres = paneViewModel::retryGenres,
                            onGenre = onGenre
                        )
                        LibraryTab.COLLECTIONS -> {
                            LaunchedEffect(Unit) { collectionsViewModel.bindCollections(dest.id) }
                            MediaGridPane(
                                state = collectionsState,
                                viewModel = collectionsViewModel,
                                metrics = metrics,
                                seedContentFocus = seedContentFocus && tab == selectedTab,
                                onContentFocusSeeded = onContentFocusSeeded,
                                onItem = { item, amb ->
                                    if (item.type == BaseItemKind.BOX_SET) {
                                        onCollection(item)
                                    } else {
                                        onItem(item, amb)
                                    }
                                },
                                onChromeVisibleChange = { collectionsChromeVisible = it },
                                offeredFilters = emptySet(),
                                upExitFocus = tabFocus,
                                emptyStateFocus = collectionsEmptyFocus,
                                onEmptyFilteredChange = { collectionsEmptyFiltered = it },
                                chromeInset = tabRowHeight
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = tabsVisible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it }
        ) {
            LibraryTabRow(
                tabs = paneState.tabs,
                selected = selectedTab,
                onSelect = paneViewModel::selectTab,
                selectedTabFocus = tabFocus,
                contentDownFocus = contentDownFocus,
                onFocusedChange = { tabRowFocused = it },
                modifier = Modifier
                    .onSizeChanged { if (it.height > 0) tabRowHeightPx = it.height }
                    .padding(top = 14.dp, bottom = 6.dp)
            )
        }
    }
}

private const val ForYouEmptyMessage =
    "Watch something from this library and suggestions will appear here."

@Composable
private fun ForYouTabContent(
    state: ForYouViewModel.UiState,
    viewModel: ForYouViewModel,
    dest: BrowseDest.Library,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    tabFocus: FocusRequester,
    active: Boolean,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onItem: (BaseItemDto, String?) -> Unit
) {
    LaunchedEffect(dest) { viewModel.bind(dest.id, dest.kinds) }
    val focus = rememberHomeBrowseFocus(
        rowCount = state.rows.size,
        focusedRowIndex = state.focusedRowIndex,
        scrollEnabled = active
    )
    val retrySeed = rememberRetrySeed()
    when {
        state.error != null -> {
            val retryFocus = rememberOneShotFocus(seedContentFocus, onContentFocusSeeded)
            DeclarePaneEntry { retryFocus }
            LoadFailedState(
                message = state.error,
                retryFocus = retryFocus,
                onRetry = {
                    retrySeed.arm()
                    viewModel.retry()
                },
                retrying = state.loading,
                upExitFocus = tabFocus
            )
        }
        !state.loading && state.rows.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(ForYouEmptyMessage)
            }
        else -> ImmersiveBrowseScaffold(
            rows = state.rows,
            ambientLoader = viewModel.ambientLoader,
            focusedItem = viewModel.focusedItem(state),
            focusedRowIndex = state.focusedRowIndex,
            focusedItemId = state.focusedItemId,
            rowFocusedItemIds = state.rowFocusedItemIds,
            focusedRowContinueWatching = false,
            seasonCounts = state.seasonCounts,
            heroStreams = state.heroStreams,
            focus = focus,
            seedContentFocus = seedContentFocus || retrySeed.pending,
            onContentFocusSeeded = {
                retrySeed.consume()
                onContentFocusSeeded()
            },
            onBrowseItemFocused = viewModel::onBrowseItemFocused,
            onItem = onItem,
            horizontalInset = horizontalInset,
            metrics = metrics,
            skeletonRows = if (state.loading && state.rows.isEmpty()) ForYouSkeletonRows else emptyList()
        )
    }
}

private val ForYouSkeletonRows = untitledSkeletonRows(landscapeFirst = false)

@Composable
private fun GenresTabContent(
    paneState: LibraryPaneViewModel.UiState,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    chromeInset: Dp,
    tabFocus: FocusRequester,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onGenreFocused: (Int) -> Unit,
    onRetryGenres: () -> Unit,
    onGenre: (BaseItemDto) -> Unit
) {
    val genreCardFocus = remember { FocusRequester() }
    val genreGridState = rememberLazyGridState()
    val retrySeed = rememberRetrySeed()
    val failed = paneState.genresError
    val retryFocus = rememberOneShotFocus(
        seed = seedContentFocus && failed && !paneState.genresLoading,
        onSeeded = onContentFocusSeeded
    )

    LaunchedEffect(seedContentFocus, retrySeed.pending, paneState.genresLoading, failed, paneState.genres.size) {
        if (paneState.genresLoading || failed) return@LaunchedEffect
        if (!seedContentFocus && !retrySeed.pending) return@LaunchedEffect
        genreCardFocus.requestFocusWhenAttached()
        retrySeed.consume()
        onContentFocusSeeded()
    }

    when {
        failed -> {
            DeclarePaneEntry { retryFocus }
            LoadFailedState(
                message = "Could not load genres",
                retryFocus = retryFocus,
                onRetry = {
                    retrySeed.arm()
                    onRetryGenres()
                },
                retrying = paneState.genresLoading,
                upExitFocus = tabFocus
            )
        }
        paneState.genresLoading -> GenreGridSkeleton(
            horizontalInset = horizontalInset,
            metrics = metrics,
            chromeInset = chromeInset + GenreGridTopGap
        )
        paneState.genres.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No genres in this library") }
        else -> {
            DeclarePaneEntry { genreCardFocus }
            GenreBrowseGrid(
                genres = paneState.genres,
                gridState = genreGridState,
                focusedGenreIndex = paneState.focusedGenreIndex,
                genreCardFocus = genreCardFocus,
                upFocus = tabFocus,
                horizontalInset = horizontalInset,
                metrics = metrics,
                onGenreFocused = onGenreFocused,
                onGenre = onGenre,
                chromeInset = chromeInset + GenreGridTopGap
            )
        }
    }
}

private val GenreGridTopGap = 8.dp
