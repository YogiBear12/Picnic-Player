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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.seerr.DiscoverRowKind
import app.picnic.player.data.seerr.SeerrCatalogItem
import app.picnic.player.data.seerr.SeerrDiscoverRow
import app.picnic.player.data.seerr.SeerrImages
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.ImmersiveBrowseFocus
import app.picnic.player.ui.browse.ImmersiveBrowseLayout
import app.picnic.player.ui.browse.RowTitleBottomGap
import app.picnic.player.ui.browse.SkeletonRow
import app.picnic.player.ui.browse.posterCardStyle
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

    when {
        !state.loading && state.rows.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
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
            onSeerrItem = onSeerrItem
        )
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
    onSeerrItem: (SeerrCatalogItem, String?, String?) -> Unit
) {
    val focused = viewModel.focusedItem(state)
    val seerr = state.seerr
    val skeletonRows = if (state.loading && state.rows.isEmpty()) DiscoverSkeletonRows else emptyList()
    ImmersiveBrowseLayout(
        rows = state.rows,
        rowKey = { it.kind.name },
        rowItems = { it.items },
        itemKey = { it.tmdbId },
        focusedItem = focused,
        focusedRowIndex = state.focusedRowIndex,
        focusedItemKey = state.focusedTmdbId,
        rowFocusedKeys = state.rowFocusedIds,
        onItemFocused = viewModel::onItemFocused,
        ambientLoader = viewModel.ambientLoader,
        focus = focus,
        seedContentFocus = seedContentFocus,
        onContentFocusSeeded = onContentFocusSeeded,
        horizontalInset = horizontalInset,
        metrics = metrics,
        skeletonRows = skeletonRows,
        hero = { heroModifier ->
            SeerrHero(item = focused, logoHeight = metrics.logoHeight, modifier = heroModifier)
        },
        row = { rowIndex, row ->
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
                modifier = Modifier.animateItem(fadeInSpec = null)
            )
        }
    )
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
    modifier: Modifier = Modifier
) {
    val focusIndex = focusedTmdbId
        ?.let { id -> row.items.indexOfFirst { it.tmdbId == id }.takeIf { it >= 0 } }
        ?: 0
    val ambientPrewarmer = LocalAmbientPrewarmer.current

    Column(modifier) {
        Text(
            text = row.kind.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = hInset, bottom = RowTitleBottomGap)
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
                        }
                    )
                }
            }
        }
    }
}

private val DiscoverSkeletonRows = DiscoverRowKind.entries.map { kind ->
    SkeletonRow(key = kind.name, title = kind.title, landscape = false)
}
