@file:OptIn(ExperimentalComposeUiApi::class)

package app.picnic.player.ui.player.osd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.RowCheck
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import app.picnic.player.ui.player.TrackOption

internal val TrackPanelWidth = PanelWidth.Picker

private val PanelGlassFill = Color(0xC0181E24)
private val PanelCornerRadius = 20.dp

@Composable
fun TrackPanel(
    title: String,
    options: List<TrackOption>,
    allowOff: Boolean,
    onSelect: (String?) -> Unit,
    active: Boolean,
    onClose: () -> Unit
) {
    BackHandler(enabled = active) { onClose() }
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected }
    LaunchedEffect(Unit) { selectedFocus.requestFocusWhenAttached() }

    val rowCount = options.size + if (allowOff) 1 else 0

    Row(
        Modifier
            .fillMaxSize()
            .focusGroup()
    ) {
        Spacer(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusProperties { canFocus = false }
        )
        Column(
            Modifier
                .width(TrackPanelWidth)
                .fillMaxHeight()
                .padding(top = SidePanelEdgeInset, bottom = SidePanelEdgeInset, end = SidePanelEdgeInset)
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(PanelGlassFill)
                .padding(vertical = 20.dp)
                .focusGroup()
        ) {
            PanelHeader(title = title)
            LazyColumn(
                modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(horizontal = PanelContentInset),
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
    PicnicListRow(
        focusRequester = focusRequester,
        keys = PanelRowKeys(blockUp = blockUp, blockDown = blockDown),
        onActivate = onClick,
        onClose = onClose
    ) { focused ->
        Column(Modifier.weight(1f)) {
            Text(
                text = primary,
                color = rowPrimaryColor(focused),
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
        RowCheck(focused, visible = selected)
    }
}
