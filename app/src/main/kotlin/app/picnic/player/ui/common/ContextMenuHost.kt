package app.picnic.player.ui.common

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import app.picnic.player.ui.common.PanelWidth
import app.picnic.player.ui.common.PicnicDialog

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

private val MenuMaxHeight = 460.dp
private val MenuCornerRadius = 28.dp

private val SelectKeyCodes = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
)

@Composable
fun ContextMenuHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    PicnicDialog(onDismiss = onDismiss) {
        val openedAt = remember { SystemClock.uptimeMillis() }
        Box(
            Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                native.keyCode in SelectKeyCodes && native.downTime < openedAt
            }
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ContextMenuPanel(
    actions: List<ContextMenuAction>,
    focus: ContextMenuFocus? = null
) {
    val seedFocus = remember { FocusRequester() }
    val seedIndex = focus?.lastIndex
        ?.takeIf { actions.getOrNull(it)?.enabled == true }
        ?: actions.indexOfFirst { it.enabled }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .panelSurface(width = PanelWidth.Panel, corner = MenuCornerRadius)
            .heightIn(max = MenuMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .focusGroup()
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
