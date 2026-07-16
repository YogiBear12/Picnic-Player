package app.picnic.player.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Search input pill. On TV the field is read-only until Select is pressed — D-pad
 * moves focus straight through it; Select opens the IME, Back closes the IME first
 * and only then bubbles up to the shell's ladder.
 */
@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var imeActive by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    // Back while typing: dismiss the IME, keep focus on the field. The shell ladder
    // (focus to tab) only sees Back once the IME is gone.
    BackHandler(enabled = imeActive) {
        imeActive = false
        keyboard?.hide()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(
                if (focused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f),
                CircleShape
            )
            .border(
                width = 2.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(end = 16.dp)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search movies and shows",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    readOnly = !imeActive,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        imeActive = false
                        keyboard?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (state.isFocused) onFocused() else imeActive = false
                        }
                        .onPreviewKeyEvent { event ->
                            val select = event.key == Key.DirectionCenter || event.key == Key.Enter
                            if (event.type == KeyEventType.KeyUp && select && !imeActive) {
                                imeActive = true
                                keyboard?.show()
                                true
                            } else {
                                false
                            }
                        }
                )
            }
        }
    }
}
