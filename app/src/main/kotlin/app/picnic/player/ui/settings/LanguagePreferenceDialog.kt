package app.picnic.player.ui.settings

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.playback.LanguagePickerRow
import app.picnic.player.ui.common.requestFocusWhenAttached

/** Align with SeasonRequestDialog / TrackPanel glass chrome. */
private val DialogGlassFill = Color(0xEA181E24)
private val PanelWidth = 360.dp
private val PanelCornerRadius = 20.dp
private val ContentInset = 16.dp
private val RowInnerPadding = 14.dp
private val RowCornerRadius = 10.dp
private val ListViewportHeight = 280.dp

/**
 * Scrollable picker for Preferred audio / subtitle language. Quick-picks (audio Default, the
 * device language) are pinned at the top above a divider drawn after [separatorAfterIndex]
 * (-1 = no pinned row); the remaining rows are Jellyfin DisplayNames, A–Z.
 */
@Composable
internal fun LanguagePreferenceDialog(
    title: String,
    rows: List<LanguagePickerRow>,
    separatorAfterIndex: Int,
    selectedRow: LanguagePickerRow?,
    onSelect: (LanguagePickerRow) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val seedFocus = remember { FocusRequester() }
    val selectedIndex = remember(rows, selectedRow) {
        rows.indexOf(selectedRow).takeIf { it >= 0 } ?: 0
    }
    // Open already scrolled to the selected row so the list never paints from the top
    // then jumps to the checked row. But when the selection is the pinned device
    // language the list is already at its natural top — keep it there so that row stays
    // visible instead of scrolling it off.
    val startIndex = if (selectedIndex <= separatorAfterIndex) 0 else selectedIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(PanelWidth)
                .shadow(8.dp, RoundedCornerShape(PanelCornerRadius))
                .clip(RoundedCornerShape(PanelCornerRadius))
                .background(DialogGlassFill)
                .padding(vertical = 20.dp)
                .focusGroup()
        ) {
            LanguagePickerHeader(title = title)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .height(ListViewportHeight)
                    .fillMaxWidth()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = ContentInset),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(rows, key = { _, item -> item.key }) { index, row ->
                    LanguagePickRow(
                        label = row.label,
                        selected = index == selectedIndex,
                        onClick = {
                            onSelect(row)
                            onDismiss()
                        },
                        onClose = onDismiss,
                        focusRequester = if (index == selectedIndex) seedFocus else null,
                        blockUp = index == 0,
                        blockDown = index == rows.lastIndex
                    )
                    // Divider between the pinned quick-picks and the full A–Z list.
                    if (index == separatorAfterIndex && index < rows.lastIndex) {
                        LanguageListDivider()
                    }
                }
            }
        }
    }
    LaunchedEffect(selectedIndex) {
        if (rows.isNotEmpty()) {
            // Deep A–Z selections start off-screen; bring the row in so focus can attach.
            // Pinned selections are already visible at the top — don't scroll them away.
            if (selectedIndex > separatorAfterIndex) listState.scrollToItem(selectedIndex)
            seedFocus.requestFocusWhenAttached(maxFrames = 20)
        }
    }
}

@Composable
private fun LanguagePickerHeader(title: String) {
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

@Composable
private fun LanguageListDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInnerPadding, vertical = 6.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.14f))
    )
}

@Composable
private fun LanguagePickRow(
    label: String,
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
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
