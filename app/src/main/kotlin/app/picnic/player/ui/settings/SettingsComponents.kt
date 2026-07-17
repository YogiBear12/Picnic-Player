package app.picnic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.theme.PicnicColors

/**
 * Focusable settings action row shared by the Account and Requests panels.
 * [blockUp]/[blockDown] pin D-pad focus at panel edges so it cannot escape
 * into off-panel chrome.
 *
 * [enterFr] marks this row as the panel's entry target for D-pad Right / Select
 * from the category rail — pass it to whichever row is first on screen.
 */
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
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (enterFr != null) Modifier.focusRequester(enterFr) else Modifier)
            .focusProperties {
                leftFocus?.let { left = it }
                right = FocusRequester.Cancel
                if (blockUp) up = FocusRequester.Cancel
                if (blockDown) down = FocusRequester.Cancel
            }
            .padding(horizontal = 20.dp, vertical = 16.dp)
            // Only Select activates — Right is a direction, not a second Select (#138).
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
            .onFocusChanged { focused = it.isFocused }
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

/** Outlined text field with the settings focus/colour conventions. */
@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leftFocus: FocusRequester? = null,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = MaterialTheme.colorScheme.primary
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            imeAction = if (password) ImeAction.Done else ImeAction.Next,
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = PicnicColors.OnDarkMuted,
            focusedLabelColor = accent,
            unfocusedLabelColor = PicnicColors.OnDarkMuted,
            cursorColor = accent,
            focusedTextColor = PicnicColors.OnDark,
            unfocusedTextColor = PicnicColors.OnDark
        ),
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties { leftFocus?.let { left = it } }
            .onFocusChanged { state ->
                if (state.isFocused) keyboard?.show()
            }
    )
}
