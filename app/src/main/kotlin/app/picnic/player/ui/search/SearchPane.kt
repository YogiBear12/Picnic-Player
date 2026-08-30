@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.search

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.landscapeCardStyle
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.CircularPersonCard
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.rememberRowFocusRequesters
import app.picnic.player.ui.genre.GenreBrowseGrid
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellSlot
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun SearchPane(
    state: SearchViewModel.UiState,
    viewModel: SearchViewModel,
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onSeerrItem: (app.picnic.player.data.seerr.SeerrCatalogItem, String?, String?) -> Unit,
    onGenre: (BaseItemDto) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onPerson: (BaseItemDto) -> Unit
) {
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val fieldFocus = remember { FocusRequester() }
    val genreCardFocus = remember { FocusRequester() }
    val rowCardFocus = rememberRowFocusRequesters(state.results.size)
    val discoverCardFocus = rememberRowFocusRequesters(state.discoverResults.size)
    val genreGridState = rememberLazyGridState()
    val resultListState = rememberLazyListState()

    LaunchedEffect(state.query) { resultListState.scrollToItem(0) }

    val entryFocus = {
        when {
            state.focusArea == SearchViewModel.FocusArea.GENRES &&
                state.query.isBlank() &&
                state.genres.isNotEmpty() -> genreCardFocus
            state.focusArea == SearchViewModel.FocusArea.DISCOVER &&
                state.discoverResults.isNotEmpty() ->
                discoverCardFocus.getOrElse(state.focusedDiscoverRow) { fieldFocus }
            state.focusArea == SearchViewModel.FocusArea.RESULTS &&
                state.results.isNotEmpty() ->
                rowCardFocus.getOrElse(state.focusedResultRow) { fieldFocus }
            else -> fieldFocus
        }
    }

    LaunchedEffect(
        seedContentFocus,
        state.loading,
        state.genres.size,
        state.results.size,
        state.discoverResults.size
    ) {
        if (!seedContentFocus || state.loading) return@LaunchedEffect
        runCatching { entryFocus().requestFocus() }
        onContentFocusSeeded()
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PicnicColors.Accent)
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusProperties {
                onEnter = { runCatching { entryFocus().requestFocus() } }
            }
    ) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            focusRequester = fieldFocus,
            onFocused = viewModel::onFieldFocused,
            modifier = Modifier
                .padding(horizontal = horizontalInset)
                .padding(top = SearchFieldGap)
        )
        Spacer(Modifier.height(SearchFieldGap))

        when {
            state.query.isBlank() -> GenreBrowseGrid(
                genres = state.genres,
                gridState = genreGridState,
                focusedGenreIndex = state.focusedGenreIndex,
                genreCardFocus = genreCardFocus,
                upFocus = fieldFocus,
                horizontalInset = horizontalInset,
                metrics = metrics,
                onGenreFocused = viewModel::onGenreFocused,
                onGenre = onGenre,
                header = "Browse"
            )
            state.searching && state.results.isEmpty() && state.discoverResults.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                Alignment.Center
            ) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
            state.results.isEmpty() && state.discoverResults.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = "No results for “${state.query}”",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            else -> ResultRowsSection(
                state = state,
                listState = resultListState,
                rowCardFocus = rowCardFocus,
                discoverCardFocus = discoverCardFocus,
                fieldFocus = fieldFocus,
                horizontalInset = horizontalInset,
                metrics = metrics,
                onResultFocused = viewModel::onResultFocused,
                onDiscoverFocused = viewModel::onDiscoverFocused,
                onItem = onItem,
                onSeerrItem = onSeerrItem,
                onCollection = onCollection,
                onPerson = onPerson
            )
        }
    }
}

