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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.browse.posterCardStyle
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
    val firstCardFocus = remember { FocusRequester() }

    LaunchedEffect(seedContentFocus, state.playlists.isEmpty()) {
        if (seedContentFocus && state.playlists.isNotEmpty()) {
            firstCardFocus.requestFocusWhenAttached(maxFrames = 20)
            onContentFocusSeeded()
        }
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
                itemsIndexed(playlists, key = { _, it -> it.id.toString() }) { index, item ->
                    Box(
                        Modifier
                            .width(style.width)
                            .height(style.topInset + gridCellHeight(style)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        MediaGridCard(
                            item = item,
                            style = style,
                            focusRequester = if (index == 0) firstCardFocus else null,
                            upFocus = null,
                            onClick = { onPlaylist(item) },
                            onFocused = {},
                            subtitleOverride = playlistCountLabel(item),
                            modifier = Modifier.padding(top = style.topInset)
                        )
                    }
                }
            }
        }
    }
}

private fun playlistCountLabel(item: BaseItemDto): String? = item.childCount?.let { if (it == 1) "1 item" else "$it items" }
