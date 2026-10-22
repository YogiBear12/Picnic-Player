package app.picnic.player.ui.player.osd

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.playback.SegmentKind

private const val CountdownFillAlpha = 0.35f

@Composable
fun SkipSegmentButton(
    kind: SegmentKind,
    elapsed: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xC0181E24),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        val fill = LocalContentColor.current.copy(alpha = CountdownFillAlpha)
        Text(
            text = skipLabel(kind),
            modifier = Modifier
                .clip(CircleShape)
                .drawBehind {
                    drawRect(fill, size = Size(size.width * elapsed(), size.height))
                }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
}
