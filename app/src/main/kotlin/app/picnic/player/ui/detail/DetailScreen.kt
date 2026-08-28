@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.picnic.player.playback.LocalThemeMusicPlayer
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.DimBackdrop
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.BrowseHero
import app.picnic.player.ui.browse.DetailPageScaffold
import app.picnic.player.ui.browse.DetailPosterRow
import app.picnic.player.ui.browse.browseLayoutMetrics
import app.picnic.player.ui.browse.heroBlockHeight
import app.picnic.player.ui.browse.heroRegionHeight
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.OverflowMenuDialog
import app.picnic.player.ui.common.PageRow
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.playTarget
import app.picnic.player.ui.common.rememberRowPageFocus
import app.picnic.player.ui.navigation.PersonKey
import app.picnic.player.ui.seerr.SeasonRequestDialog
import app.picnic.player.ui.theme.PicnicColors
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private const val ROW_CAST = "cast"
private const val ROW_COLLECTIONS = "collections"
private const val ROW_SIMILAR = "similar"

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
    val backdrop = BackdropSpec(
        backdropUrl = bgUrl ?: derivedNav?.bgUrl,
        ambientUrl = ambUrl ?: derivedNav?.ambUrl
    )
    PublishBackdrop(backdrop)

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
    var restoring by remember { mutableStateOf(true) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTrailersDialog by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    val playTarget = playTarget(item, state.nextUpEpisode)
    val cardStyle = posterCardStyle(sy = metrics.sy)

    val pageRows = buildList {
        if (people.isNotEmpty()) add(PageRow(ROW_CAST, people.map { it.id.toString() }))
        if (collections.isNotEmpty()) add(PageRow(ROW_COLLECTIONS, collections.map { it.id.toString() }))
        if (similarItems.isNotEmpty()) add(PageRow(ROW_SIMILAR, similarItems.map { it.id.toString() }))
    }
    val focus = rememberRowPageFocus(pageRows) { restoring = false }
    DimBackdrop { focus.lastRowKey != null }

    val actions = detailActions(
        item = item,
        playTitle = playTarget.title,
        trailerCount = trailerCount,
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

    val blockHeight = heroBlockHeight(metrics.logoHeight)

    DetailPageScaffold(
        focus = focus,
        metrics = metrics,
        heroRegionHeight = heroRegionHeight(metrics, maxHeight, blockHeight),
        rowSpacing = metrics.rowSpacing + metrics.sy(12f),
        restoring = restoring,
        actions = actions,
        notice = state.requestMoreError,
        hero = { heroModifier ->
            BrowseHero(
                item = item,
                logoHeight = metrics.logoHeight,
                continueWatching = false,
                streamsOverride = state.leadStreams,
                seasonCount = if (item.type == BaseItemKind.SERIES) item.childCount else null,
                onSummaryClick = { showSummaryDialog = true },
                summaryDown = { focus.lastFocusedButton },
                blockHeight = blockHeight,
                modifier = heroModifier
            )
        }
    ) { horizontalRowSpec ->
        if (people.isNotEmpty()) {
            item(key = ROW_CAST) {
                DetailCastRow(
                    people = people,
                    metrics = metrics,
                    horizontalRowSpec = horizontalRowSpec,
                    rowFocus = focus.rowFocus(ROW_CAST),
                    upFocus = { focus.lastFocusedButton },
                    onFocused = { index, person -> focus.onRowFocused(ROW_CAST, index, person.id.toString()) },
                    onPersonClick = { person -> viewModel.resolvePersonKey(person.id, onPersonClick) }
                )
            }
        }

        if (collections.isNotEmpty()) {
            item(key = ROW_COLLECTIONS) {
                DetailPosterRow(
                    title = "Included in",
                    items = collections,
                    metrics = metrics,
                    cardStyle = cardStyle,
                    horizontalRowSpec = horizontalRowSpec,
                    rowFocus = focus.rowFocus(ROW_COLLECTIONS),
                    onClick = { onCollection(it) },
                    onLongClick = { contextMenu.show(it) },
                    onFocused = { index, collection ->
                        focus.onRowFocused(ROW_COLLECTIONS, index, collection.id.toString())
                    },
                    building = focus.building.value
                )
            }
        }

        if (similarItems.isNotEmpty()) {
            item(key = ROW_SIMILAR) {
                DetailPosterRow(
                    title = "More like this",
                    items = similarItems,
                    metrics = metrics,
                    cardStyle = cardStyle,
                    horizontalRowSpec = horizontalRowSpec,
                    rowFocus = focus.rowFocus(ROW_SIMILAR),
                    onClick = { similarItem ->
                        val nav = images.navImages(similarItem)
                        ambientPrewarmer.warm(nav.ambUrl)
                        onItem(similarItem, nav.bgUrl, nav.ambUrl)
                    },
                    onLongClick = { contextMenu.show(it) },
                    onFocused = { index, similarItem ->
                        focus.onRowFocused(ROW_SIMILAR, index, similarItem.id.toString())
                    },
                    building = focus.building.value
                )
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
