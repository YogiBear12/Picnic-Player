@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.playlist

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.text.countLabel
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuDialog
import app.picnic.player.ui.common.rememberKeyedFocusRequesters
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.grid.MediaGridCard
import app.picnic.player.ui.grid.gridCellHeight
import app.picnic.player.ui.theme.PicnicColors
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun PlaylistsPane(
    metrics: BrowseLayoutMetrics,
    horizontalInset: Dp,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onPlaylist: (BaseItemDto) -> Unit,
    viewModel: PlaylistsPaneViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session
    val style = remember(metrics) { posterCardStyle(sy = metrics.sy) }
    val cardFocus = rememberKeyedFocusRequesters()
    var menuPlaylist by remember { mutableStateOf<BaseItemDto?>(null) }
    var confirmDelete by remember { mutableStateOf<BaseItemDto?>(null) }
    var refocusSlot by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(seedContentFocus, state.playlists.isEmpty()) {
        val first = state.playlists.firstOrNull()
        if (seedContentFocus && first != null) {
            cardFocus[first.id.toString()].requestFocusWhenAttached(maxFrames = 20)
            onContentFocusSeeded()
        }
    }

    LaunchedEffect(refocusSlot, state.playlists) {
        val slot = refocusSlot ?: return@LaunchedEffect
        refocusSlot = null
        val next = state.playlists.getOrNull(slot.coerceAtMost(state.playlists.lastIndex))
            ?: return@LaunchedEffect
        cardFocus[next.id.toString()].requestFocusWhenAttached(maxFrames = 20)
    }

    when {
        state.loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = PicnicColors.Accent)
        }
        session == null || state.playlists.isEmpty() -> Box(Modifier.fillMaxSize()) {
            Text(
                "No playlists yet",
                style = MaterialTheme.typography.bodyMedium,
                color = PicnicColors.OnDarkMuted,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val spacing = metrics.cardSpacing
            val contentWidth = maxWidth - horizontalInset - 40.dp
            val columns = maxOf(2, ((contentWidth + spacing) / (style.width + spacing)).toInt())
            val playlists = state.playlists

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = horizontalInset, end = 40.dp, top = 28.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize().focusGroup()
            ) {
                items(playlists, key = { it.id.toString() }) { item ->
                    Box(
                        Modifier
                            .width(style.width)
                            .height(style.topInset + gridCellHeight(style)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        MediaGridCard(
                            item = item,
                            style = style,
                            focusRequester = cardFocus[item.id.toString()],
                            upFocus = null,
                            onClick = { onPlaylist(item) },
                            onLongClick = { menuPlaylist = item },
                            onFocused = {},
                            subtitleOverride = playlistCountLabel(item),
                            modifier = Modifier.padding(top = style.topInset)
                        )
                    }
                }
            }
        }
    }

    menuPlaylist?.let { playlist ->
        ContextMenuDialog(
            actions = listOf(
                ContextMenuAction("Delete playlist", Icons.Default.Delete) {
                    menuPlaylist = null
                    confirmDelete = playlist
                }
            ),
            onDismiss = { menuPlaylist = null }
        )
    }

    confirmDelete?.let { playlist ->
        ContextMenuDialog(
            actions = listOf(
                ContextMenuAction("Cancel", Icons.Default.Close) { confirmDelete = null },
                ContextMenuAction("Delete \"${playlist.name.orEmpty()}\"", Icons.Default.Delete) {
                    confirmDelete = null
                    refocusSlot = state.playlists.indexOfFirst { it.id == playlist.id }.coerceAtLeast(0)
                    viewModel.delete(playlist)
                }
            ),
            onDismiss = { confirmDelete = null },
            openedByLongPress = false
        )
    }
}

private fun playlistCountLabel(item: BaseItemDto): String? = item.childCount?.let { countLabel(it, "item") }
