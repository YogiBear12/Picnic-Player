@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.genre

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.browse.BrowseLayoutMetrics
import app.picnic.player.ui.common.rememberSkeletonPulse
import app.picnic.player.ui.common.skeletonFill
import app.picnic.player.ui.common.skeletonPulse
import app.picnic.player.ui.search.GenreCard
import app.picnic.player.ui.search.GenreCardAspectRatio
import app.picnic.player.ui.search.GenreCardCorner
import org.jellyfin.sdk.model.api.BaseItemDto

/** Fixed genre-tile column count — the chrome rule treats indices below this as row 1. */
internal const val GenreGridColumns = 4

/**
 * Browse-by-genre tile grid, shared by the search tab (all libraries) and a library's
 * Genres tab (scoped). [upFocus] is where Up from the top tile row lands (search pill /
 * library tab row).
 */
@Composable
internal fun GenreBrowseGrid(
    genres: List<BaseItemDto>,
    gridState: LazyGridState,
    focusedGenreIndex: Int,
    genreCardFocus: FocusRequester,
    upFocus: FocusRequester?,
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics,
    onGenreFocused: (Int) -> Unit,
    onGenre: (BaseItemDto) -> Unit,
    header: String? = null,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return

    GenreGridFrame(
        gridState = gridState,
        horizontalInset = horizontalInset,
        metrics = metrics,
        header = header,
        modifier = modifier
            .focusGroup()
            .focusProperties {
                enter = { genreCardFocus }
                // Up out of the top row of tiles goes back to the host's chrome —
                // innermost group's exit is the one the focus search consults.
                exit = { direction ->
                    if (direction == FocusDirection.Up && upFocus != null) {
                        upFocus
                    } else {
                        FocusRequester.Default
                    }
                }
            }
    ) {
        itemsIndexed(genres, key = { _, genre -> genre.id }) { index, genre ->
            GenreCard(
                name = genre.name.orEmpty(),
                onClick = { onGenre(genre) },
                onFocused = { onGenreFocused(index) },
                focusRequester = if (index == focusedGenreIndex) genreCardFocus else null
            )
        }
    }
}

@Composable
internal fun GenreGridSkeleton(
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics,
    header: String? = null,
    modifier: Modifier = Modifier
) {
    val pulse = rememberSkeletonPulse()
    GenreGridFrame(
        gridState = rememberLazyGridState(),
        horizontalInset = horizontalInset,
        metrics = metrics,
        header = header,
        modifier = modifier
    ) {
        items(GenreSkeletonTiles) {
            Box(Modifier.aspectRatio(GenreCardAspectRatio).skeletonPulse(pulse).skeletonFill(GenreCardCorner))
        }
    }
}

@Composable
private fun GenreGridFrame(
    gridState: LazyGridState,
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics,
    header: String?,
    modifier: Modifier,
    tiles: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GenreGridColumns),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
        contentPadding = PaddingValues(
            start = horizontalInset,
            end = horizontalInset,
            // Top room INSIDE the viewport so a top-row tile's focus scale/glow renders
            // fully instead of clipping at the grid's own boundary (the same trick as
            // MediaGridPane's top padding).
            top = 12.dp,
            bottom = metrics.bottomInset
        ),
        modifier = modifier.fillMaxSize()
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        tiles()
    }
}

private const val GenreSkeletonGridRows = 6
private const val GenreSkeletonTiles = GenreGridColumns * GenreSkeletonGridRows
