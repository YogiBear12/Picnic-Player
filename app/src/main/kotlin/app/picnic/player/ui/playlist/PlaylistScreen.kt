@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.media.ItemQueue
import app.picnic.player.data.media.QueueKind
import app.picnic.player.text.countLabel
import app.picnic.player.ui.ambient.BackdropSpec
import app.picnic.player.ui.ambient.PublishBackdrop
import app.picnic.player.ui.browse.CardTimeLeftBadge
import app.picnic.player.ui.browse.CardWatchedBadge
import app.picnic.player.ui.browse.minutesLeft
import app.picnic.player.ui.browse.runtimeMinutes
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ExpandableButton
import app.picnic.player.ui.common.GlobalContextMenuDialog
import app.picnic.player.ui.common.ImageUrls
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.isResumable
import app.picnic.player.ui.common.rememberKeyedFocusRequesters
import app.picnic.player.ui.common.rememberRowFocusState
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.resumeTicks
import app.picnic.player.ui.common.watchProgress
import app.picnic.player.ui.theme.PicnicColors
import coil3.compose.AsyncImage
import kotlin.random.Random
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

private data class PlaylistAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun PlaylistScreen(
    playlistId: String,
    playlistName: String?,
    onPlay: (itemId: String, startTicks: Long?, queuePosition: Int) -> Unit,
    onShuffle: (itemId: String, queueSeed: Long) -> Unit,
    onGoToSeries: (String) -> Unit,
    onAddToPlaylist: (BaseItemDto) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }
    val state = viewModel.state
    val session = state.session
    val title = playlistName ?: "Playlist"

    Box(Modifier.fillMaxSize()) {
        PublishBackdrop(BackdropSpec(backdropUrl = null, ambientUrl = null))

        when {
            state.loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = PicnicColors.Accent
            )
            session == null || state.items.isEmpty() -> EmptyPlaylist(title)
            else -> PlaylistContent(
                playlistId = playlistId,
                name = title,
                items = state.items,
                viewModel = viewModel,
                onPlay = onPlay,
                onShuffle = onShuffle,
                onGoToSeries = onGoToSeries,
                onAddToPlaylist = onAddToPlaylist,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun EmptyPlaylist(name: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "No items in playlist",
            style = MaterialTheme.typography.bodyMedium,
            color = PicnicColors.OnDarkMuted
        )
    }
}

