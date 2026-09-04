package app.picnic.player.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.playback.LanguagePickerRow
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowInnerPadding
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.RowCheck
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor

private val DialogGlassFill = Color(0xEA181E24)
private val PanelWidth = 360.dp
private val PanelCornerRadius = 20.dp
private val ListViewportHeight = 280.dp

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
            PanelHeader(title = title)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .height(ListViewportHeight)
                    .fillMaxWidth()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = PanelContentInset),
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
                    if (index == separatorAfterIndex && index < rows.lastIndex) {
                        LanguageListDivider()
                    }
                }
            }
        }
    }
    LaunchedEffect(selectedIndex) {
        if (rows.isNotEmpty()) {
            if (selectedIndex > separatorAfterIndex) listState.scrollToItem(selectedIndex)
            seedFocus.requestFocusWhenAttached(maxFrames = 20)
        }
    }
}

@Composable
private fun LanguageListDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelRowInnerPadding, vertical = 6.dp)
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
    PicnicListRow(
        focusRequester = focusRequester,
        keys = PanelRowKeys(blockUp = blockUp, blockDown = blockDown),
        onActivate = onClick,
        onClose = onClose
    ) { focused ->
        Text(
            text = label,
            color = rowPrimaryColor(focused),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        RowCheck(focused, visible = selected)
    }
}
