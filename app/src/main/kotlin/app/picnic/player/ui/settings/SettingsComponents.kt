package app.picnic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

internal data class SettingsPanelFocus(
    val enterFr: FocusRequester,
    val leftFocus: FocusRequester,
    val onFocusChanged: (Boolean) -> Unit,
    val onRowFocused: (FocusRequester) -> Unit,
    val onHoldSelection: (Boolean) -> Unit
)

@Composable
internal fun ActionRow(
    label: String,
    leftFocus: FocusRequester?,
    value: String = "",
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    enterFr: FocusRequester? = null,
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    onFocused: (FocusRequester) -> Unit,
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val ownFocus = remember { FocusRequester() }
    val rowFocus = focusRequester ?: ownFocus
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .focusRequester(rowFocus)
            .then(if (enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
            .focusProperties {
                leftFocus?.let { left = it }
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        onActivate()
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(rowFocus)
            }
            .focusable(enabled),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = PicnicColors.OnDark,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = PicnicColors.Cyan)
        }
    }
}
