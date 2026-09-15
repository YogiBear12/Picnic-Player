package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PanelCornerRadius = 26.dp
internal val PanelEdgeInset = 24.dp
internal val PanelFloatingHeight = 428.dp
internal val PanelFadeLength = 22.dp
internal val PanelDividerColor = Color(0x1FFFFFFF)

private val PanelGlassTop = Color(0xF22A2E33)
private val PanelGlassBottom = Color(0xF2101214)
private val PanelHairlineTop = Color(0x3DFFFFFF)
private val PanelHairlineBottom = Color(0x0FFFFFFF)
private val PanelShadow = 20.dp

fun Modifier.panelGlass(corner: Dp = PanelCornerRadius): Modifier {
    val shape = RoundedCornerShape(corner)
    return shadow(PanelShadow, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(PanelGlassTop, PanelGlassBottom)))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(PanelHairlineTop, PanelHairlineBottom)),
            shape = shape
        )
}

fun Modifier.panelSurface(width: Dp, corner: Dp = PanelCornerRadius): Modifier = width(width).panelGlass(corner).padding(vertical = 20.dp)
