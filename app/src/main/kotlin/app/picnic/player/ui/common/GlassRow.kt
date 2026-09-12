package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import app.picnic.player.ui.theme.PicnicColors

val GlassRowShape = RoundedCornerShape(12.dp)
val GlassRowIdleFill = Color.White.copy(alpha = 0.04f)
val GlassRowFocusedFill = Color.White.copy(alpha = 0.14f)

fun Modifier.glassRow(focused: Boolean): Modifier = clip(GlassRowShape).background(if (focused) GlassRowFocusedFill else GlassRowIdleFill)

@Composable
fun GlassRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = GlassRowShape,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GlassRowIdleFill,
            focusedContainerColor = GlassRowFocusedFill,
            pressedContainerColor = GlassRowFocusedFill,
            disabledContainerColor = GlassRowIdleFill,
            contentColor = PicnicColors.OnDark,
            focusedContentColor = PicnicColors.OnDark,
            pressedContentColor = PicnicColors.OnDark,
            disabledContentColor = PicnicColors.OnDarkMuted
        ),
        border = flatSurfaceBorder(),
        content = content
    )
}
