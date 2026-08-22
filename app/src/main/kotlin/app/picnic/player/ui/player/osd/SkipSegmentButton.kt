package app.picnic.player.ui.player.osd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.picnic.player.data.playback.SegmentKind

private val SkipShadowElevation = 12.dp

@Composable
fun SkipSegmentButton(
    kind: SegmentKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (kind) {
        SegmentKind.INTRO -> "Skip Intro"
        SegmentKind.RECAP -> "Skip Recap"
        SegmentKind.OUTRO -> "Skip Outro"
        SegmentKind.PREVIEW -> "Skip Preview"
        SegmentKind.COMMERCIAL -> "Skip Ad"
    }

    Box(Modifier.shadow(SkipShadowElevation, CircleShape, clip = false)) {
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
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