@Composable
private fun ResultRowsSection(
    state: SearchViewModel.UiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    rowCardFocus: List<FocusRequester>,
    discoverCardFocus: List<FocusRequester>,
    fieldFocus: FocusRequester,
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics,
    onResultFocused: (Int, BaseItemDto) -> Unit,
    onDiscoverFocused: (Int, app.picnic.player.data.seerr.SeerrCatalogItem) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onSeerrItem: (app.picnic.player.data.seerr.SeerrCatalogItem, String?, String?) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onPerson: (BaseItemDto) -> Unit
) {
    if (state.session == null) return
    val posterStyle = posterCardStyle(sy = metrics.sy)
    val landscapeStyle = landscapeCardStyle(sy = metrics.sy)
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current
    val images = LocalImageUrls.current

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = metrics.bottomInset),
        verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(state.results, key = { _, row -> row.title }) { rowIndex, row ->
            val cardStyle = if (row.kind == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                landscapeStyle
            } else {
                posterStyle
            }
            val focusIndex = state.rowFocusedItemIds[rowIndex]
                ?.let { id -> row.items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                ?: 0
            Column {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = horizontalInset, bottom = 2.dp)
                )
                LazyRow(
                    state = remember(state.query, row.title) { LazyListState() },
                    contentPadding = PaddingValues(horizontal = horizontalInset),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            if (rowIndex == 0) {
                                exit = { direction ->
                                    if (direction == FocusDirection.Up) {
                                        fieldFocus
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            }
                        }
                        .focusRestorer(rowCardFocus[rowIndex])
                        .focusGroup()
                ) {
                    itemsIndexed(
                        items = row.items,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "SearchResultCard" }
                    ) { index, item ->
                        val requester = if (index == focusIndex) rowCardFocus[rowIndex] else null
                        if (row.kind == org.jellyfin.sdk.model.api.BaseItemKind.PERSON) {
                            CircularPersonCard(
                                imageUrl = images.primary(item),
                                name = item.name,
                                subtitle = null,
                                imageSize = metrics.sy(96f),
                                onClick = { onPerson(item) },
                                modifier = Modifier
                                    .then(requester?.let { Modifier.focusRequester(it) } ?: Modifier)
                                    .onFocusChanged { if (it.isFocused) onResultFocused(rowIndex, item) }
                            )
                        } else {
                            SearchResultCard(
                                item = item,
                                kind = row.kind,
                                style = cardStyle,
                                focusRequester = requester,
                                onClick = {
                                    when (row.kind) {
                                        org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET -> onCollection(item)
                                        else -> {
                                            val nav = images.navImages(item)
                                            ambientPrewarmer.warm(nav.ambUrl)
                                            onItem(item, nav.bgUrl, nav.ambUrl)
                                        }
                                    }
                                },
                                onLongClick = { contextMenu.show(item) },
                                onFocused = { onResultFocused(rowIndex, item) }
                            )
                        }
                    }
                }
            }
        }
        itemsIndexed(
            state.discoverResults,
            key = { _, row -> "discover-${row.title}" }
        ) { rowIndex, row ->
            val focusIndex = state.discoverRowFocusedIds[rowIndex]
                ?.let { id -> row.items.indexOfFirst { it.tmdbId == id }.takeIf { it >= 0 } }
                ?: 0
            Column {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = horizontalInset, bottom = 2.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = horizontalInset),
                    horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            if (state.results.isEmpty() && rowIndex == 0) {
                                exit = { direction ->
                                    if (direction == FocusDirection.Up) {
                                        fieldFocus
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            }
                        }
                        .focusRestorer(discoverCardFocus[rowIndex])
                        .focusGroup()
                ) {
                    itemsIndexed(
                        items = row.items,
                        key = { _, item -> "${item.mediaType}-${item.tmdbId}" }
                    ) { index, item ->
                        Box(
                            Modifier
                                .width(posterStyle.width)
                                .height(app.picnic.player.ui.seerr.seerrLabeledSlotHeight(posterStyle)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            app.picnic.player.ui.seerr.SeerrLabeledCard(
                                item = item,
                                seerrBaseUrl = state.seerrBaseUrl,
                                cacheImages = state.seerrCacheImages,
                                style = posterStyle,
                                focusRequester = if (index == focusIndex) discoverCardFocus[rowIndex] else null,
                                onFocused = { onDiscoverFocused(rowIndex, item) },
                                onClick = {
                                    val nav = app.picnic.player.data.seerr.SeerrImages.navImages(
                                        state.seerrBaseUrl,
                                        item,
                                        state.seerrCacheImages
                                    )
                                    ambientPrewarmer.warm(nav.ambUrl)
                                    onSeerrItem(item, nav.bgUrl, nav.ambUrl)
                                },
                                modifier = Modifier.padding(top = posterStyle.topInset)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: BaseItemDto,
    kind: org.jellyfin.sdk.model.api.BaseItemKind,
    style: app.picnic.player.ui.browse.BrowseCardStyle,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit
) {
    val isEpisode = kind == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE
    val images = LocalImageUrls.current
    val stillUrl = if (isEpisode) {
        images.episodeStill(item)
            ?: images.thumb(item)
            ?: images.backdrop(item)
    } else {
        null
    }
    val episodeCode = if (isEpisode) {
        "S${item.parentIndexNumber ?: "?"} E${item.indexNumber ?: "?"} · ${item.name.orEmpty()}"
    } else {
        null
    }
    Box(
        Modifier.gridCellSlot(style),
        contentAlignment = Alignment.TopCenter
    ) {
        MediaGridCard(
            item = item,
            style = style,
            focusRequester = focusRequester,
            upFocus = null,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            overrideImageUrl = stillUrl,
            titleOverride = if (isEpisode) item.seriesName.orEmpty() else null,
            subtitleOverride = episodeCode,
            modifier = Modifier.padding(top = style.topInset)
        )
    }
}

private val SearchFieldGap = 20.dp
