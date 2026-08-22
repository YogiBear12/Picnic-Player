package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val GlassRowShape = RoundedCornerShape(12.dp)
val GlassRowIdleFill = Color.White.copy(alpha = 0.04f)
val GlassRowFocusedFill = Color.White.copy(alpha = 0.14f)

fun Modifier.glassRow(focused: Boolean): Modifier = clip(GlassRowShape).background(if (focused) GlassRowFocusedFill else GlassRowIdleFill)

fun Modifier.glassRowSelectable(focused: Boolean): Modifier = clip(GlassRowShape).background(if (focused) Color.White else GlassRowIdleFill)
