package app.picnic.player.ui.settings

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.picnic.player.ui.common.requestFocusWhenAttached
import coil3.compose.AsyncImage

/** Align with SeasonRequestDialog / TrackPanel / LanguagePreferenceDialog glass chrome. */
private val DialogGlassFill = Color(0xEA181E24)
private val PanelWidth = 360.dp
private val PanelCornerRadius = 20.dp
private val ContentInset = 16.dp
private val RowInnerPadding = 14.dp
private val RowCornerRadius = 10.dp
private val ListMaxHeight = 320.dp

/** One choice in an [OptionPickerDialog]. [icon] draws a small leading image (app pickers). */
data class PickerOption(
    val label: String,
    val selected: Boolean,
    val icon: Drawable? = null,
    val onSelect: () -> Unit
)

/**
 * Standard multi-option picker for settings rows with more than On/Off — the
 * glass panel, focus-seeded selected row, and check-mark chrome shared with
 * [LanguagePreferenceDialog]. Selecting a row applies it and closes the dialog.
 */
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
            PickerHeader(title)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = ListMaxHeight)
                    .fillMaxWidth()
                    .focusGroup(),
                contentPadding = PaddingValues(horizontal = ContentInset),
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
private fun PickerHeader(title: String) {
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
private fun PickerOptionRow(
    option: PickerOption,
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
                right = FocusRequester.Cancel
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (option.icon != null) {
            AsyncImage(
                model = option.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = option.label,
            color = if (focused) Color.Black else Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (option.selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = if (focused) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
