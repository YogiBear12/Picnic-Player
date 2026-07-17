package app.picnic.player.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

private val DialogGlassFill = Color(0xEA181E24)
private val PanelWidth = 280.dp
private val PanelCornerRadius = 20.dp

/**
 * Compact Actions picker for a customisable drawer destination (#88):
 * Pin/Unpin + Reorder only. Opened via [NavigationDrawerItem] long-click.
 *
 * Rows start disabled until the long-press Select is released (same guard as
 * [app.picnic.player.ui.common.GlobalContextMenuDialog]).
 */
@Composable
internal fun NavDestActionsDialog(
    dest: BrowseDest,
    pinned: Boolean,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onReorder: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val seedFocus = remember { FocusRequester() }
    var actionsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { seedFocus.requestFocus() }
        delay(1000L)
        actionsEnabled = true
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
                .padding(vertical = 16.dp, horizontal = 16.dp)
                .focusGroup()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp &&
                        event.nativeKeyEvent.keyCode in setOf(
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                        )
                    ) {
                        actionsEnabled = true
                    }
                    false
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionRow(
                text = if (pinned) "Unpin from sidebar" else "Pin to sidebar",
                enabled = actionsEnabled,
                focusRequester = seedFocus,
                onClick = {
                    if (pinned) onUnpin() else onPin()
                    onDismiss()
                }
            )
            ActionRow(
                text = "Reorder",
                enabled = actionsEnabled,
                focusRequester = null,
                onClick = {
                    onReorder()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.85f),
            focusedContentColor = Color.Black,
            pressedContentColor = Color.Black,
            disabledContentColor = Color.White.copy(alpha = 0.85f)
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
