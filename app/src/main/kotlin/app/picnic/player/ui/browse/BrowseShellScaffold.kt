@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.tv.material3.ExperimentalTvMaterial3Api

/**
 * Browse content host. Navigation lives entirely in the left [BrowseSideNavDrawer],
 * so there is no top chrome — the content fills the full height and the
 * drawer is reached with D-pad Left (or Back).
 */
@Composable
internal fun BrowseShellScaffold(
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().focusGroup().focusRestorer()) {
        content()
    }
}
