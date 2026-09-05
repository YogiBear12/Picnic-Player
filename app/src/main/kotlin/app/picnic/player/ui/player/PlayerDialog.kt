package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.picnic.player.ui.common.ClearDialogDim
import app.picnic.player.ui.common.SilentNavigationSounds

@Composable
fun PlayerDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ClearDialogDim()
        SilentNavigationSounds()
        content()
    }
}
