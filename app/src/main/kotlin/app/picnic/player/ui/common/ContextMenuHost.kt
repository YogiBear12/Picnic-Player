package app.picnic.player.ui.common

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
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

private val MenuWidth = 380.dp
private val MenuMaxHeight = 460.dp
private val MenuCornerRadius = 28.dp

@Composable
fun ContextMenuHost(
    actions: List<ContextMenuAction>,
    onDismiss: () -> Unit,
    openedByLongPress: Boolean = true,
    subPanel: (@Composable () -> Unit)? = null,
    onCloseSubPanel: () -> Unit = {}
) {
    BackHandler { if (subPanel != null) onCloseSubPanel() else onDismiss() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (subPanel != null) {
            subPanel()
        } else {
            ContextMenuPanel(actions = actions, openedByLongPress = openedByLongPress)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContextMenuPanel(
    actions: List<ContextMenuAction>,
    openedByLongPress: Boolean = true
) {
    val guard = rememberLongPressGuard()
    val firstFocus = remember { FocusRequester() }
    val firstEnabled = actions.indexOfFirst { it.enabled }

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
                onClick = action.onClick,
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
                        if (index == firstEnabled) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }

    LaunchedEffect(actions.map { it.label }) {
        if (firstEnabled >= 0) firstFocus.requestFocusWhenAttached()
    }
}
