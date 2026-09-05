package app.picnic.player.ui.player

import androidx.compose.runtime.Composable
import app.picnic.player.ui.common.ClearDialogDim
import app.picnic.player.ui.common.PicnicDialog
import app.picnic.player.ui.common.SilentNavigationSounds

@Composable
fun PlayerDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    PicnicDialog(onDismiss = onDismissRequest) {
        ClearDialogDim()
        SilentNavigationSounds()
        content()
    }
}
