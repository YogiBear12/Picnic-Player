package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val PicnicDialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)

val DialogCornerRadius = 20.dp

object PanelWidth {
    val Rail = 300.dp

    val Picker = 360.dp

    val Panel = 380.dp

    val Form = 420.dp

    val Reading = 600.dp
}

@Composable
fun PicnicDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = PicnicDialogProperties,
        content = content
    )
}
