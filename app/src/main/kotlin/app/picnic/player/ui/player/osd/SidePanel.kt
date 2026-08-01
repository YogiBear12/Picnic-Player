package app.picnic.player.ui.player.osd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val SidePanelDimScrim = Color(0x52000000)
internal val SidePanelEdgeInset = 28.dp

/** Dim behind the side panels. Cross-fades in place so it does not travel with the panel. */
@Composable
fun SidePanelScrim(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SidePanelDimScrim)
        )
    }
}

/** Hosts a right-edge panel of [width], sliding it just clear of the screen edge and back. */
@Composable
fun SidePanel(
    visible: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val distance = with(LocalDensity.current) { (width + SidePanelEdgeInset).roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally { distance } + fadeIn(),
        exit = slideOutHorizontally { distance } + fadeOut()
    ) {
        content()
    }
}
