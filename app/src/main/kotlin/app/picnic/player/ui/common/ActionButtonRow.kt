@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.picnic.player.ui.common

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Immutable
data class PageAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

internal val ActionButtonHeight = 40.dp

private val ActionButtonSpacing = 12.dp

@Composable
fun ActionButtonRow(
    actions: List<PageAction>,
    focus: RowFocusState,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(ActionButtonHeight)
            .focusProperties { enter = { focus.requesterAt(focus.focusedIndex) } }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(ActionButtonSpacing)
    ) {
        actions.forEachIndexed { index, action ->
            ExpandableButton(
                title = action.title,
                icon = action.icon,
                onClick = action.onClick,
                modifier = Modifier
                    .focusRequester(focus.requesterAt(index))
                    .onFocusChanged {
                        if (it.isFocused) {
                            focus.onItemFocused(index)
                            onFocused()
                        }
                    }
            )
        }
    }
}
