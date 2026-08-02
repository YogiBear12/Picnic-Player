@file:OptIn(ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.player.osd

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.playback.TrickplayFrame
import app.picnic.player.ui.ambient.CardFocusBorderWidth
import app.picnic.player.ui.ambient.CardFocusGlowAlpha
import app.picnic.player.ui.ambient.CardFocusGlowElevation
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.player.ChapterMark
import coil3.compose.AsyncImage

private val CardWidth = 150.dp
private val CardImageHeight = 84.dp // 16:9

private val LabelScrim = Brush.verticalGradient(
    0.4f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.85f)
)

/** Horizontal row of chapter cards; selecting one seeks to its start. */
@Composable
fun ChapterRow(
    chapters: List<ChapterMark>,
    positionMs: Long,
    trickplayFor: (Long) -> TrickplayFrame?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Last chapter whose start has passed — the one playing, and the row's initial focus.
    val activeIndex = chapters.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    val activeFocus = remember { FocusRequester() }
    // Scroll first: an off-screen card is not composed, so its requester cannot attach.
    LaunchedEffect(Unit) {
        listState.scrollToItem(activeIndex)
        activeFocus.requestFocusWhenAttached(maxFrames = 20)
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        // Near-flush horizontally so cards run to the panel edge; vertical room for scale + glow.
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
    ) {
        items(chapters, key = { it.index }, contentType = { "chapter" }) { chapter ->
            ChapterCard(
                chapter = chapter,
                trickplayFor = trickplayFor,
                onClick = { onSelect(chapter.startMs) },
                modifier = if (chapter.index == activeIndex) {
                    Modifier.focusRequester(activeFocus)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
private fun ChapterCard(
    chapter: ChapterMark,
    trickplayFor: (Long) -> TrickplayFrame?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)

    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(CardFocusBorderWidth, Color.White), shape = shape)
        ),
        // Player OSD stays static — continuous pulse is distracting during playback.
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = CardFocusGlowAlpha),
                elevation = CardFocusGlowElevation
            )
        ),
        modifier = modifier.size(width = CardWidth, height = CardImageHeight)
    ) {
        Box(Modifier.fillMaxSize()) {
            val image = chapter.imageUrl
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val frame = trickplayFor(chapter.startMs)
                if (frame != null) {
                    TrickplayCell(frame = frame, modifier = Modifier.fillMaxSize())
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(LabelScrim)
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTime(chapter.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}
