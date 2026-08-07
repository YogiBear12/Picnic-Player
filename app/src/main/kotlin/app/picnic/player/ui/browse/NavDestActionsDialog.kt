package app.picnic.player.ui.browse

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import app.picnic.player.ui.common.ContextMenuAction
import app.picnic.player.ui.common.ContextMenuDialog

/**
 * Compact Actions picker for a customisable drawer destination (#88):
 * Pin/Unpin + Reorder only. Opened via [NavigationDrawerItem] long-click.
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
    val actions = listOf(
        ContextMenuAction(
            if (pinned) "Unpin from sidebar" else "Pin to sidebar",
            if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin
        ) {
            if (pinned) onUnpin() else onPin()
            onDismiss()
        },
        ContextMenuAction("Reorder", Icons.Default.SwapVert) {
            onReorder()
            onDismiss()
        }
    )

    ContextMenuDialog(actions = actions, onDismiss = onDismiss)
}
