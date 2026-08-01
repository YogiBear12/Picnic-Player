package app.picnic.player.ui.player.osd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val SidePanelEdgeInset = 28.dp

/** Hosts a right-edge panel of [width], sliding it just clear of the screen edge and back. */
@Composable
fun SidePanel(
    visible: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (active: Boolean) -> Unit
) {
    val distance = with(LocalDensity.current) { (width + SidePanelEdgeInset).roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally { distance } + fadeIn(),
        exit = slideOutHorizontally { distance } + fadeOut()
    ) {
        content(visible)
    }
}
