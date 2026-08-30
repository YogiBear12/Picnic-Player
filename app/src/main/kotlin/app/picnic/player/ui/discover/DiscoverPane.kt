@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrDiscoverRow
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.ui.ambient.LocalAmbientPaletteLoader
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.ImmersiveBrowseFocus
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.seerr.SeerrCardContextMenu
import app.picnic.player.ui.seerr.SeerrHero
import app.picnic.player.ui.seerr.SeerrMediaCard
import app.picnic.player.ui.theme.PicnicColors

@Composable
internal fun DiscoverPane(
    state: DiscoverViewModel.UiState,
    viewModel: DiscoverViewModel,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    focus: ImmersiveBrowseFocus,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onSeerrItem: (SeerrCatalogItem, String?, String?) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    var menuItem by remember { mutableStateOf<SeerrCatalogItem?>(null) }

    when {
        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PicnicColors.Accent)
        }
        state.rows.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(state.error ?: "Nothing here", color = PicnicColors.OnDark)
        }
        else -> DiscoverImmersiveContent(
            state = state,
            viewModel = viewModel,
            metrics = metrics,
            horizontalInset = horizontalInset,
            focus = focus,
            seedContentFocus = seedContentFocus,
            onContentFocusSeeded = onContentFocusSeeded,
            onSeerrItem = onSeerrItem,
            onLongPressItem = { menuItem = it }
        )
    }

    menuItem?.let { item ->
        SeerrCardContextMenu(item = item, onDismiss = { menuItem = null })
    }
}

@Composable
private fun DiscoverImmersiveContent(
    state: DiscoverViewModel.UiState,
    viewModel: DiscoverViewModel,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    focus: ImmersiveBrowseFocus,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onSeerrItem: (SeerrCatalogItem, String?, String?) -> Unit,
    onLongPressItem: (SeerrCatalogItem) -> Unit
) {
    val focused = viewModel.focusedItem(state)
    val seerr = state.seerr
    val restoreRow = state.focusedRowIndex.coerceIn(0, (state.rows.size - 1).coerceAtLeast(0))

    // Same restore contract as ImmersiveBrowseScaffold: the row's LazyListState is
    // recreated at scroll 0 on nav return, so scroll the saved card into composition
    // first, request focus attach-aware, and fall back to the row rather than letting
    // focus escape to the nav drawer.
    LaunchedEffect(seedContentFocus, state.rows.size, state.focusedRowIndex) {
        if (!seedContentFocus || state.rows.isEmpty()) return@LaunchedEffect
        val rowIndex = state.focusedRowIndex.coerceIn(0, state.rows.lastIndex)
        val savedCardIndex = state.rowFocusedIds[rowIndex]
            ?.let { id -> state.rows[rowIndex].items.indexOfFirst { it.tmdbId == id } }
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

    val spaceAbovePx = with(LocalDensity.current) { metrics.rowTitleHeight.toPx() }
    val rowColumnPivot = androidx.compose.runtime.remember(spaceAbovePx) {
        ScrollToTopBringIntoView(spaceAbovePx)
    }

    CompositionLocalProvider(LocalAmbientPaletteLoader provides viewModel.ambientLoader) {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties {
                    onEnter = {
                        runCatching {
                            focus.rowFocusRequesters.getOrElse(restoreRow) {
                                focus.rowFocusRequesters[0]
                            }.requestFocus()
                        }
                    }
                }
        ) {
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                SeerrHero(
                    item = focused,
                    logoHeight = metrics.logoHeight,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = horizontalInset,
                            end = horizontalInset,
                            bottom = metrics.heroGap
                        )
                        .width(metrics.heroContentWidth)
                )
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowColumnPivot) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.rowsRegionHeight.coerceAtLeast(0.dp))
                        .offset(y = metrics.rowsViewportOffset)
                        .focusProperties { enter = { focus.rowFocusRequesters[restoreRow] } },
                    state = focus.listState,
                    contentPadding = PaddingValues(bottom = metrics.bottomInset),
                    verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing)
                ) {
                    itemsIndexed(
                        items = state.rows,
                        key = { _, row -> row.title }
                    ) { rowIndex, row ->
                        DiscoverRowSection(
                            row = row,
                            rowIndex = rowIndex,
                            seerrBaseUrl = seerr.serverUrl,
                            cacheImages = seerr.cacheImages,
                            hInset = horizontalInset,
                            style = posterCardStyle(metrics.sy),
                            spacing = metrics.cardSpacing,
                            rowListState = focus.rowListStates[rowIndex],
                            rowFocus = focus.rowFocusRequesters[rowIndex],
                            rowCardFocus = focus.rowCardFocus[rowIndex],
                            focusedTmdbId = state.rowFocusedIds[rowIndex],
                            rowBringIntoView = focus.defaultRowBringIntoView,
                            onFocusItem = viewModel::onItemFocused,
                            onSeerrItem = onSeerrItem,
                            onLongPressItem = onLongPressItem
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverRowSection(
    row: SeerrDiscoverRow,
    rowIndex: Int,
    seerrBaseUrl: String?,
    cacheImages: Boolean,
    hInset: Dp,
    style: BrowseCardStyle,
    spacing: Dp,
    rowListState: androidx.compose.foundation.lazy.LazyListState,
    rowFocus: FocusRequester,
    rowCardFocus: FocusRequester,
    focusedTmdbId: Int?,
    rowBringIntoView: androidx.compose.foundation.gestures.BringIntoViewSpec,
    onFocusItem: (Int, SeerrCatalogItem) -> Unit,
    onSeerrItem: (SeerrCatalogItem, String?, String?) -> Unit,
    onLongPressItem: (SeerrCatalogItem) -> Unit
) {
    val focusIndex = focusedTmdbId
        ?.let { id -> row.items.indexOfFirst { it.tmdbId == id }.takeIf { it >= 0 } }
        ?: 0
    val ambientPrewarmer = LocalAmbientPrewarmer.current

    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = hInset, bottom = 2.dp)
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringIntoView) {
            LazyRow(
                state = rowListState,
                contentPadding = PaddingValues(horizontal = hInset),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(rowFocus)
                    .focusRestorer(rowCardFocus)
                    .focusGroup()
            ) {
                itemsIndexed(
                    items = row.items,
                    key = { _, item -> "${item.mediaType}-${item.tmdbId}" }
                ) { index, item ->
                    SeerrMediaCard(
                        item = item,
                        seerrBaseUrl = seerrBaseUrl,
                        cacheImages = cacheImages,
                        style = style,
                        focusRequester = if (index == focusIndex) rowCardFocus else null,
                        onFocused = { onFocusItem(rowIndex, item) },
                        onClick = {
                            val nav = SeerrImages.navImages(seerrBaseUrl, item, cacheImages)
                            ambientPrewarmer.warm(nav.ambUrl)
                            onSeerrItem(item, nav.bgUrl, nav.ambUrl)
                        },
                        onLongClick = { onLongPressItem(item) }
                    )
                }
            }
        }
    }
}
