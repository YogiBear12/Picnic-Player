package app.picnic.player.ui.search

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.ui.common.rememberTvTextEntry
import app.picnic.player.ui.common.tvTextEntry

@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = rememberTvTextEntry()
    val focused = entry.focused
    val focusedCallback by rememberUpdatedState(onFocused)

    LaunchedEffect(focused) { if (focused) focusedCallback() }

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
                    readOnly = entry.readOnly,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { entry.stop() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .tvTextEntry(entry)
                )
            }
        }
    }
}
