@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.picnic.player.data.media.ItemQueue
import app.picnic.player.data.media.QueueKind
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
import app.picnic.player.ui.browse.landscapeCardStyle
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.OverflowMenuDialog
import app.picnic.player.ui.common.PageRow
import app.picnic.player.ui.common.ScrollableTextDialog
import app.picnic.player.ui.common.rememberRowPageFocus
import app.picnic.player.ui.theme.PicnicColors
import kotlin.random.Random
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun CollectionScreen(
    collectionId: String,
    onPlay: (itemId: String) -> Unit,
    onShuffle: (itemId: String, queueSeed: Long) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel<CollectionViewModel, CollectionViewModel.Factory>(
        creationCallback = { factory -> factory.create(collectionId) }
    )
) {
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item

    when {
        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = PicnicColors.Accent)
        }
        item == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(state.error ?: "Not found", color = PicnicColors.OnDark)
        }
        else -> CollectionContent(
            item = item,
            state = state,
            onPlay = onPlay,
            onShuffle = onShuffle,
            onItem = onItem,
            viewModel = viewModel
        )
    }
}

@Composable
private fun CollectionContent(
    item: BaseItemDto,
    state: CollectionViewModel.UiState,
    onPlay: (String) -> Unit,
    onShuffle: (String, Long) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    viewModel: CollectionViewModel
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val images = LocalImageUrls.current
    val contextMenu = LocalContextMenuHandler.current
    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val nav = images.navImages(item)

    val metrics = browseLayoutMetrics(maxWidth, maxHeight)
    val posterStyle = posterCardStyle(sy = metrics.sy)
    val landscapeStyle = landscapeCardStyle(sy = metrics.sy)
    val rows = state.rows
    val queue = state.queue
    var restoring by remember { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSummaryDialog by remember { mutableStateOf(false) }

    val actions = collectionActions(
        item = item,
        onPlay = { queue.firstOrNull()?.let { onPlay(it.id.toString()) } },
        onShuffle = {
            if (state.queuePhase == ChildrenPhase.COMPLETE) {
                val seed = Random.nextLong()
                ItemQueue(item.id.toString(), QueueKind.COLLECTION, shuffleSeed = seed)
                    .order(queue)
                    .firstOrNull()
                    ?.let { onShuffle(it.id.toString(), seed) }
            }
        },
        onToggleWatched = viewModel::toggleWatched,
        onToggleFavorite = { viewModel.setFavorite(!(item.userData?.isFavorite ?: false)) },
        onMore = { showOverflowMenu = true }
    )

    val pageRows = rows.map { row -> PageRow(row.section.name, row.items.map { it.id.toString() }) }
    val focus = rememberRowPageFocus(
        rows = pageRows,
        ready = state.phase != ChildrenPhase.LOADING,
        moreRowsPending = state.phase == ChildrenPhase.PARTIAL,
        onSettled = { restoring = false }
    )

    PublishBackdrop(BackdropSpec(backdropUrl = nav.bgUrl, ambientUrl = nav.ambUrl))
    DimBackdrop { focus.lastRowKey != null }

    val blockHeight = heroBlockHeight(metrics.logoHeight, reserveBadgeRail = false)

    DetailPageScaffold(
        focus = focus,
        metrics = metrics,
        heroRegionHeight = heroRegionHeight(metrics, maxHeight, blockHeight),
        rowSpacing = metrics.sy(6f),
        restoring = restoring,
        actions = actions,
        notice = if (state.phase == ChildrenPhase.FAILED) "Could not load every item" else null,
        hero = { heroModifier ->
            BrowseHero(
                item = item,
                logoHeight = metrics.logoHeight,
                continueWatching = false,
                seasonCount = null,
                onSummaryClick = { showSummaryDialog = true },
                summaryDown = { focus.lastFocusedButton },
                blockHeight = blockHeight,
                modifier = heroModifier
            )
        }
    ) { horizontalRowSpec ->
        items(rows.size, key = { rows[it].section.name }) { index ->
            val row = rows[index]
            DetailPosterRow(
                title = row.section.title,
                items = row.items,
                metrics = metrics,
                cardStyle = if (row.section.landscape) landscapeStyle else posterStyle,
                horizontalRowSpec = horizontalRowSpec,
                rowFocus = focus.rowFocus(row.section.name),
                onClick = { rowItem ->
                    val rowNav = images.navImages(rowItem)
                    ambientPrewarmer.warm(rowNav.ambUrl)
                    onItem(rowItem, rowNav.bgUrl, rowNav.ambUrl)
                },
                onLongClick = { contextMenu.show(it) },
                onFocused = { cardIndex, rowItem ->
                    focus.onRowFocused(row.section.name, cardIndex, rowItem.id.toString())
                },
                listState = focus.listState,
                upFocus = if (index == 0) ({ focus.lastFocusedButton }) else null
            )
        }
    }

    if (showSummaryDialog) {
        item.overview?.let { overview ->
            ScrollableTextDialog(text = overview, onDismiss = { showSummaryDialog = false })
        }
    }

    if (showOverflowMenu) {
        OverflowMenuDialog(
            item = item,
            onPlayVersion = {},
            onDismiss = { showOverflowMenu = false },
            onToggleFavorite = viewModel::setFavorite
        )
    }
}