@Composable
private fun PlaylistContent(
    playlistId: String,
    name: String,
    items: List<BaseItemDto>,
    viewModel: PlaylistViewModel,
    onPlay: (String, Long?, Int) -> Unit,
    onShuffle: (String, Long) -> Unit,
    onGoToSeries: (String) -> Unit,
    onAddToPlaylist: (BaseItemDto) -> Unit,
    onBack: () -> Unit
) {
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val focusedIndex = remember(items, focusedKey) {
        items.indexOfFirst { keyOf(it) == focusedKey }.coerceAtLeast(0)
    }
    val focusedItem = items.getOrNull(focusedIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var contextMenuItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var reorderKey by remember { mutableStateOf<String?>(null) }

    val rowFocus = rememberKeyedFocusRequesters()
    fun keyAt(index: Int): String? = items.getOrNull(index)?.let { keyOf(it) }
    val buttons = rememberRowFocusState()
    val unwatchedIndex = remember(items) { items.indexOfFirst { it.isResumable() } }

    fun focusRow(index: Int) {
        val target = index.coerceIn(0, items.lastIndex)
        val key = keyAt(target) ?: return
        scope.launch {
            listState.scrollToItem(target)
            rowFocus[key].requestFocusWhenAttached(maxFrames = 20)
        }
    }

    LaunchedEffect(Unit) {
        val target = focusedIndex.coerceIn(0, items.lastIndex)
        listState.scrollToItem(target)
        keyAt(target)?.let { rowFocus[it].requestFocusWhenAttached(maxFrames = 20) }
    }

    fun moveRow(from: Int, to: Int) {
        if (to !in items.indices) return
        val key = keyAt(from) ?: return
        viewModel.move(from, to)
        scope.launch { rowFocus[key].requestFocusWhenAttached(maxFrames = 20) }
    }

    fun exitReorder() {
        reorderKey = null
        viewModel.commitMove()
    }

    DisposableEffect(Unit) { onDispose { viewModel.commitMove() } }

    fun rightToListConsumed(): Boolean {
        val target = focusedIndex.coerceIn(0, items.lastIndex)
        val onScreen = listState.layoutInfo.visibleItemsInfo.any { it.index == target }
        if (onScreen) return false
        focusRow(target)
        return true
    }

    BackHandler(enabled = reorderKey != null) { exitReorder() }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .padding(start = 56.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            PosterMosaic(items, Modifier.width(160.dp))
            Spacer(Modifier.height(14.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                text = playlistMetaLine(items),
                style = MaterialTheme.typography.labelMedium,
                color = PicnicColors.OnDarkMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            val actions = buildList {
                add(
                    PlaylistAction("Play from start", Icons.Default.PlayArrow) {
                        items.firstOrNull()?.let { onPlay(it.id.toString(), 1L, 0) }
                    }
                )
                if (unwatchedIndex >= 0) {
                    val resumeItem = items[unwatchedIndex]
                    add(
                        PlaylistAction("Resume", Icons.Default.PlayCircleOutline) {
                            onPlay(resumeItem.id.toString(), resumeItem.resumeTicks(), unwatchedIndex)
                        }
                    )
                }
                add(
                    PlaylistAction("Shuffle", Icons.Default.Shuffle) {
                        val seed = Random.nextLong()
                        ItemQueue(playlistId, QueueKind.PLAYLIST, shuffleSeed = seed).order(items).firstOrNull()
                            ?.let { onShuffle(it.id.toString(), seed) }
                    }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .focusGroup()
                    .focusProperties {
                        enter = { buttons.requesterAt(buttons.focusedIndex.coerceAtMost(actions.lastIndex)) }
                    }
            ) {
                actions.forEachIndexed { index, action ->
                    ExpandableButton(
                        title = action.title,
                        icon = action.icon,
                        onClick = action.onClick,
                        modifier = Modifier
                            .focusRequester(buttons.requesterAt(index))
                            .onFocusChanged { if (it.isFocused) buttons.onItemFocused(index) }
                            .then(
                                if (index == actions.lastIndex) {
                                    Modifier
                                        .focusProperties {
                                            keyAt(focusedIndex.coerceIn(0, items.lastIndex))?.let { right = rowFocus[it] }
                                        }
                                        .onKeyEvent { event ->
                                            if (event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown) {
                                                rightToListConsumed()
                                            } else {
                                                false
                                            }
                                        }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.15f)))
            Spacer(Modifier.height(14.dp))

            if (!focusedItem?.overview.isNullOrBlank()) {
                Text(
                    text = focusedItem?.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp, top = 48.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items, key = { _, it -> keyOf(it) }) { index, item ->
                val itemKey = keyOf(item)
                PlaylistRow(
                    index = index,
                    item = item,
                    focusRequester = rowFocus[itemKey],
                    leftFocus = buttons.requesterAt(buttons.focusedIndex),
                    reordering = reorderKey == itemKey,
                    onFocused = { focusedKey = itemKey },
                    onPlay = onPlay,
                    onLongClick = { contextMenuItem = item },
                    onMoveUp = { moveRow(index, index - 1) },
                    onMoveDown = { moveRow(index, index + 1) },
                    onExitReorder = { exitReorder() }
                )
            }
        }
    }

    contextMenuItem?.let { item ->
        val entryId = item.playlistItemId
        GlobalContextMenuDialog(
            item = item,
            onDismiss = { contextMenuItem = null },
            onPlay = { id, ticks ->
                contextMenuItem = null
                onPlay(id, ticks, items.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
            },
            onMarkWatched = { played -> viewModel.markWatched(item.id.toString(), played, item.seriesId?.toString()) },
            onToggleFavorite = { fav -> viewModel.markFavorite(item.id.toString(), fav, item.seriesId?.toString()) },
            onGoToSeries = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
                { seriesId ->
                    contextMenuItem = null
                    onGoToSeries(seriesId)
                }
            } else {
                null
            },
            onAddToPlaylist = {
                contextMenuItem = null
                onAddToPlaylist(item)
            },
            extraActions = buildList {
                if (items.size > 1 && entryId != null) {
                    add(
                        ContextMenuAction("Reorder", Icons.Default.SwapVert) {
                            reorderKey = entryId
                        }
                    )
                }
                add(
                    ContextMenuAction("Remove from playlist", Icons.Default.Delete) {
                        entryId?.let { viewModel.removeEntry(it) }
                    }
                )
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    index: Int,
    item: BaseItemDto,
    focusRequester: FocusRequester,
    leftFocus: FocusRequester,
    reordering: Boolean,
    onFocused: () -> Unit,
    onPlay: (String, Long?, Int) -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onExitReorder: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(12.dp)
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(reordering, index) {
        if (reordering) bringIntoView.bringIntoView()
    }

    Card(
        onClick = {
            if (reordering) onExitReorder() else onPlay(item.id.toString(), item.resumeTicks(), index)
        },
        onLongClick = onLongClick,
        shape = CardDefaults.shape(rowShape),
        colors = CardDefaults.colors(
            containerColor = if (reordering) Color.White.copy(alpha = 0.18f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.18f)
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = rowShape),
            focusedBorder = Border(BorderStroke(0.dp, Color.Transparent), shape = rowShape)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .focusRequester(focusRequester)
            .focusProperties { left = leftFocus }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                if (!reordering || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        onMoveUp()
                        true
                    }
                    Key.DirectionDown -> {
                        onMoveDown()
                        true
                    }
                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = if (focused) 0.9f else 0.45f),
                modifier = Modifier.width(32.dp)
            )

            Box(Modifier.size(width = 148.dp, height = 83.dp).clip(RoundedCornerShape(6.dp))) {
                AsyncImage(
                    model = landscapeImage(LocalImageUrls.current, item),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray)
                )
                ItemProgressBar(item)
                val progress = item.watchProgress()
                val remaining = minutesLeft(item)
                if (remaining != null && progress > 0.01f) {
                    CardTimeLeftBadge(remaining)
                } else if (item.userData?.played == true && progress < 0.01f) {
                    CardWatchedBadge()
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = rowTitle(item),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (focused && !reordering) Modifier.basicMarquee() else Modifier
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rowSubtitle(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (reordering) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = Color.White)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = Color.White)
                }
            } else {
                runtimeMinutes(item)?.let { mins ->
                    Text(
                        text = runtimeText(mins),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterMosaic(items: List<BaseItemDto>, modifier: Modifier = Modifier) {
    val images = LocalImageUrls.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.take(4).chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { item ->
                    AsyncImage(
                        model = posterImage(images, item),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(PicnicColors.SurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ItemProgressBar(item: BaseItemDto) {
    val progress = item.watchProgress()
    if (progress <= 0.01f) return
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 6.dp, end = 6.dp, bottom = 5.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.Black.copy(alpha = 0.50f))
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
    }
}

private fun keyOf(item: BaseItemDto): String = item.playlistItemId ?: item.id.toString()

private fun seLabel(item: BaseItemDto): String = "S${item.parentIndexNumber ?: "?"} E${item.indexNumber ?: "?"}"

private fun rowTitle(item: BaseItemDto): String = (if (item.type == BaseItemKind.EPISODE) item.seriesName else item.name) ?: "Unknown"

private fun rowSubtitle(item: BaseItemDto): String = when (item.type) {
    BaseItemKind.EPISODE -> "${seLabel(item)} · ${item.name.orEmpty()}"
    else -> listOfNotNull("Movie", item.productionYear?.toString()).joinToString(" • ")
}

private fun runtimeText(mins: Int): String = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"

private fun playlistMetaLine(items: List<BaseItemDto>): String {
    val totalMins = items.sumOf { runtimeMinutes(it) ?: 0 }
    val count = countLabel(items.size, "item")
    return if (totalMins > 0) "$count • ${runtimeText(totalMins)}" else count
}

private fun landscapeImage(images: ImageUrls, item: BaseItemDto): String? = if (item.type == BaseItemKind.EPISODE) {
    images.primary(item, fillWidth = 480)
} else {
    images.thumb(item)
        ?: images.backdrop(item, fillWidth = 640)
        ?: images.primary(item, fillWidth = 480)
}

private fun posterImage(images: ImageUrls, item: BaseItemDto): String? = if (item.type == BaseItemKind.EPISODE) {
    images.seriesPrimary(item)
} else {
    images.primary(item)
}
