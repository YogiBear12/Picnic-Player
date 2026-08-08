package app.picnic.player.ui.browse

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun BrowseShellScaffold(
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().focusGroup()) {
        content()
    }
}
