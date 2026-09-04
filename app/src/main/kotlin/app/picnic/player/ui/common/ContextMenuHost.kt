package app.picnic.player.ui.common

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text

data class ContextMenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Stable
class ContextMenuFocus internal constructor() {
    internal var lastIndex by mutableIntStateOf(-1)
}

@Composable
fun rememberContextMenuFocus(): ContextMenuFocus = remember { ContextMenuFocus() }

private val MenuWidth = 380.dp
private val MenuMaxHeight = 460.dp
private val MenuCornerRadius = 28.dp

@Composable
fun ContextMenuHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContextMenuPanel(
    actions: List<ContextMenuAction>,
    openedByLongPress: Boolean = true,
    focus: ContextMenuFocus? = null
) {
    val guard = rememberLongPressGuard()
    val seedFocus = remember { FocusRequester() }
    val seedIndex = focus?.lastIndex
        ?.takeIf { actions.getOrNull(it)?.enabled == true }
        ?: actions.indexOfFirst { it.enabled }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .panelSurface(width = MenuWidth, corner = MenuCornerRadius)
            .heightIn(max = MenuMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .focusGroup()
            .then(if (openedByLongPress) Modifier.longPressGuard(guard) else Modifier)
    ) {
        actions.forEachIndexed { index, action ->
            ListItem(
                selected = false,
                enabled = action.enabled,
                onClick = {
                    focus?.lastIndex = index
                    action.onClick()
                },
                headlineContent = { Text(action.label, color = Color.White) },
                leadingContent = {
                    Icon(action.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == seedIndex) {
                            Modifier.focusRequester(seedFocus)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }

    LaunchedEffect(seedIndex) {
        if (seedIndex >= 0) seedFocus.requestFocusWhenAttached()
    }
}
