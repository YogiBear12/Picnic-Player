package app.picnic.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun SilentNavigationSounds() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.isSoundEffectsEnabled = false
        onDispose { view.isSoundEffectsEnabled = true }
    }
}
