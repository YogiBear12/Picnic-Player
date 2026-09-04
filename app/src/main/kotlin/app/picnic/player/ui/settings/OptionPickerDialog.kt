package app.picnic.player.ui.settings

import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.PanelContentInset
import app.picnic.player.ui.common.PanelHeader
import app.picnic.player.ui.common.PanelRowKeys
import app.picnic.player.ui.common.PicnicListRow
import app.picnic.player.ui.common.RowCheck
import app.picnic.player.ui.common.requestFocusWhenAttached
import app.picnic.player.ui.common.rowPrimaryColor
import coil3.compose.AsyncImage

private val DialogGlassFill = Color(0xEA181E24)
private val PanelWidth = 360.dp
private val PanelCornerRadius = 20.dp
private val ListMaxHeight = 320.dp

data class PickerOption(
    val label: String,
    val selected: Boolean,
    val icon: Drawable? = null,
    val onSelect: () -> Unit
)

@Composable
fun OptionPickerDialog(
    title: String,
    options: List<PickerOption>,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val listState = rememberLazyListState()
    val seedFocus = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)

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
            PanelHeader(title)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = ListMaxHeight)
                    .fillMaxWidth()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = PanelContentInset),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    PickerOptionRow(
                        option = option,
                        onClick = {
                            option.onSelect()
                            onDismiss()
                        },
                        onClose = onDismiss,
                        focusRequester = if (index == selectedIndex) seedFocus else null,
                        blockUp = index == 0,
                        blockDown = index == options.lastIndex
                    )
                }
            }
        }
    }
    LaunchedEffect(selectedIndex) {
        if (options.isNotEmpty()) {
            listState.scrollToItem(selectedIndex)
            seedFocus.requestFocusWhenAttached(maxFrames = 20)
        }
    }
}

@Composable
private fun PickerOptionRow(
    option: PickerOption,
    onClick: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester?,
    blockUp: Boolean,
    blockDown: Boolean
) {
    PicnicListRow(
        focusRequester = focusRequester,
        keys = PanelRowKeys(blockRight = true, blockUp = blockUp, blockDown = blockDown),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        onActivate = onClick,
        onClose = onClose
    ) { focused ->
        if (option.icon != null) {
            AsyncImage(
                model = option.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = option.label,
            color = rowPrimaryColor(focused),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        RowCheck(focused, visible = option.selected)
    }
}
