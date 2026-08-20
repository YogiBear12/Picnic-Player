@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.BrowseCardStyle
import app.picnic.player.ui.browse.ScrollToTopBringIntoView
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellHeight
import app.picnic.player.ui.navigation.PersonKey
import app.picnic.player.ui.seerr.SeasonRequestDialog
import app.picnic.player.ui.theme.PicnicColors
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun DetailScreen(
    itemId: String,
    bgUrl: String?,
    ambUrl: String?,
    onPlay: (itemId: String, startTicks: Long?, sourceId: String?) -> Unit,
    onItem: (item: BaseItemDto, bgUrl: String?, ambUrl: String?) -> Unit,
    onBack: () -> Unit,
    onEpisodes: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onCollection: (BaseItemDto) -> Unit = {},
    onPersonClick: (PersonKey) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel<DetailViewModel, DetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(itemId) }
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item
    val session = state.session

    val themeMusic = LocalThemeMusicPlayer.current
    val images = LocalImageUrls.current
    DisposableEffect(itemId) {
        val ownerId = runCatching { UUID.fromString(itemId) }.getOrNull()
        ownerId?.let(themeMusic::acquire)
        onDispose { ownerId?.let(themeMusic::release) }
    }

    val derivedNav = item?.let { images.navImages(it) }
    PublishBackdrop(
        BackdropSpec(
            backdropUrl = bgUrl ?: derivedNav?.bgUrl,
            ambientUrl = ambUrl ?: derivedNav?.ambUrl
        )
    )

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = PicnicColors.Accent)
            }
            item == null || session == null -> {
                BackHandler { onBack() }
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error ?: "Not found", color = PicnicColors.OnDark)
                }
            }
            else -> DetailContent(
                item = item,
                state = state,
                onPlay = onPlay,
                onItem = onItem,
                onEpisodes = onEpisodes,
                onCollection = onCollection,
                onPersonClick = onPersonClick,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun DetailContent(
    item: BaseItemDto,
    state: DetailViewModel.UiState,
    onPlay: (String, Long?, String?) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onEpisodes: (String, String?, String?, String?) -> Unit,
    onCollection: (BaseItemDto) -> Unit,
    onPersonClick: (PersonKey) -> Unit,
    viewModel: DetailViewModel
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val images = LocalImageUrls.current
    val people = item.people.orEmpty()
    val collections = state.collections
    val similarItems = state.similarItems
    val localTrailers = state.localTrailers
    val remoteTrailers = item.remoteTrailers?.toList() ?: emptyList()
    val trailerCount = remoteTrailers.size + localTrailers.size
    val requestMoreVisible = item.type == BaseItemKind.SERIES && state.requestMoreTmdbId != null
    val context = LocalContext.current
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current

    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    val heroRegionHeight = maxHeight - metrics.rowsRegionHeight

    val focus = rememberDetailPageFocus(people, collections, similarItems)

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTrailersDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    val playTarget = detailPlayTarget(item, state.nextUpEpisode)
    val cardStyle = posterCardStyle(sy = metrics.sy)

    val rowsGap = metrics.rowsViewportOffset + metrics.rowTitleHeight
    val buttonsToRowGap = rowsGap / 3
    val pinSpec = with(LocalDensity.current) {
        remember(heroRegionHeight, buttonsToRowGap) {
            ScrollToTopBringIntoView((heroRegionHeight + buttonsToRowGap).toPx())
        }
    }
    val horizontalRowSpec = LocalBringIntoViewSpec.current

    CompositionLocalProvider(LocalBringIntoViewSpec provides pinSpec) {
        LazyColumn(
            state = focus.listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.bottomInset),
            verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing + metrics.sy(12f))
        ) {
            item(key = "hero") {
                Column(Modifier.fillMaxWidth()) {
                    DetailHero(
                        item = item,
                        metrics = metrics,
                        heroRegionHeight = heroRegionHeight,
                        leadStreams = state.leadStreams,
                        focus = focus,
                        onSummaryClick = { showSummaryDialog = true }
                    )
                    DetailActionButtons(
                        item = item,
                        playTitle = playTarget.title,
                        trailerCount = trailerCount,
                        requestMoreError = state.requestMoreError,
                        metrics = metrics,
                        focus = focus,
                        buttonsToRowGap = buttonsToRowGap,
                        actions = DetailButtonActions(
                            onPlay = { onPlay(playTarget.itemId, playTarget.resumeTicks, null) },
                            onEpisodes = {
                                onEpisodes(
                                    item.id.toString(),
                                    images.ambient(item),
                                    state.nextUpEpisode?.seasonId?.toString(),
                                    state.nextUpEpisode?.id?.toString()
                                )
                            },
                            onToggleWatched = viewModel::toggleWatched,
                            onTrailers = {
                                if (trailerCount == 1) {
                                    if (localTrailers.isNotEmpty()) {
                                        onPlay(localTrailers.first().id.toString(), null, null)
                                    } else {
                                        context.launchRemoteTrailer(remoteTrailers.first().url ?: "", state.trailerYouTubePackage)
                                    }
                                } else {
                                    showTrailersDialog = true
                                }
                            },
                            onMore = { showOverflowMenu = true }
                        )
                    )
                }
            }

            if (people.isNotEmpty()) {
                item(key = "cast") {
                    DetailCastRow(
                        people = people,
                        metrics = metrics,
                        horizontalRowSpec = horizontalRowSpec,
                        focus = focus,
                        onPersonClick = { person -> viewModel.resolvePersonKey(person.id, onPersonClick) }
                    )
                }
            }

            if (collections.isNotEmpty()) {
                item(key = "collections") {
                    DetailPosterRow(
                        title = "Included in",
                        items = collections,
                        metrics = metrics,
                        cardStyle = cardStyle,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = focus.collections,
                        onClick = { onCollection(it) },
                        onLongClick = { contextMenu.show(it) },
                        onFocused = { index, collection -> focus.onCollectionFocused(index, collection.id.toString()) }
                    )
                }
            }

            if (similarItems.isNotEmpty()) {
                item(key = "similar") {
                    DetailPosterRow(
                        title = "More like this",
                        items = similarItems,
                        metrics = metrics,
                        cardStyle = cardStyle,
                        horizontalRowSpec = horizontalRowSpec,
                        rowFocus = focus.similar,
                        onClick = { similarItem ->
                            val nav = images.navImages(similarItem)
                            ambientPrewarmer.warm(nav.ambUrl)
                            onItem(similarItem, nav.bgUrl, nav.ambUrl)
                        },
                        onLongClick = { contextMenu.show(it) },
                        onFocused = { index, similarItem -> focus.onSimilarFocused(index, similarItem.id.toString()) }
                    )
                }
            }
        }
    }

    if (showSummaryDialog) {
        item.overview?.let { overview ->
            ScrollableTextDialog(
                text = overview,
                onDismiss = { showSummaryDialog = false }
            )
        }
    }

    if (showTrailersDialog) {
        TrailersDialog(
            localTrailers = localTrailers,
            remoteTrailers = remoteTrailers,
            trailerYouTubePackage = state.trailerYouTubePackage,
            onPlayLocal = { onPlay(it, null, null) },
            onDismiss = { showTrailersDialog = false }
        )
    }

    if (showOverflowMenu) {
        OverflowMenuDialog(
            item = item,
            onPlayVersion = { sourceId ->
                showOverflowMenu = false
                onPlay(item.id.toString(), null, sourceId)
            },
            onDismiss = { showOverflowMenu = false },
            onToggleFavorite = viewModel::setFavorite,
            showRequestMore = requestMoreVisible,
            requestMoreBusy = state.requestMoreBusy,
            onRequestMore = { viewModel.showSeasonPicker() }
        )
    }

    if (state.showSeasonPicker) {
        SeasonRequestDialog(
            seasons = state.requestMoreSeasons,
            onConfirm = viewModel::requestMoreSeasons,
            onDismiss = viewModel::dismissSeasonPicker
        )
    }
}

@Composable
internal fun DetailRowCard(
    item: BaseItemDto,
    style: BrowseCardStyle,
    focusRequester: FocusRequester?,
    firstInRow: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit
) {
    Box(
        Modifier.width(style.width).height(style.topInset + gridCellHeight(style)),
        contentAlignment = Alignment.TopCenter
    ) {
        MediaGridCard(
            item = item,
            style = style,
            focusRequester = focusRequester,
            upFocus = null,
            leftFocus = if (firstInRow) FocusRequester.Cancel else null,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            modifier = Modifier.padding(top = style.topInset)
        )
    }
}
