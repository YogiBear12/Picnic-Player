package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.picnic.player.ui.theme.PicnicColors

internal val PanelGlassFill = Color(0xF2181E24)
internal val PanelCornerRadius = 20.dp
internal val PanelEdgeInset = 24.dp

fun Modifier.panelSurface(width: Dp, corner: Dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return width(width)
        .shadow(8.dp, shape)
        .clip(shape)
        .background(PicnicColors.GlassFill)
        .padding(vertical = 20.dp)
}
