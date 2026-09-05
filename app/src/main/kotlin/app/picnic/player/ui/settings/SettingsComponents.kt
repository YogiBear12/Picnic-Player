package app.picnic.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.GlassRow
import app.picnic.player.ui.theme.PicnicColors

internal val SettingsTopInset = 59.dp

internal val SettingsBottomInset = 27.dp

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
    val ownFocus = remember { FocusRequester() }
    val rowFocus = focusRequester ?: ownFocus
    GlassRow(
        onClick = onActivate,
        enabled = enabled,
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .focusRequester(rowFocus)
            .then(if (enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
            .focusProperties {
                canFocus = enabled
                leftFocus?.let { left = it }
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .onFocusChanged { if (it.isFocused) onFocused(rowFocus) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
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
}
