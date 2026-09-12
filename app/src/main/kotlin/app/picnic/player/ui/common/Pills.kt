package app.picnic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun Pill(text: String, color: Color, fill: Color, horizontal: Dp, vertical: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fill)
            .padding(horizontal = horizontal, vertical = vertical)
    )
}

@Composable
internal fun CountPill(count: Int, accent: Color) {
    Pill(count.toString(), accent, accent.copy(alpha = 0.16f), horizontal = 8.dp, vertical = 2.dp)
}
