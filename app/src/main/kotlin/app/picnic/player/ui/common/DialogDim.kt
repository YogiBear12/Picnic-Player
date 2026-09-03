package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun ClearDialogDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val previous = window.attributes.dimAmount
        window.setDimAmount(0f)
        onDispose { window.setDimAmount(previous) }
    }
}
