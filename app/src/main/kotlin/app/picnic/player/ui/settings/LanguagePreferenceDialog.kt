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
import app.picnic.player.data.playback.CulturePickerOption
import app.picnic.player.data.playback.languageMatches
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
 * Scrollable culture picker for Preferred audio / subtitle language.
 * Unspecified is always first; remaining rows are Jellyfin DisplayNames.
 */
@Composable
internal fun LanguagePreferenceDialog(
    title: String,
    options: List<CulturePickerOption>,
    selectedLanguageCode: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val listState = rememberLazyListState()
    val seedFocus = remember { FocusRequester() }
    val selectedIndex = remember(options, selectedLanguageCode) {
        options.indexOfFirst { option ->
            when {
                option.languageCode == null -> selectedLanguageCode.isNullOrBlank()
                selectedLanguageCode.isNullOrBlank() -> false
                else -> languageMatches(option.languageCode, selectedLanguageCode)
            }
        }.takeIf { it >= 0 } ?: 0
    }

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
                itemsIndexed(options, key = { _, item -> item.languageCode ?: "unspecified" }) { index, option ->
                    LanguagePickRow(
                        label = option.displayName,
                        selected = index == selectedIndex,
                        onClick = {
                            onSelect(option.languageCode)
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
