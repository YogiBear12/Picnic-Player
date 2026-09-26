@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.picnic.player.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun ColumnScope.SeasonList(
    seasons: List<BaseItemDto>,
    selectedSeasonId: String?,
    rail: SeasonRail,
    rightTarget: () -> FocusRequester,
    onRightPressed: () -> Boolean,
    onSelect: (String) -> Unit,
    onLongPress: (BaseItemDto) -> Unit
) {
    val seasonBringIntoViewSpec = remember {
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val targetForLeadingEdge = size * 3f
                return offset - targetForLeadingEdge
            }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    CompositionLocalProvider(LocalBringIntoViewSpec provides seasonBringIntoViewSpec) {
        val selectedIndex = seasons.indexOfFirst { it.id.toString() == selectedSeasonId }.coerceAtLeast(0)
        LazyColumn(
            state = rail.listState,
            modifier = Modifier
                .weight(1f)
                .focusGroup()
                .onFocusChanged { rail.hasFocus = it.hasFocus }
                .focusProperties {
                    enter = { rail.requesterFor(selectedIndex) ?: FocusRequester.Default }
                },
            contentPadding = PaddingValues(top = SeasonListTopPadding, bottom = SeasonListBottomPadding),
            verticalArrangement = Arrangement.spacedBy(SeasonListSpacing)
        ) {
            items(
                count = seasons.size,
                key = { seasons[it].id.toString() }
            ) { index ->
                val season = seasons[index]
                val isSelected = season.id.toString() == selectedSeasonId
                val fr = rail.requesterFor(index) ?: return@items
                var seasonFocused by remember(season.id) { mutableStateOf(false) }
                ListItem(
                    selected = isSelected,
                    onClick = { onSelect(season.id.toString()) },
                    onLongClick = { onLongPress(season) },
                    headlineContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = season.name ?: "Season",
                                color = Color.White,
                                textAlign = if (isSelected) TextAlign.Start else TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .then(if (seasonFocused) Modifier.basicMarquee() else Modifier)
                            )
                            val count = season.childCount
                            if (isSelected && count != null && count > 0) {
                                Text(
                                    text = if (count == 1) "1 episode" else "$count episodes",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) Color.White.copy(alpha = 0.40f) else Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.25f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .focusRequester(fr)
                        .focusProperties { right = rightTarget() }
                        .onKeyEvent { event ->
                            if (event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown) {
                                onRightPressed()
                            } else {
                                false
                            }
                        }
                        .onFocusChanged {
                            seasonFocused = it.isFocused
                            if (it.isFocused && !isSelected) onSelect(season.id.toString())
                        }
                )
            }
        }
    }
}

internal val SeasonListTopPadding = 16.dp
internal val SeasonListSpacing = 2.dp
private val SeasonListBottomPadding = 24.dp
