package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.picnic.player.ui.theme.PicnicColors

enum class PanelStyle {
    DIALOG,
    OSD
}

private val OsdCornerRadius = 20.dp

fun Modifier.panelSurface(
    style: PanelStyle,
    width: Dp,
    dialogCornerRadius: Dp
): Modifier {
    val dialog = style == PanelStyle.DIALOG
    val shape = RoundedCornerShape(if (dialog) dialogCornerRadius else OsdCornerRadius)
    return width(width)
        .then(if (dialog) Modifier.shadow(8.dp, shape) else Modifier)
        .clip(shape)
        .background(if (dialog) PicnicColors.GlassFill else PicnicColors.OsdGlassFill)
        .padding(vertical = 20.dp)
}
