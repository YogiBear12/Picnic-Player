@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.player.osd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.player.TrackOption

private val PanelDimScrim = Color(0x52000000)
private val PanelGlassFill = Color(0xC0181E24)
private val PanelCornerRadius = 20.dp
private val PanelEdgeInset = 28.dp
private val ContentInset = 16.dp
private val RowInnerPadding = 14.dp
private val RowCornerRadius = 10.dp

/** Frosted right-side audio/subtitle selection panel. Back closes it;
 *  D-pad stays within the list until then. */
@Composable
fun TrackPanel(
    title: String,
    options: List<TrackOption>,
    allowOff: Boolean,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected }
    // The selected row can start below the fold in a long track list — wait for its
    // requester to attach instead of a single (possibly-thrown) request.
    LaunchedEffect(Unit) { selectedFocus.requestFocusWhenAttached() }

    val rowCount = options.size + if (allowOff) 1 else 0

    Row(
        Modifier
            .fillMaxSize()
            .focusGroup()
            .background(PanelDimScrim)
    ) {
        Spacer(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusProperties { canFocus = false }
        )
        Column(
            Modifier
                .width(360.dp)
                .fillMaxHeight()
                .padding(top = PanelEdgeInset, bottom = PanelEdgeInset, end = PanelEdgeInset)
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(PanelGlassFill)
                .padding(vertical = 20.dp)
                .focusGroup()
        ) {
            PanelHeader(title = title)
            LazyColumn(
                modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(horizontal = ContentInset),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (allowOff) {
                    item {
                        TrackRow(
                            primary = "Off",
                            secondary = null,
                            selected = options.none { it.selected },
                            onClick = { onSelect(null) },
                            onClose = onClose,
                            focusRequester = if (selectedIndex < 0) selectedFocus else null,
                            blockUp = true,
                            blockDown = rowCount <= 1
                        )
                    }
                }
                itemsIndexedTracks(options) { index, option ->
                    val rowIndex = index + if (allowOff) 1 else 0
                    TrackRow(
                        primary = option.displayLanguage,
                        secondary = option.label,
                        selected = option.selected,
                        onClick = { onSelect(option.id) },
                        onClose = onClose,
                        focusRequester = if (index == selectedIndex) selectedFocus else null,
                        blockUp = rowIndex == 0,
                        blockDown = rowIndex == rowCount - 1
                    )
                }
            }
        }
    }
}

/** Section title + divider — same inset as list rows (Material menu / Apple TV popover). */
@Composable
private fun PanelHeader(title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ContentInset)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = RowInnerPadding)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
        Spacer(Modifier.height(12.dp))
    }
}

private inline fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedTracks(
    items: List<TrackOption>,
    crossinline row: @Composable (Int, TrackOption) -> Unit
) {
    items(items.size) { index -> row(index, items[index]) }
}

@Composable
private fun TrackRow(
    primary: String,
    secondary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    val base = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(RowCornerRadius))
        .background(if (focused) Color.White else Color.Transparent)
        .padding(horizontal = RowInnerPadding, vertical = 9.dp)
    val withFocus = if (focusRequester != null) base.focusRequester(focusRequester) else base
    Row(
        modifier = withFocus
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                left = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onClick()
                        true
                    }
                    Key.Back -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = primary,
                color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    color = if (focused) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
